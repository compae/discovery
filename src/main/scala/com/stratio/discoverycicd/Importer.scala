/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import com.stratio.discoverycicd.utils.ApiClient
import com.stratio.discoverycicd.utils.TypeAliases._
import com.stratio.discoverycicd.model._

class Importer(api: ApiClient) {

  import Importer._

  lazy val allDatabases = api.getAllDatabases
  lazy val dbId2Name = allDatabases map { db => db.id -> db.name } toMap

  lazy val allTables = api.getAllTables map { _.toTable(dbId2Name) }
  lazy val id2Table = allTables map { t => t.id -> t } toMap

  def importCollection(exportedCollection: ExportedCollection, parentCollId: Option[BigInt]) = {

    val dbMaps = getDbMap(exportedCollection)
    val collMap = createCollection(exportedCollection.collection, parentCollId)
    val cardMap = createCards(exportedCollection.cards, dbMaps, collMap)
    createDashboards(exportedCollection.dashboards, dbMaps, collMap, cardMap)

    val targetCollId = collMap(exportedCollection.collection.id)
    updatePermissionsInColl(targetCollId, exportedCollection.permissionsPerGroup)
  }

  def updatePermissionsInColl(targetCollId: BigInt, sourcePermissionsPerGroup: Map[GroupName, Permission]) = {
    val targetGroupNameToId = api.getGroupNameToIdMap
    val targetPermissionsPerGroup = sourcePermissionsPerGroup collect {
      case (gName, permission) if targetGroupNameToId contains gName => (targetGroupNameToId(gName), permission)
    }
    val collectionGraph = api.getCollectionGraph.updatePermissionInColl(targetCollId, targetPermissionsPerGroup)
    api.updateCollectionGraph(collectionGraph)
  }

  def createDashboards(dashboards: List[Dashboard], dbMaps: DbMaps, collMap: IdMap, cardMap: IdMap) = {
    val dashboardsToPost = dashboards
      .map(d => d.copy(collection_id = d.collection_id.map(collMap)))
      .map(_.copy(creator_id = api.userId))
      .map(_.replaceDatabaseIds(dbMaps.db))
      .map(_.replaceTableIds(dbMaps.table))
      .map(_.replaceFieldIds(dbMaps.field))
      .map(_.replaceCardIds(cardMap))

    dashboardsToPost.foreach(createDashboard)
  }

  def createDashboard(dashboard: Dashboard) = {
      // create dashboard and get its id
      val savedDashboardId = api.createDashboard(dashboard)

      // add parameters that cannot be set in the creation post request
      api.updateDashboard(savedDashboardId, dashboard)

      // add the needed cards as dashcards, and update the dashcard list with the new ids
      val dashCardsWithNewIds = dashboard.ordered_cards.map { dashCard =>
        val dashCardId = api.addCardToDashboard(savedDashboardId, CardPost(dashCard.card_id)).id
        dashCard.copy(id = dashCardId, dashboard_id = savedDashboardId)
      }

      // add the correct positions, settings etc to all dashcards
      api.updateDashCardsInDashboard(savedDashboardId, dashCardsWithNewIds)
  }

  def createCards(cards: List[Card], dbMaps: DbMaps, collMap: IdMap): IdMap = {
    val cardsToPost = cards
      .map(_.replaceCollectionIds(collMap))
      .map(_.replaceDatabaseIds(dbMaps.db))
      .map(_.replaceTableIds(dbMaps.table))
      .map(_.replaceFieldIds(dbMaps.field))

    cardsToPost.foldLeft(Map.empty[BigInt, BigInt]) { (map, card) =>
      map + (card.id -> api.createCard(card.withoutId).id)
    }
  }

  // creates (or deletes all items if one exists with the same name) the collection to import
  // and returns a map mapping the source id with the target id
  def createCollection(sourceColl: Collection, parentCollId: Option[BigInt]) = {
    val existing = api.getItemsInCollecton(parentCollId)
    .filter(_.model == "collection")
    .filter(_.name == sourceColl.name)

    assert(existing.size <= 1)

    // delete all elements if exists, otherwise create collection and get its id
    val targetCollId = existing.headOption.map(_.id) match {
      case Some(existingCollId) =>
        api.getItemsInCollecton(existingCollId)
          .filter { Set("card", "dashboard") contains _.model }
          .foreach(api.deleteCollectionItem)
        existingCollId

      case None => api.createCollection(sourceColl.copy(parent_id = parentCollId)).id
    }

    Map(sourceColl.id -> targetCollId)
  }


  def getDbMap(exportedCollection: ExportedCollection) = {
    val databases = allDatabases filter { db => exportedCollection.databases exists (_ isTheSameAs db) }
    assert(exportedCollection.databases.size == databases.size,
           "Some of the needed databases to perform the import are not found in the target metabase")

    val tables = allTables filter { t => exportedCollection.tables exists (_ isTheSameAs t) }
    assert(exportedCollection.tables.size == tables.size,
           "Some of the needed tables to perform the import are not found in the target metabase")

    val fields = for {
      db <- databases
      fieldDto <- api.getFieldsInDb(db.id)
      field = api.getField(fieldDto.id).toField(id2Table)
      if exportedCollection.fields exists (_ isTheSameAs field)
    } yield field

    // Maps with id correspondence between source and target metabases
    val dBsource2Target = exportedCollection.databases map { sourceDb =>
      sourceDb.id -> databases.find(_ isTheSameAs sourceDb).get.id
    } toMap

    val tableSource2Target = exportedCollection.tables map { sourceTable =>
      sourceTable.id -> tables.find(_ isTheSameAs sourceTable).get.id
    } toMap

    val fieldSource2Target = exportedCollection.fields map { sourceField =>
      sourceField.id -> fields.find(_ isTheSameAs sourceField).get.id
    } toMap

    DbMaps(db = dBsource2Target,
           table = tableSource2Target,
           field = fieldSource2Target)
  }

}

object Importer {

  case class DbMaps(db: IdMap, table: IdMap, field: IdMap)

}
