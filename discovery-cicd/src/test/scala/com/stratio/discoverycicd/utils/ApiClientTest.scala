/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.exceptions.ResponseDeserializationException
import com.stratio.discoverycicd.model.{Database, Table}
import com.stratio.discoverycicd.utils.ApiClient.tablesInSchemaPath
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8

/**
 * The tables of an import are resolved schema by schema, because the whole catalog of the instance
 * ('GET table') does not fit in memory when there are tens of thousands of tables (see ROCK-15286).
 * The schema of that request travels in the path, so how it is written is what decides whether a schema
 * whose name is not a plain identifier can be imported at all
 */
@RunWith(classOf[JUnitRunner])
class ApiClientTest extends WordSpec with Matchers {

  "The path of 'GET database/:id/schema/:schema'" should {

    "ask for the tables of the given schema of the given database" in {
      tablesInSchemaPath(7, Some("PUBLIC")) shouldBe "database/7/schema/PUBLIC?include_hidden=true"
    }

    // metabase has a separate endpoint for them, 'GET database/:id/schema/', and it answers with the
    // tables whose schema is null and with the ones whose schema is the empty string, which is how
    // 'GET table' and 'GET table/:id/query_metadata' spell the very same thing
    "ask the endpoint of the tables that have no schema when there is no schema" in {
      tablesInSchemaPath(7, None) shouldBe "database/7/schema/?include_hidden=true"
      tablesInSchemaPath(7, Some("")) shouldBe "database/7/schema/?include_hidden=true"
    }

    "percent-encode the schema, as it travels in the path and not in a query parameter" in {
      tablesInSchemaPath(7, Some("my schema")) shouldBe "database/7/schema/my%20schema?include_hidden=true"
      tablesInSchemaPath(7, Some("a/b")) shouldBe "database/7/schema/a%2Fb?include_hidden=true"
      tablesInSchemaPath(7, Some("año")) shouldBe "database/7/schema/a%C3%B1o?include_hidden=true"
    }

    // a space must not become a '+': in a path segment that is a literal '+' and not a space
    "never encode a space as a plus sign" in {
      tablesInSchemaPath(7, Some("my schema")) should not include "+"
    }

    "leave alone the characters that need no encoding, so the request stays readable in a log" in {
      tablesInSchemaPath(7, Some("catalog.schema-1_2")) shouldBe
        "database/7/schema/catalog.schema-1_2?include_hidden=true"
    }

    // 'GET table', the endpoint this one replaces, did not filter the tables by visibility type, so
    // without this parameter the hidden tables of an export would stop being resolved (see ROCK-15286)
    "always ask for the hidden tables too" in {
      tablesInSchemaPath(7, Some("PUBLIC")) should include("include_hidden=true")
      tablesInSchemaPath(7, None) should include("include_hidden=true")
    }
  }

  "Asking metabase for the tables of a schema" should {

    "give us the tables it answers with, and send the schema percent-encoded" in {
      withMetabase(200, tablesResponse) { (api, requested) =>
        api.getTablesInSchema(7, Some("my schema")).map(_.toTable(Database(7, "sample"))) shouldBe
          List(Table(1, "ANALYTIC_EVENTS", Database(7, "sample"), Some("PUBLIC")))

        requested() shouldBe "/api/database/7/schema/my%20schema?include_hidden=true"
      }
    }

    // metabase answers 404 and not an empty list when the schema has no table we can read, which only
    // means that none of the tables we are looking for lives there: the tables that are missing are
    // reported all together by the importer (see ROCK-15286).
    // The body is the one metabase 0.56.4 really answers: a 'text/plain' 'Not found.' and not json, so
    // this also pins down that the status is what decides and that we never try to read such a body
    "take a 404 as 'there is no table of ours there' and not as a failure" in {
      withMetabase(404, "Not found.") { (api, _) =>
        api.getTablesInSchema(7, Some("NOT_SYNCED")) shouldBe List.empty
      }
    }

    // the 404 above must not turn every failure into an empty list, or a metabase that is answering
    // errors would look exactly like a metabase whose schemas are empty
    "still fail when metabase answers something else that we cannot read" in {
      withMetabase(500, "<html>Internal Server Error</html>") { (api, _) =>
        a [ResponseDeserializationException] should be thrownBy api.getTablesInSchema(7, Some("PUBLIC"))
      }
    }
  }

  // Checked against metabase 0.56.4: 'include_sensitive_fields' brings back only the fields whose
  // visibility type is sensitive and 'include_hidden_fields' only the hidden ones, neither covers what
  // the other does, and without them this endpoint leaves both out. Dropping either parameter would
  // make a whole class of field disappear from the export in silence, and the import would then fail
  // saying that a field it needs is not in the target metabase (see ROCK-15286)
  "Asking metabase for the metadata of a table" should {

    "ask for the sensitive fields and for the hidden ones too" in {
      withMetabase(200, tableMetadataResponse) { (api, requested) =>
        api.getTableMetadata(131119).fields.map(_.name) shouldBe List("id", "password")

        requested() should include("include_sensitive_fields=true")
        requested() should include("include_hidden_fields=true")
      }
    }
  }

  private val tableMetadataResponse =
    """{"id": 131119, "name": "core_user", "schema": "public", "db_id": 1, "active": true,
      |  "fields": [{"id": 262244, "name": "id", "visibility_type": "normal"},
      |             {"id": 262245, "name": "password", "visibility_type": "sensitive"}]}""".stripMargin

  private val tablesResponse =
    """[{"id": 1, "name": "ANALYTIC_EVENTS", "display_name": "Analytic Events", "schema": "PUBLIC",
      |   "db_id": 7, "active": true, "visibility_type": null, "field_order": "database"}]""".stripMargin

  // A stub of metabase, so that what actually travels to it is checked and not only how we build it.
  // 'com.sun.net.httpserver' comes with the JDK, so this needs no new dependency
  private def withMetabase(status: Int, body: String)(check: (ApiClient, () => String) => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    var requested: String = ""
    server.createContext("/api", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        val uri = exchange.getRequestURI
        requested = uri.getRawPath + Option(uri.getRawQuery).map("?" + _).getOrElse("")
        val bytes = body.getBytes(UTF_8)
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        exchange.getResponseBody.write(bytes)
        exchange.close()
      }
    })
    server.start()
    try check(new ApiClient(s"http://127.0.0.1:${server.getAddress.getPort}", "a-session", timeout = "5s"), () => requested)
    finally server.stop(0)
  }
}
