package com.stratio.discoverycicd.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, ExceptionHandler, Route}
import akka.stream.Materializer
import com.stratio.discoverycicd.config.ConfigFacade._
import com.stratio.discoverycicd.jobs.JobManager
import com.stratio.discoverycicd.progress.ProgressReporter
import com.stratio.discoverycicd.utils.ApiClient
import com.stratio.discoverycicd.{Exporter, Importer}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.DefaultFormats
import com.stratio.discoverycicd.utils.Logging
import com.stratio.discoverycicd.exceptions.{CustomException, PayloadTooLargeException, UnauthorizedException, UnhandledException}

import scala.util.{Failure, Success}

class ServerRoutes(
  jobs: JobManager,
  apiClientFor: String => ApiClient = ServerRoutes.apiClientFromConfig
)(implicit val system: ActorSystem[_]) extends Json4sSupport {

  implicit val formats = DefaultFormats
  implicit val serialization = org.json4s.native.Serialization

  /** Built from the typed system, and not from a classic one left in scope, so that it cannot get in
    * the way of the conversion of a route into the flow the server is bound with */
  implicit val materializer: Materializer = Materializer(system)

  import ServerRoutes._

  /** What a body may weigh, from the configuration of the system these routes were built with, so
    * that it is the same number the server gives its own parser (see [[ImportUploads.maxBodySizeOf]]) */
  private val maxBodySize: Long = ImportUploads.maxBodySizeOf(system.settings.config)

  private val asyncRoutes = new AsyncRoutes(jobs, apiClientFor, maxBodySize)

  val apiRoutes: Route =
    handleExceptions(exceptionHandler) {
      // first, so that a path under '/async' is never taken for one of the synchronous ones
      asyncRoutes.routes ~
        (path(Segment / ExportSegment / Segment) & get) { (_, id) =>
          authenticatedSuperuser(apiClientFor) { api =>
            withRequestTimeout(apiClientConfig.getTimeoutExport, configurableTimeoutHandler(ExportSegment)) {
              val exporter = new Exporter(api)
              complete(exporter.exportCollection(BigInt(id)))
            }
          }
        } ~
        (path(Segment / ImportSegment) & post) { _ =>
          // It seems that unmarshalling directly to BigInt does not work
          parameter(ParentId.as[Long].?) { parentId =>
            authenticatedSuperuser(apiClientFor) { api =>
              // The body of an import is taken the same way the asynchronous endpoint takes it, and
              // for the same two reasons. The timeout, because this used to sit inside an
              // 'entity(as[ExportedCollection])' and so did not govern the reading of the body,
              // which is the part that takes the time: the configured import timeout was ignored and
              // the 20s default of the server answered a 126MB import with a 503 while the import
              // itself went on to the end. And the file, because reading that document whole needs
              // more heap than this process has (see ImportUploads)
              ImportUploads.bigUploads(maxBodySize) {
                extractRequestEntity { entity =>
                  val uploaded = jobs.workDir.resolve(s"${jobs.newId()}${ImportUploads.UploadSuffix}")
                  onComplete(ImportUploads.receive(entity, uploaded)) {
                    case Success(_) =>
                      try {
                        val importer = new Importer(api, parallelWrites = apiClientConfig.getParallelWrites)
                        importer.importCollection(
                          ImportUploads.read(uploaded, ProgressReporter.NoOp),
                          parentId.map(BigInt.apply)
                        )
                        complete(StatusCodes.OK)
                      } finally ImportUploads.deleteQuietly(uploaded)

                    // the upload itself failed: the caller hung up halfway through it, or the
                    // timeout above went off while it was still coming in
                    case Failure(e) =>
                      ImportUploads.deleteQuietly(uploaded)
                      failWith(e)
                  }
                }
              }
            }
          }
        }
    }
}

object ServerRoutes extends Logging {

  val ExportSegment = "export"
  val ImportSegment = "import"
  val ParentId = "parentId"
  val SessionIdHeaderName = "x-metabase-session"

