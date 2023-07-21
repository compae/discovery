package com.stratio.discoverycicd.config

import com.typesafe.config.ConfigFactory
import pureconfig.ConfigSource
import enumeratum._
import pureconfig.generic.auto._
import pureconfig.module.enumeratum._
import scala.util.Try


object ConfigFacade {

  lazy val config = ConfigFactory.load()

  sealed trait AuthMode extends EnumEntry

  object AuthMode extends Enum[AuthMode]{
    val values = findValues
    case object NoAuth extends AuthMode
    case object MutualTLS extends AuthMode
  }

  private val ServerPath = "discovery-cicd-server"
  private val ApiClientPath = "discovery-cicd-api-client"

  case class ServerConfig(hostname: String,
                          port: Int,
                          authMode: AuthMode,
                          keystorePath: String,
                          keystorePasswordEnv: String,
                          truststorePath: String,
                          truststorePasswordEnv: String)

  case class ApiClientConfig(metabaseUrl: String,
                             allowUnsafeSsl: Boolean,
                             timeoutMetabase: String,
                             timeoutExport: String,
                             timeoutImport: String) {
    import scala.concurrent.duration._
    lazy val getTimeoutExport: Duration = Try(Duration(timeoutExport)).getOrElse(60 seconds)
    lazy val getTimeoutImport: Duration = Try(Duration(timeoutImport)).getOrElse(200 seconds)
  }

  lazy val serverConfig = ConfigSource.fromConfig(config.getConfig(ServerPath)).loadOrThrow[ServerConfig]
  lazy val apiClientConfig = ConfigSource.fromConfig(config.getConfig(ApiClientPath)).loadOrThrow[ApiClientConfig]
}
