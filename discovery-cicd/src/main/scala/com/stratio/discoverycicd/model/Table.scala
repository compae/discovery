/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.Jsonable

// This is the model we use to "identify" a metabase Table
// based on its name, schema and the name of the datbase it lives in
// so we can match the tables in the source and target metabases
case class Table(id: BigInt, name: String,
                 dbName: String,
                 schema: Option[String],
                 display_name: String) extends Jsonable {
    def isTheSameAs(that: Table) =
    (this.name == that.name) && (this.dbName == that.dbName) && (this.schema == that.schema)
}

// This is the model that metabase api responds with while getting a table
// (actually the model has more fields but these are the ones we need)
case class TableDto(id: BigInt,
                    name: String,
                    db_id: BigInt,
                    schema: Option[String],
                    display_name: String) extends Jsonable {

  def mappingError(db_id: BigInt) = new Exception(
    s"Table obtained from api: ${toJson} claims to be in db with id $db_id, but this db was not found in metabase"
  )

  def toTable(dbIdToNameMap: Map[BigInt, String]) = Table(
    id, name, dbIdToNameMap.getOrElse(db_id, throw mappingError(db_id)), schema, display_name
  )
}
