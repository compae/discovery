/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.utils.ImplicitExtensions.HttpRequestExtension
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write
import scalaj.http._


class ApiClient(metabaseUrl: String, sessionIdParams: Either[String, (String,String)], allowUnsafeSsl: Boolean = false) {

  implicit val formats = DefaultFormats

  private val sessionId: String = getAuthToken

  private val headers = Map(
    "content-type" -> "application/json",
    "X-Metabase-Session" -> sessionId
  )

  val user = request("user/current").as[User]
  val userId = user.body.id

  def getCollection(collectionId: BigInt) =
    request(s"collection/$collectionId").as[Collection].body

  def getItemsInCollecton(collectionId: BigInt): List[CollectionItem] =
    request(s"collection/$collectionId/items").as[List[CollectionItem]].body

  // if we want to get the root collection we pass a None collectionId
  def getItemsInCollecton(collectionId: Option[BigInt]): List[CollectionItem] ={
    val collApiPath = collectionId.map(_.toString).getOrElse("root")
    request(s"collection/$collApiPath/items").as[List[CollectionItem]].body
  }

  def archiveCollecton(collectionId: BigInt) =
    request(s"collection/$collectionId").put("""{"archived":true}""").execute()

  def deleteCollectionItem(item: CollectionItem) =
    if (item.model == "collection") archiveCollecton(item.id)
    else request(s"${item.model}/${item.id}").method("DELETE").execute()

  def createCollection(collection: Collection) = request("collection").postData(write(collection)).as[Collection].body

  def renameCollection(id: BigInt, newName: String) =
    request(s"collection/$id").put(s"""{"name": $newName}""").execute()

  def getDashboard(dashboardId: BigInt) = request(s"dashboard/$dashboardId").as[Dashboard].body

  def createDashboard(dashboard: Dashboard) = request("dashboard").postData(write(dashboard)).as[Id].body.id

  def updateDashboard(id: BigInt, dashboard: Dashboard) =
    request(s"dashboard/$id")
      .put(write(dashboard)(formats.preservingEmptyValues))
      .execute()

  def addCardToDashboard(id: BigInt, card: CardPost) =
    request(s"dashboard/$id/cards")
      .postData(write(card))
      .as[Id]
      .body

  def updateDashCardsInDashboard(id: BigInt, dashCards: List[DashCard]) =
    request(s"dashboard/$id/cards")
      .put(s""" { "cards": ${write(dashCards)} } """)
      .execute()

  def getCard(cardId: BigInt) = request(s"card/$cardId").as[Card].body

  def createCard(card: CardWithoutId) = request("card").postData(write(card)).as[Card].body

  def getDatabase(databaseId: BigInt) = request(s"database/$databaseId").as[Database].body

  def getAllDatabases = request("database").as[List[Database]].body

  def getFieldsInDb(dbId: BigInt) = request(s"database/$dbId/fields").as[List[DbFieldDto]].body

  def getTable(tableId: BigInt) = request(s"table/$tableId").as[TableDto].body

  def getAllTables = request("table").as[List[TableDto]].body

  def getField(fieldId: BigInt) = request(s"field/$fieldId").as[FieldDto].body

  def getCollectionGraph = request("collection/graph").as[CollectionGraph].body

  def updateCollectionGraph(graph: CollectionGraph) =
    request("collection/graph").put(write(graph)).as[CollectionGraph].body

  def getGroups = request("permissions/group").as[List[Group]].body

  // The MetaBot group always have id 3. It is not included in this response, but it is included in the
  // collectionGraph so we need it to be in this map
  def getGroupIdToNameMap = getGroups.map(g => g.id -> g.name).toMap + ("3" -> "MetaBot")

  def getGroupNameToIdMap = getGroupIdToNameMap.map(_.swap)

  def request(apiPath: String): HttpRequest = {
    val safeRequest = Http(s"$metabaseUrl/api/$apiPath").headers(headers)
    if (allowUnsafeSsl) safeRequest.option(HttpOptions.allowUnsafeSSL) else safeRequest
  }

  private def getAuthToken: String = {

    sessionIdParams match {
      case Left(sessionId) =>
        sessionId
      case Right((user,password)) =>
        val safeRequest = Http(s"$metabaseUrl/api/session")
          .header("content-type", "application/json")
          .postData(s"""{"username": "$user", "password": "$password"}""")
        val request = if (allowUnsafeSsl) safeRequest.option(HttpOptions.allowUnsafeSSL) else safeRequest
        request.as[SessionId].body.id
    }
 }
}


case class SessionId(id: String)
