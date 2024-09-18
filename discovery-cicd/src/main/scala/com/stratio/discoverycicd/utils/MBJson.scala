/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.model.LinkType
import com.stratio.discoverycicd.model.LinkType._
import com.stratio.discoverycicd.utils.ImplicitExtensions.MapExtension
import com.stratio.discoverycicd.utils.TypeAliases.IdMap
import org.json4s.{JArray, JField, JInt, JObject, JString, JValue}

object MBJson extends {

  // fields in MBQL (metabase query language for visual queries) are always represented with this format:
  // ["field", <id>, <other-options>] (https://github.com/metabase/metabase/pull/14897), but there are a
  // couple of situations where we can find field ids in other places, like in visualization_settings of
  // cards or dashcards, where they may appear in json keys or values as strings but with the above format
  // (e. g., "[\"field\", <id>, null]"). Also, the card's result_metadata is a list of "column information"
  // (with some metadata info about the columns that the result of the query returns), and there, if the
  // column is associated with a field, the field id appears directly as "id". Since there are a lot of
  // places where an "id" key may appear that do not represent a field, we have to make sure to only collect
  // these "id" keys as field-ids when we are processing a card's result_metadata
  def getFieldIds(jval: JValue, isResultMetadata: Boolean = false): Set[BigInt] = jval.fold(Set.empty[BigInt]) {
    // standard way of representing fields ["field", <id>, <options>]
    case (acc, JArray(List(JString("field"), JInt(fieldId), _))) => acc + fieldId

    case (acc, JObject(kvs)) => acc ++ kvs.flatMap {
      // legacy format (I believe it is not used anymore) for retrocompatibility and just in case...
      case ("source-field", JInt(id)) => Set(id)

      // string with field format ("[\"field\", <id>, null]") as json key
      case (k, _) if hasFieldRefs(k) => getReferencedFieldIds(k)

      // string with field format ("[\"field\", <id>, null]") as json string value
      case (_, JString(v)) if hasFieldRefs(v) => getReferencedFieldIds(v)

      // for card's result metadata, field ids just appear as "id" :S
      case ("id", JInt(id)) if isResultMetadata => Set(id)

      case _ => Set.empty[BigInt]
    }
    case (acc, _) => acc
  }


  def getTableIds(jval: JValue): Set[BigInt] = jval.fold(Set.empty[BigInt]) {
    case (acc, JObject(kvs)) => acc ++ kvs.collect {
      case ("source-table", JInt(id)) => id
      case ("table_id"    , JInt(id)) => id
    }
    case (acc, _) => acc
  }


  def getDatabaseIds(jval: JValue): Set[BigInt] = jval.fold(Set.empty[BigInt]) {
    case (acc, JObject(kvs)) => acc ++ kvs.collect {
      case ("database"   , JInt(id)) => id
      case ("database_id", JInt(id)) => id
    }
    case (acc, _) => acc
  }

  // jval format examples can be found in tests 
  def getSnippetIds(jval: JValue) = jval.fold(Set.empty[BigInt]) {
    case (acc, JObject(kvs)) => acc ++ kvs.collect {
      case ("snippet-id", JInt(id)) => id
    }
    case (acc, _) => acc
  }

