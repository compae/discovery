/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.model._
import org.json4s.native.Serialization.write
import org.json4s.DefaultFormats

trait Jsonable[A] {
  implicit val formats = DefaultFormats
  def toJson(a: A): String = write(a)
}

object Jsonable {

  implicit class JsonableOps[A: Jsonable](a: A) {
    def toJson = implicitly[Jsonable[A]].toJson(a)
  }

  implicit def defaultJsonableInstance[A] = new Jsonable[A] {}

}
