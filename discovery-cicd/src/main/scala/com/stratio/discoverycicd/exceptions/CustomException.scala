/*
 * © 2021 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.exceptions

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, PreconditionFailed, Unauthorized}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

sealed trait CustomException extends Exception with Json4sSupport {
  implicit val formats = DefaultFormats
  implicit val serialization = org.json4s.native.Serialization
  case class ErrorResponse(statusCode: Int, message: String)

  def msg: String
  def statusCode: StatusCode

  def toJson: String = write(ErrorResponse(statusCode.intValue, msg))
}

final case class UnauthorizedException(msg: String) extends CustomException {
  def statusCode: StatusCode = Unauthorized
}

final case class ReplacementException(msg: String) extends CustomException {
  def statusCode: StatusCode = BadRequest
}

final case class DuplicatedEntityException(msg: String) extends CustomException {
  def statusCode: StatusCode = BadRequest
}

final case class UnsupportedEntityException(msg: String) extends CustomException {
  def statusCode: StatusCode = BadRequest
}

final case class ResponseDeserializationException(msg: String) extends CustomException {
  def statusCode: StatusCode = InternalServerError
}

final case class MappingException(msg: String) extends CustomException {
  def statusCode: StatusCode = PreconditionFailed
}

final case class ValidationDataException(msg: String) extends CustomException {
  def statusCode: StatusCode = BadRequest
}

final case class GetDataException(msg: String) extends CustomException {
  def statusCode: StatusCode = InternalServerError
}

final case class UnhandledException(error: Throwable) extends CustomException {
  def statusCode: StatusCode = InternalServerError
  def msg = s"Unexpected exception handling export/import request: $error"
}
