/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

object TypeAliases {

  type IdMap = Map[BigInt, BigInt]
  type CollId = BigInt
  type DashboardId = BigInt
  type SubscriptionId = BigInt

  // We add this aliases to be able to see what the String -> String maps
  // in the permissions parts are about. We have tried to put the Ids as
  // BigInts but since in the json responses they come as Strings, something odd
  // hapens while serializing and we get a cast exception from java.Integer to BigInt
  type GroupId = String
  type GroupName = String
  type CollectionId = String
  type Permission = String

}
