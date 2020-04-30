/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.TypeAliases._

case class CollectionGraph(revision: BigInt, groups: Map[GroupId, Map[CollectionId, Permission]]) {

  def updatePermissionInColl(thisCollId: CollectionId,
                             groupToPermission: Map[GroupId, Permission]): CollectionGraph = {
    val updatedGroups = groups map { case (gId, collectionPermissions) =>
      val updatedCollPermsions = collectionPermissions.map {
        // In case there are some groups in the target metabase that do not exist in the source one
        // we do not give any permission on the new imported collection
        case (collId, _) if collId == thisCollId => (collId, groupToPermission.getOrElse(gId, "none"))
        case other => other
      }
      (gId, updatedCollPermsions)
    }
    copy(groups = updatedGroups)
  }


  def updatePermissionInColl(thisCollId: BigInt,
                             groupToPermission: Map[GroupId, Permission]): CollectionGraph =
    updatePermissionInColl(thisCollId.toString, groupToPermission)
}
