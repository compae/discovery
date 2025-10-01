/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.ImplicitExtensions.MapExtension
import com.stratio.discoverycicd.utils.MBJson
import com.stratio.discoverycicd.utils.ReplacementUtils.replacementMayFail
import com.stratio.discoverycicd.utils.TypeAliases.IdMap
import org.json4s.{JObject, JValue}


// Card (question, models, metrics) data model. When making GET requests, metabase returns more fields, but these are the
// ones we need to later make a POST/PUT to create the card in the target metabase
case class Card(
  id: BigInt,
  name: String,
  parameters: JValue,
  parameter_mappings: JValue,
  dataset_query: JValue,
  `type`: Option[String],
  display: String,
  description: Option[String],
  visualization_settings: JValue,
  enable_embedding: Option[Boolean],
  embedding_params: Option[JValue],
  collection_id: Option[BigInt],
  dashboard_id: Option[BigInt],
  collection_position: Option[BigInt],
  result_metadata: JValue,
  cache_ttl: Option[String],
  collection_preview: Option[Boolean],
  based_on_upload: Option[BigInt]
) {

  private val isJObject = dataset_query match {
    case JObject(_) => true
    case _ => false
  }
  require(isJObject, s"Received Card with invalid dataset_query: $dataset_query")

  def getFieldIds: Set[BigInt] =
    MBJson.getFieldIds(dataset_query) ++
      MBJson.getFieldIds(visualization_settings) ++
      MBJson.getFieldIds(result_metadata, isResultMetadata = true) ++
      MBJson.getFieldIds(parameter_mappings) ++
      // I am not sure if in these fields we should also look for field ids but just in case...
      MBJson.getFieldIds(parameters) ++
      MBJson.getFieldIds(embedding_params.getOrElse(JObject()))

  def getTableIds: Set[BigInt] =
    MBJson.getTableIds(dataset_query)

  def getDatabaseIds: Set[BigInt] =
    MBJson.getDatabaseIds(dataset_query)

  def getSnippetIds: Set[BigInt] =
   MBJson.getSnippetIds(dataset_query)

  lazy val getCardsUsedAsTablesOrMetrics: Set[BigInt] =
    MBJson.getCardIdsUsedAsTablesOrMetrics(dataset_query)

  def replaceFieldIds(replacements: IdMap): Card = replacementMayFail(this, "Field") {
    _.copy(
      dataset_query          = MBJson.replaceFieldIds(dataset_query, replacements),
      result_metadata        = MBJson.replaceFieldIds(result_metadata, replacements, isResultMetadata = true),
      visualization_settings = MBJson.replaceFieldIds(visualization_settings, replacements),
      parameter_mappings     = MBJson.replaceFieldIds(parameter_mappings, replacements),
      // I am not sure if in these fields we should also look for field ids but just in case...
      parameters             = MBJson.replaceFieldIds(parameters, replacements),
      embedding_params       = embedding_params.map(MBJson.replaceFieldIds(_, replacements))
    )
  }

  def replaceTableIds(replacements:  IdMap): Card = replacementMayFail(this, "Table") {
    _.copy(dataset_query = MBJson.replaceTableIds(dataset_query, replacements))
  }

  def replaceCardIdsUsedAsTables(replacements: IdMap): Card = replacementMayFail(this, "Card acting as table") {
    _.copy(
      dataset_query          = MBJson.replaceCardIdsUsedAsTables(dataset_query, replacements),
      result_metadata        = MBJson.replaceCardIdsUsedAsTables(result_metadata, replacements),
      visualization_settings = MBJson.replaceCardIdsUsedAsTables(visualization_settings, replacements)
    )
  }

  def replaceCardIdsUsedAsMetrics(replacements: IdMap): Card = replacementMayFail(this, "Metric") {
    _.copy(
      dataset_query = MBJson.replaceCardIdsUsedAsMetrics(dataset_query, replacements)
    )
  }

  def replaceDatabaseIds(replacements: IdMap): Card = replacementMayFail(this, "Database") {
    _.copy(dataset_query = MBJson.replaceDatabaseIds(dataset_query, replacements))
  }

  def replaceCollectionIds(replacements: IdMap): Card = replacementMayFail(this, "Collection") {
    _.copy(collection_id = Some(replacements.getTargetCollection(collection_id.get)))
  }

  def replaceSnippets(replacements: IdMap): Card =
    replacementMayFail(this, "Snippet") {
      _.copy(dataset_query = MBJson.replaceSnippets(dataset_query, replacements))
    }

  def replaceDashboardId(replacements: IdMap): Card = replacementMayFail(this, "Dashboard") {
    _.copy(dashboard_id = dashboard_id.map(replacements(_)))
  }

  def cleanDescription: Card =
    description match {
      case Some(description) if description.trim.isEmpty => copy(description = None)
      case _ => this
    }

  def fixHyphens: Card = copy(result_metadata = MBJson.fixHyphens(result_metadata))

}
