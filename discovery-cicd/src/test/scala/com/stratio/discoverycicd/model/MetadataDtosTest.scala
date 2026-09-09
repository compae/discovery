/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.model

import org.json4s._
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

/**
 * The responses in this test are real (trimmed) responses of metabase 0.50 and 0.56. What they pin down is
 * which properties each endpoint gives us to resolve the tables and fields of an export and, above all, that
 * 'active' is optional in every DTO, so that a metabase that did not send it would not break the parsing
 */
@RunWith(classOf[JUnitRunner])
class MetadataDtosTest extends WordSpec with Matchers {

  implicit val formats: Formats = DefaultFormats

  private def queryMetadataResponse(active: String = """"active": true,""", schema: String = "\"PUBLIC\"") =
    s"""
       |{
       | "id": 1,
       | "name": "ANALYTIC_EVENTS",
       | "schema": $schema,
       | "db_id": 1,
       | $active
       | "display_name": "ANALYTIC_EVENTS",
       | "fields": [
       |   {"id": 19, "name": "ID", "active": true, "visibility_type": "normal"},
       |   {"id": 18, "name": "ACCOUNT_ID", "active": true, "visibility_type": "normal"}
       | ]
       |}
     """.stripMargin

  // 'GET field/:id' is the only endpoint that returns the fields metabase has marked as inactive, so it is
  // also the only one that can put such a field in an export (see ROCK-15286)
  private def fieldResponse(
    fieldActive: String = """"active": true,""",
    tableActive: String = """"active": true,"""
  ) =
    s"""
       |{
       | "id": 18,
       | "name": "ACCOUNT_ID",
       | $fieldActive
       | "visibility_type": "normal",
       | "table_id": 1,
       | "table": {
       |   "id": 1,
       |   "name": "ANALYTIC_EVENTS",
       |   "schema": "PUBLIC",
       |   "db_id": 1,
       |   $tableActive
       |   "display_name": "ANALYTIC_EVENTS"
       | }
       |}
     """.stripMargin

  "The response of 'GET table/:id/query_metadata'" should {

    "give us the table and all its fields" in {
      val table = parse(queryMetadataResponse()).extract[TableMetadataDto]

      table.toTable("sample") shouldBe Table(1, "ANALYTIC_EVENTS", Database(1, "sample"), Some("PUBLIC"))
      table.toFields("sample") shouldBe List(
        Field(19, "ID", "ANALYTIC_EVENTS", Some("PUBLIC"), "sample"),
        Field(18, "ACCOUNT_ID", "ANALYTIC_EVENTS", Some("PUBLIC"), "sample")
      )
      table.isActive shouldBe true
    }

    "tell us when the table is inactive, as such a table can only be imported if it is active in the target" in {
      parse(queryMetadataResponse(""""active": false,""")).extract[TableMetadataDto].isActive shouldBe false
    }

    "take the table as active when metabase does not say whether it is active" in {
      parse(queryMetadataResponse(active = "")).extract[TableMetadataDto].isActive shouldBe true
    }
  }

  "The response of 'GET field/:id'" should {

    "identify the field by the table and the database it belongs to" in {
      val field = parse(fieldResponse()).extract[FieldWithTableDto]

      field.toField("sample") shouldBe Field(18, "ACCOUNT_ID", "ANALYTIC_EVENTS", Some("PUBLIC"), "sample")
      field.table.db_id shouldBe 1
      field.isActive shouldBe true
    }

    "tell us when the field itself is inactive" in {
      parse(fieldResponse(fieldActive = """"active": false,""")).extract[FieldWithTableDto].isActive shouldBe false
    }

    "tell us when it is the table of the field the one that is inactive" in {
      val field = parse(fieldResponse(tableActive = """"active": false,""")).extract[FieldWithTableDto]

      field.table.isActive shouldBe false
      field.isActive shouldBe false
    }

    "take the field as active when metabase does not say whether it is active" in {
      val field = parse(fieldResponse(fieldActive = "", tableActive = "")).extract[FieldWithTableDto]

      field.isActive shouldBe true
    }
  }

  // 'GET database/:id/schema/:schema' is what resolves the tables of an import: the whole catalog of the
  // instance ('GET table') does not fit in memory when there are tens of thousands of tables
  private def schemaResponse(schema: String = "\"PUBLIC\"") =
    s"""
       |[
       | {"id": 1, "name": "ANALYTIC_EVENTS", "display_name": "Analytic Events", "schema": $schema,
       |  "db_id": 1, "active": true, "visibility_type": null, "entity_type": "entity/GenericTable",
       |  "description": null, "caveats": null, "points_of_interest": null, "is_upload": false,
       |  "field_order": "database", "initial_sync_status": "complete", "estimated_row_count": null,
       |  "show_in_getting_started": false, "created_at": "2026-07-14T09:12:31.884Z",
       |  "updated_at": "2026-07-14T09:12:33.104Z"},
       | {"id": 6, "name": "ACCOUNTS", "display_name": "Accounts", "schema": $schema,
       |  "db_id": 1, "active": true, "visibility_type": "hidden", "entity_type": "entity/GenericTable",
       |  "description": null, "caveats": null, "points_of_interest": null, "is_upload": false,
       |  "field_order": "database", "initial_sync_status": "complete", "estimated_row_count": null,
       |  "show_in_getting_started": false, "created_at": "2026-07-14T09:12:31.884Z",
       |  "updated_at": "2026-07-14T09:12:33.104Z"}
       |]
     """.stripMargin

  "The response of 'GET database/:id/schema/:schema'" should {

    "give us the tables of that schema with the database we asked them for" in {
      val tables = parse(schemaResponse()).extract[List[TableInSchemaDto]]

      tables.map(_.toTable(Database(1, "sample"))) shouldBe List(
        Table(1, "ANALYTIC_EVENTS", Database(1, "sample"), Some("PUBLIC")),
        Table(6, "ACCOUNTS", Database(1, "sample"), Some("PUBLIC"))
      )
    }

    // this is what would silently break the import of a table with no schema: 'GET table' and
    // 'GET table/:id/query_metadata' turn a null schema into the empty string and this endpoint does
    // not, so both spellings have to identify the same table (see 'Table.normalizeSchema')
    "identify a table with no schema the same way the exported data does" in {
      val exported = parse(queryMetadataResponse(schema = "\"\"")).extract[TableMetadataDto].toTable("sample")
      val inTarget = parse(schemaResponse(schema = "null")).extract[List[TableInSchemaDto]]
                       .head.toTable(Database(1, "sample"))

      inTarget.schema shouldBe None
      inTarget.key shouldBe exported.key
    }
  }

  "The response of 'GET database/:id/fields'" should {

    "identify every field by its table and schema" in {
      val response =
        """
          |[
          | {"id": 8, "name": "ID", "display_name": "ID", "base_type": "type/BigInteger",
          |  "semantic_type": "type/PK", "table_name": "ACCOUNTS", "table_id": 6, "schema": "PUBLIC"},
          | {"id": 45, "name": "ZIP", "display_name": "ZIP", "base_type": "type/Text",
          |  "semantic_type": "type/ZipCode", "table_name": "PEOPLE", "table_id": 3, "schema": "PUBLIC"}
          |]
        """.stripMargin

      parse(response).extract[List[FieldInDbDto]].map(_.toField("sample")) shouldBe List(
        Field(8, "ID", "ACCOUNTS", Some("PUBLIC"), "sample"),
        Field(45, "ZIP", "PEOPLE", Some("PUBLIC"), "sample")
      )
    }
  }
}
