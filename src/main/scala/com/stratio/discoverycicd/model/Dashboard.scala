/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.{MBJson, ReplacementUtils}
import org.json4s.{JValue, JArray, JString, JInt, JObject, JField}
import scala.util.Try
import com.stratio.discoverycicd.utils.ReplacementException
import org.json4s.native.Serialization.write

case class Dashboard(name: String,
                     description: Option[String],
                     caveats: Option[String],
                     points_of_interest: Option[String],
                     show_in_getting_started: Option[Boolean],
                     parameters: JValue,
                     position: Option[BigInt],
                     collection_id: Option[BigInt],
                     collection_position: Option[BigInt],
                     ordered_cards: List[DashCard],
                     creator_id: BigInt) extends ReplacementUtils {

  override val entityType: String = "Dashboard"

  def replaceFieldIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail("Field") {
    copy(ordered_cards = ordered_cards.map(_.replaceFieldIds(replacements)))
  }

  def replaceTableIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail("Table") {
    copy(ordered_cards = ordered_cards.map(_.replaceTableIds(replacements)))
  }

  def replaceDatabaseIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail("Database") {
    copy(ordered_cards = ordered_cards.map(_.replaceDatabaseIds(replacements)))
  }

  def replaceCardIds(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail("Card") {
    copy(ordered_cards = ordered_cards.map(_.replaceCardIds(replacements)))
  }

  def replaceCollectionId(replacements: Map[BigInt, BigInt]): Dashboard = replacementMayFail("Collection") {
    copy(collection_id = collection_id.map(replacements.getTargetCollection))
  }



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
                    parameter_mappings: JValue) extends ReplacementUtils {

  override val entityType: String = "DashCard"

  def replaceFieldIds(replacements: Map[BigInt, BigInt]): DashCard = replacementMayFail("Field") {
    copy(parameter_mappings = MBJson.replaceFieldIds(parameter_mappings, replacements))
  }

  def replaceTableIds(replacements: Map[BigInt, BigInt]): DashCard = replacementMayFail("Table") {
    copy(parameter_mappings = MBJson.replaceTableIds(parameter_mappings, replacements))
  }

  def replaceDatabaseIds(replacements: Map[BigInt, BigInt]): DashCard = replacementMayFail("Database") {
    copy(parameter_mappings = MBJson.replaceDatabaseIds(parameter_mappings, replacements))
  }

  def replaceCardIds(replacements: Map[BigInt, BigInt]): DashCard = replacementMayFail("Card") {
    copy(
      card_id = card_id.map(replacements.getTargetCard),
      card = MaybeId(card_id.map(replacements.getTargetCard)),
      series = series.map(cid => Id(replacements.getTargetCard(cid.id))),
      parameter_mappings =  MBJson.replaceCardIds(parameter_mappings, replacements)
    )
  }
}

case class DashboardPut(collection_id: Option[BigInt],
                        description: Option[String],
                        collection_position: Option[BigInt],
                        position: Option[BigInt])

// this is used to add cards to a dashboard, we should be careful that metabase is
// looking to refactor this enpoint so the body should be card_id instead of cardId :S
case class CardPost(cardId: Option[BigInt])
