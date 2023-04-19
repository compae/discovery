/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.utils.ImplicitExtensions.MapExtension
import org.json4s.{JArray, JField, JInt, JObject, JString, JValue}

import scala.util.Try

object MBJson extends {

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

  // In metabase a question can use other questions as input data instead of actual tables. It can be done
  // both in "query builder" queries and in native sql queries:
  //
  // When used in "query builder" queries, the "source-table" and the "table_id" fields of the json-query have a
  // string value with the format "card__{id}" (where {id} is the id of the card used as data) instead of a numeric
  // value with the table id. When the card is used as "source table" in a join, the json-query also contains some
  // keys "alias" and "join-alias" with value "Question {id}" (I do not really know what are these keys used for, I
  // think it might be to build the actual SQL). Also, in the "result_metadata" and "display_settings" there appear
  // the "display_name" and "column_title" keys with value "Question {id}" (note however that the "column_title" can
  // be edited by the user in the UI). An example of json would be like the following:
  //
  //   {
  //       "dataset_query": {
  //           "query": {
  //               "source-table": "card__9",
  //               "joins": [{
  //                   "source-table": "card__10",
  //                   "condition": [["field", 22, {"join-alias": "Question 10"}], ...],
  //                   "alias": "Question 10",
  //                   ...
  //               }, ...]
  //           },
  //           ...
  //       },
  //       "result_metadata": [{ "display_name": "Question 10 → EMAIL", ... }, ...],
  //       "visualization_settings": {
  //           "column_settings": { "[\"ref\",[\"field\",22,null]]": {"column_title": "Question 10 → ID" }},
  //           ...
  //       },
  //       ...
  //   }
  //
  //
  // In native queries, the cards can be used as tables in the sql using a template with the format {{#123}}
  // (123 here is an example of a question id), like "select * from {{#123}}". Then, in the "tempate-tags"
  // object there should appear a template "#123" of type card with the following shape:
  //
  //  {
  //    "dataset-query": {
  //      "native": {
  //        "query": "select * from {{#123}}"
  //        "template-tags": {
  //          "#123": { "id": "some uuid" , "name": "#123" , "display-name": "#123" , "type": "card" , "card-id": 123 }
  //          ...
  //        },
  //        ...
  //      },
  //      ...
  //    },
  //    ...
  //  }
  //
  def getCardIdsUsedAsTables(jval: JValue): Set[BigInt] = jval.fold(Set.empty[BigInt]) {
    case (acc, JObject(kvs)) =>
      val cardIds = kvs
        .collect {
          // cards from "query builder" queries
          case jField @ (_, JString(cardIdStr)) if isTableWithCardValue(jField) =>
            Set(BigInt(cardIdStr.replaceFirst("card__", "")))

          // cards from native sql queries
          case ("template-tags", JObject(templateTags)) =>
            templateTags
              .collect {
                case jField @ (_, JObject(tags)) if isCardTemplate(jField) =>
                  tags.collect {
                    case ("card-id", JInt(cardId)) => cardId
                  }
              }
              .flatten
        }
        .flatten
        .toSet

      acc ++ cardIds

    case (acc, _) => acc
  }

  def replaceCardIdsUsedAsTables(jval: JValue, replacements: Map[BigInt, BigInt]): JValue = {
    def replaceCardIdInPattern(pattern: BigInt => String, value: String) =
      replacements.foldLeft(value) {
        case (updatedValue, (sourceId, targetId)) =>
          updatedValue.replace(pattern(sourceId), pattern(targetId))
      }

    def replaceCardIdWithPrefix(prefix: String, str: String) = {
        val cardId = BigInt(str.replaceFirst(prefix, ""))
        s"$prefix${replacements.getTargetCardAsTable(cardId)}"
    }

    jval.transform {
      case JObject(kvs) => JObject(
        kvs.map {
          // "query builder" queries: { "source-table": "card__9" }
          case jField @ (k, JString(value)) if isTableWithCardValue(jField) =>
            val updatedValue = replaceCardIdWithPrefix("card__", value)
            k -> JString(updatedValue)

          // "query builder" queries: { "join-alias": "Question 10" } etc...
          case (k @ ("join-alias" | "alias" | "column_title" | "display_name"), JString(value)) if value contains "Question" =>
            val updatedValue = replaceCardIdInPattern(id => s"Question $id", value)
            k -> JString(updatedValue)

          // native queries
          case ("native", JObject(nativeQuery)) => "native" -> JObject(
            nativeQuery.map {
              // { "query": "select * from {{#123}} join {{#456}}" }
              case ("query", JString(sql)) => "query" -> JString(
                replaceCardIdInPattern(id => s"{{#$id}}", sql)
              )

              // "template-tags": { "#123": { "name": "#123" , "display-name": "#123" , "card-id": 123, ... }
              case ("template-tags", JObject(templateTags)) => "template-tags" -> JObject(
                templateTags.map {
                  case jField @ (template, JObject(tags)) if isCardTemplate(jField) =>
                    val updatedTemplate = replaceCardIdWithPrefix("#", template)
                    val updatedTags = tags.map {
                      case (k @ ("name" | "display-name"), JString(name)) =>
                        val updatedName = replaceCardIdWithPrefix("#", name)
                        k -> JString(updatedName)

                      case ("card-id", JInt(cardId)) =>
                        "card-id" -> JInt(replacements.getTargetCardAsTable(cardId))

                      case other => other
                    }
                    updatedTemplate -> JObject(updatedTags)

                  case other => other
                }
              )

              case other => other
            }
          )

          case other => other
        }
      )
    }
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

  private def isTableWithCardValue(jsonField: JField) = jsonField match {
    case jField @ (_, JString(value)) if isTable(jField) => value startsWith "card__"
    case _ => false
  }

  private def isCardTemplate(jsonField: JField) = jsonField match {
    case (template, JObject(tags)) => (template startsWith "#") && (tags contains "type" -> JString("card"))
    case _ => false
  }

  private def isDatabase(jsonField: JField) = Set("database", "database_id") contains jsonField._1

  private def isCard(jsonField: JField) = Set("card_id", "cardId") contains jsonField._1

  private def isField(jsonField: JField) = Set("source-field") contains jsonField._1

}
