package com.stratio.discoverycicd.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import com.stratio.discoverycicd.config.ConfigFacade._
import com.stratio.discoverycicd.model.ExportedCollection
import com.stratio.discoverycicd.utils.ApiClient
import com.stratio.discoverycicd.{Exporter, Importer}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.DefaultFormats
import com.stratio.discoverycicd.utils.Logging
import com.stratio.discoverycicd.exceptions.CustomException

class ServerRoutes()(implicit val system: ActorSystem[_]) extends Json4sSupport {

  implicit val formats = DefaultFormats
  implicit val serialization = org.json4s.native.Serialization

  import ServerRoutes._

  val apiRoutes: Route =
    handleExceptions(exceptionHandler) {
      (path(Segment / ExportSegment / Segment) & get) { (_, id) =>
        headerValueByName(SessionIdHeaderName) { sessionId =>
          val api =
            new ApiClient(
              apiClientConfig.metabaseUrl,
              sessionId,
              apiClientConfig.allowUnsafeSsl,
              apiClientConfig.timeout
            )
          if (api.user.body.is_superuser) {
            val exporter = new Exporter(api)
            complete(exporter.exportCollection(BigInt(id)))
          } else {
            complete(StatusCodes.Unauthorized, unauthorizedReason(sessionId))
          }
        }
      } ~
        (path(Segment / ImportSegment) & post) { _ =>
          // It seems that unmarshalling directly to BigInt does not work
          parameter(ParentId.as[Long].?) { parentId =>
            headerValueByName(SessionIdHeaderName) { sessionId =>
              entity(as[ExportedCollection]) { collection =>
                val api =
                  new ApiClient(
                    apiClientConfig.metabaseUrl,
                    sessionId,
                    apiClientConfig.allowUnsafeSsl,
                    apiClientConfig.timeout
                  )
                if (api.user.body.is_superuser) {
                  val importer = new Importer(api)
                  importer.importCollection(collection, parentId.map(BigInt.apply))
                  complete(StatusCodes.OK)
                } else {
                  complete(StatusCodes.Unauthorized, unauthorizedReason(sessionId))
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

  def unauthorizedReason(sessionId: String): String = s"The user associated with the sessionId: $sessionId is not a superuser"

  def exceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case error: CustomException => complete(HttpResponse(error.statusCode, entity = error.msg))
      case error =>
        val msg = s"Unexpected exception handling export/import request: $error"
        log.error(msg, error)
        complete(HttpResponse(InternalServerError, entity = msg))
    }
}
