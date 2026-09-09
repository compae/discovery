/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.exceptions.{ResponseDeserializationException, ReplacementException}
import org.json4s.Formats
import org.json4s.native.Serialization.read
import scalaj.http.{HttpRequest, HttpResponse, StringBodyConnectFunc, ByteBodyConnectFunc, MultiPartConnectFunc}
import scala.util.{Try, Success, Failure}


object ImplicitExtensions {

  private val NotFound = 404

  // How much of a metabase response we can afford to report when we cannot read it. Such a body can be
  // as big as the one we asked for ('GET database/:id/fields' still answers with every field of a
  // database, see 'Importer.fieldsHiddenInTableMetadata'), and it goes both into the message of the
  // exception (which travels to the client as the body of a 500) and into the log
  private[utils] object ReportedBody extends Logging {

    // deliberately generous: no real answer of metabase, not even a stack trace or the error page of a
    // proxy, comes close to it, so nothing worth reading is ever lost and the only thing the limit
    // does is keep a pathological body from being copied around
    private[utils] val DefaultMaxChars = 1024 * 1024
    private[utils] val MaxCharsEnvVar = "DISCOVERY_CICD_API_MAX_REPORTED_BODY_CHARS"

    // taken from the environment and not from 'application.conf' because this is a static utility used
    // on the error path: every other setting of the project is injected through a constructor, and
    // there is no way into an implicit class over 'HttpRequest' short of threading the value through
    // the whole 'ApiClient'
    val maxChars: Int = maxCharsOf(sys.env get MaxCharsEnvVar)

    // a limit of zero or less would report nothing at all, and anything that is not a number is a
    // typo: reporting the default is more useful than reporting nothing, so neither is taken
    private[utils] def maxCharsOf(configured: Option[String]): Int =
      configured.map(_.trim).filter(_.nonEmpty) match {
        case None => DefaultMaxChars
        case Some(value) =>
          Try(value.toInt).toOption.filter(_ > 0) getOrElse {
            log.warn(s"'$MaxCharsEnvVar' is set to '$value', which is not a number of characters greater " +
              s"than zero, so the default of $DefaultMaxChars is used instead")
            DefaultMaxChars
          }
      }
  }

  private def abbreviate(body: String): String =
    if (body == null || body.length <= ReportedBody.maxChars) body
    else s"${body.take(ReportedBody.maxChars)}... (truncated, ${body.length} characters in total)"

  implicit class HttpRequestExtension(request: HttpRequest) extends Logging {

    def as[T](implicit formats: Formats, mf: Manifest[T]): HttpResponse[T] = {
      val response = request.asString
      response.copy(body = deserialize[T](response))
    }

    // Some metabase endpoints answer 404 instead of an empty response when they have nothing to give
    // (see 'ApiClient.getTablesInSchema'), and 'as' would fail trying to deserialize the error body
    // as the type we expect
    def asIfFound[T](implicit formats: Formats, mf: Manifest[T]): Option[T] = {
      val response = request.asString
      if (response.code == NotFound) {
        // the 404 is swallowed on purpose, so without this there is no trace at all of a request that
        // came back empty because the url was wrong or something in the middle answered for metabase.
        // Metabase 0.56.4 answers a 'text/plain' 'Not found.' here, while a url it does not route at
        // all answers 'application/json' with '"API endpoint does not exist."': that is what tells one
        // from the other when this shows up in a log
        log.debug(s"Metabase answered 404 to ${request.method} to ${request.url}, so it is taken as " +
          s"'there is nothing there'. Response body: ${abbreviate(response.body)}")
        None
      } else Some(deserialize[T](response))
    }

    private def deserialize[T](response: HttpResponse[String])
                              (implicit formats: Formats, mf: Manifest[T]): T =
      Try { read[T](response.body) } match {
        case Success(body) =>
          body

        case Failure(e) =>
          val msg = s"Error reading metabase response from ${request.method} to ${request.url}. " +
                    s"Status: ${response.code}. Response body: ${abbreviate(response.body)}. " +
                    s"Original exception: $e"
          logError(response)
          throw ResponseDeserializationException(msg)
      }

    private def getRequestBody: Option[String] = request.connectFunc match {
      case stringBody: StringBodyConnectFunc => Some(stringBody.data)
      case _: ByteBodyConnectFunc => Some("<Byte array body>")
      case _: MultiPartConnectFunc => Some("<Multi part request>")
      case _ => None
    }

    private def logError(response: HttpResponse[String]): Unit = {
      val requestBody = getRequestBody
      log.error(s"ERROR: Could not deserialize response from ${request.method} to ${request.url}")
      log.error(s"Response Status code: ${response.code}")
      log.error(s"Body received: ${abbreviate(response.body)}")
      requestBody.foreach(body => log.error(s"Body in request: $body"))
    }
  }


  // 'def' and not 'val': a 'val' of a function type is built when the implicit class is, so every
  // single lookup of an id built all ten of these closures and used one of them. This runs on every
  // field, table, card and snippet reference of every question of an import, which for a big
  // collection is millions of times (see 'MBJson.replaceFieldRefs'). They still work where one is
  // handed over as a function, such as 'collection_id.map(replacements.getTargetCollection)'
  implicit class MapExtension[K, V](replacements: Map[K, V]) extends Logging {

    def getTargetField       (id: K): V = getOrFail(id, "Field")
    def getTargetTable       (id: K): V = getOrFail(id, "Table")
    def getTargetDatabase    (id: K): V = getOrFail(id, "Database")
    def getTargetCollection  (id: K): V = getOrFail(id, "Collection")
    def getTargetDashboard   (id: K): V = getOrFail(id, "Dashboard")
    def getTargetDashboardTab(id: K): V = getOrFail(id, "Dashboard Tab")
    def getTargetCard        (id: K): V = getOrFail(id, "Card")
    def getTargetCardAsTable (id: K): V = getOrFail(id, "Card acting as table")
    def getTargetMetric      (id: K): V = getOrFail(id, "Metric")
    def getTargetSnippet     (id: K): V = getOrFail(id, "Snippet")


    private def getOrFail(id: K, replacedThing: String): V =
      replacements.getOrElse(id, throwReplacementError(replacedThing, id.toString))

    private def throwReplacementError(replacedThing: String, id: String): Nothing = {
      log.error(s"ERROR: It seems that an exported object references a $replacedThing that has id '$id' in the source Metabase, but was not found in the target Metabase")
      throw ReplacementException(s"Could not replace $replacedThing id $id with the found ${replacedThing}s in target Metabase")
    }

  }
}
