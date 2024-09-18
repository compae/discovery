/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import com.stratio.discoverycicd.exceptions.{DuplicatedEntityException, GetDataException, ResponseDeserializationException, UnsupportedEntityException}
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


  def exportCollection(collectionId: BigInt): ExportedCollection = {
    implicit val formats = DefaultFormats

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
    checkNoCsvUploads(cards)
    val dashboards = getDashboards(dashboardIdsInColl)
    val subCollections = getCollections(collsIdsInColl)
    val subscriptions = getSubscriptions(dashboardIdsInColl)

    val snippets = getSnippets(cards.flatMap(_.getSnippetIds).distinct)

    // we do not allow to export two entities with the same identifier across metabase instances
    checkDuplicates(cards)
    checkDuplicates(dashboards)
    checkDuplicates(dashboards.flatMap(_.tabs))
    checkDuplicates(collection :: subCollections)
    checkDuplicates(snippets) // this check could be omitted since the name is a unique identifier within a metabase instance

    val dashboardsJson = Extraction.decompose(dashboards)
    checkAllCardsAreInCollection(cardIdsInColl, dashboardsJson)
    checkAllDashboardsAreInCollection(dashboardIdsInColl, dashboardsJson)

    // Collect needed DB data to be able to obtain in the target metabase all field, table and database IDs
    val databaseIds = cards.map(_.getDatabaseIds).fold(Set.empty)(_ union _) union MBJson.getDatabaseIds(dashboardsJson)
    val fieldIds = cards.map(_.getFieldIds).fold(Set.empty)(_ union _) union MBJson.getFieldIds(dashboardsJson)
    val tableIds = cards.map(_.getTableIds).fold(Set.empty)(_ union _) union MBJson.getTableIds(dashboardsJson)

    val databases = databaseIds map api.getDatabase
    val tables = api.getAllTables.filter(tableIds contains _.id)
    val fields = databases.flatMap { db =>
      api
        .getFieldsInDb(db.id)
        .filter(fieldIds contains _.id)
        .map(_.toField(db.name))
    }

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
      tables = tables,
      databases = databases.toList,
      subscriptions = subscriptions,
      snippets = snippets,
      collection = collection,
      children = children,
      permissionsPerGroup = permissionsPerGroup,
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


  def checkAllCardsAreInCollection(cardIdsInColl: List[BigInt], dashboardsJson: JValue): Unit = {
    val cardsInDashboards = MBJson.getCardIs(dashboardsJson) ++ MBJson.getItemIdsInLinks(LinkType.Question)(dashboardsJson)
    val neededCardsNotInColl = cardsInDashboards diff cardIdsInColl.toSet
    require(neededCardsNotInColl.isEmpty,
      s"""The questions with ids '${neededCardsNotInColl.mkString(",")}' are used in some dashboard but are not present in the collection"""
    )
  }

  def checkAllDashboardsAreInCollection(dashboardIdsInColl: List[BigInt], dashboardsJson: JValue): Unit = {
    val dashboardsInDashboards = MBJson.getItemIdsInLinks(LinkType.Dashboard)(dashboardsJson)
    val neededDashboardsNotInColl = dashboardsInDashboards diff dashboardIdsInColl.toSet
    require(neededDashboardsNotInColl.isEmpty,
      s"""The dashboards with ids '${neededDashboardsNotInColl.mkString(",")}' are linked in some dashboard but are not present in the collection"""
    )
  }

  def itemsInColl(collectionId: BigInt): List[CollectionItem] =
    api.getItemsInCollection(collectionId)

  def getDashboardIdsInColl(items: List[CollectionItem]): List[BigInt] =
    items collect { case CollectionItem(id, _, Models.DASHBOARD) => id }

  def getCardIdsInColl(items: List[CollectionItem]): List[BigInt] =
    items collect { case CollectionItem(id, _, Models.CARD | Models.DATASET) => id }

  def getCollsInColl(items: List[CollectionItem]): List[BigInt] =
    items collect { case CollectionItem(id, _, Models.COLLECTION) => id }

  def getDashboards(dashboardIds: List[BigInt]): List[Dashboard] =
    dashboardIds map api.getDashboard

  def getCards(cardIds: List[BigInt]): List[Card] =
    cardIds.map(api.getCard).map(_.cleanDescription)

  def getCollections(collIds: List[BigInt]): List[Collection] =
    collIds map api.getCollection

  def getSubscriptions(dashboardIds: List[BigInt]): List[Subscription] =
    dashboardIds flatMap api.getSubscriptions

  def getSnippets(snippetIds: List[BigInt]): List[Snippet] =
    snippetIds.map(api.getSnippet)

  def checkNoCsvUploads(cards: List[Card]): Unit = {
    val csvUploadModels = cards
      .filter(_.`type` contains Collection.Models.DATASET)
      .filter(_.based_on_upload.isDefined)
    if (csvUploadModels.nonEmpty) {
      val msg =
        "Cannot export. Models based on csv uploads are not supported. The following models based" +
          s"on csv uploads were found: '${csvUploadModels.map(m => m.id + "-" + m.name).mkString(", ")}'"
      log.error(msg)
      throw UnsupportedEntityException(msg)
    }
  }

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
      throw DuplicatedEntityException(errorMsg)
    }
  }

}
