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
) {
  def key: Table.Key = Table.keyOf(db.name, schema, name)
}

object Table {

  // The properties that identify a table across metabase instances (the same ones as
  // 'Identificable[Table]'). Having it as a key lets us index the tables the target metabase has and
  // resolve each table of the export, and each table an exported field belongs to, with a lookup
  // instead of a scan of everything we brought
  type Key = (String, Option[String], String) // database name, schema, table name

  def keyOf(dbName: String, schema: Option[String], tableName: String): Key =
    (dbName, normalizeSchema(schema), tableName)

  // Metabase spells 'no schema' as a null and as the empty string depending on the endpoint: 'GET
  // table' and 'GET table/:id/query_metadata' turn the null into the empty string, while 'GET
  // database/:id/schema/:schema', 'GET database/:id/fields' and the table hydrated inside 'GET
  // field/:id' do not. So the two spellings have to mean the same thing everywhere a table or a field
  // is identified, or the same table of the same instance would not be recognized depending on which
  // endpoint each side of the comparison came from
  def normalizeSchema(schema: Option[String]): Option[String] = schema.filter(_.nonEmpty)

  // How a schema is named in a message or in the log, as neither of the two spellings above says
  // anything to whoever reads it
  def describeSchema(dbName: String, schema: Option[String]): String =
    s"$dbName / ${normalizeSchema(schema).getOrElse("<no schema>")}"
}


// Response of 'GET table/:id/query_metadata': it returns the table together with all its fields, so
// it lets us resolve the tables and fields a collection references without asking metabase for the
// whole catalog of the instance (see ROCK-15286)
case class TableMetadataDto(
  id: BigInt,
  name: String,
  schema: Option[String],
  db_id: BigInt,
  active: Option[Boolean],
  fields: List[TableFieldDto]
) {
  def isActive: Boolean = active.getOrElse(true)

  def toTable(dbName: String): Table =
    Table(id, name, Database(db_id, dbName), schema)

  def toFields(dbName: String): List[Field] =
    fields.map(field => Field(field.id, field.name, name, schema, dbName))
}

case class TableFieldDto(
  id: BigInt,
  name: String
)

// Response of 'GET database/:id/schema/:schema': the tables of one schema of one database, which is
// all an import needs to resolve its tables without asking metabase for the whole catalog of the
// instance ('GET table', see ROCK-15286)
case class TableInSchemaDto(
  id: BigInt,
  name: String,
  schema: Option[String]
) {
  // Unlike 'GET table' and 'GET table/:id/query_metadata', this endpoint does not turn a null schema
  // into the empty string, so the schema travels as metabase gave it and it is 'Table.keyOf' that
  // makes both spellings identify the same table (see 'Table.normalizeSchema')
  def toTable(db: Database): Table =
    Table(id, name, db, schema)
}

// The table as it comes hydrated inside the response of 'GET field/:id'. Beware that this is the one
// endpoint that does not turn a null schema into the empty string (see 'Table.normalizeSchema')
case class TableRefDto(
  id: BigInt,
  name: String,
  schema: Option[String],
  db_id: BigInt,
  active: Option[Boolean]
) {
  def isActive: Boolean = active.getOrElse(true)
}
