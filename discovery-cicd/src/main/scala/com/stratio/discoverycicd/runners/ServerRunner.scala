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
import com.stratio.discoverycicd.routes.ServerRoutes
import com.stratio.discoverycicd.utils.Logging
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
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val routes = new ServerRoutes()(context.system)
      startHttpServer(routes.apiRoutes, context.system)
      Behaviors.empty
    }
    ActorSystem[Nothing](rootBehavior, "DiscoveryServer")
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
