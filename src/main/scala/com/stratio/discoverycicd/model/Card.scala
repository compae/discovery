/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.MBJson
import org.json4s.{JValue, JArray, JString, JInt, JObject, JField}

// Card (question) data model. When making GET requests, metabase returns more fields, but these are the
// ones we need to later make a POST to create the card in the target metabase
case class Card(id: BigInt,
                name: String,
                description: Option[String],
                display: String,
                visualization_settings: JValue,
                collection_id: Option[BigInt],
                collection_position: Option[BigInt],
                dataset_query: JValue) {

  def withoutId = CardWithoutId(
    name, description, display, visualization_settings,
    collection_id, collection_position, dataset_query
  )

  def getFieldIds = MBJson.getFieldIds(dataset_query)

  def getTableIds = MBJson.getTableIds(dataset_query)

  def getDatabaseIds = MBJson.getDatabaseIds(dataset_query)

  def replaceFieldIds(replacements:  BigInt => BigInt): Card =
    copy(dataset_query = MBJson.replaceFieldIds(dataset_query, replacements))

  def replaceTableIds(replacements:  BigInt => BigInt): Card =
    copy(dataset_query = MBJson.replaceTableIds(dataset_query, replacements))

  def replaceDatabaseIds(replacements: BigInt => BigInt): Card =
    copy(dataset_query = MBJson.replaceDatabaseIds(dataset_query, replacements))

  def replaceCollectionIds(replacements: BigInt => BigInt): Card =
    copy(collection_id = Some(replacements(collection_id.get)))
}


// To make the post requests we should not include the id
case class CardWithoutId(name: String,
                         description: Option[String],
                         display: String,
                         visualization_settings: JValue,
                         collection_id: Option[BigInt],
                         collection_position: Option[BigInt],
                         dataset_query: JValue)
