/*
 * © 2021 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.utils.TypeAliases.{CollId, DashboardId}

/**
 * Trait representing the capability of being able to tell that an entity
 * read from the exported data "is the same as" an entity of the same type
 * in the target metabase by selecting some "identifier" properties (different from the DB id)
 * that signal that this is the same entity across metabase instances
 */
trait Identificable[A] {
  type Identifier
  def identifier(a: A): Identifier
}

object Identificable {

  implicit class IdentificableOps[A: Identificable](entity: A) {
    def identifier = implicitly[Identificable[A]].identifier(entity)
    def isTheSameAs(other: A) = entity.identifier == other.identifier
  }


  implicit val identificableTable = new Identificable[Table] {
    type Identifier = (String, String, Option[String])
    def identifier(table: Table): Identifier = (table.name, table.db.name, table.schema)
  }

  implicit val identificableField = new Identificable[Field] {
    type Identifier = (String, String, String, Option[String])
    def identifier(field: Field): Identifier = (field.name, field.table_name, field.dbName, field.schema)
  }

  implicit val identificableDatabase = new Identificable[Database] {
    type Identifier = String
    def identifier(database: Database): Identifier = database.name
  }

  implicit val identificableCard = new Identificable[Card] {
    type Identifier = (String, Option[BigInt])
    // when comparing with cards in the target metabase, make sure that the
    // collection_id has been replaced with the corresponding target collection_id!
    def identifier(card: Card) = (card.name, card.collection_id)
  }

  implicit val identificableDashboard = new Identificable[Dashboard] {
    type Identifier = (String, Option[BigInt])
    // when comparing with dashboards in the target metabase, make sure that the
    // collection_id has been replaced with the corresponding target collection_id!
    def identifier(dashboard: Dashboard) = (dashboard.name, dashboard.collection_id)
  }

  implicit val identificableCollection = new Identificable[Collection] {
    type Identifier = String
    def  identifier(collection: Collection) = collection.name
  }

  implicit val identificableSubscription = new Identificable[Subscription] {
    type Identifier = (String, DashboardId, CollId)
    def  identifier(subscription: Subscription) = (subscription.name, subscription.dashboard_id, subscription.collection_id)
  }

  implicit val identificableSnippet= new Identificable[Snippet] {
    type Identifier = String
    def  identifier(snippet: Snippet) = snippet.name
  }

  implicit val identificableDashboardTab = new Identificable[DashboardTab] {
    type Identifier = (String, DashboardId)
    def identifier(tab: DashboardTab): Identifier = (tab.name, tab.dashboard_id)
  }
}
