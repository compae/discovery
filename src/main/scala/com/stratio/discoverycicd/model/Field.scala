/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

// This is the model we use to "identify" a metabase Field
// based on its name and the table it belongs to
// so we can match the tables in the source and target metabases
case class Field(id: BigInt, name: String, table: Table, display_name: String) {
    def isTheSameAs(that: Field) = (this.name == that.name) && (this.table isTheSameAs that.table)
}


// These are the models that metabase api responds with while getting a field
// (it responds with different models depending on the enpoint, and we need both...)
// (actually the models have more fields but these are the ones we need)
case class FieldDto(id: BigInt, name: String, table_id: BigInt, display_name: String) {
    def toField(tableIdToTableMap: Map[BigInt, Table]) =
      Field(id, name, tableIdToTableMap(table_id), display_name)
}

case class DbFieldDto(id: BigInt, name: String, table_name: String, schema: Option[String])
