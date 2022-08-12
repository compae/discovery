/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.MBJson
import com.stratio.discoverycicd.utils.ReplacementUtils.replacementMayFail
import com.stratio.discoverycicd.utils.ImplicitExtensions.MapExtension
import com.stratio.discoverycicd.utils.TypeAliases.{CollId, DashboardId}
import org.json4s.{JArray, JField, JInt, JObject, JString, JValue}

case class Dashboard(id: DashboardId,
                     name: String,
                     description: Option[String],
                     caveats: Option[String],
                     points_of_interest: Option[String],
                     show_in_getting_started: Option[Boolean],
                     parameters: JValue,
                     position: Option[BigInt],
                     collection_id: Option[CollId],
                     collection_position: Option[BigInt],
                     ordered_cards: List[DashCard],
                     creator_id: BigInt) {

  def replaceFieldIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Field") {
    _.copy(ordered_cards = ordered_cards.map(_.replaceFieldIds(replacements)))
  }

  def replaceTableIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Table") {
    _.copy(ordered_cards = ordered_cards.map(_.replaceTableIds(replacements)))
  }

  def replaceDatabaseIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Database") {
    _.copy(ordered_cards = ordered_cards.map(_.replaceDatabaseIds(replacements)))
  }

  def replaceCardIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Card") {
    _.copy(ordered_cards = ordered_cards.map(_.replaceCardIds(replacements)))
  }

  def replaceCollectionId(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail(this, "Collection") {
    _.copy(collection_id = collection_id.map(replacements.getTargetCollection))
  }

  def toDto = DashboardDto(
    name,
    description,
    caveats,
    points_of_interest,
    show_in_getting_started,
    parameters,
    position,
    collection_id,
    collection_position,
    ordered_cards,
    creator_id
  )

}

case class DashCard(id: BigInt,
                    sizeX: BigInt,
                    sizeY: BigInt,
                    row: BigInt,
                    col: BigInt,
                    dashboard_id: BigInt,
                    card_id: Option[BigInt],
                    card: MaybeId,
                    series: List[Id],
                    visualization_settings: JValue,
                    parameter_mappings: JValue) {

  def replaceFieldIds(replacements: Map[BigInt, BigInt]): DashCard = replacementMayFail(this, "Field") {
    _.copy(parameter_mappings = MBJson.replaceFieldIds(parameter_mappings, replacements))
  }

  def replaceTableIds(replacements: Map[BigInt, BigInt]): DashCard = replacementMayFail(this, "Table") {
    _.copy(parameter_mappings = MBJson.replaceTableIds(parameter_mappings, replacements))
  }

  def replaceDatabaseIds(replacements: Map[BigInt, BigInt]): DashCard = replacementMayFail(this, "Database") {
    _.copy(parameter_mappings = MBJson.replaceDatabaseIds(parameter_mappings, replacements))
  }

  def replaceCardIds(replacements: Map[BigInt, BigInt]): DashCard = replacementMayFail(this, "Card") {
    _.copy(
      card_id = card_id.map(replacements.getTargetCard),
      card = MaybeId(card_id.map(replacements.getTargetCard)),
      series = series.map(cid => Id(replacements.getTargetCard(cid.id))),
      parameter_mappings =  MBJson.replaceCardIds(parameter_mappings, replacements)
    )
  }
}

case class DashboardDto(name: String,
                        description: Option[String],
                        caveats: Option[String],
                        points_of_interest: Option[String],
                        show_in_getting_started: Option[Boolean],
                        parameters: JValue,
                        position: Option[BigInt],
                        collection_id: Option[CollId],
                        collection_position: Option[BigInt],
                        ordered_cards: List[DashCard],
                        creator_id: BigInt)

// this is used to add cards to a dashboard, we should be careful that metabase is
// looking to refactor this enpoint so the body should be card_id instead of cardId :S
case class CardPost(cardId: Option[BigInt])
