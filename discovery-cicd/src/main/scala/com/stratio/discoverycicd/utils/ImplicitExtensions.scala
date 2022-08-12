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

  implicit class HttpRequestExtension(request: HttpRequest) extends Logging {

    def as[T](implicit formats: Formats, mf: Manifest[T]): HttpResponse[T] = {
      val response = request.asString
      Try { read[T](response.body) } match {
        case Success(body) =>
          response.copy(body = body)

        case Failure(e) =>
          val msg = s"Error reading metabase response from ${request.method} to ${request.url}. " +
                    s"Status: ${response.code}. Response body: ${response.body}. " +
                    s"Original exception: $e"
          logError(response)
          throw ResponseDeserializationException(msg)
      }
    }

    private def getRequestBody: Option[String] = request.connectFunc match {
      case stringBody: StringBodyConnectFunc => Some(stringBody.data)
      case _: ByteBodyConnectFunc => Some("<Byte array body>")
      case _: MultiPartConnectFunc => Some("<Multi part request>")
      case _ => None
    }

    private def logError(response: HttpResponse[String]) = {
      val requestBody = getRequestBody
      log.error(s"ERROR: Could not deserialize response from ${request.method} to ${request.url}")
      log.error(s"Response Status code: ${response.code}")
      log.error(s"Body received: ${response.body}")
      requestBody.foreach(body => log.error(s"Body in request: $body"))
      log.debug(s"Full HttpResponse object: $response")
    }
  }


  implicit class MapExtension(replacements: Map[BigInt, BigInt]) extends Logging {

    val getTargetField = getOrFail("Field")
    val getTargetTable = getOrFail("Table")
    val getTargetDatabase = getOrFail("Database")
    val getTargetCollection = getOrFail("Collection")
    val getTargetDashboard = getOrFail("Dashboard")
    val getTargetDashboardCard = getOrFail("Dashboard Card")
    val getTargetCard = getOrFail("Card")

    private def getOrFail(replacedThing: String) = (id: BigInt) => {
      replacements.getOrElse(id, throwReplacementError(replacedThing, id))
    }

    private def throwReplacementError(replacedThing: String, id: BigInt) = {
      log.error(s"ERROR: It seems that an exported object references a $replacedThing that has id '$id' in the source Metabase, but was not found in the target Metabase")
      throw ReplacementException(s"Could not replace $replacedThing id $id with the found ${replacedThing}s in target Metabase")
    }

  }
}
