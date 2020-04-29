/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import org.json4s.Formats
import org.json4s.native.Serialization.read
import scalaj.http.{HttpRequest, HttpResponse}


object ImplicitExtensions {

  implicit class HttpRequestExtension(request: HttpRequest) {

    def as[T](implicit formats: Formats, mf: Manifest[T]): HttpResponse[T] =
      request.execute(parser = inputStream => read[T](inputStream))

  }
}
