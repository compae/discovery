/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import scala.util.Try
import scala.util.{Try, Success, Failure}

trait ReplacementUtils extends Logging with Jsonable {
  val entityType: String

  def replacementMayFail[T](replacedThing: String)(block: => T) =
    Try(block) match {
      case Success(result) => result

      case Failure(e: ReplacementException) =>
        log.error(s"Error replacing $replacedThing ID in $entityType: $toJson")
        throw e

      case Failure(e) =>
        log.error(s"Unexpected error while replacing $replacedThing ID in $entityType: $toJson")
        throw e
    }

  implicit class MapExtension(replacements: Map[BigInt, BigInt]) {

    val getTargetField = getOrFail("Field")
    val getTargetTable = getOrFail("Table")
    val getTargetDatabase = getOrFail("Database")
    val getTargetCollection = getOrFail("Collection")
    val getTargetCard = getOrFail("Card")

    private def getOrFail(replacedThing: String) = (id: BigInt) => {
      replacements.getOrElse(id, throwReplacementError(replacedThing, id))
    }

    private def throwReplacementError(replacedThing: String, id: BigInt) = {
      log.error(s"ERROR: It seems that an exported $entityType references a $replacedThing that has id '$id' in the source Metabase, but was not found in the target Metabase")
      throw new ReplacementException(s"Could not replace $replacedThing id $id with the found ${replacedThing}s in target Metabase")
    }

  }
}
