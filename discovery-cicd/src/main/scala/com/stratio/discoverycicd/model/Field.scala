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
) {
  // The table this field belongs to, as the tables the target metabase has are indexed (see
  // 'Table.Key'), so a field whose schema metabase spelled as a null still finds it (see
  // 'Table.normalizeSchema')
  def tableKey: Table.Key = Table.keyOf(dbName, schema, table_name)
}

// Response of 'GET field/:id': the field with its table hydrated, which is all we need to identify it
// in the target metabase. Used to resolve the few fields that do not belong to any of the tables the
// collection references (see ROCK-15286)
case class FieldWithTableDto(
  id: BigInt,
  name: String,
  table: TableRefDto,
  active: Option[Boolean]
) {
  def toField(dbName: String): Field =
    Field(id, name, table.name, table.schema, dbName)

  // this endpoint is the only one that returns the fields metabase has marked as inactive, so what it
  // says about it has to be checked to warn that such a field will hardly be importable
  def isActive: Boolean = active.getOrElse(true) && table.isActive
}

// Response of 'GET database/:id/fields' (see 'ApiClient.getFieldsInDb')
case class FieldInDbDto(
  id: BigInt,
  name: String,
  table_name: String,
  schema: Option[String]
) {
  def toField(dbName: String): Field =
    Field(id, name, table_name, schema, dbName)
}
