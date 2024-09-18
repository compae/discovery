/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.exceptions.MappingException
import com.stratio.discoverycicd.utils.Jsonable.JsonableOps

// This is the model we use to "identify" a metabase Field
// based on its name and the table it belongs to
// so we can match the tables in the source and target metabases
case class Field(
  id: BigInt,
  name: String,
  table_name: String,
  schema: Option[String],
  dbName: String
)


case class FieldDto(
  id: BigInt,
  name: String,
  table_name: String,
  schema: Option[String]
) {
  def toField(dbName: String): Field =
    Field(id, name, table_name, schema, dbName)
}
