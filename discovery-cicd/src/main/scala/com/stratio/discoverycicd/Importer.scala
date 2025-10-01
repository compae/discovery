/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import com.stratio.discoverycicd.exceptions.ValidationDataException
import com.stratio.discoverycicd.utils.{ApiClient, Logging}
import com.stratio.discoverycicd.utils.TypeAliases._
import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.model.Collection.Models
import com.stratio.discoverycicd.utils.Identificable.IdentificableOps
import com.stratio.discoverycicd.utils.Jsonable.JsonableOps

import scala.annotation.tailrec
import org.json4s.JsonAST.JObject

class Importer(api: ApiClient) extends Logging {

  import Importer._

  private lazy val allDatabases = api.getAllDatabases
  private lazy val allTables = api.getAllTables
  private lazy val allSnippets = api.getAllSnippets
  private lazy val snippetNameToId = allSnippets.map { s => s.name -> s.id }.toMap

  def importCollection(exportedCollection: ExportedCollection, parentCollId: Option[BigInt]): Unit = {

    log.debug("Getting all needed source => target mappings between ids of dbs, tables and fields ")
    val dbMaps = getDbMap(exportedCollection)

    log.debug(
      "Checking that all questions used as input data to other questions are present in the data to import " +
      "and that there are no circular dependencies among them"
    )
    val trees = dependencyTrees(exportedCollection.cards)
    val depNotFoundError = checkNotPresentDependencies(trees)
    val circularDepError = checkCircularDependencies(trees)
    (depNotFoundError, circularDepError) match {
      case (Some(notFound), Some(circular)) => throw ValidationDataException(notFound + "\n\n" + circular)
      case (Some(notFound), None          ) => throw ValidationDataException(notFound)
      case (None          , Some(circular)) => throw ValidationDataException(circular)
      case _                                => ()
    }

    val childColls = exportedCollection.children.map(_.collection)
    val childNames = childColls.filter(_.parent_id contains exportedCollection.collection.id).map(_.name)

    // create necessary items while getting the mapping between source and target ids
    log.debug("Importing root collection from export data")
    val ((sourceCollId, targetCollId), dashboardsInTargetColl, cardsInTargetColl) =
      createCollection(exportedCollection.collection, parentCollId, childNames)

    if (childColls.nonEmpty) log.debug("Importing subcollections of root collection from export data")
    val (collMap, dashboardsByCollection, cardsByCollection) =
      createSubCollections(
        childColls,
        Map(sourceCollId -> targetCollId),
        Map(targetCollId -> dashboardsInTargetColl),
        Map(targetCollId -> cardsInTargetColl)
      )

    log.debug("Importing snippets from export data")
    val snippetMap = createSnippets(exportedCollection.snippets)

    // Since some cards may live in the dashboards directly we need the dashboardIds map before creating the cards,
    // but we cannot create the "full" dashboards until we have the cardIds map, thus, we need to firtsly create
    // all dashboards (but without any card) to get the Ids, and then create and populate the them with cards
    log.debug("Importing dashboards with empty content")
    val dashboardMap = createEmptyDashboards(exportedCollection.dashboards, collMap, dashboardsByCollection)

    log.debug("Importing cards from export data")
    val cardMap = createCards(exportedCollection.cards, dbMaps, collMap, snippetMap, dashboardMap, cardsByCollection)

    log.debug("Importing dashboards from export data")
    createDashboards(exportedCollection.dashboards, dbMaps, collMap, cardMap, dashboardsByCollection)

    log.debug("Importing subscriptions from export data")
    createSubscriptions(exportedCollection.subscriptions, collMap, dashboardMap, cardMap, dashboardsByCollection)

    // update permissions in collection and its children
    log.debug(s"Importing Permissions of collection $targetCollId (id in target)")
    updatePermissionsInColl(targetCollId, exportedCollection.permissionsPerGroup)
    exportedCollection.children.foreach { case Child(coll, permissionMap) =>
      log.debug(s"Importing Permissions of subcollection ${coll.id} (id in target)")
      updatePermissionsInColl(collMap(coll.id), permissionMap)
    }
  }

