/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

case class Collection(id: BigInt,
                      name: String,
                      description: String,
                      parent_id: Option[BigInt])

case class CollectionItem(id: BigInt,
                          name: String,
                          model: String)


case class CollectionItemsResponse(data: List[CollectionItem], total: Option[Long], limit: Option[Long])

object Collection {

  object Models {
    val COLLECTION = "collection"
    val DASHBOARD = "dashboard"
    val CARD = "card"
    // these are what metabase calls models (https://www.metabase.com/docs/latest/data-modeling/models),
    // which are implemented just like saved questions (cards) with a flag { "dataset": true }, and are
    // declared of type "dataset" instead of "card" when retrieving items from a collection
    val DATASET = "dataset"
    // the same with metrics
    val METRIC = "metric"
  }
}
