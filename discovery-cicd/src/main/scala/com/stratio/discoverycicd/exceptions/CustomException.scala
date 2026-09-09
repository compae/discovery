/*
 * © 2021 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.exceptions

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes.{BadRequest, Conflict, InternalServerError, NotFound,
  PayloadTooLarge, PreconditionFailed, TooManyRequests, Unauthorized}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

object CustomException {

  // What to write in a log or in a message to say why something failed. The class of the exception
  // goes with the message because some of the ones we get from the http client say only, for
  // instance, the name of the host, and some others have no message at all
  def causeOf(ex: Throwable): String =
    Option(ex.getMessage).filter(_.nonEmpty).fold(ex.toString)(msg => s"${ex.getClass.getSimpleName}: $msg")
}

sealed trait CustomException extends Exception with Json4sSupport {
  implicit val formats = DefaultFormats
  implicit val serialization = org.json4s.native.Serialization
  case class ErrorResponse(statusCode: Int, message: String)

  def msg: String
  def statusCode: StatusCode

  // these exceptions carry their message in 'msg' and do not pass it to 'Exception', so without this
  // anything that logs them the usual way (through 'getMessage') would say only 'null'
  override def getMessage: String = msg

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

// --- asynchronous export and import -------------------------------------------------------------

final case class JobNotFoundException(msg: String) extends CustomException {
  def statusCode: StatusCode = NotFound
}

final case class JobNotFinishedException(msg: String) extends CustomException {
  def statusCode: StatusCode = Conflict
}

final case class TooManyJobsException(msg: String) extends CustomException {
  def statusCode: StatusCode = TooManyRequests
}

/**
 * The body of an import was bigger than `DISCOVERY_CICD_API_MAX_BODY_SIZE` allows, either as it
 * arrived or once unpacked (see [[com.stratio.discoverycicd.routes.ImportUploads.bigUploads]]).
 *
 * It exists so that the answer is the same 413 the nginx in front gives to the very same body:
 * without it the catch-all of the exception handler would call it an unexpected error and answer a
 * 500, and which of the two a client got would depend on which layer happened to cut first.
 */
final case class PayloadTooLargeException(msg: String) extends CustomException {
  def statusCode: StatusCode = PayloadTooLarge
}

/**
 * Thrown from inside an export or an import that somebody asked to stop, to unwind out of whatever
 * loop it was in. It is not a [[CustomException]] because it never becomes a response: the job
 * catches it and marks itself cancelled, and the one who asked for it already got their answer
 */
final case class JobCancelledException(jobId: String)
  extends Exception(s"The job with id:[$jobId] was cancelled")
