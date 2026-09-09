package com.stratio.discoverycicd.config

import com.stratio.discoverycicd.utils.{Concurrently, Logging}
import com.typesafe.config.ConfigFactory
import pureconfig.ConfigSource
import enumeratum._
import pureconfig.generic.auto._
import pureconfig.module.enumeratum._
import scala.util.Try


object ConfigFacade extends Logging {

  lazy val config = ConfigFactory.load()

  sealed trait AuthMode extends EnumEntry

  object AuthMode extends Enum[AuthMode]{
    val values = findValues
    case object NoAuth extends AuthMode
    case object MutualTLS extends AuthMode
  }

  private val ServerPath = "discovery-cicd-server"
  private val ApiClientPath = "discovery-cicd-api-client"
  private val JobsPath = "discovery-cicd-jobs"

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
                             timeoutImport: String,
                             parallelWrites: Int = Concurrently.WriteRequests) {
    import scala.concurrent.duration._
    lazy val getTimeoutExport: Duration = Try(Duration(timeoutExport)).getOrElse(300 seconds)
    lazy val getTimeoutImport: Duration = Try(Duration(timeoutImport)).getOrElse(300 seconds)

    /**
     * How many questions, dashboards and subscriptions an import creates at a time. Anything below
     * one would create none at all, so it is taken as one, which is the old sequential import and
     * a legitimate thing to ask for; a number far above what metabase can take only makes the
     * import slower, so it is capped and said out loud instead of being obeyed silently.
     */
    lazy val getParallelWrites: Int =
      if (parallelWrites < 1) {
        log.warn(s"'$ParallelWritesEnvVar' is set to $parallelWrites, which would create nothing at " +
          s"all, so one question at a time is used instead")
        1
      } else if (parallelWrites > MaxParallelWrites) {
        log.warn(s"'$ParallelWritesEnvVar' is set to $parallelWrites, which is more than metabase can " +
          s"take from a single import, so it is capped at $MaxParallelWrites")
        MaxParallelWrites
      } else parallelWrites
  }

  private[config] val ParallelWritesEnvVar = "DISCOVERY_CICD_IMPORT_PARALLEL_WRITES"

  /** Not a measurement, a guard rail: metabase already saturates at 4 reads at a time (see
    * [[Concurrently.CardRequests]]) and it shares the two cores of the pod with us, so a number in
    * the hundreds can only be a typo */
  private[config] val MaxParallelWrites = 32

  /**
   * The asynchronous exports and imports. `maxConcurrent` is 1 by default because the pod is given
   * 2 cores and an export of a big collection already uses 1.8 of them: a second one at the same
   * time makes both slower and neither finishes any sooner.
   *
   * Nothing in `workDir` survives a restart, and it is on the ephemeral filesystem of the pod (there
   * is no volume in the deployment), so `retention` and `maxStoredResults` are what keep a handful
   * of exports of a couple hundred MB each from filling it up.
   */
  case class JobsConfig(maxConcurrent: Int,
                        maxQueued: Int,
                        workDir: String,
                        retention: String,
                        maxStoredResults: Int) {
    import scala.concurrent.duration._
    lazy val getRetention: Duration = Try(Duration(retention)).getOrElse(24 hours)
  }

  lazy val serverConfig = ConfigSource.fromConfig(config.getConfig(ServerPath)).loadOrThrow[ServerConfig]
  lazy val apiClientConfig = ConfigSource.fromConfig(config.getConfig(ApiClientPath)).loadOrThrow[ApiClientConfig]
  lazy val jobsConfig = ConfigSource.fromConfig(config.getConfig(JobsPath)).loadOrThrow[JobsConfig]
}
