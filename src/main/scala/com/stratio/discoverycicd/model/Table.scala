/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model


// This is the model we use to "identify" a metabase Table
// based on its name, schema and the name of the datbase it lives in
// so we can match the tables in the source and target metabases
case class Table(id: BigInt, name: String, dbName: String, schema: Option[String]) {
    def isTheSameAs(that: Table) =
    (this.name == that.name) && (this.dbName == that.dbName) && (this.schema == that.schema)
}

// This is the model that metabase api responds with while getting a table
// (actually the model has more fields but these are the ones we need)
case class TableDto(id: BigInt, name: String, db_id: BigInt, schema: Option[String]) {
    def toTable(dbIdToNameMap: Map[BigInt, String]) = Table(id, name, dbIdToNameMap(db_id), schema)
}
