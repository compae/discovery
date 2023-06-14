/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.model

sealed trait LinkType extends Product with Serializable

object LinkType {
  case object Dashboard extends LinkType
  case object Question extends LinkType

  // We need LinkType acting as a string to check if it it appears in some json keys
  implicit def LinkTypeToString(linkType: LinkType): String =
    linkType match {
      case Dashboard => "dashboard"
      case Question => "question"
    }

}
