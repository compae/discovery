package com.stratio.discoverycicd.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, _}
import akka.http.scaladsl.server.Route
import com.stratio.discoverycicd.config.ConfigFacade._
import com.stratio.discoverycicd.model.ExportedCollection
import com.stratio.discoverycicd.utils.ApiClient
import com.stratio.discoverycicd.{Exporter, Importer}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.DefaultFormats

class ServerRoutes()(implicit val system: ActorSystem[_]) extends Json4sSupport {

  implicit val formats = DefaultFormats
  implicit val serialization = org.json4s.native.Serialization

  import ServerRoutes._

  val apiRoutes: Route =
    (path(Segment/ ExportSegment / Segment) & get) { (_, id) =>
      headerValue(extractSessionId) { sessionId =>
        val api = new ApiClient(apiClientConfig.metabaseUrl, Left(sessionId), apiClientConfig.allowUnsafeSsl)
        if(api.user.body.is_superuser) {
          val exporter = new Exporter(api)
          complete(exporter.exportCollection(BigInt(id)))
        } else {
          complete(StatusCodes.Unauthorized, unauthorizedReason(sessionId))
        }
      }
    } ~
    (path(Segment / ImportSegment) & post) { _ =>
      parameter(ParentId.as[BigInt].?) { parentId =>
        headerValue(extractSessionId) { sessionId =>
          entity(as[ExportedCollection]) { collection =>
            val api = new ApiClient(apiClientConfig.metabaseUrl, Left(sessionId), apiClientConfig.allowUnsafeSsl)
            if(api.user.body.is_superuser) {
              val importer = new Importer(api)
              importer.importCollection(collection, parentId)
              complete(StatusCodes.OK)
            } else {
              complete(StatusCodes.Unauthorized, unauthorizedReason(sessionId))
            }
          }
        }
      }
    }
}

object ServerRoutes {

  val ExportSegment = "export"
  val ImportSegment = "import"
  val ParentId = "parentId"
  val SessionIdHeaderName = "x-metabase-session"

  def unauthorizedReason(sessionId: String): String = s"The user associated with the sessionId: $sessionId is not a superuser"

  def extractSessionId: HttpHeader => Option[String] = {
    case h if h.name == SessionIdHeaderName => Some(h.value())
    case _         => None
  }
}