  private def updatePermissionsInColl(targetCollId: CollId, sourcePermissionsPerGroup: Map[GroupName, Permission]) = {
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

  // Since some cards may live in the dashboards directly we need the dashboardIds map before creating the cards,
  // but we cannot create the "full" dashboards until we have the cardIds map, thus, we need to firtsly create
  // all dashboards (but without any card) to get the Ids, and then create and populate the them with cards
  private def createEmptyDashboards(
    dashboards: List[Dashboard],
    collMap: IdMap,
    existingDashboards: Map[CollId, List[CollectionItem]]
  ): IdMap = {
    dashboards
      .map(_.copy(dashcards = List.empty, parameters = JObject(), tabs = List.empty))
      .map(_.replaceCollectionId(collMap))
      .map { dashboard =>
        val idInTarget = dashboard
          .collection_id
          .flatMap(existingDashboards(_).filter(_.name == dashboard.name).headOption)
          .map(_.id)
          .getOrElse(api.createDashboard(dashboard).id)
        dashboard.id -> idInTarget
      }
      .toMap
  }

  // TODO: now that we create the emtpy dashboards beforehand maybe this could be simplified/refactored
  //  but for the moment we just leave it as is since it works correctly
  private def createDashboards(
    dashboards: List[Dashboard],
    dbMaps: DbMaps,
    collMap: IdMap,
    cardMap: IdMap,
    existingDashboards: Map[CollId, List[CollectionItem]]
  ): IdMap = {
    val dashboardSourceToTarget = dashboards.map { dashboard =>
      log.debug(s"Creating/updating dashboard from exported data: ${dashboard.toJson}")
      log.debug(s"Replacing collection, database, table and field Ids in dashboard")
      val dashboardToCreateOrUpdate =
        dashboard
          .replaceCollectionId(collMap)
          .replaceDatabaseIds(dbMaps.db)
          .replaceTableIds(dbMaps.table)
          .replaceFieldIds(dbMaps.field)
          .replaceCardIds(cardMap)
          .withNegativeDashcardsAndTabs

      val createdOrUpdatedDashboard = dashboardToCreateOrUpdate
        .collection_id
        .flatMap {
          collId =>
            existingDashboards(collId).filter(_.name == dashboard.name) match {
              case Nil => None
              case head :: Nil => Some(head)
              case head :: tail =>
                log.warn(s"Found ${tail.length + 1} dashboards in the target collection with the same name as the imported one: '${dashboard.name}'. Will update the one with id ${head.id} and delete the rest (ids: ${tail.map(_.id)}).")
                tail.foreach(api.deleteCollectionItem)
                Some(head)
            }
        }
        .map {
          existingDashboard =>
            log.debug(s"dashboard with name ${existingDashboard.name} already present in collection. Removing all its dashcards and tabs")
            // we cannot yet create the dashcards since we need the full dashboardIds mapping to replace dashboardIds in dashcard links.
            api.updateDashboard(
              existingDashboard.id,
              dashboardToCreateOrUpdate.copy(dashcards = List.empty, tabs = List.empty)
            )
            dashboardToCreateOrUpdate.copy(id = existingDashboard.id)
        }
        .getOrElse {
          log.debug(s"create (POST) dashboard with name ${dashboardToCreateOrUpdate.name} in Metabase")
          // we cannot yet create the dashcards since we need the full dashboardIds mapping to replace dashboardIds in dashcard links.
          api.createDashboard(dashboardToCreateOrUpdate.copy(dashcards = List.empty, tabs = List.empty))
            .copy(dashcards = dashboardToCreateOrUpdate.dashcards, tabs = dashboardToCreateOrUpdate.tabs)
        }

      dashboard.id -> createdOrUpdatedDashboard

    }.toMap

    val dashboardIdSourceToTarget = dashboardSourceToTarget.mapValues(_.id)

    // add parameters that cannot be set in the creation post request, including the dashcards and tabs
    dashboardSourceToTarget.values.foreach { dashboard =>
      log.debug(s"update (PUT) dashboard with name ${dashboard.name} to add extra parameters, dashcards and tabs")
      api.updateDashboard(dashboard.id, dashboard.replaceDashboardLinks(dashboardIdSourceToTarget))
    }

    // Delete dashboards that have not been created or updated (not present in export data)
    val dashboardsToDelete = existingDashboards.values.flatten.filterNot(dashboardIdSourceToTarget.toMap.values.toSet contains _.id)
    log.debug(s"Deleting dashboards not present in export data $dashboardsToDelete")
    dashboardsToDelete.foreach(api.deleteCollectionItem)

    dashboardIdSourceToTarget
  }


  private def createCards(
    cards: List[Card],
    dbMaps: DbMaps,
    collMap: IdMap,
    snippetMap: IdMap,
    dashboardMap: IdMap,
    existingCards: Map[CollId, List[CollectionItem]]
  ): IdMap = {
    @tailrec
    def createCardsWithSatisfiedDependencies(cards: List[Card], createdCardsMap: IdMap): IdMap = cards match {
      // base case
      case Nil =>
        createdCardsMap

      // recursive case
      case pendingCards =>
        // we can only create those whose card dependencies are already created
        val cardsToCreate = pendingCards.filter(_.getCardsUsedAsTablesOrMetrics subsetOf createdCardsMap.keySet)

        // sanity check (and guard to avoid infinite recursion) in case we receive inconsistent data
        if (cardsToCreate.isEmpty) {
          // we still have pending cards to create, but they depend on other cards that we have not created yet:
          // either the depended-on cards were not exported (were not part of the collection) or there may
          // be circular dependencies between the cards, in either case, we cannot continue with the import
          // process and must raise an error. Neither case should happen because we check the data before
          // starting the import process, but just in case...
          throw ValidationDataException(
            s"Cannot create cards '${pendingCards.map(_.id).mkString(",")}' since it is impossible to satisfy their " +
              s"card dependencies. This should have been detected in a previos check but it was missed. The import " +
              "process has been interrupted before its conclusion, some data may be inconsistent in the target metabase"
          )
        }

        val cardIdSourceToTarget = cardsToCreate.foldLeft(Map.empty[BigInt, BigInt]) { (map, card) =>
          log.debug(s"Creating card from export data: ${card.toJson}")
          log.debug(s"Replacing collection, database, table and field Ids in card")
          val cardToCreateOrUpdate = card
            .replaceCollectionIds(collMap)
            .replaceDatabaseIds(dbMaps.db)
            .replaceTableIds(dbMaps.table)
            .replaceFieldIds(dbMaps.field)
            .replaceCardIdsUsedAsTables(createdCardsMap)
            .replaceCardIdsUsedAsMetrics(createdCardsMap)
            .replaceSnippets(snippetMap)
            .replaceDashboardId(dashboardMap)

          val createdOrUpdatedCard = cardToCreateOrUpdate
            .collection_id
            .flatMap {
              collId =>
                existingCards(collId).filter(_.name == card.name) match {
                  case Nil => None
                  case head :: Nil => Some(head)
                  case head :: tail =>
                    log.warn(s"Found ${tail.length + 1} cards in the target collection with the same name as the imported one: '${card.name}'. Will update the one with id ${head.id} and delete the rest (ids: ${tail.map(_.id)}).")
                    tail.foreach(api.deleteCollectionItem)
                    Some(head)
                }
            }
            .map {
              existingCard =>
                log.debug("Updating existing card in Metabase")
                api.updateCard(cardToCreateOrUpdate.copy(id = existingCard.id))
            }
            .getOrElse {
              log.debug("Creating card to Metabase")
              api.createCard(cardToCreateOrUpdate)
            }

          map + (cardToCreateOrUpdate.id -> createdOrUpdatedCard.id)
        }

        createCardsWithSatisfiedDependencies(
          pendingCards.filterNot(cardIdSourceToTarget contains _.id), // remove the cards created in this pass from the list
          createdCardsMap ++ cardIdSourceToTarget                     // accumulate the cardId mappings created in this pass
        )
    }

    val cardIdSourceToTarget = createCardsWithSatisfiedDependencies(cards, Map.empty)

    val cardsToDelete = existingCards.values.flatten.filterNot(cardIdSourceToTarget.values.toSet contains _.id)
    log.debug(s"Deleting cards not present in export data $cardsToDelete")
    cardsToDelete.foreach(api.deleteCollectionItem)

    cardIdSourceToTarget
  }

  private def createSubscriptions(
    subscriptions: List[Subscription],
    collMap: IdMap,
    dashboardMap: IdMap,
    cardMap: IdMap,
    existingDashboards: Map[CollId, List[CollectionItem]]
  ): Unit = {
    //Find all subscriptions from target
    val existingSubscriptions = existingDashboards.values.flatten.map {
      dashboardItem => (dashboardItem.id, api.getSubscriptions(dashboardItem.id))
    }.toMap.withDefaultValue(List.empty[Subscription])

    // Create or update subscription depending on whether each subscription is found in the target
    val subscriptionIdCreatedOrUpdated = subscriptions.map { subscription =>
      log.debug(s"Creating/updating subscription from exported data: ${subscription.toJson}")
      log.debug(s"Replacing collection, dashboard and card Ids in subscription")
      val subscriptionToCreateOrUpdate =
        subscription
          .replaceCollectionId(collMap)
          .replaceDashboardIds(dashboardMap)
          .replaceCardIds(cardMap)
          .clearSubscribers

      (existingSubscriptions(subscriptionToCreateOrUpdate.dashboard_id)
          .filter(_.name == subscriptionToCreateOrUpdate.name) match {
          case Nil => None
          case head :: Nil => Some(head)
          case head :: tail =>
            log.warn(s"Found ${tail.length + 1} subscriptions in the target dashboard with the same name as the imported one: '${subscription.name}'. Will update the one with id ${head.id} and delete the rest (ids: ${tail.map(_.id)}).")
            tail.foreach(sub => api.deleteSubscription(sub.id))
            Some(head)
      }).map {
        subscription =>
          log.debug(s"Subscription with name ${subscription.name} already present in dashboard target. Updating the subscription.")
          api.updateSubscription(subscriptionToCreateOrUpdate.useRecipientsOf(subscription))
          subscription.id
      }
      .getOrElse {
        log.debug(s"Subscription with name ${subscription.name} not found in dashboard target. Creating new subscription.")
        api.createSubscription(subscriptionToCreateOrUpdate)
      }
    }

    // Delete all non-exported subscriptions that have not been found
    val subscriptionsToDelete = existingSubscriptions.values.flatten.filterNot(subscriptionIdCreatedOrUpdated contains _.id)
    log.debug(s"Deleting subscriptions not present in export data $subscriptionsToDelete")
    subscriptionsToDelete.foreach(sub => api.deleteSubscription(sub.id))
  }

  // Create or update snippets depending on whether each snippet is found in the target
  private def createSnippets(snippets: List[Snippet]): IdMap =
    snippets
      .map { snippet =>
        log.debug(s"Creating/updating snippet from exported data: ${snippets.toJson}")
        snippetNameToId
          .get(snippet.name)
          .map { existingTargetId =>
            log.debug(s"Snippet with name '${snippet.name}' already present in target. Updating the snippet.")
            api.updateSnippet(existingTargetId, snippet)
            snippet.id -> existingTargetId
          }
          .getOrElse {
            log.debug(s"Snippet with name '${snippet.name}' not found in target. Creating new snippet.")
            val targetId = api.createSnippet(snippet)
            snippet.id -> targetId
          }
      }
      .toMap

  @tailrec
  private final def createSubCollections(
    toCreate: List[Collection],
    collMap: Map[CollId, CollId],
    dashboardsInCollMap: Map[CollId, List[CollectionItem]],
    cardsInCollMap: Map[CollId, List[CollectionItem]]
  ): (Map[CollId, CollId], Map[CollId, List[CollectionItem]], Map[CollId, List[CollectionItem]]) = {

    def parentIsCreated(child: Collection): Boolean =
      collMap contains child.parent_id.getOrElse(
        throw new ValidationDataException("Child existingCollections should always have a parent Id!")
      )

    toCreate match {
      case Nil =>
        (collMap, dashboardsInCollMap, cardsInCollMap)

      case remaining =>
        val (parentCreated, parentNotCreated) = remaining partition parentIsCreated
        val (updatedCollMap, updatedDashboardsInCollMap, updatedCardsInCollMap) =
          parentCreated.foldLeft((collMap, dashboardsInCollMap, cardsInCollMap)) {
            case ((accColl, accDash, accCard), coll) =>
              val childrenNames = parentNotCreated.filter(_.parent_id contains coll.id).map(_.name)
              val ((source, target), dashboards, cards) = createCollection(coll, coll.parent_id.map(collMap), childrenNames)
              (
                accColl + (source -> target),
                accDash + (target -> dashboards),
                accCard + (target -> cards)
              )
          }
        createSubCollections(parentNotCreated, updatedCollMap, updatedDashboardsInCollMap, updatedCardsInCollMap)
    }
  }

  // creates the collection to import, or if it already exists, collect all its dashboards and cards,
  // and returns a map mapping the source id with the target id
  private def createCollection(
    sourceColl: Collection,
    parentCollId: Option[BigInt],
    childCollNames: List[String]
  ): ((CollId, CollId), List[CollectionItem], List[CollectionItem]) = {
    log.debug(s"Creating collection from export data: ${sourceColl.toJson}")
    val existsInTarget = api.getItemsInCollection(parentCollId)
      .filter(_.model == Models.COLLECTION)
      .filter(_.name == sourceColl.name)

    require(
      existsInTarget.size <= 1,
      "Found more than one collection with the same name as the collection to import in the parent collection. Do not know which one overwrite"
    )

    // Create collection and get its id if it does not exists, otherwise store all its elements (cards and dashboards)
    // the subcollections that are not included in this export are archived or if
    // we see that we are going to create a new one and archive an old one we
    // simply rename the old one to the new name
    val (targetCollId, dashboards, cards) = existsInTarget.headOption.map(_.id) match {
      case Some(existingCollId) =>
        log.debug("A collection with the same name as the imported collection in the parent collection exists. Will now overwrite its contents")

        val existingItems          = api.getItemsInCollection(existingCollId).groupBy(_.model)
        val existingSubcollections = existingItems.getOrElse(Models.COLLECTION, List.empty)
        val existingDashboards     = existingItems.getOrElse(Models.DASHBOARD , List.empty)
        val existingNormalCards    = existingItems.getOrElse(Models.CARD      , List.empty)
        val existingCardsModels    = existingItems.getOrElse(Models.DATASET   , List.empty)
        val existingCardsMetrics   = existingItems.getOrElse(Models.METRIC    , List.empty)
        val existingCards          = existingNormalCards ++ existingCardsModels ++ existingCardsMetrics

        // delete the collections that are currently present in target but not in the export
        val collsToArchive = existingSubcollections filterNot { childCollNames contains _.name }
        if (collsToArchive.nonEmpty) log.debug(
          s"The following subcollections in the existing collection are not present in the export data. Will now archive them: ${collsToArchive.map(_.toJson)}"
        )
        collsToArchive.map(_.id).foreach(api.archiveCollection)

        (existingCollId, existingDashboards, existingCards)

      case None =>
        log.debug("Imported collection does not exists inside the parent collection. Will now create it")
        val collId = api.createCollection(sourceColl.copy(parent_id = parentCollId)).id
        (collId, List.empty[CollectionItem], List.empty[CollectionItem])
    }

    ((sourceColl.id -> targetCollId), dashboards, cards)
  }

  private def deleteCollection(id: BigInt): Unit = {
    // get items in collection
    val items = api.getItemsInCollection(id)

    // archive collection
    api.archiveCollection(id)

    // now we delete the inner items
    val (colls, notColls) = items.partition(_.model == Models.COLLECTION)
    notColls.foreach(api.deleteCollectionItem)
    colls match {
      case Nil => ()
      case nonEmpty => nonEmpty.map(_.id).foreach(deleteCollection)
    }
  }

  private def getDbMap(exportedCollection: ExportedCollection): DbMaps = {

    val databases = allDatabases filter { db => exportedCollection.databases exists (_ isTheSameAs db) }
    lazy val missingDatabases = exportedCollection.databases.filterNot { db => allDatabases exists (_ isTheSameAs db) }
    require(
      exportedCollection.databases.size == databases.size,
      "Some of the needed Metabase databases to perform the import are not found in the target metabase:" +
        s"\nNeeded ${exportedCollection.databases.map(_.toJson)}\n\nFound: ${missingDatabases.map(_.toJson)}"
    )

    val tables = if (exportedCollection.tables.isEmpty) List.empty
                 else allTables filter { t => exportedCollection.tables exists (_ isTheSameAs t) }
    lazy val missingTables = exportedCollection.tables.filterNot { t => allTables exists (_ isTheSameAs t) }
    require(exportedCollection.tables.size == tables.size,
           s"Some of the needed tables to perform the import are not found in the target metabase:\nNeeded: ${exportedCollection.tables.map(_.toJson)}\n\nMissing: ${missingTables.map(_.toJson)}\n\nAll found tables: ${allTables.map(_.toJson)}")

    val fields =
      if (exportedCollection.fields.isEmpty) List.empty
      else databases.flatMap { db =>
        api
          .getFieldsInDb(db.id)
          .map(_.toField(db.name))
          .filter(field => exportedCollection.fields exists (_ isTheSameAs field))
      }
    require(exportedCollection.fields.size == fields.size,
           s"Some of the needed fields to perform the import are not found in the target metabase.\n\nNeeded: ${exportedCollection.fields.map(_.toJson)}.\n\nFound: ${fields.map(_.toJson)}")

    // Maps with id correspondence between source and target metabases
    val dBSource2Target: IdMap =
      exportedCollection
        .databases
        .map { sourceDb => sourceDb.id -> databases.find(_ isTheSameAs sourceDb).get.id }
        .toMap

    val tableSource2Target: IdMap =
      exportedCollection
        .tables
        .map { sourceTable => sourceTable.id -> tables.find(_ isTheSameAs sourceTable).get.id }
        .toMap

    val fieldSource2Target: IdMap =
      exportedCollection
        .fields
        .map { sourceField => sourceField.id -> fields.find(_ isTheSameAs sourceField).get.id }
        .toMap

    DbMaps(
      db = dBSource2Target,
      table = tableSource2Target,
      field = fieldSource2Target
    )
  }

}

object Importer {

