/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import com.stratio.discoverycicd.exceptions.{DuplicatedEntityException, GetDataException, ResponseDeserializationException}
import com.stratio.discoverycicd.model.Collection.Models
import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.utils.{ApiClient, Identificable, Logging, MBJson}
import com.stratio.discoverycicd.utils.Identificable.IdentificableOps
import com.stratio.discoverycicd.utils.Jsonable.JsonableOps
import org.json4s.{DefaultFormats, Extraction}
import org.json4s.JsonAST.JValue

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class Exporter(api: ApiClient) extends Logging {


  def exportCollection(collectionId: BigInt) = {
    implicit val formats = DefaultFormats

    if (api.humanizationEnabled)
      log.warn("Metabase seems to have table and field names humanization activated. This will make necessary to get all fields in the DB when importing")

    // get collection
    val collection =
      Try {
        api.getCollection(collectionId)
      } match {
        case Success(collection) => collection
        case Failure(exRes: ResponseDeserializationException) => throw GetDataException(s"An error occurred getting the collection with id:[$collectionId]. Cause: ${exRes.msg}.")
        case Failure(ex) => throw GetDataException(s"An error occurred getting the collection with id:[$collectionId]. Cause: ${ex.getMessage}.")
      }

    // get cards, dashboards and subcollections in collection
    val items = itemsInCollAndSubColls(collectionId)
    val cardIdsInColl = getCardIdsInColl(items)
    val dashboardIdsInColl = getDashboardIdsInColl(items)
    val collsIdsInColl = getCollsInColl(items)

    val cards = getCards(cardIdsInColl)
    val dashboards = getDashboards(dashboardIdsInColl)
    val subCollections = getCollections(collsIdsInColl)

    // we do not allow to export two entities with the same identifier across metabase instances
    checkDuplicates(cards)
    checkDuplicates(dashboards)
    checkDuplicates(collection :: subCollections)

    val dashboardsJson = Extraction.decompose(dashboards)
    checkAllCardsAreInCollection(cardIdsInColl, dashboardsJson)

    // Collect needed DB data to be able to obtain in the target metabase all field, table and database IDs
    val databaseIds = cards.map(_.getDatabaseIds).fold(Set.empty)(_ union _) union MBJson.getDatabaseIds(dashboardsJson)
    val fieldIds = cards.map(_.getFieldIds).fold(Set.empty)(_ union _) union MBJson.getFieldIds(dashboardsJson)
    val fieldDtos = fieldIds.map(api.getField)
    val tableIds = cards.map(_.getTableIds).fold(Set.empty)(_ union _)
      .union(MBJson.getTableIds(dashboardsJson))
      .union(fieldDtos.map(_.table_id).toSet)

    val databases = databaseIds map api.getDatabase
    val dbId2Name = databases map { db => db.id -> db.name } toMap

    val tables = tableIds.map(api.getTable).map(_.toTable(dbId2Name))
    val id2Table = tables map { t => t.id -> t } toMap

    val fields = fieldDtos.map(_.toField(id2Table))

    // Collect permissions data
    val groupIdToName = api.getGroupIdToNameMap
    val collectionGraph =
      Try {
        api.getCollectionGraph
      } match {
        case Success(collectionGraph) => collectionGraph
        case Failure(exRes: ResponseDeserializationException) => throw GetDataException(s"An error occurred getting the permissions per group of collection with id:[$collectionId]. Cause: ${exRes.msg}.")
        case Failure(ex) => throw GetDataException(s"An error occurred getting the permissions per group of collection with id:[$collectionId]. Cause: ${ex.getMessage}.")
      }

    // of parent collection
    val permissionsPerGroup =
      collectionGraph.groups.map {
        case (gId, map) => (groupIdToName(gId), map(collectionId.toString))
      }
    // of children
    val children = subCollections map { coll =>
      val permissionsPerGroup = collectionGraph.groups.map {
        case (gId, map) => (groupIdToName(gId), map(coll.id.toString))
      }
      Child(coll, permissionsPerGroup)
    }

    ExportedCollection(
      cards = cards,
      dashboards = dashboards,
      fields = fields.toList,
      tables = tables.toList,
      databases = databases.toList,
      collection = collection,
      children = children,
      permissionsPerGroup = permissionsPerGroup,
      humanizationEnabled = api.humanizationEnabled
    )
  }

  def itemsInCollAndSubColls(collectionId: BigInt): List[CollectionItem] = {
    @tailrec
    def loop(collectionIds: List[BigInt], itemsInParents: List[CollectionItem]): List[CollectionItem] = {
      val items = collectionIds flatMap itemsInColl
      getCollsInColl(items) match {
        case Nil => itemsInParents ++ items
        case childrenCollIds => loop(childrenCollIds, itemsInParents ++ items)
      }
    }
    loop(List(collectionId), List.empty)
  }


  def checkAllCardsAreInCollection(cardIdsInColl: List[BigInt], dashoardsJson: JValue) = {
    val cardsInDashboards = MBJson.getCardIs(dashoardsJson).toSet
    val neededCardsNotInColl = cardsInDashboards diff cardIdsInColl.toSet
    require(neededCardsNotInColl isEmpty,
      s"""The question with ids '${neededCardsNotInColl.mkString(",")}' are used in some dashboard but are not present in the collection"""
    )
  }

  def itemsInColl(collectionId: BigInt) = api.getItemsInCollecton(collectionId)

  def getDashboardIdsInColl(items: List[CollectionItem]) =
    items collect { case CollectionItem(id, _, Models.DASHBOARD) => id }

  def getCardIdsInColl(items: List[CollectionItem]) =
    items collect { case CollectionItem(id, _, Models.CARD) => id }

  def getCollsInColl(items: List[CollectionItem]) =
    items collect { case CollectionItem(id, _, Models.COLLECTION) => id }

  def getDashboards(dashboardIds: List[BigInt]) = dashboardIds map api.getDashboard

  def getCards(cardIds: List[BigInt]) = cardIds.map(api.getCard).map(_.cleanDescription)

  def getCollections(collIds: List[BigInt]) = collIds map api.getCollection

  def checkDuplicates[A: Identificable](items: List[A]): Unit = {
    val duplicatedItems = items
      .groupBy(_.identifier)
      .collect {
        case (identifier, duplicates) if duplicates.length > 1 =>
          Map("identifier" -> identifier.toString, "duplicatedItems" -> duplicates)
      }

    if (duplicatedItems.nonEmpty) {
      val itemType = items.head.getClass.getName.split("\\.").last
      val errorMsg =
        "Cannot export. Exporting items with the same identifier across instances (usually name) is not supported. " +
          s"Found the following duplicated items of type $itemType: ${duplicatedItems.toJson}"

      log.error(errorMsg)
      throw new DuplicatedEntityException(errorMsg)
    }
  }

}
