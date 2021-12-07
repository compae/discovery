/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import org.json4s.{JValue, JArray, JString, JInt, JObject, JField}

object MBJson extends ReplacementUtils {

  override val entityType: String = "Object"

  def getFieldIds(jval: JValue): Set[BigInt] = jval.fold(Set.empty[BigInt]) {
    case (acc, JArray(List(JString("field"), JInt(fieldId), _))) => acc + fieldId
    case (acc, JObject(kvs)) => acc ++ kvs.collect { case ("source-field", JInt(id)) => id }
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


  def replaceFieldIds(jval: JValue, replacements: Map[BigInt, BigInt]): JValue = jval.transform {
    case JArray(List(JString("field"), JInt(fieldId), optionsMap)) =>
      JArray(List(JString("field"), JInt(replacements.getTargetField(fieldId)), optionsMap))

    case JObject(kvs) if kvs exists isField => JObject(
      kvs map {
        case jField @ (k, JInt(id)) if isField(jField) => (k, JInt(replacements.getTargetField(id)))
        case other => other
      }
    )
  }


  def replaceTableIds(jval: JValue, replacements: Map[BigInt, BigInt]) = jval.transform {
    case JObject(kvs) if kvs exists isTable => JObject(
      kvs map {
        case jField @ (k, JInt(id)) if (isTable(jField)) => (k, JInt(replacements.getTargetTable(id)))
        case other => other
      }
    )
  }


  def replaceDatabaseIds(jval: JValue, replacements: Map[BigInt, BigInt]) = jval.transform {
    case JObject(kvs) if kvs exists isDatabase => JObject(
      kvs map {
        case jField @ (k, JInt(id)) if (isDatabase(jField)) => (k, JInt(replacements.getTargetDatabase(id)))
        case other => other
      }
    )
  }

  def replaceCardIds(jval: JValue, replacements: Map[BigInt, BigInt]) = jval.transform {
    case JObject(kvs) if kvs exists isCard => JObject(
      kvs map {
        case jField @ (k, JInt(id)) if (isCard(jField)) => (k, JInt(replacements.getTargetCard(id)))
        case other => other

      }
    )
  }

  private def isTable(jsonField: JField) = Set("source-table", "table_id") contains jsonField._1

  private def isDatabase(jsonField: JField) = Set("database", "database_id") contains jsonField._1

  private def isCard(jsonField: JField) = Set("card_id", "cardId") contains jsonField._1

  private def isField(jsonField: JField) = Set("source-field") contains jsonField._1



}
