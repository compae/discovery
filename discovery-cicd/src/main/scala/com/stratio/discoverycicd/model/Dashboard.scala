/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.MBJson
import com.stratio.discoverycicd.utils.ReplacementUtils.replacementMayFail
import com.stratio.discoverycicd.utils.ImplicitExtensions.MapExtension
import com.stratio.discoverycicd.utils.TypeAliases.{CollId, DashboardId, IdMap}
import org.json4s.{JArray, JField, JInt, JObject, JString, JValue}

case class Dashboard(
  id: BigInt,
  name : String,
  description: Option[String],
  caveats: Option[String],
  points_of_interest: Option[String],
  show_in_getting_started: Option[Boolean],
  parameters: JValue,
  position: Option[String],
  collection_id: Option[CollId],
  collection_position: BigInt,
  cache_ttl: BigInt,
  dashcards: List[Dashcard],
  tabs: List[DashboardTab],
  enable_embedding: Boolean,
  embedding_params: JValue,
  width: Option[String]
) {

  def replaceFieldIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Field") {
    _.copy(dashcards = dashcards.map(_.replaceFieldIds(replacements)))
  }

  def replaceTableIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Table") {
    _.copy(dashcards = dashcards.map(_.replaceTableIds(replacements)))
  }

  def replaceDatabaseIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Database") {
    _.copy(dashcards = dashcards.map(_.replaceDatabaseIds(replacements)))
  }

  def replaceCardIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Card") {
    _.copy(dashcards = dashcards.map(_.replaceCardIds(replacements)))
  }

  def replaceCollectionId(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Collection") {
    _.copy(collection_id = collection_id.map(replacements.getTargetCollection))
  }

  def replaceDashboardLinks(replacements: IdMap): Dashboard = replacementMayFail(this, "Dashboard (link)") {
    _.copy(dashcards = dashcards.map(_.replaceDashboardLinks(replacements)))
  }

  // in the metabase api, when making a dashboard update and new dashcards/tabs are added, these new dashcards/tabs
  // must have negative ids to flag that these are new dashcards. Since in our flow we always create the dashcards/tabs
  // from scratch we always have to send the dashcards with negative ids
  def withNegativeDashcardsAndTabs: Dashboard =
    copy(
      dashcards = dashcards.map(d => d.copy(id = -d.id.abs, dashboard_tab_id = d.dashboard_tab_id.map(-_.abs))),
      tabs = tabs.map(tab => tab.copy(id = -tab.id.abs))
    )

}


case class DashboardTab(
  id: BigInt,
  name: String,
  // we add the dashboard_id here just to be able to give the dashboard_id in the error message when two
  // tabs with the same name are found in a dashboard while exporting, but when creating the tabs metabase
  // ignores this and automatically adds the correct dashboard_id, so we do not need to make replacements here
  dashboard_id: BigInt,
)


case class Dashcard(
 id: BigInt,
 card_id: Option[BigInt],
 dashboard_tab_id: Option[BigInt],
 size_x: BigInt,
 size_y: BigInt,
 row: BigInt,
 col: BigInt,
 parameter_mappings: JValue,
 visualization_settings: JValue,
 series: JValue
) {

  def replaceFieldIds(replacements: Map[BigInt, BigInt]): Dashcard = replacementMayFail(this, "Field") {
    _.copy(
      parameter_mappings = MBJson.replaceFieldIds(parameter_mappings, replacements),
      visualization_settings = MBJson.replaceFieldIds(visualization_settings, replacements)
    )
  }

  def replaceTableIds(replacements: Map[BigInt, BigInt]): Dashcard = replacementMayFail(this, "Table") {
    _.copy(parameter_mappings = MBJson.replaceTableIds(parameter_mappings, replacements))
  }

  def replaceDatabaseIds(replacements: Map[BigInt, BigInt]): Dashcard = replacementMayFail(this, "Database") {
    _.copy(parameter_mappings = MBJson.replaceDatabaseIds(parameter_mappings, replacements))
  }

  def replaceCardIds(replacements: Map[BigInt, BigInt]): Dashcard = replacementMayFail(this, "Card") {
    _.copy(
      card_id = card_id.map(replacements.getTargetCard),
      series = MBJson.replaceCardIds(series, replacements),
      parameter_mappings =  MBJson.replaceCardIds(parameter_mappings, replacements),
      visualization_settings = MBJson.replaceItemIdsInDashCardLinks(LinkType.Question)(visualization_settings, replacements)
    )
  }

  def replaceDashboardLinks(replacements: IdMap): Dashcard = replacementMayFail(this, "Dashboard (link)") {
    _.copy(
      visualization_settings = MBJson.replaceItemIdsInDashCardLinks(LinkType.Dashboard)(visualization_settings, replacements)
    )
  }

  def replaceTabIds(replacements: IdMap): Dashcard = replacementMayFail(this, "Dashboard Tab") {
    _.copy(
      dashboard_tab_id = dashboard_tab_id.map(replacements.getTargetDashboardTab)
    )
  }
}
