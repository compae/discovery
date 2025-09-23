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

import scala.concurrent.duration.Duration
import scala.util.Try

object ApiClient {
  val DefaultTimeout = 10000
  val Limit = 9999L
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

  def createDashboard(dashboard: Dashboard): Dashboard =
    request("dashboard").postData(write(dashboard)).as[Dashboard].body

  def updateDashboard(id: BigInt, dashboard: Dashboard): Dashboard =
    request(s"dashboard/$id")
      .put(write(dashboard.copy(id = id))(formats.preservingEmptyValues))
      .as[Dashboard]
      .body

  def getCard(cardId: BigInt): Card =
    request(s"card/$cardId").as[Card].body.fixHyphens

  def createCard(card: Card): Card =
    request("card").postData(write(card)).as[Card].body

  def updateCard(card: Card): Card =
    request(s"card/${card.id}").put(write(card)).as[Card].body

  def getDatabase(databaseId: BigInt): Database =
    request(s"database/$databaseId").as[Database].body

  def getAllDatabases: List[Database] =
    request("database").as[ListDatabasesResponse].body.data

  def getFieldsInDb(dbId: BigInt): List[FieldDto] =
    request(s"database/$dbId/fields").as[List[FieldDto]].body

  def getAllTables: List[Table] =
    request("table").as[List[Table]].body

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
