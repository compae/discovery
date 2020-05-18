/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import com.stratio.discoverycicd.utils.ApiClient
import com.stratio.discoverycicd.utils.TypeAliases._
import com.stratio.discoverycicd.model._
import scala.annotation.tailrec

class Importer(api: ApiClient, strictTableNames: Boolean = true) {

  import Importer._

  lazy val allDatabases = api.getAllDatabases
  lazy val dbId2Name = allDatabases map { db => db.id -> db.name } toMap

  lazy val allTables = api.getAllTables map { _.toTable(dbId2Name) }
  lazy val id2Table = allTables map { t => t.id -> t } toMap

  def importCollection(exportedCollection: ExportedCollection, parentCollId: Option[BigInt]) = {

    val childColls = exportedCollection.children.map(_.collection)
    val childNames = childColls filter { _.parent_id == Some(exportedCollection.collection.id) } map { _.name }

    // create neccesary itesm while getting the mapping between source and target ids
    val (sourceCollId, targetCollId) = createCollection(exportedCollection.collection, parentCollId, childNames)
    val collMap = createSubCollections(childColls, Map(sourceCollId -> targetCollId))
    val dbMaps = getDbMap(exportedCollection)
    val cardMap = createCards(exportedCollection.cards, dbMaps, collMap)
    createDashboards(exportedCollection.dashboards, dbMaps, collMap, cardMap)

    // update permissions in collection and its children
    updatePermissionsInColl(targetCollId, exportedCollection.permissionsPerGroup)
    exportedCollection.children.foreach { case Child(coll, permissionMap) =>
      updatePermissionsInColl(collMap(coll.id), permissionMap)
    }
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

  @tailrec
  final def createSubCollections(toCreate: List[Collection], collMap: Map[BigInt, BigInt]): Map[BigInt, BigInt] = {
    def parentIsCreated(child: Collection) =
      collMap contains child.parent_id.getOrElse(
        throw new Exception("Child existingCollections should always have a parent Id!")
      )
    toCreate match {
      case Nil =>
        collMap
      case remaining =>
        val (parentCreated, parentNotCreated) = remaining partition parentIsCreated
        val updatedCollMap = parentCreated.foldLeft(collMap) { (acc, coll) =>
          val childrenNames = parentNotCreated filter { _.parent_id == Some(coll.id) } map { _.name }
          acc + createCollection(coll, coll.parent_id.map(collMap), childrenNames)
        }
        createSubCollections(parentNotCreated, updatedCollMap)
    }
  }

  // creates (or deletes all items if one exists with the same name) the collection to import
  // and returns a map mapping the source id with the target id
  def createCollection(sourceColl: Collection,
                       parentCollId: Option[BigInt],
                       childCollNames: List[String]) = {
    val existsInTarget = api.getItemsInCollecton(parentCollId)
      .filter(_.model == "collection")
      .filter(_.name == sourceColl.name)

    assert(existsInTarget.size <= 1)

    // Create collection and get its id if it does not exits, otherwise delete all its elements
    // the subcolections that are not included in this export are archived or if
    // we see that we are going to create a new one and archive an old one we
    // simply rename the old one to the new name
    val targetCollId = existsInTarget.headOption.map(_.id) match {
      case Some(existingCollId) =>
        val (existingSubcollections, existingNonCollItems) = api.getItemsInCollecton(existingCollId)
          .partition { _.model == "collection" }

        // delete all non-collection items
        existingNonCollItems.foreach(api.deleteCollectionItem)

        // delete the collections that are currently present in target but not in the export
        val collsToArchive = existingSubcollections filterNot { childCollNames contains _.name }
        collsToArchive.map(_.id).foreach(api.archiveCollecton)

        existingCollId

      case None => api.createCollection(sourceColl.copy(parent_id = parentCollId)).id
    }

    (sourceColl.id -> targetCollId)
  }

  def deleteCollection(id: BigInt): Unit = {
    // get items in collection
    val items = api.getItemsInCollecton(id)

    // archive collection
    api.archiveCollecton(id)

    // now we delete the inner items
    val (colls, notColls) = items.partition(_.model == "collection")
    notColls.foreach(api.deleteCollectionItem)
    colls match {
      case Nil => ()
      case nonEmpty => nonEmpty.map(_.id).foreach(deleteCollection)
    }
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
      dbFieldDto <- if (strictTableNames) api.getFieldsInDb(db.id)
                    // TODO: this below is not working as expected. Check it.
                    // For now we do not optimize and always use stric table names
                    else api.getFieldsInDb(db.id) filter {
                      x => exportedCollection.fields exists (_.isSimilarTo(x, db.name))
                    }
      fieldDto = api.getField(dbFieldDto.id)
      // we may not have the table in the map if the table happens to be inactive
      actualTableMap = if (id2Table contains fieldDto.table_id) id2Table
                       else Map(fieldDto.table_id -> api.getTable(fieldDto.table_id).toTable(dbId2Name))
      field = fieldDto.toField(actualTableMap)
      if exportedCollection.fields exists (_ isTheSameAs field)
    } yield field

    assert(exportedCollection.fields.size == fields.size,
           s"Some of the needed fields to perform the import are not found in the target metabase. Needed: ${exportedCollection.fields}; found: $fields")

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
