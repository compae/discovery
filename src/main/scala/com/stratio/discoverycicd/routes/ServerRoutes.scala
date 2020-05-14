package com.stratio.discoverycicd.routes

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, get, _}
import akka.http.scaladsl.server.Route
import com.stratio.discoverycicd.model.ExportedCollection
import com.stratio.discoverycicd.utils.ApiClient
import com.stratio.discoverycicd.{Exporter, Importer}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.DefaultFormats
import com.stratio.discoverycicd.config.ConfigFacade._

class ServerRoutes()(implicit val system: ActorSystem[_]) extends Json4sSupport {

  implicit val formats = DefaultFormats
  implicit val serialization = org.json4s.native.Serialization

  import ServerRoutes._

  val apiRoutes: Route =
    (path(Segment/ ExportSegment / Segment) & get) { (_, id) =>
      headerValue(extractSessionId) { sessionId =>
        val api = new ApiClient(apiClientConfig.metabaseUrl, Left(sessionId), apiClientConfig.allowUnsafeSsl)
        val exporter = new Exporter(api)
        complete(exporter.exportCollection(BigInt(id)))
      }
    } ~
    (path(Segment / ImportSegment) & post) { _ =>
      parameter(StrictTableNames.as[Boolean].?, ParentId.as[BigInt].?) { (strictTableNames, parentId) =>
        headerValue(extractSessionId) { sessionId =>
          entity(as[ExportedCollection]) { collection =>
            val api = new ApiClient(apiClientConfig.metabaseUrl, Left(sessionId), apiClientConfig.allowUnsafeSsl)
            val importer = new Importer(api, strictTableNames.getOrElse(true))
            importer.importCollection(collection, parentId)
            complete(StatusCodes.OK)
          }
        }
      }
    }
}

object ServerRoutes {

  val ExportSegment = "export"
  val ImportSegment = "import"
  val StrictTableNames = "strictTableNames"
  val ParentId = "parentId"
  val SessionIdHeaderName = "x-metabase-session"

  def extractSessionId: HttpHeader => Option[String] = {
    case h if h.name == SessionIdHeaderName => Some(h.value())
    case _         => None
  }
}