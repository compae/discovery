package com.stratio.discoverycicd.runners

import java.io.FileInputStream
import java.security.KeyStore

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.server.Route
import akka.stream.TLSClientAuth
import com.stratio.discoverycicd.config.ConfigFacade
import com.stratio.discoverycicd.config.ConfigFacade.AuthMode.{MutualTLS, NoAuth}
import com.stratio.discoverycicd.routes.ServerRoutes
import javax.net.ssl.SSLContext
import org.apache.http.ssl.SSLContextBuilder

import scala.collection.immutable
import scala.util.{Failure, Success}

object ServerRunner {

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
    import com.stratio.discoverycicd.config.ConfigFacade._
    import system.executionContext
    import ConnectionContextUtils._

    val futureBinding = Http().bindAndHandle(routes, serverConfig.hostname, serverConfig.port, connectionContext)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
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
