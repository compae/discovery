/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.{ MBJson, Logging, MetadataChecksum }
import com.stratio.discoverycicd.utils.ReplacementUtils.replacementMayFail
import com.stratio.discoverycicd.utils.ImplicitExtensions.MapExtension
import org.json4s.{JValue, JArray, JString, JInt, JObject, JField}
import scala.util.Try


// Card (question) data model. When making GET requests, metabase returns more fields, but these are the
// ones we need to later make a POST to create the card in the target metabase
case class Card(id: BigInt,
                name: String,
                description: Option[String],
                display: String,
                visualization_settings: JValue,
                collection_id: Option[BigInt],
                collection_position: Option[BigInt],
                dataset_query: JValue,
                result_metadata: JValue) {

  private val isJObject = dataset_query match {
    case JObject(_) => true
    case _ => false
  }
  assert(isJObject, s"Received Card with invalid dataset_query: $dataset_query")

  def toPostDto = CardToPost(
    name, description, display, visualization_settings,
    collection_id, collection_position, dataset_query, result_metadata, metadata_checksum
  )

  def getFieldIds = MBJson.getFieldIds(dataset_query)

  def getTableIds = MBJson.getTableIds(dataset_query)

  def getDatabaseIds = MBJson.getDatabaseIds(dataset_query)

  def replaceFieldIds(replacements:  Map[BigInt, BigInt]): Card = replacementMayFail(this, "Field") {
    _.copy(dataset_query = MBJson.replaceFieldIds(dataset_query, replacements))
  }

  def replaceTableIds(replacements:  Map[BigInt, BigInt]): Card = replacementMayFail(this, "Table") {
    _.copy(dataset_query = MBJson.replaceTableIds(dataset_query, replacements))
  }

  def replaceDatabaseIds(replacements: Map[BigInt, BigInt]): Card = replacementMayFail(this, "Database") {
    _.copy(dataset_query = MBJson.replaceDatabaseIds(dataset_query, replacements))
  }

  def replaceCollectionIds(replacements: Map[BigInt, BigInt]): Card = replacementMayFail(this, "Collection") {
    _.copy(collection_id = Some(replacements.getTargetCollection(collection_id.get)))
  }

  def metadata_checksum = Try(MetadataChecksum(result_metadata)).toOption

  def cleanDescription = description match {
    case Some(description) if description.trim.isEmpty => copy(description = None)
    case _ => this
  }
}


// To make the post requests we should not include the id, we also include the metadata_checksum
case class CardToPost(name: String,
                         description: Option[String],
                         display: String,
                         visualization_settings: JValue,
                         collection_id: Option[BigInt],
                         collection_position: Option[BigInt],
                         dataset_query: JValue,
                         result_metadata: JValue,
                         metadata_checksum: Option[String])
