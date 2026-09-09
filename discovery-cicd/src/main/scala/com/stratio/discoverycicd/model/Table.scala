/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.exceptions.MappingException
import com.stratio.discoverycicd.utils.Jsonable.JsonableOps

// This is the model we use to "identify" a metabase Table
// based on its name, schema and the name of the database it lives in
// so we can match the tables in the source and target metabases
case class Table(
  id: BigInt,
  name: String,
  db: Database,
  schema: Option[String]
)
