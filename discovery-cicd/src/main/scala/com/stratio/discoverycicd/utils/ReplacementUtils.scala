/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.exceptions.ReplacementException
import com.stratio.discoverycicd.utils.Jsonable.JsonableOps
import scala.util.Try
import scala.util.{Try, Success, Failure}

object ReplacementUtils extends Logging {
  def entityType[A](entity: A): String = entity.getClass.getName.split("\\.").last

  def replacementMayFail[A, T](entity: A, replacedThingName: String)(replacingFun: A => T) =
    Try(replacingFun(entity)) match {
      case Success(result) => result

      case Failure(e: ReplacementException) =>
        log.error(s"Error replacing $replacedThingName ID in ${entityType(entity)}: ${entity.toJson}")
        throw e

      case Failure(e) =>
        log.error(s"Unexpected error while replacing $replacedThingName ID in ${entityType(entity)}: ${entity.toJson}")
        throw e
    }

}