  case class DbMaps(db: IdMap, table: IdMap, field: IdMap)

  sealed trait DependencyTree extends Product with Serializable {
    def id: BigInt
    def children: Set[DependencyTree]
    def notPresentDependencies: Set[NotFoundDep]
    def circularDependencies: Set[CircularDep]
  }
  case class OkDep(id: BigInt, children: Set[DependencyTree]) extends DependencyTree {
    def notPresentDependencies: Set[NotFoundDep] = children.flatMap(_.notPresentDependencies)
    def circularDependencies: Set[CircularDep] = children.flatMap(_.circularDependencies)
  }
  case class CircularDep(id: BigInt, cycle: List[BigInt]) extends DependencyTree {
    def children: Set[DependencyTree] = Set.empty
    def notPresentDependencies: Set[NotFoundDep] = Set.empty
    def circularDependencies: Set[CircularDep] = Set(this)
  }
  case class NotFoundDep(id: BigInt, neededBy: BigInt) extends DependencyTree {
    def children: Set[DependencyTree] = Set.empty
    def notPresentDependencies: Set[NotFoundDep] = Set(this)
    def circularDependencies: Set[CircularDep] = Set.empty
  }

  private def dependencyTrees(cardsInData: List[Card]): List[DependencyTree] = {
    def dependencyTree(cardId: BigInt): DependencyTree = {
      def loop(cardId: BigInt, lineage: List[BigInt]): DependencyTree = {
        if (lineage contains cardId) CircularDep(cardId, lineage.reverse.dropWhile(cardId != _))
        else cardsInData
          .find(_.id == cardId)
          .map {
            card =>
              // recursion ends when card does not use other cards. If all cards have dependencies, since the list
              // is finite, either there are circular dependencies (recursion finish in the if branch above)
              // or there are missing ones (recursion finish in the getOrElse below)
              val children = card.getCardsUsedAsTablesOrMetrics.map(loop(_, cardId :: lineage))
              OkDep(cardId, children)
          }
          .getOrElse(NotFoundDep(cardId, lineage.head))
      }
      loop(cardId, List.empty)
    }
    cardsInData.map(_.id).map(dependencyTree)
  }

  private def checkNotPresentDependencies(trees: List[DependencyTree]): Option[String] = {
    trees.flatMap(_.notPresentDependencies) match {
      case Nil => None
      case notPresent => Some(
        "Some cards used as input data by some imported cards are not present in the import data:\n * " +
          notPresent
            .distinct
            .map {
              case NotFoundDep(card, neededBy) =>
                s"card $card (needed by card $neededBy) is not present in the import data."
            }
            .mkString("\n * ")
      )
    }
  }

  private def checkCircularDependencies(trees: List[DependencyTree]): Option[String] = {
    trees.flatMap(_.circularDependencies) match {
      case Nil => None
      case circular => Some(
        "Circular dependencies detected in cards that use other cards as input data:\n * cycle: " +
          circular
            .distinct
            .map {
              case CircularDep(card, cycle) =>
                s"${cycle.mkString(" -> ")} -> $card."
            }
            .mkString("\n * cycle: ")
      )
    }
  }

}
