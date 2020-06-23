/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import com.stratio.discoverycicd.utils.{ApiClient, Logging}
import com.stratio.discoverycicd.utils.TypeAliases._
import com.stratio.discoverycicd.model._
import scala.annotation.tailrec

class Importer(api: ApiClient) extends Logging {

  import Importer._

  lazy val allDatabases = api.getAllDatabases
  lazy val dbId2Name = allDatabases map { db => db.id -> db.name } toMap

  lazy val allTables = api.getAllTables map { _.toTable(dbId2Name) }
  lazy val id2Table = allTables map { t => t.id -> t } toMap

  def importCollection(exportedCollection: ExportedCollection, parentCollId: Option[BigInt]) = {

    log.debug("Getting all needed source => target mappings between ids of dbs, tables and fields ")
    val dbMaps = getDbMap(exportedCollection)

    val childColls = exportedCollection.children.map(_.collection)
    val childNames = childColls filter { _.parent_id == Some(exportedCollection.collection.id) } map { _.name }

    // create necessary items while getting the mapping between source and target ids
    log.debug("Importing root collection from export data")
    val (sourceCollId, targetCollId) = createCollection(exportedCollection.collection, parentCollId, childNames)

    if (childColls.nonEmpty) log.debug("Importing subcollections of root collection from export data")
    val collMap = createSubCollections(childColls, Map(sourceCollId -> targetCollId))

    log.debug("Importing cards from export data")
    val cardMap = createCards(exportedCollection.cards, dbMaps, collMap)

    log.debug("Importing dashboards from export data")
    createDashboards(exportedCollection.dashboards, dbMaps, collMap, cardMap)

    // update permissions in collection and its children
    log.debug(s"Importing Permissions of collection $targetCollId (id in target)")
    updatePermissionsInColl(targetCollId, exportedCollection.permissionsPerGroup)
    exportedCollection.children.foreach { case Child(coll, permissionMap) =>
      log.debug(s"Importing Permissions of subcollection ${coll.id} (id in target)")
      updatePermissionsInColl(collMap(coll.id), permissionMap)
    }
  }

  def updatePermissionsInColl(targetCollId: BigInt, sourcePermissionsPerGroup: Map[GroupName, Permission]) = {
    val targetGroupNameToId = api.getGroupNameToIdMap
    val targetPermissionsPerGroup = sourcePermissionsPerGroup flatMap {
      case (gName, permission) if targetGroupNameToId contains gName =>
        Some((targetGroupNameToId(gName), permission))

      case (gName, _) =>
        log.warn(s"Permission group with name $gName needed to set permissions for collection $targetCollId (target Id) was not found in target Metabase. This collection will be imported with permissions only to admin users")
        None
    }
    val collectionGraph = api.getCollectionGraph.updatePermissionInColl(targetCollId, targetPermissionsPerGroup)
    api.updateCollectionGraph(collectionGraph)
  }

  def createDashboards(dashboards: List[Dashboard], dbMaps: DbMaps, collMap: IdMap, cardMap: IdMap) =
    dashboards.foreach { dashboard =>
      log.debug(s"Creating dashboard from exported data: ${dashboard.toJson}")
      log.debug(s"Replacing collection, database, table and field Ids in dashboard")
      val dashboardToPost = dashboard
        .copy(creator_id = api.userId)
        .replaceCollectionId(collMap)
        .replaceDatabaseIds(dbMaps.db)
        .replaceTableIds(dbMaps.table)
        .replaceFieldIds(dbMaps.field)
        .replaceCardIds(cardMap)

      createDashboard(dashboardToPost)
    }


  def createDashboard(dashboard: Dashboard) = {
      // create dashboard and get its id
      log.debug("create (POST) dashboard in Metabase")
      val savedDashboardId = api.createDashboard(dashboard)

      // add parameters that cannot be set in the creation post request
      log.debug("update (PUT) dashboard with extra parameters")
      api.updateDashboard(savedDashboardId, dashboard)

      // add the needed cards as dashcards, and update the dashcard list with the new ids
      log.debug("Adding dashcards to dashboard")
      val dashCardsWithNewIds = dashboard.ordered_cards.map { dashCard =>
        log.debug(s"Adding dashcard ${dashCard.toJson} to dashboard")
        val dashCardId = api.addCardToDashboard(savedDashboardId, CardPost(dashCard.card_id)).id
        dashCard.copy(id = dashCardId, dashboard_id = savedDashboardId)
      }

      // add the correct positions, settings etc to all dashcards
      log.debug("Updating dashcards in dashboard with correct positions, settings, etc...")
      api.updateDashCardsInDashboard(savedDashboardId, dashCardsWithNewIds)
  }