  def getCardIs(jval: JValue): Set[BigInt] = jval.fold(Set.empty[BigInt]) {
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

  def replaceCardIdsUsedAsTables(jval: JValue, replacements: IdMap): JValue = {
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


  // In dashboard cards the users can configure custom click behaviors for the different columns of the card.
  // One of the options is to link to other question or to other dashboard, and when they do this the
  // visualization_settings of the ordered_card (DashCard) json get a value like the following:

  //   {
  //     "visualization_settings": {
  //       "column_settings": {
  //         "[\"ref\",[\"field\",6,null]]": {
  //           "click_behavior": {
  //             "type": "link",
  //             "linkType": "dashboard",
  //             "parameterMapping": {},
  //             "targetId": 11,
  //             "linkTextTemplate": "link absurdo ({{SUBTOTAL}})"
  //           }
  //         },
  //         "[\"ref\",[\"field\",3,null]]": {
  //           "click_behavior": {
  //             "targetId": 81,
  //             "parameterMapping": {
  //               "[\"dimension\",[\"field\",22,null]]": {
  //                 "source": {"type": "column", "id": "USER_ID", "name": "USER_ID"},
  //                 "target": {"type": "dimension", "id": "[\"dimension\",[\"field\",22,null]]", "dimension": ["dimension", ["field", 22, null]]},
  //                 "id": "[\"dimension\",[\"field\",22,null]]"
  //               }
  //             },
  //             "linkType": "question",
  //             "type": "link",
  //             "linkTextTemplate": "Details of user {{USER_ID}}"
  //           }
  //         },
  //         "[\"ref\",[\"field\",9,null]]": {}
  //       }
  //     }
  //  }
  //  There we see that there is the "targetId" for the question/dashboard link, and also several keys and values
  //  whose name is a array-looking string with references to field Ids ("ref,["field",3,"null"]") and we need
  //  to replace these keys so the click_behaviors work correctly
  //
  def replaceItemIdsInDashCardLinks(linkType: LinkType)(jval: JValue, replacements: IdMap): JValue = jval.transform {
    case JObject(kvs) => JObject(
      kvs.map {
        case jField @ (k, JObject(clickBehavior)) if isLinkOf(linkType)(jField) =>
          val replacedClickBehavior = clickBehavior.map {
            case ("targetId", JInt(cardId)) => ("targetId", JInt(replacements.getTargetCard(cardId)))
            case other => other
          }
          (k, JObject(replacedClickBehavior))

        case other => other
      }
    )
  }

  def getItemIdsInLinks(linkType: LinkType)(jval: JValue): Set[BigInt] = jval.fold(Set.empty[BigInt]) {
    case (acc, JObject(kvs)) =>
      acc ++ kvs.collect {
        case jField @ (_, JObject(clickBehavior)) if isLinkOf(linkType)(jField) =>
          clickBehavior.collect { case ("targetId", JInt(id)) => id }.toSet
      }.flatten

    case (acc, _) => acc
  }


  def replaceFieldIds(jval: JValue, replacements: IdMap, isResultMetadata: Boolean = false): JValue = jval.transform {
    case JArray(List(JString("field"), JInt(fieldId), optionsMap)) =>
      JArray(List(JString("field"), JInt(replacements.getTargetField(fieldId)), optionsMap))

    case JObject(kvs) => JObject(
      kvs.map {
        case (k, JInt(id)) if isField(k) =>
          (k, JInt(replacements.getTargetField(id)))

        case (key, value) if hasFieldRefs(key) =>
          val updatedKey = replaceFieldRefs(replacements, key)
          (updatedKey, value)

        case (key, JString(value)) =>
          val updatedValue = replaceFieldRefs(replacements, value)
          (key, JString(updatedValue))

        case ("id",  JInt(id)) if isResultMetadata =>
          ("id", JInt(replacements.getTargetField(id)))

        case other => other
      }
    )
  }


  def replaceTableIds(jval: JValue, replacements: IdMap): JValue = jval.transform {
    case JObject(kvs) => JObject(
      kvs map {
        case (k, JInt(id)) if isTable(k) => (k, JInt(replacements.getTargetTable(id)))
        case other => other
      }
    )
  }


  def replaceDatabaseIds(jval: JValue, replacements: IdMap): JValue = jval.transform {
    case JObject(kvs) => JObject(
      kvs map {
        case (k, JInt(id)) if isDatabase(k) => (k, JInt(replacements.getTargetDatabase(id)))
        case other => other
      }
    )
  }

  def replaceCardIds(jval: JValue, replacements: IdMap): JValue = jval.transform {
    case JObject(kvs) => JObject(
      kvs map {
        case (k, JInt(id)) if isCard(k) => (k, JInt(replacements.getTargetCard(id)))
        case other => other

      }
    )
  }


  // "native": {
  //   "query": "select * FROM people WHERE {{snippet: alwaystrue}} {{snippet: limitresutls}}",
  //   "template-tags": {
  //     "snippet: limitresutls": {
  //       "id": "d6b8c3ea-1cc4-8d86-41ce-243ccc73c038",
  //       "name": "snippet: limitresutls",
  //       "display-name": "Snippet: limitresutls",
  //       "type": "snippet",
  //       "snippet-name": "limitresutls",
  //       "snippet-id": 2
  //     },
  //   "snippet: alwaystrue": {
  //     "id": "b95727fd-ecf5-64bd-8244-d71238d4b6f6",
  //     "name": "snippet: alwaystrue",
  //     "display-name": "Snippet: alwaystrue",
  //     "type": "snippet",
  //     "snippet-name": "alwaystrue",
  //     "snippet-id": 1
  //     }
  //   }
  // }
  // replacements (source -> target)
  def replaceSnippets(jval: JValue, replacements: Map[BigInt, BigInt]): JValue = {
    jval.transform {
      case JObject(kvs) => JObject(
        kvs.map {
          // native queries
          case ("native", JObject(nativeQuery)) => "native" -> JObject(
            nativeQuery.map {
              // "template-tags": { ... }
              case ("template-tags", JObject(templateTags)) => "template-tags" -> JObject(
                templateTags.map {
                  case jField @ (snippetKey, JObject(snippetInfo)) if isSnippetKey(snippetKey) =>
                    // "id": "b95727fd-ecf5-64bd-8244-d71238d4b6f6", // keep the same id?
                    // "name": "snippet: alwaystrue", // keep the same name
                    // "display-name": "Snippet: alwaystrue", // keep the same display-name
                    // "type": "snippet", // keep the same type
                    // "snippet-name": "alwaystrue", // keep the same name
                    // "snippet-id": 1 // replace the snippet-id
                    val updatedSnippetInfo = snippetInfo.map {
                      case ("snippet-id", JInt(sourceSnippetId)) =>
                        "snippet-id" -> JInt(replacements.getTargetSnippet(sourceSnippetId))
                      case other => other
                    }
                    snippetKey -> JObject(updatedSnippetInfo)

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

  private def isTable(jsonKey: String) = Set("source-table", "table_id") contains jsonKey

  private def isTableWithCardValue(jsonField: JField) = jsonField match {
    case (k, JString(value)) if isTable(k) => value startsWith "card__"
    case _ => false
  }

  private def isCardTemplate(jsonField: JField) = jsonField match {
    case (template, JObject(tags)) => (template startsWith "#") && (tags contains "type" -> JString("card"))
    case _ => false
  }

  private def isLinkOf(linkType: LinkType)(jsonField: JField) = jsonField match {
    case ("click_behavior", JObject(clickBehaviors)) => clickBehaviors contains "linkType" -> JString(linkType)
    case _ => false
  }

  private val fieldRefPattern = """["field","""

  private val fieldRefRegex = """(?<=\["field",)\d+""".r

  private def hasFieldRefs(s: String) = s contains fieldRefPattern

  private def getReferencedFieldIds(s: String): Set[BigInt] =
    fieldRefRegex.findAllIn(s).map(BigInt(_)).toSet

  private def replaceFieldRefs(replacements: IdMap, value: String) = {
    val stringReplacements = replacements.map { case (k, v) => (k.toString, v.toString) }
    fieldRefRegex.replaceAllIn(value, m => stringReplacements.getTargetField(m.matched))
  }

  private def isDatabase(jsonKey: String) = Set("database", "database_id") contains jsonKey

  private def isCard(jsonKey: String) = Set("card_id", "cardId") contains jsonKey

  private def isField(jsonKey: String) = Set("source-field") contains jsonKey

  // "snippet: <snippet-name>": { .. }
  private def isSnippetKey(key: String): Boolean = key.startsWith("snippet:")

}