  /** Only to be named in the message of a 413, so that it says what to raise. */
  val MaxBodySizeEnvVar = "DISCOVERY_CICD_API_MAX_BODY_SIZE"

  def unauthorizedReason(sessionId: String): String = s"The user associated with the sessionId: $sessionId is not a superuser"

  /**
   * The session of the caller, checked to belong to a superuser, as a client of the metabase api.
   *
   * The same check the two synchronous endpoints have always made, in one place so that the
   * asynchronous ones cannot drift from it. Note that it asks metabase who the caller is on every
   * request, the status endpoint included: that is one cheap 'GET user/current' per poll
   */
  def authenticatedSuperuser(apiClientFor: String => ApiClient): Directive1[ApiClient] =
    headerValueByName(SessionIdHeaderName) map { sessionId =>
      val api = apiClientFor(sessionId)
      if (api.user.is_superuser) api
      else throw UnauthorizedException(unauthorizedReason(sessionId))
    }

  /** The metabase this instance sits in front of. A parameter and not something the routes reach for
    * themselves so that a test can point them at a stub */
  def apiClientFromConfig(sessionId: String): ApiClient =
    new ApiClient(
      apiClientConfig.metabaseUrl,
      sessionId,
      apiClientConfig.allowUnsafeSsl,
      apiClientConfig.timeoutMetabase
    )

  def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case error: CustomException =>
        complete(HttpResponse(error.statusCode, entity = HttpEntity(MediaTypes.`application/json`, error.toJson)))

      // the body did not fit in what 'DISCOVERY_CICD_API_MAX_BODY_SIZE' allows. Translated instead
      // of being left to the catch-all below, which would call it an unexpected error and answer a
      // 500 to what the nginx in front answers a 413 to
      case error if bodyTooLarge(error).isDefined =>
        val tooLarge = tooLargeError(bodyTooLarge(error).get)
        log.error(tooLarge.msg, error)
        complete(HttpResponse(tooLarge.statusCode, entity = HttpEntity(MediaTypes.`application/json`, tooLarge.toJson)))

      case error =>
        val exception = UnhandledException(error)
        log.error(exception.msg, error)
        complete(HttpResponse(exception.statusCode, entity = HttpEntity(MediaTypes.`application/json`, exception.toJson)))
    }

  /**
   * The complaint about the size of a body, wherever it is buried.
   *
   * The body of an import is not read by a directive but written to a file by a stream of our own
   * (see [[ImportUploads.receive]]), and akka reports anything that goes wrong there wrapped in an
   * `IOOperationIncompleteException`. So matching the exception itself is not enough: what arrives
   * at the handler is the wrapper, and without looking into it an oversized body was answered with
   * a 500 saying 'unexpected exception'.
   */
  private[routes] def bodyTooLarge(error: Throwable): Option[EntityStreamSizeException] =
    // bounded on purpose: a chain of causes is not supposed to be circular, and this runs while a
    // request is already failing
    Iterator.iterate(Option(error))(_.flatMap(cause => Option(cause.getCause)))
      .take(MaxCauseDepth)
      .flatten
      .collectFirst { case tooLarge: EntityStreamSizeException => tooLarge }

  private val MaxCauseDepth = 10

  private def tooLargeError(tooLarge: EntityStreamSizeException): PayloadTooLargeException =
    PayloadTooLargeException(
      s"The body of this request, or what it unpacks to, is bigger than the ${tooLarge.limit} bytes " +
        s"allowed. Raise '$MaxBodySizeEnvVar' and the 'NGINX_CLIENT_MAX_BODY_SIZE' of the proxy in " +
        "front of it to the same value, or upload the export gzipped with the header " +
        "'Content-Encoding: gzip', which is how a big one is meant to travel"
    )

  // actionMsg should be imoprt/export
  def configurableTimeoutHandler(actionMsg: String): HttpRequest => HttpResponse = _ => {
    HttpResponse(
      StatusCodes.ServiceUnavailable,
      entity = s"The server was not able to produce a timely response to your request.\r\nPlease try increasing the $actionMsg timeout in CCT!"
    )
  }

}
