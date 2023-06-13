/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.TypeAliases.{ GroupName, Permission }

case class ExportedCollection(cards: List[Card],
                              dashboards: List[Dashboard],
                              fields: List[Field],
                              tables: List[Table],
                              databases: List[Database],
                              subscriptions: List[Subscription],
                              snippets: List[Snippet] = List.empty,
                              collection: Collection,
                              children: List[Child],
                              permissionsPerGroup: Map[GroupName, Permission],
                              humanizationEnabled: Boolean = true)

case class Child(collection: Collection, permissionsPerGroup: Map[GroupName, Permission])