  def createCards(cards: List[Card], dbMaps: DbMaps, collMap: IdMap): IdMap = {
    cards.foldLeft(Map.empty[BigInt, BigInt]) { (map, card) =>
      log.debug(s"Creating card from export data: ${card.toJson}")
      log.debug(s"Replacing collection, database, table and field Ids in card")
      val cardWithTargetIds = card
        .replaceCollectionIds(collMap)
        .replaceDatabaseIds(dbMaps.db)
        .replaceTableIds(dbMaps.table)
        .replaceFieldIds(dbMaps.field)

      log.debug("Uploading card to Metabase")
      map + (cardWithTargetIds.id -> api.createCard(cardWithTargetIds.toPostDto).id)
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
    log.debug(s"Creating collection from export data: ${sourceColl.toJson}")
    val existsInTarget = api.getItemsInCollecton(parentCollId)
      .filter(_.model == "collection")
      .filter(_.name == sourceColl.name)

    assert(
      existsInTarget.size <= 1,
      "Found more than one collection with the same name as the collection to import in the parent collection. Do not know which one overwrite"
    )

    // Create collection and get its id if it does not exits, otherwise delete all its elements
    // the subcolections that are not included in this export are archived or if
    // we see that we are going to create a new one and archive an old one we
    // simply rename the old one to the new name
    val targetCollId = existsInTarget.headOption.map(_.id) match {
      case Some(existingCollId) =>
        log.debug("A collection with the same name as the imported collection in the parent collection exists. Will now overwrite its contents")
        val (existingSubcollections, existingNonCollItems) = api.getItemsInCollecton(existingCollId)
          .partition { _.model == "collection" }

        // delete all non-collection items
        log.debug("Deleting non-collection items from existing collection")
        existingNonCollItems.foreach(api.deleteCollectionItem)

        // delete the collections that are currently present in target but not in the export
        val collsToArchive = existingSubcollections filterNot { childCollNames contains _.name }
        if (collsToArchive.nonEmpty) log.debug(
          s"The following subcollections in the exisiting collection are not present in the export data. Will now archive them: ${collsToArchive.map(_.toJson)}"
        )
        collsToArchive.map(_.id).foreach(api.archiveCollecton)

        existingCollId

      case None =>
        log.debug("Imported collection does not exists inside the parent collection. Will now create it")
        api.createCollection(sourceColl.copy(parent_id = parentCollId)).id
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
    if (api.humanizationEnabled)
      log.warn("Metabase seems to have table and field names humanization activated. This will make necessary to get all fields in the DBs when importing")
    if (exportedCollection.humanizationEnabled)
      log.warn("Export data was taken in a Metabase with humanization for table and field names activated. This will make necessary to get all fields in the DBs when importing")
    val needAllFields = api.humanizationEnabled || exportedCollection.humanizationEnabled

    val databases = allDatabases filter { db => exportedCollection.databases exists (_ isTheSameAs db) }
    assert(exportedCollection.databases.size == databases.size,
           "Some of the needed databases to perform the import are not found in the target metabase")

    val tables = if (exportedCollection.tables.isEmpty) List.empty
                 else allTables filter { t => exportedCollection.tables exists (_ isTheSameAs t) }
    lazy val missingTables = exportedCollection.tables.filterNot { t => allTables exists (_ isTheSameAs t) }
    assert(exportedCollection.tables.size == tables.size,
           s"Some of the needed tables to perform the import are not found in the target metabase:\nNeeded: ${exportedCollection.tables.map(_.toJson)}\n\nMissing: ${missingTables.map(_.toJson)}\n\nAll found tables: ${allTables.map(_.toJson)}")

    val fields =
      if (exportedCollection.fields.isEmpty) List.empty
      else for {
        db            <- databases
        dbFieldDto    <- if (needAllFields) api.getFieldsInDb(db.id)
                         else api.getFieldsInDb(db.id) filter {
                           x => exportedCollection.fields exists (_.isTheSameAs(x, db.name))
                         }
        fieldDto       = api.getField(dbFieldDto.id)
        // we may not have the table in the map if the table happens to be inactive
        actualTableMap = if (id2Table contains fieldDto.table_id) id2Table
                         else Map(fieldDto.table_id -> api.getTable(fieldDto.table_id).toTable(dbId2Name))
        field          = fieldDto.toField(actualTableMap)
        if exportedCollection.fields exists (_ isTheSameAs field)
      } yield field

    assert(exportedCollection.fields.size == fields.size,
           s"Some of the needed fields to perform the import are not found in the target metabase.\n\nNeeded: ${exportedCollection.fields.map(_.toJson)}.\n\nFound: ${fields.map(_.toJson)}")

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
