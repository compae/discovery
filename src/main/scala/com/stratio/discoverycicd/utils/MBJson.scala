/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import org.json4s.{JValue, JArray, JString, JInt, JObject, JField}

object MBJson {

  def getFieldIds(jval: JValue): Set[BigInt] = jval.fold(Set.empty[BigInt]) {
    case (acc, JArray(List(JString("field-id"), JInt(fieldId)))) => acc + fieldId
    case (acc, _) => acc
  }


  def getTableIds(jval: JValue): Set[BigInt] = jval.fold(Set.empty[BigInt]) {
    case (acc, JObject(kvs)) => acc ++ kvs.collect {
      case ("source-table", JInt(id)) => id
      case ("table_id"    , JInt(id)) => id
    }
    case (acc, _) => acc
  }


  def getDatabaseIds(jval: JValue) = jval.fold(Set.empty[BigInt]) {
    case (acc, JObject(kvs)) => acc ++ kvs.collect {
      case ("database"   , JInt(id)) => id
      case ("database_id", JInt(id)) => id
    }
    case (acc, _) => acc
  }


  def getCardIs(jval: JValue) = jval.fold(Set.empty[BigInt]) {
    case (acc, JObject(kvs)) => acc ++ kvs.collect { case ("card_id", JInt(id)) => id }
    case (acc, _) => acc
  }


  def replaceFieldIds(jval: JValue, replacements: BigInt => BigInt): JValue = jval.transform {
    case JArray(List(JString("field-id"), JInt(fieldId))) => JArray(List(JString("field-id"), JInt(replacements(fieldId))))
  }


  def replaceTableIds(jval: JValue, replacements: BigInt => BigInt) = jval.transform {
    case JObject(kvs) if kvs exists isTable => JObject(
      kvs map {
        case jField @ (k, JInt(id)) if (isTable(jField)) => (k, JInt(replacements(id)))
        case other => other
      }
    )
  }


  def replaceDatabaseIds(jval: JValue, replacements: BigInt => BigInt) = jval.transform {
    case JObject(kvs) if kvs exists isDatabase => JObject(
      kvs map {
        case jField @ (k, JInt(id)) if (isDatabase(jField)) => (k, JInt(replacements(id)))
        case other => other
      }
    )
  }


  private def isTable(jsonField: JField) = Set("source-table", "table_id") contains jsonField._1

  private def isDatabase(jsonField: JField) = Set("database", "database_id") contains jsonField._1



}
