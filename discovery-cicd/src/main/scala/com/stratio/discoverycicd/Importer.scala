/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import com.stratio.discoverycicd.exceptions.CustomException.causeOf
import com.stratio.discoverycicd.exceptions.{GetDataException, ValidationDataException}
import com.stratio.discoverycicd.utils.{ApiClient, Concurrently, Logging}
import com.stratio.discoverycicd.utils.TypeAliases._
import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.model.Collection.Models
import com.stratio.discoverycicd.progress.{Phase, ProgressReporter}
import com.stratio.discoverycicd.utils.Identificable.IdentificableOps
import com.stratio.discoverycicd.utils.Jsonable.JsonableOps

import scala.annotation.tailrec
import org.json4s.JsonAST.JArray
import scala.util.{Failure, Success, Try}

/**
 * @param parallelWrites how many questions, dashboards and subscriptions are created at a time.
 *                       Everything an import does is one request to metabase per item and nothing
 *                       else, so this is what decides how long it takes; see
 *                       [[Concurrently.WriteRequests]] for where the default comes from and
 *                       'DISCOVERY_CICD_IMPORT_PARALLEL_WRITES' for changing it without a rebuild
 */
class Importer(
  api: ApiClient,
  progress: ProgressReporter = ProgressReporter.NoOp,
  parallelWrites: Int = Concurrently.WriteRequests
) extends Logging {

  import Importer._

  private lazy val allDatabases = api.getAllDatabases
  private lazy val allSnippets = api.getAllSnippets
  private lazy val snippetNameToId = allSnippets.map { s => s.name -> s.id }.toMap

  /** An export that is already in memory: the synchronous endpoint, the shell and the tests. */
  def importCollection(exportedCollection: ExportedCollection, parentCollId: Option[BigInt]): Unit =
    importCollection(InMemoryExportSource(exportedCollection), parentCollId)

  def importCollection(source: ExportSource, parentCollId: Option[BigInt]): Unit = {
    val startedAt = System.nanoTime()
    val header = source.header

    // Unlike the export, an import knows exactly how much work it has before doing any of it: the
    // file it was given holds every question, dashboard and subscription it is going to create. So
    // every total is declared here and the status endpoint can answer '3400 of 60000' from the very
    // first poll. Only the two phases that resolve tables and fields in the target metabase learn
    // their size later, because it depends on how many schemas the export turns out to touch
    declareTotals(source)

    log.debug("Getting all needed source => target mappings between ids of dbs, tables and fields ")
    val dbMaps = getDbMap(header)

    log.debug(
      "Checking that all questions used as input data to other questions are present in the data to import " +
      "and that there are no circular dependencies among them"
    )
    val trees = progress.step(Phase.Validating)(dependencyTrees(source.cardDependencies))
    val depNotFoundError = checkNotPresentDependencies(trees)
    val circularDepError = checkCircularDependencies(trees)
    (depNotFoundError, circularDepError) match {
      case (Some(notFound), Some(circular)) => throw ValidationDataException(notFound + "\n\n" + circular)
      case (Some(notFound), None          ) => throw ValidationDataException(notFound)
      case (None          , Some(circular)) => throw ValidationDataException(circular)
      case _                                => ()
    }

    val childColls = header.children.map(_.collection)
    val childNames = childColls.filter(_.parent_id contains header.collection.id).map(_.name)

    // create necessary items while getting the mapping between source and target ids
    log.debug("Importing root collection from export data")
    val ((sourceCollId, targetCollId), dashboardsInTargetColl, cardsInTargetColl) =
      createCollection(header.collection, parentCollId, childNames)

    if (childColls.nonEmpty) log.debug("Importing subcollections of root collection from export data")
    val (collMap, dashboardsByCollection, cardsByCollection) =
      createSubCollections(
        childColls,
        Map(sourceCollId -> targetCollId),
        Map(targetCollId -> dashboardsInTargetColl),
        Map(targetCollId -> cardsInTargetColl)
      )

    log.debug("Importing snippets from export data")
    val snippetMap = createSnippets(header.snippets)

    // Since some cards may live in the dashboards directly we need the dashboardIds map before creating the cards,
    // but we cannot create the "full" dashboards until we have the cardIds map, thus, we need to firtsly create
    // all dashboards (but without any card) to get the Ids, and then create and populate the them with cards
    log.debug("Importing dashboards with empty content")
    val dashboardMap = createEmptyDashboards(source, collMap, dashboardsByCollection)

    log.debug("Importing cards from export data")
    val cardMap = createCards(source, dbMaps, collMap, snippetMap, dashboardMap, cardsByCollection)

    log.debug("Importing dashboards from export data")
    updateDashboards(source, dbMaps, collMap, cardMap, dashboardMap)
    deleteDashboardsNotInExportData(dashboardMap, dashboardsByCollection.values.flatten)

    log.debug("Importing subscriptions from export data")
    createSubscriptions(source, collMap, dashboardMap, cardMap, dashboardsByCollection)

    // update permissions in the collection and its children, in one go
    updatePermissions(
      (targetCollId -> header.permissionsPerGroup) ::
        header.children.map { case Child(coll, permissionMap) => collMap(coll.id) -> permissionMap }
    )


    // same as in the export: when a proxy in front gives up first the client sees only a gateway
    // timeout, and this is the line that says whether the import finished and how long it needed
    log.info(s"Imported the collection '${header.collection.name}', with " +
      s"${source.cardCount} question(s) and ${source.dashboardCount} " +
      s"dashboard(s), in ${(System.nanoTime() - startedAt) / 1000000000L}s")
  }

  private def declareTotals(source: ExportSource): Unit = {
    val collections = 1L + source.header.children.size
    progress.declare(Phase.Validating, 1)
    progress.declare(Phase.CreatingCollections, collections)
    progress.declare(Phase.CreatingSnippets, source.header.snippets.size.toLong)
    progress.declare(Phase.CreatingDashboards, source.dashboardCount)
    progress.declare(Phase.CreatingQuestions, source.cardCount)
    progress.declare(Phase.UpdatingDashboards, source.dashboardCount)
    progress.declare(Phase.CreatingSubscriptions, source.subscriptionCount)
    progress.declare(Phase.UpdatingPermissions, collections)
  }

  /**
   * The permissions of the imported collection and of every subcollection of it, as one read and
   * one write of the permission graph.
   *
   * There used to be one call of this per collection, and each of them asked metabase for the
   * groups, then for the **whole** permission graph of the instance, changed one entry of it and
   * wrote the whole thing back. With a hundred subcollections that is three hundred requests over
   * the biggest document metabase serves, and each write invalidated the `revision` the next read
   * had not made yet.
   */
  private def updatePermissions(
    permissionsPerCollection: List[(CollId, Map[GroupName, Permission])]
  ): Unit = {
    val phase = progress.tracked(Phase.UpdatingPermissions, permissionsPerCollection.size.toLong)
    phase.start()

    val targetGroupNameToId = api.getGroupNameToIdMap
    val updatedGraph = permissionsPerCollection.foldLeft(api.getCollectionGraph) {
      case (graph, (targetCollId, sourcePermissionsPerGroup)) =>
        log.debug(s"Importing Permissions of collection $targetCollId (id in target)")
        phase.checkCancelled()
        val targetPermissionsPerGroup = sourcePermissionsPerGroup flatMap {
          case (gName, permission) if targetGroupNameToId contains gName =>
            Some((targetGroupNameToId(gName), permission))

          case (gName, _) =>
            log.warn(s"Permission group with name $gName needed to set permissions for collection $targetCollId (target Id) was not found in target Metabase. This collection will be imported with permissions only to admin users")
            None
        }
        val updated = graph.updatePermissionInColl(targetCollId, targetPermissionsPerGroup)
        phase.advance()
        updated
    }
    val _ = api.updateCollectionGraph(updatedGraph)
  }

  // Since some cards may live in the dashboards directly we need the dashboardIds map before creating the cards,
  // but we cannot create the "full" dashboards until we have the cardIds map, thus, we need to firtsly create
  // all dashboards (but without any card) to get the Ids, and then create and populate the them with cards
  private def createEmptyDashboards(
    source: ExportSource,
    collMap: IdMap,
    existingDashboards: Map[CollId, List[CollectionItem]]
  ): IdMap = {
    val phase = progress.tracked(Phase.CreatingDashboards, source.dashboardCount)
    source.withDashboards(dashboards => Concurrently.fold(
      dashboards, "empty dashboard(s)", parallelWrites, phase
    )(Map.empty[BigInt, BigInt]) { exported =>
      // emptying it and pointing it at the target collection are pure, so they cost nothing to do
      // on the thread that is about to make the request instead of in a pass of their own
      val dashboard = exported
        .copy(dashcards = List.empty, parameters = JArray(List.empty), tabs = List.empty)
        .replaceCollectionId(collMap)
      val idInTarget = dashboard
        .collection_id
        .flatMap(existingDashboards(_).find(_.name == dashboard.name))
        .map { existingDashboard =>
          log.debug(s"dashboard with name ${existingDashboard.name} already present in collection. Will remove all its dashcards and tabs")
          existingDashboard.id
        }
        .getOrElse {
          log.debug(s"Creating empty dashboard ${dashboard.name}")
          api.createDashboard(dashboard)
        }
      dashboard.id -> idInTarget
    }((map, entry) => map + entry))
  }

  private def updateDashboards(
    source: ExportSource,
    dbMaps: DbMaps,
    collMap: IdMap,
    cardMap: IdMap,
    dashboardMap: IdMap
  ): Unit = {
    // out of the loop: built inside it, a new reporter was made per dashboard, and each one of them
    // declared the size of the phase all over again
    val phase = progress.tracked(Phase.UpdatingDashboards, source.dashboardCount)
    source.withDashboards(dashboards => Concurrently.foreach(
      dashboards, "dashboard(s)", parallelWrites, phase
    ) { exported =>
      if (log.isDebugEnabled) {
        log.debug(s"Updating empty dashboard '${exported.name}' using exported data: ${exported.toJson}")
        log.debug(s"Replacing id, collection, database, table and field Ids in dashboard")
      }
      val dashboard = exported
        .replaceId(dashboardMap)
        .replaceCollectionId(collMap)
        .replaceDatabaseIds(dbMaps.db)
        .replaceTableIds(dbMaps.table)
        .replaceFieldIds(dbMaps.field)
        .replaceCardIds(cardMap)
        .replaceDashboardLinks(dashboardMap)
        .withNegativeDashcardsAndTabs

      log.debug(s"update (PUT) dashboard with name ${dashboard.name}")
      val _ = api.updateDashboard(dashboard.id, dashboard)
    })
  }

  private def deleteDashboardsNotInExportData(dashboardMap: IdMap, existingDashboards: Iterable[CollectionItem]): Unit = {
    // Delete dashboards that have not been created or updated (not present in export data)
    // the set is built once and not once per dashboard examined, which is what 'filterNot(map.values.toSet
    // contains _)' does
    val importedDashboardIds = dashboardMap.values.toSet
    val dashboardsToDelete = existingDashboards.filterNot(importedDashboardIds contains _.id)
    log.debug(s"Deleting dashboards not present in export data $dashboardsToDelete")
    // out of the loop, see the same call in 'createCards'
    val phase = progress.growing(Phase.DeletingLeftovers, dashboardsToDelete.size.toLong)
    Concurrently.foreach(dashboardsToDelete.iterator, "deleted dashboard(s)", parallelWrites, phase)(
      api.deleteCollectionItem
    )
  }

  private def createCards(
    source: ExportSource,
    dbMaps: DbMaps,
    collMap: IdMap,
    snippetMap: IdMap,
    dashboardMap: IdMap,
    existingCards: Map[CollId, List[CollectionItem]]
  ): IdMap = {
    // indexed by name: looking for the question that is already in the target collection by scanning
    // every question it holds is quadratic when there are thousands on both sides
    val existingCardsByName: Map[CollId, Map[String, List[CollectionItem]]] =
      existingCards.map { case (collId, items) => collId -> items.groupBy(_.name) }

    val questionsPhase = progress.tracked(Phase.CreatingQuestions, source.cardCount)

    // Which questions can be created together, worked out from the ids alone before reading a
    // single question. A question that uses another one as a table or as a metric needs the id that
    // other one got in the target metabase, so it cannot go in the same round as the one it uses
    val rounds = roundsOf(source.cardDependencies)
    if (rounds.size > 1) log.info(s"The questions of this import will be created in ${rounds.size} " +
      s"round(s), because some of them are used as tables or as metrics by others")

    // one pass over the export per round, creating the questions of that round and stepping over
    // the rest. Almost always there is a single round, and a round is never empty, so this is
    // bounded by the number of questions in the worst case the same way the old recursion was
    val cardIdSourceToTarget = rounds.foldLeft(Map.empty[BigInt, BigInt]) { (createdSoFar, round) =>
      // several at a time, and read one at a time: everything the loop reads besides the question
      // itself is immutable and finished before the round starts, and 'ApiClient' keeps no state,
      // so the only thing being shared is the map being built, which 'fold' takes care of. It has
      // to be 'Concurrently.fold' and not 'Concurrently.map': the questions come out of a pass over
      // the file and holding all of them is exactly what does not fit in the heap
      val createdInRound = source.withCards(cards => Concurrently.fold(
        cards.filter(round contains _.id), "question(s)", parallelWrites, questionsPhase
      )(Map.empty[BigInt, BigInt]) {
        card =>
          // 'card.toJson' serializes the whole question, so it is not paid unless somebody is
          // going to read it: at INFO, which is the default, this loop ran it sixty thousand times
          // and threw every one of them away
          if (log.isDebugEnabled) {
            log.debug(s"Creating card from export data: ${card.toJson}")
            log.debug(s"Replacing collection, database, table and field Ids in card")
          }
          val cardToCreateOrUpdate = card
            .replaceCollectionIds(collMap)
            .replaceDatabaseIds(dbMaps.db)
            .replaceTableIds(dbMaps.table)
            .replaceFieldIds(dbMaps.field)
            // the ones of the rounds before this one: no question of a round uses another of the
            // same round, which is what put them in the same round to begin with
            .replaceCardIdsUsedAsTables(createdSoFar)
            .replaceCardIdsUsedAsMetrics(createdSoFar)
            .replaceSnippets(snippetMap)
            .replaceDashboardId(dashboardMap)

          // Note that two exported questions with the same name in the same target collection both
          // resolve to this same existing one and overwrite each other. That was already so when
          // this loop was sequential; the only difference is that the one that wins is no longer
          // guaranteed to be the last of the file
          val createdOrUpdatedCardId = cardToCreateOrUpdate
            .collection_id
            .flatMap {
              collId =>
                existingCardsByName(collId).getOrElse(card.name, Nil) match {
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

          cardToCreateOrUpdate.id -> createdOrUpdatedCardId
      }((map, entry) => map + entry))
      createdSoFar ++ createdInRound
    }

    // same as above, and here it really counts: a collection can hold thousands of questions on each
    // side, so rebuilding the set for every one of them is quadratic
    val importedCardIds = cardIdSourceToTarget.values.toSet
    val cardsToDelete = existingCards.values.flatten.filterNot(importedCardIds contains _.id)
    log.debug(s"Deleting cards not present in export data $cardsToDelete")
    // out of the loop: built inside it, the phase was told it had grown by the whole size once per
    // card deleted, so it ended up declaring the square of what there was to do
    val deletingPhase = progress.growing(Phase.DeletingLeftovers, cardsToDelete.size.toLong)
    Concurrently.foreach(cardsToDelete.iterator, "deleted question(s)", parallelWrites, deletingPhase)(
      api.deleteCollectionItem
    )

    cardIdSourceToTarget
  }

  private def createSubscriptions(
    source: ExportSource,
    collMap: IdMap,
    dashboardMap: IdMap,
    cardMap: IdMap,
    existingDashboards: Map[CollId, List[CollectionItem]]
  ): Unit = {
    // Find all subscriptions from target. One 'GET pulse?dashboard_id=x' per dashboard already in
    // the target collection, and there can be thousands of them, so they go several at a time like
    // every other fan-out of reads (see 'Concurrently')
    val dashboardsInTarget = existingDashboards.values.flatten.toList
    val existingSubscriptions = Concurrently.map(
      dashboardsInTarget, "dashboard(s) to look for subscriptions in", Concurrently.MetadataRequests
    )(dashboardItem => dashboardItem.id -> api.getSubscriptions(dashboardItem.id))
      .toMap
      .withDefaultValue(List.empty[Subscription])

    // Create or update subscription depending on whether each subscription is found in the target
    val subscriptionsPhase = progress.tracked(Phase.CreatingSubscriptions, source.subscriptionCount)
    // only the ids are kept, so that the subscriptions themselves are read one at a time like
    // everything else that grows with the collection
    val subscriptionIdCreatedOrUpdated = source.withSubscriptions(subscriptions => Concurrently.fold(
      subscriptions, "subscription(s)", parallelWrites, subscriptionsPhase
    )(Set.empty[BigInt]) {
      subscription: Subscription =>
      if (log.isDebugEnabled) {
        log.debug(s"Creating/updating subscription from exported data: ${subscription.toJson}")
        log.debug(s"Replacing collection, dashboard and card Ids in subscription")
      }
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
    }((ids, id) => ids + id))

    // Delete all non-exported subscriptions that have not been found
    val subscriptionsToDelete = existingSubscriptions.values.flatten.filterNot(subscriptionIdCreatedOrUpdated contains _.id)
    log.debug(s"Deleting subscriptions not present in export data $subscriptionsToDelete")
    // out of the loop, see the same call in 'createCards'
    val deletingPhase = progress.growing(Phase.DeletingLeftovers, subscriptionsToDelete.size.toLong)
    Concurrently.foreach(
      subscriptionsToDelete.iterator, "deleted subscription(s)", parallelWrites, deletingPhase
    )(sub => api.deleteSubscription(sub.id))
  }

  // Create or update snippets depending on whether each snippet is found in the target
  private def createSnippets(snippets: List[Snippet]): IdMap =
    snippets
      .map(progress.tracked(Phase.CreatingSnippets, snippets.size.toLong).tracking { snippet: Snippet =>
        // 'snippets' and not 'snippet' serialized the whole list once per snippet in it
        if (log.isDebugEnabled) log.debug(s"Creating/updating snippet from exported data: ${snippet.toJson}")
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
      })
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
    val phase = progress.phase(Phase.CreatingCollections)
    phase.start()
    phase.checkCancelled()
    if (log.isDebugEnabled) log.debug(s"Creating collection from export data: ${sourceColl.toJson}")
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

    phase.advance()
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

  // visible to the tests: this is where the tables and the fields of the export are matched with the
  // ones of the target metabase, and the only part of the import that can be exercised on its own
  private[discoverycicd] def getDbMap(exportedCollection: ExportHeader): DbMaps = {

    // Indexed by name, which is what identifies a database across metabase instances, and taken from
    // every database of the target metabase and not only from the ones the export lists among its
    // databases: an export can name in its tables or in its fields a database that is not there (see
    // 'getTablesOfNeededSchemas')
    val dbByName = allDatabases.map(db => db.name -> db).toMap
    val missingDatabases = exportedCollection.databases.filterNot(dbByName contains _.name)
    require(missingDatabases.isEmpty, databasesNotFoundError(missingDatabases, allDatabases))

    if (exportedCollection.tables.nonEmpty || exportedCollection.fields.nonEmpty)
      log.info(s"Resolving in the target metabase the ${exportedCollection.tables.size} table(s) and the " +
        s"${exportedCollection.fields.size} field(s) this import needs")

    // Neither the whole catalog of the instance ('GET table') nor every field of every database
    // ('GET database/:id/fields') can be asked for: the first one does not fit in memory when the
    // instance has tens of thousands of tables, as metabase hydrates the whole database object in
    // every table it returns, and metabase resolves the second one with a single query enumerating
    // every table of the database, so above the 65535 parameters PostgreSQL can bind in a statement it
    // always fails (see ROCK-15286).
    // Unlike the exporter, here we do not know the ids the tables and the fields have in this
    // metabase, only their name, their schema and the database they belong to, so we ask only for the
    // tables of the schemas the export references and then, for the fields, for the metadata of each
    // one of the tables they live in.
    val tablesInTarget = getTablesOfNeededSchemas(exportedCollection, dbByName)

    // driven by the export and not by what we found, so that what the check says and what the message
    // reports can never disagree
    val missingTables = exportedCollection.tables.filterNot(tablesInTarget contains _.key)
    require(missingTables.isEmpty, tablesNotFoundError(missingTables, tablesInTarget))

    val neededFields = exportedCollection.fields.map(_.identifier).toSet
    val fields =
      if (exportedCollection.fields.isEmpty) List.empty
      else {
        val tablesOfFields =
          exportedCollection.fields.flatMap(field => tablesInTarget get field.tableKey).distinct
        log.info(s"Getting from the target metabase the fields of ${tablesOfFields.size} table(s), to resolve " +
          s"the ${exportedCollection.fields.size} field(s) this import needs")
        // several requests at a time: there is one per table and metabase takes its time with each of
        // them, same as in the export (see 'Concurrently' and ROCK-15286)
        val fieldsInTables =
          Concurrently.map(tablesOfFields, "table(s)", Concurrently.MetadataRequests,
            progress.phase(Phase.ResolvingFields)) { table =>
          // filtered per table and not over everything afterwards: the answer holds every field of the
          // table, and keeping those of every table at once is the kind of list this whole change is
          // about not building (see ROCK-15286)
          getFieldsOfTable(table).filter(field => neededFields contains field.identifier)
        }.flatten
        fieldsInTables ++ fieldsHiddenInTableMetadata(exportedCollection, fieldsInTables)
      }
    // a field of the export and a field of the target metabase are the same one when their identifier
    // is the same, so the same target field can well answer for two exported ones: metabase keeps the
    // old, inactive 'Field' when a column is dropped and added again, and a card can reference either
    val foundFields = fields.map(field => field.identifier -> field).toMap
    val missingFields = exportedCollection.fields.filterNot(foundFields contains _.identifier)
    require(missingFields.isEmpty, fieldsNotFoundError(missingFields))

    // Maps with id correspondence between source and target metabases
    val dBSource2Target: IdMap =
      exportedCollection
        .databases
        .map { sourceDb => sourceDb.id -> dbByName(sourceDb.name).id }
        .toMap

    val tableSource2Target: IdMap =
      exportedCollection
        .tables
        .map { sourceTable => sourceTable.id -> tablesInTarget(sourceTable.key).id }
        .toMap

    val fieldSource2Target: IdMap =
      exportedCollection
        .fields
        .map { sourceField => sourceField.id -> foundFields(sourceField.identifier).id }
        .toMap

    DbMaps(
      db = dBSource2Target,
      table = tableSource2Target,
      field = fieldSource2Target
    )
  }

  // The tables the target metabase has in the schemas the export references, indexed by the properties
  // that identify a table across metabase instances. Only those schemas are asked for: they are a
  // handful, while the catalog of the instance can have tens of thousands of tables and not fit in
  // memory (see ROCK-15286)
  private def getTablesOfNeededSchemas(
    exportedCollection: ExportHeader,
    dbByName: Map[String, Database]
  ): Map[Table.Key, Table] = {
    // metabase has one endpoint for a named schema and another one for the tables that have none, so
    // 'no schema' is the same request no matter how the export spells it (see 'Table.normalizeSchema')
    val neededSchemas =
      (exportedCollection.tables.map(table => table.db.name -> table.schema) ++
        exportedCollection.fields.map(field => field.dbName -> field.schema))
        .map { case (dbName, schema) => dbName -> Table.normalizeSchema(schema) }
        .toSet

    // only known here: how many schemas the export touches depends on its tables and its fields
    val phase = progress.tracked(Phase.ResolvingTables, neededSchemas.size.toLong)
    val tables = neededSchemas.toList flatMap phase.tracking { case (dbName: String, schema: Option[String]) =>
      dbByName.get(dbName) match {
        // the export can name a database that the target metabase does not have at all: those tables
        // are just not resolved, and 'tablesNotFoundError' tells which ones are missing. It is not
        // reported as an error here because the export may not need any table of that schema after all
        case None =>
          log.warn(s"The exported data references the database '$dbName' in its tables or fields, but the " +
            "target metabase has no database with that name, so its tables cannot be looked for there")
          List.empty

        case Some(db) =>
          val tablesInSchema = getTablesInSchema(db, schema)
          // only the count: it is what tells how big the answer we are bringing into memory is, which is
          // the whole point of asking schema by schema. Their names are in 'tablesNotFoundError' already
          log.debug(s"The target metabase has ${tablesInSchema.size} table(s) in " +
            s"'${Table.describeSchema(dbName, schema)}'")
          tablesInSchema
      }
    }

    if (neededSchemas.nonEmpty)
      log.info(s"The target metabase has ${tables.size} table(s) in the ${neededSchemas.size} schema(s) this " +
        "import needs")

    tables.map(table => table.key -> table).toMap
  }

  // Which schema it was is not in the response when the request fails, and its database only travels
  // there as an id of the target metabase, which says nothing to whoever reads the error
  private def getTablesInSchema(db: Database, schema: Option[String]): List[Table] =
    Try(api.getTablesInSchema(db.id, schema)) match {
      case Success(tables) => tables.map(_.toTable(db))
      case Failure(ex) =>
        throw GetDataException(s"An error occurred getting from the target metabase the tables of " +
          s"'${Table.describeSchema(db.name, schema)}'. Cause: ${causeOf(ex)}.")
    }

  // Same as above: a failure of 'GET table/:id/query_metadata' only names the table by the id it has in
  // the target metabase, so without this whoever reads the error cannot tell which table failed
  private def getFieldsOfTable(table: Table): List[Field] =
    Try(api.getTableMetadata(table.id).toFields(table.db.name)) match {
      case Success(fields) => fields
      case Failure(ex) =>
        throw GetDataException(s"An error occurred getting from the target metabase the fields of the table " +
          s"'${table.name}' of '${Table.describeSchema(table.db.name, table.schema)}' (id:[${table.id}] in the " +
          s"target metabase). Cause: ${causeOf(ex)}.")
    }

  // What went wrong is not that we did not find a table but that the target metabase does not have it,
  // so besides the tables that are missing the message tells what the target metabase does have in the
  // schemas they should live in: an empty schema and a schema with tables but not this one are very
  // different problems, and having the names in front tells a renamed table from one whose name is
  // only written differently
  private def tablesNotFoundError(missing: List[Table], tablesInTarget: Map[Table.Key, Table]): String = {
    // 'no schema' is grouped and looked up the same way on both sides, so that a table the export
    // spells with a null schema is still reported against the tables that have none
    def schemaOf(dbName: String, schema: Option[String]) = dbName -> Table.normalizeSchema(schema)
    val tablesPerSchema = tablesInTarget.values.groupBy(table => schemaOf(table.db.name, table.schema))
    val foundInSchemasOfMissing =
      missing.groupBy(table => schemaOf(table.db.name, table.schema)).toList.sortBy(_._1.toString) map {
        case ((dbName, schema), missingInSchema) =>
          val found = tablesPerSchema.getOrElse((dbName, schema), Nil).map(_.name)
          val detail =
            if (found.isEmpty)
              "no table at all. Is that schema synchronized in the target metabase, and has it any active table?"
            else describeFound("table", found, missingInSchema.map(_.name))
          s"  ${Table.describeSchema(dbName, schema)}: $detail"
      }

    s"Some of the needed tables to perform the import are not found in the target metabase.\n\n" +
      s"Missing, ${describeMissing(missing, describeTable)}\n\n" +
      s"Tables the target metabase has in those schemas:\n${foundInSchemasOfMissing.mkString("\n")}"
  }

  private def fieldsNotFoundError(missing: List[Field]): String =
    "Some of the needed fields to perform the import are not found in the target metabase. They may be " +
      "inactive there, belong to an inactive table, or have been retired: synchronize the schema of their " +
      "database in the target metabase and try again.\n\n" +
      s"Missing, ${describeMissing(missing, describeField)}"

  private def databasesNotFoundError(missing: List[Database], inTarget: List[Database]): String =
    "Some of the needed Metabase databases to perform the import are not found in the target metabase.\n\n" +
      s"Missing: ${missing.map(_.name).mkString(", ")}\n\n" +
      s"The target metabase has ${describeFound("database", inTarget.map(_.name), missing.map(_.name))}"

  // 'GET table/:id/query_metadata' never returns the fields metabase has marked as inactive (the columns
  // that are no longer in the table) nor the retired ones, so whatever it did not resolve is looked for
  // with 'GET database/:id/fields', which does return the inactive ones (the retired ones are returned
  // by no endpoint that answers by table or by database, so those cannot be resolved at all). That is
  // precisely the endpoint that does not work with big catalogs (see ROCK-15286), so it is asked only
  // when something is really missing, and only for the databases of the missing fields
  private def fieldsHiddenInTableMetadata(
    exportedCollection: ExportHeader,
    foundFields: List[Field]
  ): List[Field] = {
    val foundIdentifiers = foundFields.map(_.identifier).toSet
    val missing = exportedCollection.fields.filterNot(foundIdentifiers contains _.identifier)
    if (missing.isEmpty) List.empty
    else {
      val missingIdentifiers = missing.map(_.identifier).toSet
      log.warn("The following field(s) are not in the metadata of their tables in the target metabase " +
        "(metabase hides there the fields it has marked as inactive), so the whole list of fields of their " +
        "databases has to be asked for. That request can fail if the catalog is big (see ROCK-15286), " +
        describeMissing(missing, describeField))
      val recovered = allDatabases
        .filter(db => missing exists (_.dbName == db.name))
        .flatMap { db =>
          Try(api.getFieldsInDb(db.id)) match {
            // filtered here and not afterwards: the answer holds every field of the database, which is
            // exactly the list we cannot afford to keep (see ROCK-15286)
            case Success(fieldsInDb) =>
              fieldsInDb.map(_.toField(db.name)).filter(field => missingIdentifiers contains field.identifier)
            case Failure(ex) =>
              log.error(s"Could not get the fields of the database '${db.name}', so the fields of the export " +
                s"that are not in the metadata of their tables cannot be resolved. Cause: ${causeOf(ex)}.")
              List.empty
          }
        }
      if (recovered.nonEmpty)
        log.warn("The following field(s) were only found in the whole list of fields of their database, so " +
          "they are most likely inactive in the target metabase: the import goes on with them, but the " +
          "questions that use them will not work there until the schema of their database is synchronized, " +
          describeMissing(recovered, describeField))
      recovered
    }
  }

}

object Importer {

  case class DbMaps(db: IdMap, table: IdMap, field: IdMap)

  /**
   * The questions grouped by when they can be created: first the ones that use no other question,
   * then the ones that only use those, and so on.
   *
   * A question that uses another one as a table or as a metric carries its id, and that id has to
   * be the one it got in the target metabase, so it cannot be created until the one it uses is.
   * The old code discovered this while walking a list of questions it had in memory; here it is
   * worked out from the ids alone, before reading a single question, because each round costs one
   * pass over the file (see [[com.stratio.discoverycicd.model.ExportSource]]).
   *
   * A collection almost always gives a single round: it takes a question built on top of another
   * one to make a second, and a chain of three to make a third.
   */
  private[discoverycicd] def roundsOf(dependencies: Map[BigInt, Set[BigInt]]): List[Set[BigInt]] = {
    val rounds = List.newBuilder[Set[BigInt]]
    var pending = dependencies
    var created = Set.empty[BigInt]

    while (pending.nonEmpty) {
      val ready = pending.collect { case (id, uses) if uses subsetOf created => id }.toSet

      // sanity check (and guard against looping for ever) in case we receive inconsistent data.
      // Either the depended-on questions were not exported (were not part of the collection) or
      // there are circular dependencies between them; neither should happen, because the data is
      // checked before anything is created ('dependencyTrees'), but just in case...
      if (ready.isEmpty) throw ValidationDataException(
        s"Cannot create cards '${pending.keys.mkString(",")}' since it is impossible to satisfy their " +
          s"card dependencies. This should have been detected in a previos check but it was missed. The import " +
          "process has been interrupted before its conclusion, some data may be inconsistent in the target metabase"
      )

      rounds += ready
      created ++= ready
      pending = pending -- ready
    }
    rounds.result()
  }

  // How many names of the target metabase an error message can carry. The whole point of asking schema
  // by schema is not to bring the catalog of the instance into memory (see ROCK-15286), and a single
  // schema can still hold tens of thousands of tables: listing all of them would put them back in
  // memory, in the log and in the body of the response
  private[discoverycicd] val MaxNamesReported = 30

  // The message travels to the client as the body of a 500 and into the log, and an import can be
  // missing thousands of tables at once: a database whose tables metabase has marked as inactive (the
  // schema was synchronized once and the tables are gone) leaves exactly that. Listing every one of
  // them helps nobody and puts back in memory, in the log and in the response the very list this whole
  // change is about not building (see ROCK-15286)
  private[discoverycicd] def describeMissing[A](missing: List[A], describe: A => String): String = {
    val reported = missing.take(MaxNamesReported).map(describe)
    val andMore = if (missing.size > reported.size) s"\n  ... and ${missing.size - reported.size} more" else ""
    s"${missing.size} in total:\n${reported.mkString("\n")}$andMore"
  }

  // What the target metabase does have, so that an empty schema can be told from a schema where the
  // table is only written differently. The names that look like the ones we did not find go first,
  // because those are the ones that explain what happened
  private[discoverycicd] def describeFound(what: String, found: Iterable[String], lookingFor: List[String]): String = {
    // 'found' holds every table of a schema, and a schema can have tens of thousands of them (see
    // ROCK-15286), so every name is lowercased once and not once per comparison: this runs while an
    // import is failing, and a catalog that big is precisely what makes it fail
    val lookingForLower = lookingFor.map(_.toLowerCase)

    // Enough to catch a name written in another case, or with something around it, which is what tells
    // a renamed table from one that is simply not there
    def isSimilar(name: String): Boolean = {
      val lowerName = name.toLowerCase
      lookingForLower exists (lowerOther => (lowerName contains lowerOther) || (lowerOther contains lowerName))
    }

    val (similar, rest) = found.toList.sorted.partition(isSimilar)
    val reported = (similar ++ rest).take(MaxNamesReported)
    val andMore = if (found.size > reported.size) s", ... and ${found.size - reported.size} more" else ""
    s"${found.size} $what(s): ${reported.mkString(", ")}$andMore"
  }

  // Tables and fields are named as whoever reads the message thinks of them, and with the id they have
  // in the export so that they can be found in the exported data
  private[discoverycicd] def describeTable(table: Table): String =
    s"  ${Table.describeSchema(table.db.name, table.schema)} / ${table.name} (id:[${table.id}] in the export)"

  private[discoverycicd] def describeField(field: Field): String =
    s"  ${Table.describeSchema(field.dbName, field.schema)} / ${field.table_name}.${field.name} " +
      s"(id:[${field.id}] in the export)"

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

  // Takes the index of what each question uses and not the questions themselves: their id and their
  // dependencies is the whole of what this needs, and an export of a big collection cannot be held
  // as a list of questions at all (see 'ExportSource')
  private def dependencyTrees(dependencies: Map[BigInt, Set[BigInt]]): List[DependencyTree] = {

    def dependencyTree(cardId: BigInt): DependencyTree = {
      def loop(cardId: BigInt, lineage: List[BigInt]): DependencyTree = {
        if (lineage contains cardId) CircularDep(cardId, lineage.reverse.dropWhile(cardId != _))
        else dependencies
          .get(cardId)
          .map {
            usedCards =>
              // recursion ends when card does not use other cards. If all cards have dependencies, since the list
              // is finite, either there are circular dependencies (recursion finish in the if branch above)
              // or there are missing ones (recursion finish in the getOrElse below)
              val children = usedCards.map(loop(_, cardId :: lineage))
              OkDep(cardId, children)
          }
          .getOrElse(NotFoundDep(cardId, lineage.head))
      }
      loop(cardId, List.empty)
    }
    dependencies.keys.toList.map(dependencyTree)
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
