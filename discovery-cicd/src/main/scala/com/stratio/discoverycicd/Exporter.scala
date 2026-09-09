/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import com.stratio.discoverycicd.exceptions.CustomException.causeOf
import com.stratio.discoverycicd.exceptions.{DuplicatedEntityException, GetDataException, UnsupportedEntityException}
import com.stratio.discoverycicd.model.Collection.Models
import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.progress.{Phase, ProgressReporter}
import com.stratio.discoverycicd.utils.{ApiClient, Concurrently, Identificable, Logging, MBJson}
import com.stratio.discoverycicd.utils.Identificable.IdentificableOps
import org.json4s.{DefaultFormats, Extraction}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class Exporter(api: ApiClient, progress: ProgressReporter = ProgressReporter.NoOp) extends Logging {

  import Exporter._

  // How many names a warning can carry: a collection can reference thousands of tables that are all
  // inactive, and listing every one of them helps nobody
  private val MaxNamesReported = 30

  def exportCollection(collectionId: BigInt): ExportedCollection = {
    implicit val formats = DefaultFormats
    val startedAt = System.nanoTime()

    // get collection
    val collection =
      progress.step(Phase.Collection) {
        Try {
          api.getCollection(collectionId)
        } match {
          case Success(collection) => collection
          case Failure(ex) =>
            throw GetDataException(s"An error occurred getting the collection with id:[$collectionId]. " +
              s"Cause: ${causeOf(ex)}.")
        }
      }

    // get cards, dashboards and subcollections in collection
    val items = progress.step(Phase.CollectionItems)(itemsInCollAndSubColls(collectionId))
    val cardIdsInColl = getCardIdsInColl(items)
    val dashboardIdsInColl = getDashboardIdsInColl(items)
    val collsIdsInColl = getCollsInColl(items)

    // Listing the items of the collection and of its subcollections costs one request each and
    // brings only ids and names, and it already says how many questions and how many dashboards are
    // coming: the two phases where an export of a big collection spends nearly all of its time. So
    // whoever polls the status gets '28100 of 60000' from the first answer, instead of a counter
    // that only says what it has got so far and never what is left
    progress.declare(Phase.Questions, cardIdsInColl.size.toLong)
    progress.declare(Phase.Dashboards, dashboardIdsInColl.size.toLong)
    progress.declare(Phase.SubCollections, collsIdsInColl.size.toLong)

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

    // one dashboard at a time and not the whole list at once: every one of the six sets below is a
    // union over the dashboards, and 'Extraction.decompose(dashboards)' built a second copy of every
    // dashboard of the collection in memory on top of the ones already there, only to be scanned
    val idsInDashboards = idsUsedInDashboards(dashboards)
    checkAllCardsAreInCollection(cardIdsInColl, idsInDashboards)
    checkAllDashboardsAreInCollection(dashboardIdsInColl, idsInDashboards)

    // Collect needed DB data to be able to obtain in the target metabase all field, table and database IDs
    // one pass and one set each, instead of 'map(...).fold(Set.empty)(_ union _)', which builds one set
    // per question and then merges them one by one. Measured with 10.000 questions it is only some tens
    // of milliseconds either way, so this is tidiness more than a saving: what really costs on this side
    // are the requests, not the ids
    val databaseIds = cards.iterator.flatMap(_.getDatabaseIds).toSet union idsInDashboards.databases
    val fieldIds = cards.iterator.flatMap(_.getFieldIds).toSet union idsInDashboards.fields
    val tableIds = cards.iterator.flatMap(_.getTableIds).toSet union idsInDashboards.tables

    log.info(s"Getting from metabase the metadata of the ${tableIds.size} table(s) and the ${fieldIds.size} " +
      s"field(s) this collection uses")

    val databasesPhase = progress.tracked(Phase.Databases, databaseIds.size.toLong)
    val databases = databaseIds map databasesPhase.tracking(api.getDatabase)
    val tablesMetadata = getTablesMetadata(tableIds, cards)
    val looseFields = getLooseFields(fieldIds, tablesMetadata, cards)

    // the databases the cards reference are not necessarily all the ones the tables and fields above
    // belong to, and we need their names to identify tables and fields in the target metabase. They
    // also go into the export: the importer looks for a table in the database of the export whose name
    // matches, so one that is not there leaves its tables unresolvable
    val extraDatabaseIds =
      (tablesMetadata.map(_.db_id).toSet union looseFields.map(_.table.db_id).toSet) diff databaseIds
    // the ones above and these ones are the same phase, so it only knows its real size here
    progress.declare(Phase.Databases, (databaseIds.size + extraDatabaseIds.size).toLong)
    val extraDatabases = getExtraDatabases(extraDatabaseIds)
    val allNeededDatabases = databases union extraDatabases
    val dbNameById = (allNeededDatabases map (db => db.id -> db.name)).toMap

    // a table or a field whose database could not be read is left out, the same way one that metabase
    // did not give us is: 'dbNameById' is the only place where that can be noticed
    val tables = tablesMetadata flatMap (table => dbNameById.get(table.db_id).map(table.toTable))
    val fields =
      tablesMetadata.flatMap { table =>
        // filtered per table and not over everything afterwards: a table can have hundreds of columns
        // and a collection uses a handful of them, so building the fields of every table only to throw
        // almost all of them away is the kind of list this whole change is about not building
        // (see ROCK-15286)
        dbNameById.get(table.db_id).toList.flatMap { dbName =>
          table.toFields(dbName).filter(fieldIds contains _.id)
        }
      } ++ looseFields.flatMap(field => dbNameById.get(field.table.db_id).map(field.toField))

    log.info(s"Exporting ${tables.size} table(s) and ${fields.size} field(s) of " +
      s"${allNeededDatabases.size} database(s)")

    // Collect permissions data
    val permissionsPhase = progress.tracked(Phase.Permissions, 2)
    val groupIdToName = permissionsPhase.one(api.getGroupIdToNameMap)
    val collectionGraph =
      Try {
        permissionsPhase.one(api.getCollectionGraph)
      } match {
        case Success(collectionGraph) => collectionGraph
        case Failure(ex) =>
          throw GetDataException(s"An error occurred getting the permissions per group of collection with " +
            s"id:[$collectionId]. Cause: ${causeOf(ex)}.")
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

    val exported = ExportedCollection(
      cards = cards,
      dashboards = dashboards,
      fields = fields.toList,
      tables = tables,
      databases = allNeededDatabases.toList,
      subscriptions = subscriptions,
      snippets = snippets,
      collection = collection,
      children = children,
      permissionsPerGroup = permissionsPerGroup,
    )

    // Whoever looks into this after a failed export needs to know whether it ever finished and how long
    // it needed: when a proxy in front gives up first, the client sees a gateway timeout and nothing
    // else, and this is the line that says whether what has to be raised is the proxy timeout or the
    // export one (see 'ServerRunner.logTimeoutConfigurationError')
    log.info(s"Exported the collection with id:[$collectionId], with ${cards.size} question(s) and " +
      s"${dashboards.size} dashboard(s), in ${secondsSince(startedAt)}s")
    exported
  }

  private def secondsSince(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1000000000L

  // We ask metabase only for the tables the collection references. Asking for the whole catalog of the
  // instance ('GET table') and for every field of every database ('GET database/:id/fields') and then
  // filtering in the client does not work with big catalogs: metabase resolves the fields with a single
  // query enumerating every table of the database, and PostgreSQL cannot bind more than 65535
  // parameters in a statement, so above 65535 tables every export fails (see ROCK-15286)
  private[discoverycicd] def getTablesMetadata(tableIds: Set[BigInt], cards: List[Card]): List[TableMetadataDto] = {
    // several requests at a time: there is one per table and metabase takes its time with each, so a
    // collection that uses thousands of them is unbearable one after another (see ROCK-15286)
    val tables =
      Concurrently.map(tableIds.toList, "table(s) this collection uses", Concurrently.MetadataRequests,
        progress.phase(Phase.Tables)) { tableId =>
      Try(api.getTableMetadata(tableId)) match {
        // the inactive ones are reported all together afterwards, see 'warnAboutInactive'
        case Success(table) => Some(table)
        case Failure(ex) =>
          // filtering the whole catalog also left out silently the tables metabase does not return,
          // so we keep on exporting without them
          val msg = s"Could not get the metadata of the table with id:[$tableId], used by " +
            s"${usedBy(cards, _.getTableIds, tableId)}, so it will not be exported and the import of this " +
            s"export will fail. Cause: ${causeOf(ex)}."
          log.error(msg)
          progress.warn(msg)
          None
      }
    }.flatten

    // 'GET table' did not return the inactive tables either, so this is not a new limitation: it is only
    // reported now, when we can tell that the collection needs such a table
    warnAboutInactive("table", tables.filterNot(_.isActive).map(_.name), tables.size,
      "the import will fail unless they are active in the target metabase")
    tables
  }

  // One line and not one per table: a collection can reference thousands of tables that metabase has
  // marked as inactive (a synchronized schema whose tables are gone leaves exactly that), and a warning
  // for each of them buries everything else in the log and costs more than the request that found it
  private def warnAboutInactive(what: String, names: List[String], total: Int, consequence: String): Unit =
    if (names.nonEmpty) {
      val reported = names.take(MaxNamesReported)
      val andMore = if (names.size > reported.size) s", ... and ${names.size - reported.size} more" else ""
      val msg = s"${names.size} of the $total $what(s) this collection uses are inactive in this metabase, " +
        s"so they are exported, but $consequence: ${reported.mkString(", ")}$andMore"
      log.warn(msg)
      progress.warn(msg)
    }

  // a collection can reference fields of tables it does not reference itself, through implicit joins
  // by foreign key for instance, so the fields that none of the tables above brought get a request of
  // their own, several of them at a time (see 'Concurrently')
  private[discoverycicd] def getLooseFields(
    fieldIds: Set[BigInt],
    tablesMetadata: List[TableMetadataDto],
    cards: List[Card]
  ): List[FieldWithTableDto] = {
    val fieldIdsInTables = tablesMetadata.flatMap(_.fields.map(_.id)).toSet
    val ids = (fieldIds diff fieldIdsInTables).toList
    if (ids.nonEmpty)
      log.info(s"Getting the ${ids.size} field(s) this collection uses that do not belong to any of its " +
        "tables, with a request each")

    val fields =
      Concurrently.map(ids, "field(s) that belong to no table of the collection",
        Concurrently.MetadataRequests, progress.phase(Phase.Fields)) { fieldId =>
      Try(api.getFieldWithTable(fieldId)) match {
        // the inactive ones are reported all together afterwards, see 'warnAboutInactive'
        case Success(field) => Some(field)
        case Failure(ex) =>
          val msg = s"Could not get the field with id:[$fieldId], used by " +
            s"${usedBy(cards, _.getFieldIds, fieldId)}, so it will not be exported and the import of this " +
            s"export will fail. Cause: ${causeOf(ex)}."
          log.error(msg)
          progress.warn(msg)
          None
      }
    }.flatten

    // 'GET field/:id' is the only endpoint that returns the fields metabase has marked as inactive, so a
    // field can only get here inactive through it. Qualified with the name of their table, because a
    // field name alone ('id', 'name') hardly tells which field it is
    val inactiveNames = fields.filterNot(_.isActive).map(field => s"${field.table.name}.${field.name}")
    warnAboutInactive("field", inactiveNames, fields.size,
      "the target metabase must have them too: the import only resolves such a field if it is there")
    fields
  }

  // A table or a field can belong to a database that no card of the collection references, and we need
  // its name to identify them in the target metabase. One that cannot be read is not worth failing the
  // whole export for: it only leaves out its tables and its fields, the same way a table metabase does
  // not give us is left out
  private[discoverycicd] def getExtraDatabases(databaseIds: Set[BigInt]): Set[Database] = {
    // the same phase as the databases the cards reference: to whoever is watching they are all
    // 'databases', and its total was raised to cover these before we got here
    val phase = progress.phase(Phase.Databases)
    // 'map' and then 'flatten' and not 'flatMap': through a function value scala does not apply the
    // implicit that turns the Option into a collection
    (databaseIds map phase.tracking { databaseId: BigInt =>
      Try(api.getDatabase(databaseId)) match {
        case Success(database) => Some(database)
        case Failure(ex) =>
          val msg = s"Could not get the database with id:[$databaseId], which some table or field this " +
            s"collection uses belongs to, so they will not be exported and the import of this export will " +
            s"fail. Cause: ${causeOf(ex)}."
          log.error(msg)
          progress.warn(msg)
          None
      }
    }).flatten
  }

  // The id alone says nothing to whoever reads the log: what tells them what they are about to lose is
  // which questions of the collection use it
  private def usedBy(cards: List[Card], idsOf: Card => Set[BigInt], id: BigInt): String =
    cards.filter(card => idsOf(card) contains id).map(card => s"'${card.name}'") match {
      case Nil   => "no question of the collection (only by one of its dashboards)"
      case names => s"the question(s) ${names.mkString(", ")}"
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


  /**
   * Every id the dashboards of the collection name, gathered in one pass with only one dashboard
   * turned into json at a time.
   *
   * Taking them from the json of the whole list instead put a second copy of every dashboard of the
   * collection in memory next to the ones that are already there and are going to be exported.
   */
  private[discoverycicd] def idsUsedInDashboards(dashboards: List[Dashboard]): DashboardIds =
    dashboards.foldLeft(DashboardIds.Empty) { (ids, dashboard) =>
      val json = Extraction.decompose(dashboard)(DefaultFormats)
      DashboardIds(
        cards = ids.cards union MBJson.getCardIs(json),
        linkedCards = ids.linkedCards union MBJson.getItemIdsInLinks(LinkType.Question)(json),
        linkedDashboards = ids.linkedDashboards union MBJson.getItemIdsInLinks(LinkType.Dashboard)(json),
        databases = ids.databases union MBJson.getDatabaseIds(json),
        fields = ids.fields union MBJson.getFieldIds(json),
        tables = ids.tables union MBJson.getTableIds(json)
      )
    }

  def checkAllCardsAreInCollection(cardIdsInColl: List[BigInt], idsInDashboards: DashboardIds): Unit = {
    val cardsInDashboards = idsInDashboards.cards ++ idsInDashboards.linkedCards
    val neededCardsNotInColl = cardsInDashboards diff cardIdsInColl.toSet
    require(neededCardsNotInColl.isEmpty,
      s"""The questions with ids '${neededCardsNotInColl.mkString(",")}' are used in some dashboard but are not present in the collection"""
    )
  }

  def checkAllDashboardsAreInCollection(dashboardIdsInColl: List[BigInt], idsInDashboards: DashboardIds): Unit = {
    val neededDashboardsNotInColl = idsInDashboards.linkedDashboards diff dashboardIdsInColl.toSet
    require(neededDashboardsNotInColl.isEmpty,
      s"""The dashboards with ids '${neededDashboardsNotInColl.mkString(",")}' are linked in some dashboard but are not present in the collection"""
    )
  }

  def itemsInColl(collectionId: BigInt): List[CollectionItem] =
    api.getItemsInCollection(collectionId)

  def getDashboardIdsInColl(items: List[CollectionItem]): List[BigInt] =
    items collect { case CollectionItem(id, _, Models.DASHBOARD) => id }

  def getCardIdsInColl(items: List[CollectionItem]): List[BigInt] =
    items collect { case CollectionItem(id, _, Models.CARD | Models.DATASET | Models.METRIC) => id }

  def getCollsInColl(items: List[CollectionItem]): List[BigInt] =
    items collect { case CollectionItem(id, _, Models.COLLECTION) => id }

  // A dashboard costs metabase about as much as a question, so it goes at the same rate
  def getDashboards(dashboardIds: List[BigInt]): List[Dashboard] =
    Concurrently.map(dashboardIds, "dashboard(s) of the collection", Concurrently.CardRequests,
      progress.phase(Phase.Dashboards))(api.getDashboard)

  // This is where an export of a big collection spends nearly all of its time: there is one request per
  // question and a collection can hold thousands of them, with no endpoint that answers for several at
  // once ('GET card?f=all' fails with the 65535 parameters of ROCK-15286). Beware that metabase
  // saturates here, so more at a time is not faster: see 'Concurrently.CardRequests'
  def getCards(cardIds: List[BigInt]): List[Card] =
    Concurrently.map(cardIds, "question(s) of the collection", Concurrently.CardRequests,
      progress.phase(Phase.Questions))(api.getCard)
      .map(_.cleanDescription)

  def getCollections(collIds: List[BigInt]): List[Collection] =
    collIds map progress.tracked(Phase.SubCollections, collIds.size.toLong).tracking(api.getCollection)

  def getSubscriptions(dashboardIds: List[BigInt]): List[Subscription] =
    dashboardIds flatMap
      progress.tracked(Phase.Subscriptions, dashboardIds.size.toLong).tracking(api.getSubscriptions)

  def getSnippets(snippetIds: List[BigInt]): List[Snippet] =
    snippetIds map progress.tracked(Phase.Snippets, snippetIds.size.toLong).tracking(api.getSnippet)

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
    val duplicated = items
      .groupBy(_.identifier)
      .collect { case (identifier, duplicates) if duplicates.length > 1 => identifier.toString -> duplicates.length }
      .toList
      .sortBy(_._1)

    if (duplicated.nonEmpty) {
      val itemType = items.head.getClass.getName.split("\\.").last
      // The identifier and how many items share it, and not the whole json of every duplicated item: a
      // collection can hold tens of thousands of questions, each of them a couple of KB of json, so
      // serializing all the duplicates makes a message of several MB that goes into the log, into the
      // exception and into the body of the 500 (see ROCK-15286). The identifier is what has to be fixed
      // anyway, and it is usually the name
      val reported = duplicated.take(MaxNamesReported).map { case (id, count) => s"  $id ($count items)" }
      val andMore =
        if (duplicated.size > reported.size) s"\n  ... and ${duplicated.size - reported.size} more" else ""
      val errorMsg =
        "Cannot export. Exporting items with the same identifier across instances (usually name) is not " +
          s"supported. Found ${duplicated.size} duplicated identifier(s) of type $itemType:\n" +
          reported.mkString("\n") + andMore

      log.error(errorMsg)
      throw DuplicatedEntityException(errorMsg)
    }
  }

}

object Exporter {

  /**
   * The ids the dashboards of a collection name, each kind on its own.
   *
   * Gathered together because they all come from the same json, and that json is built one
   * dashboard at a time: see [[Exporter.idsUsedInDashboards]].
   */
  case class DashboardIds(
    cards: Set[BigInt],
    linkedCards: Set[BigInt],
    linkedDashboards: Set[BigInt],
    databases: Set[BigInt],
    fields: Set[BigInt],
    tables: Set[BigInt]
  )

  object DashboardIds {
    val Empty = DashboardIds(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty, Set.empty)
  }
}
