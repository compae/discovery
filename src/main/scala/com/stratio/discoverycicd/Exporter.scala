/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.utils.{ ApiClient, MBJson }
import org.json4s.{DefaultFormats, Extraction}
import org.json4s.JsonAST.JValue

class Exporter(api: ApiClient) {


  def exportCollection(collectionId: BigInt) = {
    implicit val formats = DefaultFormats

    // get collection
    val collection = api.getCollection(collectionId)

    // get cards and dashboards in collection
    val items = itemsInColl(collectionId)
    val cardIdsInColl = getCardIdsInColl(items)
    val dashboardIdsInColl = getDashboardIdsInColl(items)

    val cards = getCards(cardIdsInColl)
    val dashboards = getDashboards(dashboardIdsInColl)
    val dashboardsJson = Extraction.decompose(dashboards)

    checkAllCardsAreInCollection(cardIdsInColl, dashboardsJson)

    // Collect needed DB data to be able to obtain in the target metabase all field, table and database IDs
    val databaseIds = cards.map(_.getDatabaseIds).reduce(_ union _) union MBJson.getDatabaseIds(dashboardsJson)
    val fieldIds = cards.map(_.getFieldIds).reduce(_ union _) union MBJson.getFieldIds(dashboardsJson)
    val fieldDtos = fieldIds.map(api.getField)
    val tableIds = cards.map(_.getTableIds).reduce(_ union _)
      .union(MBJson.getTableIds(dashboardsJson))
      .union(fieldDtos.map(_.table_id).toSet)

    val databases = databaseIds map api.getDatabase
    val dbId2Name = databases map { db => db.id -> db.name } toMap

    val tables = tableIds.map(api.getTable).map(_.toTable(dbId2Name))
    val id2Table = tables map { t => t.id -> t } toMap

    val fields = fieldDtos.map(_.toField(id2Table))

    // Collect permissions data
    val groupIdToName = api.getGroupIdToNameMap
    val collectionGraph = api.getCollectionGraph
    val permissionsPerGroup = collectionGraph.groups.map {
      case (gId, map) => (groupIdToName(gId), map(collectionId.toString))
    }

    ExportedCollection(
      cards = cards,
      dashboards = dashboards,
      fields = fields.toList,
      tables = tables.toList,
      databases = databases.toList,
      collection = collection,
      permissionsPerGroup = permissionsPerGroup
    )
  }


  def checkAllCardsAreInCollection(cardIdsInColl: List[BigInt], dashoardsJson: JValue) = {
    val cardsInDashboards = MBJson.getCardIs(dashoardsJson).toSet
    val neededCardsNotInColl = cardsInDashboards diff cardIdsInColl.toSet
    assert(neededCardsNotInColl isEmpty,
      s"""The question with ids '${neededCardsNotInColl.mkString(",")}' are used in some dashboard but are not present in the collection"""
    )
  }

  def itemsInColl(collectionId: BigInt) = api.getItemsInCollecton(collectionId)

  def getDashboardIdsInColl(items: List[CollectionItem]) =
    items collect { case CollectionItem(id, _, "dashboard") => id }

  def getCardIdsInColl(items: List[CollectionItem]) =
    items collect { case CollectionItem(id, _, "card") => id }

  def getDashboards(dashboardIds: List[BigInt]) = dashboardIds map api.getDashboard

  def getCards(cardIds: List[BigInt]) = cardIds map api.getCard

}
