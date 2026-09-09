/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.model.Collection.Models
import com.stratio.discoverycicd.utils.ApiClient._
import com.stratio.discoverycicd.utils.ImplicitExtensions.HttpRequestExtension
import com.stratio.discoverycicd.utils.TypeAliases._
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write
import scalaj.http._

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.Duration
import scala.util.Try

object ApiClient {
  val DefaultTimeout = 10000
  val Limit = 9999L

  // The schema goes in the path of 'GET database/:id/schema/:schema', so it has to be percent-encoded.
  // 'URLEncoder' on its own does not do: it encodes a space as '+', which in a path segment is a
  // literal '+' and not a space. Metabase has a separate endpoint, 'GET database/:id/schema/', for the
  // tables whose schema is null or the empty string
  def tablesInSchemaPath(dbId: BigInt, schema: Option[String]): String = {
    val encodedSchema =
      schema.filter(_.nonEmpty).fold("")(URLEncoder.encode(_, StandardCharsets.UTF_8.name).replace("+", "%20"))
    s"database/$dbId/schema/$encodedSchema?include_hidden=true"
  }
}

class ApiClient(metabaseUrl: String,
                sessionId: String,
                allowUnsafeSsl: Boolean = false,
                timeout: String) {

  implicit val formats = DefaultFormats

  private val headers = Map(
    "content-type" -> "application/json",
    "X-Metabase-Session" -> sessionId
  )

  private def consumeAllPages(makePaginatedRequest: (Long, Long) => CollectionItemsResponse) = {
    val response = makePaginatedRequest(Limit, 0)
    val limit = response.limit.getOrElse(Limit)
    val total = response.total.getOrElse(0L)
    val offset = limit
    val remainingItems = if (total - limit > 0) makePaginatedRequest(total, offset).data else List.empty
    response.data ++ remainingItems
  }

  val connectionAndReadTimeout: Int = Try(Duration(timeout).toMillis.toInt).getOrElse(DefaultTimeout)

  lazy val user: User = request("user/current").as[User].body

  def getCollection(collectionId: BigInt): Collection =
    request(s"collection/$collectionId").as[Collection].body

  def getItemsInCollection(collectionId: BigInt): List[CollectionItem] = consumeAllPages { (limit, offset) =>
    request(s"collection/$collectionId/items?limit=$limit&offset=$offset&show_dashboard_questions=true").as[CollectionItemsResponse].body
  }

  // if we want to get the root collection we pass a None collectionId
  def getItemsInCollection(collectionId: Option[BigInt]): List[CollectionItem] = consumeAllPages { (limit, offset) =>
    val collApiPath = collectionId.map(_.toString).getOrElse("root")
    request(s"collection/$collApiPath/items?limit=$limit&offset=$offset&show_dashboard_questions=true").as[CollectionItemsResponse].body
  }

  def archiveCollection(collectionId: BigInt): Unit =
    request(s"collection/$collectionId").put("""{"archived":true}""").execute()

  def deleteCollectionItem(item: CollectionItem): Unit =
    if (item.model == Models.COLLECTION) archiveCollection(item.id)
    else request(s"${item.model}/${item.id}").method("DELETE").execute()

  def createCollection(collection: Collection): Collection =
    request("collection").postData(write(collection)).as[Collection].body

  def getDashboard(dashboardId: BigInt): Dashboard =
    request(s"dashboard/$dashboardId").as[Dashboard].body

  // Metabase answers a write with the whole object it just wrote, 'result_metadata' and all, and
  // building a 'Card' out of that costs about what building the one we sent cost. An import of a
  // collection with sixty thousand questions paid that sixty thousand times over for a 'BigInt',
  // which is the only thing it does with the answer, so the four writes below read an 'Id' like the
  // pulse and the snippet ones already did. json4s ignores the fields it was not asked for, and it
  // still fails when the answer is an error and not the object we asked for, which is what tells a
  // failed write from a good one

  def createDashboard(dashboard: Dashboard): BigInt =
    request("dashboard").postData(write(dashboard)).as[Id].body.id

  def updateDashboard(id: BigInt, dashboard: Dashboard): BigInt =
    request(s"dashboard/$id")
      .put(write(dashboard.copy(id = id))(formats.preservingEmptyValues))
      .as[Id]
      .body
      .id

  def getCard(cardId: BigInt): Card =
    request(s"card/$cardId").as[Card].body.fixHyphens

  def createCard(card: Card): BigInt =
    request("card").postData(write(card)).as[Id].body.id

  def updateCard(card: Card): BigInt =
    request(s"card/${card.id}").put(write(card)).as[Id].body.id

  def getDatabase(databaseId: BigInt): Database =
    request(s"database/$databaseId").as[Database].body

  def getAllDatabases: List[Database] =
    request("database").as[ListDatabasesResponse].body.data

  // Only the tables of one schema of one database, so a big catalog does not have to be brought into
  // memory to resolve the few tables an import needs. Asking for the whole catalog of the instance
  // ('GET table') did not work with big catalogs: metabase hydrates the whole database object in every
  // one of the tables it returns, so the response of an instance with tens of thousands of tables is
  // hundreds of MB and does not fit in the heap (see ROCK-15286).
  // 'include_hidden' is needed to match what 'GET table' returned: that endpoint did not filter the
  // tables by visibility type. Metabase answers 404 instead of an empty list when the schema has no
  // table we can read, which here only means that none of the tables we are looking for lives there.
  // Both checked against Metabase 0.56.4: without 'include_hidden' the tables whose visibility type is
  // set are missing, and with it the answer is exactly the one 'GET table' gave for that schema. The
  // 404 of this endpoint is a 'text/plain' 'Not found.', while a url metabase does not route at all
  // answers 'application/json' with '"API endpoint does not exist."' (see 'ImplicitExtensions.asIfFound')
  def getTablesInSchema(dbId: BigInt, schema: Option[String]): List[TableInSchemaDto] =
    request(tablesInSchemaPath(dbId, schema)).asIfFound[List[TableInSchemaDto]].getOrElse(List.empty)

  // Returns a single table with all its fields. This is how the tables and fields a collection needs
  // must be resolved: asking metabase for every field of a database ('GET database/:id/fields') does
  // not work with big catalogs, as metabase resolves it with a single query enumerating every table of
  // the database and PostgreSQL cannot bind more than 65535 parameters in a statement (see ROCK-15286).
  // 'include_sensitive_fields' and 'include_hidden_fields' are needed because this endpoint filters out
  // the fields whose visibility type is sensitive or hidden, while 'GET field/:id' does return them:
  // without them the exporter would put such a field in the export and the importer would not be able
  // to find it in the target metabase. Checked against Metabase 0.56.4, and neither parameter covers
  // what the other one does: 'include_sensitive_fields' brings back only the sensitive fields and
  // 'include_hidden_fields' only the hidden ones, so dropping either would make a whole class of field
  // disappear from the export in silence. The 'details-only' fields come back without either of them.
  // Beware that no parameter makes this endpoint return the fields metabase has marked as inactive (the
  // columns that are no longer in the table) or retired: the inactive ones are resolved with
  // 'getFieldsInDb' as a last resort (see 'Importer.fieldsHiddenInTableMetadata') and the retired ones
  // cannot be resolved at all, as no endpoint that answers by table or by database returns them
  def getTableMetadata(tableId: BigInt): TableMetadataDto =
    request(s"table/$tableId/query_metadata?include_sensitive_fields=true&include_hidden_fields=true")
      .as[TableMetadataDto]
      .body

  def getFieldWithTable(fieldId: BigInt): FieldWithTableDto =
    request(s"field/$fieldId").as[FieldWithTableDto].body

  // The only endpoint that returns the fields metabase has marked as inactive (the columns that are no
  // longer in the table), but it is also the one that does not work with big catalogs (see the comment
  // in 'getTableMetadata' and ROCK-15286), so it must be asked only when 'getTableMetadata' left some
  // needed field unresolved, and only for the databases of those fields.
  // It does filter out the fields whose visibility type is sensitive or retired, so it is not a way of
  // resolving those: the sensitive ones come in 'getTableMetadata' and the retired ones nowhere
  def getFieldsInDb(dbId: BigInt): List[FieldInDbDto] =
    request(s"database/$dbId/fields").as[List[FieldInDbDto]].body

  def getCollectionGraph: CollectionGraph =
    request("collection/graph").as[CollectionGraph].body

  def updateCollectionGraph(graph: CollectionGraph): CollectionGraph =
    request("collection/graph").put(write(graph)).as[CollectionGraph].body

  def getGroups: List[Group] =
    request("permissions/group").as[List[Group]].body

  // The MetaBot group always have id 3. It is not included in this response, but it is included in the
  // collectionGraph so we need it to be in this map
  def getGroupIdToNameMap: Map[GroupId, GroupName] =
    getGroups.map(g => g.id -> g.name).toMap + ("3" -> "MetaBot")

  def getGroupNameToIdMap: Map[GroupName, GroupId]=
    getGroupIdToNameMap.map(_.swap)

  def getSubscriptions(dashboardId: BigInt): List[Subscription] =
    request(s"pulse?dashboard_id=$dashboardId").as[List[Subscription]].body

  def updateSubscription(subscription: Subscription): Subscription =
    request(s"pulse/${subscription.id}").put(write(subscription)).as[Subscription].body

  def createSubscription(subscription: Subscription): BigInt =
    request("pulse").postData(write(subscription)).as[Id].body.id

  def deleteSubscription(idSubscription: BigInt): Unit =
    request(s"pulse/$idSubscription").put("""{"archived":true}""").execute()

  def getSnippet(snippetId: BigInt): Snippet =
    request(s"native-query-snippet/$snippetId").as[Snippet].body

  def getAllSnippets: List[Snippet] =
    request("native-query-snippet").as[List[Snippet]].body

  def updateSnippet(id: BigInt, snippet: Snippet): Snippet =
    request(s"native-query-snippet/$id").put(write(snippet)).as[Snippet].body

  def createSnippet(snippet: Snippet): BigInt =
    request("native-query-snippet").postData(write(snippet)).as[Id].body.id

  def request(apiPath: String): HttpRequest = {
    val safeRequest = Http(s"$metabaseUrl/api/$apiPath").headers(headers).timeout(connectionAndReadTimeout, connectionAndReadTimeout)
    if (allowUnsafeSsl) safeRequest.option(HttpOptions.allowUnsafeSSL) else safeRequest
  }

}
