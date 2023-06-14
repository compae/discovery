/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.{Logging, MBJson, MetadataChecksum}
import com.stratio.discoverycicd.utils.ReplacementUtils.replacementMayFail
import com.stratio.discoverycicd.utils.ImplicitExtensions.MapExtension
import com.stratio.discoverycicd.utils.TypeAliases.IdMap
import org.json4s.{JArray, JField, JInt, JObject, JString, JValue}

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
                result_metadata: JValue,
                // metabase models are questions marked as "dataset" with this flag.
                // Since we do not export it in previous versions, we give a default value
                dataset: Boolean = false) {

  private val isJObject = dataset_query match {
    case JObject(_) => true
    case _ => false
  }
  require(isJObject, s"Received Card with invalid dataset_query: $dataset_query")

  def toPostDto = CardToPost(
    name, description, display, visualization_settings,
    collection_id, collection_position, dataset_query, result_metadata, metadata_checksum, dataset
  )

  def getFieldIds =
    MBJson.getFieldIds(dataset_query) ++
      MBJson.getFieldIds(visualization_settings) ++
      MBJson.getFieldIds(result_metadata, isResultMetadata = true)

  def getTableIds = MBJson.getTableIds(dataset_query)

  def getDatabaseIds = MBJson.getDatabaseIds(dataset_query)

  def getSnippetIds = MBJson.getSnippetIds(dataset_query)

  lazy val getCardsUsedAsTables = MBJson.getCardIdsUsedAsTables(dataset_query)

  def replaceFieldIds(replacements:  Map[BigInt, BigInt]): Card = replacementMayFail(this, "Field") {
    _.copy(
      dataset_query          = MBJson.replaceFieldIds(dataset_query, replacements),
      result_metadata        = MBJson.replaceFieldIds(result_metadata, replacements, isResultMetadata = true),
      visualization_settings = MBJson.replaceFieldIds(visualization_settings, replacements)
    )
  }

  def replaceTableIds(replacements:  Map[BigInt, BigInt]): Card = replacementMayFail(this, "Table") {
    _.copy(dataset_query = MBJson.replaceTableIds(dataset_query, replacements))
  }

  def replaceCardIdsUsedAsTables(replacements: Map[BigInt, BigInt]): Card = replacementMayFail(this, "Card acting as table") {
    _.copy(
      dataset_query          = MBJson.replaceCardIdsUsedAsTables(dataset_query, replacements),
      result_metadata        = MBJson.replaceCardIdsUsedAsTables(result_metadata, replacements),
      visualization_settings = MBJson.replaceCardIdsUsedAsTables(visualization_settings, replacements)
    )
  }

  def replaceDatabaseIds(replacements: Map[BigInt, BigInt]): Card = replacementMayFail(this, "Database") {
    _.copy(dataset_query = MBJson.replaceDatabaseIds(dataset_query, replacements))
  }

  def replaceCollectionIds(replacements: Map[BigInt, BigInt]): Card = replacementMayFail(this, "Collection") {
    _.copy(collection_id = Some(replacements.getTargetCollection(collection_id.get)))
  }

  // replacements (source -> target)
  def replaceSnippets(replacements: Map[BigInt, BigInt]): Card =
    replacementMayFail(this, "Snippet") {
      _.copy(dataset_query = MBJson.replaceSnippets(dataset_query, replacements))
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
                      metadata_checksum: Option[String],
                      dataset: Boolean)
