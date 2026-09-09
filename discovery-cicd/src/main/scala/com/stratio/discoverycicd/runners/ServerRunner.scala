package com.stratio.discoverycicd.runners

import java.io.FileInputStream
import java.security.KeyStore

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.TLSClientAuth
import com.stratio.discoverycicd.config.ConfigFacade
import com.stratio.discoverycicd.config.ConfigFacade.AuthMode.{MutualTLS, NoAuth}
import com.stratio.discoverycicd.jobs.{JobManager, JobRegistry}
import com.stratio.discoverycicd.routes.ServerRoutes
import com.stratio.discoverycicd.utils.{Concurrently, Logging}
import javax.net.ssl.SSLContext
import org.apache.http.ssl.SSLContextBuilder

import scala.collection.immutable
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object ServerRunner extends Logging {

  import com.stratio.discoverycicd.config.ConfigFacade._

  private lazy val largestRequestTimeout: Duration =
    apiClientConfig.getTimeoutExport.max(apiClientConfig.getTimeoutImport)

  def run(args: Array[String]): Unit = {
    allowMetabaseConnectionsToBeReused()
    val jobs = buildJobManager()
    // the jvm is stopped by s6, not by this process, so this is the only chance to take the result
    // files with us. They are ephemeral anyway: the work directory is emptied again on start up
    sys.addShutdownHook(jobs.close())

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val routes = new ServerRoutes(jobs)(context.system)
      startHttpServer(routes.apiRoutes, context.system)
      Behaviors.empty
    }
    ActorSystem[Nothing](rootBehavior, "DiscoveryServer")
  }

  /**
   * Keeps the connections to metabase alive across the requests an export or an import makes in
   * parallel.
   *
   * `scalaj-http` is `HttpURLConnection` underneath, and its keep-alive cache holds five
   * connections per destination by default. An export already asks for metadata eight at a time
   * (see [[Concurrently.MetadataRequests]]) and an import now writes several at a time on top of
   * that, so past five every extra request pays a TCP and a TLS handshake it did not need to, and
   * a good part of what the parallelism buys is spent there.
   *
   * Set here and not in the `java` command line of the service so that it follows whatever the
   * parallelism is configured to be. An explicit `-Dhttp.maxConnections` in
   * `DISCOVERY_CICD_JAVA_OPTIONS` still wins: whoever put it there meant it.
   */
  private def allowMetabaseConnectionsToBeReused(): Unit = {
    val Property = "http.maxConnections"
    val needed = Concurrently.MetadataRequests max apiClientConfig.getParallelWrites
    Option(System.getProperty(Property)) match {
      case Some(configured) =>
        log.info(s"Leaving '$Property' at the configured $configured; this import and export need $needed")
      case None =>
        val _ = System.setProperty(Property, needed.toString)
        log.debug(s"'$Property' set to $needed, so that the requests going to metabase at the same " +
          s"time reuse their connections instead of opening one each")
    }
  }

  private def buildJobManager(): JobManager = {
    val config = jobsConfig
    log.info(s"Asynchronous exports and imports: up to ${config.maxConcurrent} at a time with " +
      s"${config.maxQueued} waiting, results in '${config.workDir}' for ${config.retention}, " +
      s"keeping the last ${config.maxStoredResults}")
    JobManager(new JobRegistry(config), config.maxConcurrent, config.maxQueued)
  }

  private def startHttpServer(routes: Route, system: ActorSystem[_]): Unit = {
    implicit val classicSystem: akka.actor.ActorSystem = system.toClassic
    import system.executionContext
    import ConnectionContextUtils._

    val serverIdleTimeout = getServerIdleTiemeout
    log.debug(s"The 'akka.http.server.idle-timeout' is set to $serverIdleTimeout")

    val serverSettings = {
      val defaultServerSettings = ServerSettings(config)
      defaultServerSettings.withTimeouts(defaultServerSettings.withIdleTimeout(serverIdleTimeout))
    }

    logTimeoutConfigurationError()

    val futureBinding = Http().bindAndHandle(routes, serverConfig.hostname, serverConfig.port, connectionContext, settings = serverSettings)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  private def getServerIdleTiemeout: Duration = {
    val configuredIdleTimeout = Duration.fromNanos(config.getDuration("akka.http.server.idle-timeout").toNanos) // Java to Scala duration conversion
    if (largestRequestTimeout.gt(configuredIdleTimeout))
      largestRequestTimeout.plus(5 seconds)
    else
      configuredIdleTimeout
  }

  private def logTimeoutConfigurationError(): Unit = {
    val proxyReadTimeoutOpt: Option[Duration] =
      Try(sys.env.get("PROXY_READ_TIMEOUT").map(_.toInt seconds)).toOption.flatten

    proxyReadTimeoutOpt.foreach { proxyReadTimeout =>
      if (largestRequestTimeout.gt(proxyReadTimeout)) {
        log.warn(
          s"The PROXY_READ_TIMEOUT is lower than the configured request timeout. " +
          s"Please, increase the proxy timeout in CCT to avoid errors; otherwise, the actual timeout will be PROXY_READ_TIMEOUT seconds"
        )
      }
    }
  }

}

object ConnectionContextUtils {

  val ProtocolVersion = "TLSv1.2"

  val EnabledCipherSuites = Option(immutable.Seq(
    "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384", "TLS_RSA_WITH_AES_256_CBC_SHA256",
    "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384", "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA",
    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA"))

  val EnabledProtocols = Option(immutable.Seq("TLSv1.2"))

  protected[runners] def connectionContext(): ConnectionContext = {
    ConfigFacade.serverConfig.authMode match {
      case NoAuth =>
        ConnectionContext.noEncryption
      case MutualTLS =>
        ConnectionContext.https(
          sslContext = getSSLContextV2,
          enabledCipherSuites = EnabledCipherSuites,
          enabledProtocols = EnabledProtocols,
          clientAuth = Option(TLSClientAuth.Need)
        )
    }
  }

  private[this] def getSSLContextV2(): SSLContext = {
    import com.stratio.discoverycicd.config.ConfigFacade._
    val (keyStoreFile, keyStorePassword) = (serverConfig.keystorePath, System.getenv(serverConfig.keystorePasswordEnv))
    val (trustStoreFile, trustStorePassword) = (serverConfig.keystorePath, System.getenv(serverConfig.truststorePasswordEnv))

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(new FileInputStream(keyStoreFile), keyStorePassword.toCharArray)

    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
    trustStore.load(new FileInputStream(trustStoreFile), trustStorePassword.toCharArray)

    new SSLContextBuilder()
      .setProtocol(ProtocolVersion)
      .loadTrustMaterial(trustStore, null)
      .loadKeyMaterial(keyStore, keyStorePassword.toCharArray)
      .build()
  }
}
