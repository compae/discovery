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
import org.json4s.JsonAST.{JArray, JInt}
import org.json4s.{JBool, JNothing, JObject, JString, JValue}

case class Subscription(
  id: SubscriptionId,
  name: String,
  // for dashboard subscriptions the cards here are not used, but metabase still requires a non-empty
  // list of them in the API spec so we need to keep this in our model ¯\_(ツ)_/¯
  cards: JArray,
  channels: List[Channel],
  skip_if_empty: Boolean,
  collection_id: CollId,
  collection_position: Option[BigInt],
  dashboard_id: DashboardId,
  parameters: JValue,
  archived: Boolean
) {

  def useRecipientsOf(otherSubscription: Subscription): Subscription = {
    val newChannels = otherSubscription.channels.headOption.map { existingChannel =>
      channels.map(_.copy(
        recipients = existingChannel.recipients,
        details = existingChannel.details
      ))
    }.getOrElse(channels)

    copy(channels = newChannels)
  }

  def replaceCollectionId(replacements: Map[BigInt, BigInt]): Subscription = replacementMayFail(this, "Collection") {
    _.copy(collection_id = replacements.getTargetCollection(collection_id))
  }

  def replaceDashboardIds(replacements: Map[BigInt, BigInt]): Subscription = replacementMayFail(this, "Dashboard") {
    _.copy(dashboard_id = replacements.getTargetDashboard(dashboard_id))
  }

  def replaceCardIds(replacements: Map[BigInt, BigInt]): Subscription = replacementMayFail(this, "Card") {
    _.copy(parameters =  MBJson.replaceCardIds(parameters, replacements))
  }

  def clearSubscribers: Subscription = replacementMayFail(this, "Channels") {
    _.copy(channels = channels.map(_.clearSubscribers))
  }

  /**
   * Metabase only uses the cards in the body when creating normal pulses. For dashboard subscriptions they are
   * not used for anything when creating/updating the subscription. However, the API still performs some common
   * validations (which would not be necessary for the subscription case, but the code is shared) like checking
   * the body has a non-empty list of cards and making some extra validations on them. Ensuring we pass these
   * validations would force us to make more id replacements, even for the dashboard_card_ids, for which we do
   * not have an IdMap (and it is impossible to build one, there is no "extra" identifier across Metabase instances).
   *
   * So to overcome this, we just put a list with one card with the minimum fields required by the endpoint
   * validations. We also have to provide a card ID, the but ID we put is irrelevant as long as it exists in
   * the targe Metabase since this data is not really used.
   */
  def clearCards(irrelevantCardId: BigInt): Subscription = copy(
    cards = JArray(List(JObject(
      "include_csv" -> JBool(false),
      "include_xls" -> JBool(false),
      "id"          -> JInt(irrelevantCardId)
    )))
  )
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
  import Channel._

  def clearSubscribers: Channel = replacementMayFail(this, "Channel") {
    if (channel_type == "slack") _.copy(recipients = JArray(List.empty), details = DefaultSlackChannel)
    else _.copy(recipients = JArray(List.empty), details = JNothing)
  }
}

object Channel {
  final val DefaultSlackChannel: JObject =
    JObject(("channel", JString("#canal")) :: Nil)
}

