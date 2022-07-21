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
case class Field(id: BigInt, name: String, table: Table, display_name: String) {

  // Be careful, in the DbFieldDto, table_name and name are actually the display names
  def isTheSameAs(dbField: DbFieldDto, dbName: String): Boolean =
    (display_name == dbField.name) &&
      (table.display_name == dbField.table_name) &&
      (table.schema == dbField.schema) &&
      (table.dbName == dbName)
}


// These are the models that metabase api responds with while getting a field
// (it responds with different models depending on the enpoint, and we need both...)
// (actually the models have more fields but these are the ones we need)
case class FieldDto(id: BigInt, name: String, table_id: BigInt, display_name: String) {
  def toField(tableIdToTableMap: Map[BigInt, Table]) =
    Field(id, name, tableIdToTableMap.getOrElse(table_id, throw mappingError(table_id)), display_name)

  def mappingError(table_id: BigInt) = MappingException(
    s"Field obtained from api: ${this.toJson} claims to be in table with id $table_id but this table was not found in metabase"
  )
}

case class DbFieldDto(id: BigInt, name: String, table_name: String, schema: Option[String])
