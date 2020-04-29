/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.utils.ImplicitExtensions.HttpRequestExtension
import com.stratio.discoverycicd.model._
import org.json4s.native.Serialization.{read, write}
import org.json4s.DefaultFormats
import scalaj.http._

import scala.util.{Failure, Success, Try}


class ApiClient(metabaseUrl: String, user: String, password: String) {

  implicit val formats = DefaultFormats

  private val sessionId: String = getAuthToken

  private val headers = Map(
    "content-type" -> "application/json",
    "X-Metabase-Session" -> sessionId
  )

  val userId = request("user/current").as[Id].body.id

  def getCollection(collectionId: BigInt) =
    request(s"collection/$collectionId").as[Collection].body

  def getItemsInCollecton(collectionId: BigInt): List[CollectionItem] =
    request(s"collection/$collectionId/items").as[List[CollectionItem]].body

  // if we want to get the root collection we pass a None collectionId
  def getItemsInCollecton(collectionId: Option[BigInt]): List[CollectionItem] ={
    val collApiPath = collectionId.map(_.toString).getOrElse("root")
    request(s"collection/$collApiPath/items").as[List[CollectionItem]].body
  }

  def deleteCollectionItem(item: CollectionItem) = request(s"${item.model}/${item.id}").method("DELETE").execute()

  def createCollection(collection: Collection) = request("collection").postData(write(collection)).as[Collection].body

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

  def request(apiPath: String): HttpRequest = Http(s"$metabaseUrl/api/$apiPath").headers(headers)

  private def getAuthToken: String = Http(s"$metabaseUrl/api/session")
    .header("content-type", "application/json")
    .postData(s"""{"username": "$user", "password": "$password"}""")
    .as[SessionId]
    .body
    .id




}


case class SessionId(id: String)
