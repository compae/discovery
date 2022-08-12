/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.ImplicitExtensions.MapExtension
import com.stratio.discoverycicd.utils.MBJson
import com.stratio.discoverycicd.utils.ReplacementUtils.replacementMayFail
import com.stratio.discoverycicd.utils.TypeAliases._
import org.json4s.JsonAST.JArray
import org.json4s.{JNothing, JObject, JString, JValue}

case class Subscription(
                         id: SubscriptionId,
                         name: String,
                         cards: List[SubscriptionCard],
                         channels: List[Channel],
                         skip_if_empty: Boolean,
                         collection_id: CollId,
                         collection_position: Option[BigInt],
                         dashboard_id: DashboardId,
                         parameters: JValue,
                         archived: Boolean
                       ) {

  def toPostDto = SubscriptionToPost(
    name, cards.map(_.toDto), channels, skip_if_empty, collection_id, collection_position, dashboard_id, parameters)

  def toPutDto(otherSubscription: Subscription): SubscriptionToPut = {
    val newChannels = otherSubscription.channels.headOption.map { existingChannel =>
      channels.map(_.copy(recipients = existingChannel.recipients, details = existingChannel.details))
    }.getOrElse(channels)

    SubscriptionToPut(
      otherSubscription.id, name, cards.map(_.toDto), newChannels, skip_if_empty, collection_id, collection_position, dashboard_id, parameters
    )
  }

  def replaceCollectionId(replacements: Map[BigInt, BigInt]): Subscription = replacementMayFail(this, "Collection") {
    _.copy(
      collection_id = replacements.getTargetCollection(collection_id),
      cards = cards.map(_.replaceCollectionIds(replacements))
    )
  }

  def replaceDashboardIds(replacements: Map[BigInt, BigInt]): Subscription = replacementMayFail(this, "Dashboard") {
    _.copy(
      dashboard_id = replacements.getTargetDashboard(dashboard_id),
      cards = cards.map(_.replaceDashboardIds(replacements))
    )
  }

  def replaceDashboardCardsIds(replacements: Map[BigInt, BigInt]): Subscription = replacementMayFail(this, "Dashboard Cards") {
    _.copy(
      cards = cards.map(_.replaceDashboardCardsIds(replacements))
    )
  }

  def replaceCardIds(replacements: Map[BigInt, BigInt]): Subscription = replacementMayFail(this, "Card") {
    _.copy(
      parameters =  MBJson.replaceCardIds(parameters, replacements),
      cards = cards.map(_.replaceCardIds(replacements))
    )
  }

  def replaceFieldIds(replacements: Map[BigInt, BigInt]): Subscription = replacementMayFail(this, "Field") {
    _.copy(cards = cards.map(_.replaceFieldIds(replacements)))
  }

  def clearSubscribers: Subscription = replacementMayFail(this, "Channels") {
    _.copy(channels = channels.map(_.clearSubscribers))
  }
}

case class SubscriptionCard(
                             id:	BigInt,
                             collection_id: CollId,
                             dashboard_id: DashboardId,
                             dashboard_card_id: BigInt,
                             description: String,
                             include_xls: Boolean,
                             name:	String,
                             parameter_mappings: JValue,
                             include_csv: Boolean,
                             display: String,
                           ) {

  def toDto = SubscriptionCardDTO(
    id, collection_id, dashboard_id, dashboard_card_id, description, include_xls, name, parameter_mappings, include_csv, display
  )

  def replaceCollectionIds(replacements: Map[BigInt, BigInt]): SubscriptionCard = replacementMayFail(this, "Collection") {
    _.copy(collection_id = replacements.getTargetCollection(collection_id))
  }

  def replaceDashboardIds(replacements: Map[BigInt, BigInt]): SubscriptionCard = replacementMayFail(this, "Dashboard") {
    _.copy(dashboard_id = replacements.getTargetDashboard(dashboard_id))
  }

  def replaceDashboardCardsIds(replacements: Map[BigInt, BigInt]): SubscriptionCard = replacementMayFail(this, "Dashboard Card") {
    _.copy(dashboard_card_id = replacements.getTargetDashboardCard(dashboard_card_id))
  }

  def replaceCardIds(replacements: Map[BigInt, BigInt]): SubscriptionCard = replacementMayFail(this, "Card") {
    _.copy(
      id = replacements.getTargetCard(id),
      parameter_mappings =  MBJson.replaceCardIds(parameter_mappings, replacements)
    )
  }

  def replaceFieldIds(replacements: Map[BigInt, BigInt]): SubscriptionCard = replacementMayFail(this, "Field") {
    _.copy(parameter_mappings = MBJson.replaceFieldIds(parameter_mappings, replacements))
  }
}

case class Channel(
                    id: BigInt,
                    pulse_id: BigInt,
                    schedule_type: String,
                    schedule_hour: Option[Int],
                    channel_type: String,
                    schedule_frame: Option[String],
                    schedule_day: Option[String],
                    enabled: Boolean,
                    recipients: JValue,
                    details: JValue
                  ) {
  final val DefaultSlackChannel = JObject(("channel", JString("#canal")) :: Nil)

  def clearSubscribers: Channel = replacementMayFail(this, "Channel") {
    if (channel_type == "slack")
      _.copy(recipients = JArray(List.empty), details = DefaultSlackChannel)
    else
      _.copy(recipients = JArray(List.empty), details = JNothing)
  }
}

/**
 * DTOs
 */

case class SubscriptionToPost(
                               name: String,
                               cards: List[SubscriptionCardDTO],
                               channels: List[Channel],
                               skip_if_empty: Boolean,
                               collection_id: CollId,
                               collection_position: Option[BigInt],
                               dashboard_id: DashboardId,
                               parameters: JValue
                             )

case class SubscriptionToPut(
                              id: SubscriptionId,
                              name: String,
                              cards: List[SubscriptionCardDTO],
                              channels: List[Channel],
                              skip_if_empty: Boolean,
                              collection_id: CollId,
                              collection_position: Option[BigInt],
                              dashboard_id: DashboardId,
                              parameters: JValue
                            )

case class SubscriptionCardDTO(
                                id:	BigInt,
                                collection_id: CollId,
                                dashboard_id: DashboardId,
                                dashboard_card_id: BigInt,
                                description: String,
                                include_xls: Boolean,
                                name:	String,
                                parameter_mappings: JValue,
                                include_csv: Boolean,
                                display: String,
                              )


