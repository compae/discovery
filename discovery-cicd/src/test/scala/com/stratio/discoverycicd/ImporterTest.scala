/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd

import com.stratio.discoverycicd.exceptions.ValidationDataException
import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.utils.ApiClient
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.mutable.ListBuffer

/**
 * How the tables and the fields of an export are matched with the ones of the target metabase. Since
 * ROCK-15286 that is done schema by schema and table by table, and never with the whole list of fields of
 * a database, which is the request that does not work with big catalogs: what these tests pin down is that
 * the fast path resolves everything it can, whatever endpoint of metabase each side of the export came
 * from, and that the slow one is only used for what nothing else can resolve
 */
@RunWith(classOf[JUnitRunner])
class ImporterTest extends WordSpec with Matchers {

  // The target metabase: two databases, a schema 'PUBLIC' in each one and a table with no schema.
  // 'GET database/:id/schema/:schema' gives a null schema as it is stored, while
  // 'GET table/:id/query_metadata' turns it into the empty string, as metabase really does
  private val targetMetabase = Map(
    "database" -> """{"data": [{"id": 1, "name": "sample"}, {"id": 2, "name": "other"}]}""",

    "database/1/schema/PUBLIC" ->
      """[{"id": 10, "name": "ACCOUNTS", "schema": "PUBLIC", "db_id": 1, "active": true}]""",
    "database/1/schema/" ->
      """[{"id": 11, "name": "NOSCHEMA", "schema": null, "db_id": 1, "active": true}]""",
    "database/2/schema/PUBLIC" ->
      """[{"id": 20, "name": "EVENTS", "schema": "PUBLIC", "db_id": 2, "active": true}]""",

    "table/10/query_metadata" ->
      """{"id": 10, "name": "ACCOUNTS", "schema": "PUBLIC", "db_id": 1, "active": true,
        | "fields": [{"id": 100, "name": "ID"}, {"id": 101, "name": "EMAIL"}]}""".stripMargin,
    "table/11/query_metadata" ->
      """{"id": 11, "name": "NOSCHEMA", "schema": "", "db_id": 1, "active": true,
        | "fields": [{"id": 110, "name": "CODE"}]}""".stripMargin,
    "table/20/query_metadata" ->
      """{"id": 20, "name": "EVENTS", "schema": "PUBLIC", "db_id": 2, "active": true,
        | "fields": [{"id": 200, "name": "TS"}]}""".stripMargin,

    // the column 'OLD' is no longer in the table, so metabase marks it as inactive and only gives it
    // here (see 'Importer.fieldsHiddenInTableMetadata')
    "database/1/fields" ->
      """[{"id": 100, "name": "ID", "table_name": "ACCOUNTS", "schema": "PUBLIC"},
        | {"id": 104, "name": "OLD", "table_name": "ACCOUNTS", "schema": "PUBLIC"}]""".stripMargin
  )

  private val accounts = Table(90, "ACCOUNTS", Database(9, "sample"), Some("PUBLIC"))
  // as 'GET table/:id/query_metadata' exports a table with no schema
  private val noSchema = Table(91, "NOSCHEMA", Database(9, "sample"), Some(""))
  // a table of a database that no card of the collection referenced, so an export made before
  // ROCK-15286 does not have it among its databases
  private val events = Table(92, "EVENTS", Database(8, "other"), Some("PUBLIC"))

  "Matching the tables and fields of an export with the ones of the target metabase" should {

    "resolve them all without asking for the whole list of fields of any database" in {
      val exported = exportOf(
        databases = List(Database(9, "sample")),
        tables = List(accounts, noSchema, events),
        fields = List(
          Field(900, "ID", "ACCOUNTS", Some("PUBLIC"), "sample"),
          // a field of a table with no schema comes from 'GET field/:id', the one endpoint that does
          // not turn the null schema into the empty string
          Field(901, "CODE", "NOSCHEMA", None, "sample"),
          Field(902, "TS", "EVENTS", Some("PUBLIC"), "other")
        )
      )

      withMetabase(targetMetabase) { (importer, requested) =>
        val maps = importer.getDbMap(exported.header)

        maps.db shouldBe Map(BigInt(9) -> BigInt(1))
        maps.table shouldBe Map(BigInt(90) -> BigInt(10), BigInt(91) -> BigInt(11), BigInt(92) -> BigInt(20))
        maps.field shouldBe Map(BigInt(900) -> BigInt(100), BigInt(901) -> BigInt(110), BigInt(902) -> BigInt(200))

        requested() should contain("database/1/schema/")
        requested() should not contain "database/1/fields"
      }
    }

    // metabase keeps the old, inactive 'Field' when a column is dropped and added again, so two
    // different fields of the export can be the same field of the target metabase
    "point two exported fields that name the same column to the same field of the target metabase" in {
      val exported = exportOf(
        databases = List(Database(9, "sample")),
        tables = List(accounts),
        fields = List(
          Field(900, "ID", "ACCOUNTS", Some("PUBLIC"), "sample"),
          Field(903, "ID", "ACCOUNTS", Some("PUBLIC"), "sample")
        )
      )

      withMetabase(targetMetabase) { (importer, _) =>
        importer.getDbMap(exported.header).field shouldBe
          Map(BigInt(900) -> BigInt(100), BigInt(903) -> BigInt(100))
      }
    }

    // this is what ROCK-15286 lets the import go on with: the field is not in the metadata of its table
    // because metabase has marked it as inactive there
    "fall back to the whole list of fields of a database only for what nothing else resolves" in {
      val exported = exportOf(
        databases = List(Database(9, "sample")),
        tables = List(accounts),
        fields = List(Field(904, "OLD", "ACCOUNTS", Some("PUBLIC"), "sample"))
      )

      withMetabase(targetMetabase) { (importer, requested) =>
        importer.getDbMap(exported.header).field shouldBe Map(BigInt(904) -> BigInt(104))

        requested() should contain("database/1/fields")
      }
    }

    "say which table is missing, and what the target metabase does have in its schema" in {
      val exported = exportOf(
        databases = List(Database(9, "sample")),
        tables = List(accounts.copy(name = "accounts")),
        fields = List.empty
      )

      withMetabase(targetMetabase) { (importer, _) =>
        val error = the [IllegalArgumentException] thrownBy importer.getDbMap(exported.header)

        error.getMessage should include("sample / PUBLIC / accounts (id:[90] in the export)")
        error.getMessage should include("sample / PUBLIC: 1 table(s): ACCOUNTS")
      }
    }

    "say which field is missing, naming it as whoever reads the error thinks of it" in {
      val exported = exportOf(
        databases = List(Database(9, "sample")),
        tables = List(accounts),
        fields = List(Field(905, "GONE", "ACCOUNTS", Some("PUBLIC"), "sample"))
      )

      withMetabase(targetMetabase) { (importer, _) =>
        val error = the [IllegalArgumentException] thrownBy importer.getDbMap(exported.header)

        error.getMessage should include("sample / PUBLIC / ACCOUNTS.GONE (id:[905] in the export)")
      }
    }

    "say which database is missing" in {
      val exported = exportOf(databases = List(Database(9, "absent")), tables = List.empty, fields = List.empty)

      withMetabase(targetMetabase) { (importer, _) =>
        val error = the [IllegalArgumentException] thrownBy importer.getDbMap(exported.header)

        error.getMessage should include("Missing: absent")
        error.getMessage should include("2 database(s): other, sample")
      }
    }
  }

  // The whole point of asking schema by schema is not to bring the catalog of the instance into memory
  // (see ROCK-15286), and a single schema can still hold tens of thousands of tables: an error message
  // that listed them all would put them back in memory, in the log and in the body of the response
  "The description of what the target metabase does have" should {

    "report the count, and only as many names as a message can carry" in {
      val found = (1 to 40).map(number => f"T_$number%02d")

      val described = Importer.describeFound("table", found, List("nothing like it"))

      described should startWith("40 table(s): T_01, T_02")
      described should endWith(s", ... and ${40 - Importer.MaxNamesReported} more")
      described.split(", ").size shouldBe Importer.MaxNamesReported + 1 // the names, the count and the tail
    }

    "put first the names that look like the one that is missing, which are the ones that explain it" in {
      val found = (1 to 40).map(number => f"T_$number%02d")

      Importer.describeFound("table", found, List("t_07")) should startWith("40 table(s): T_07, T_01")
    }

    "not say there is more when everything fits" in {
      Importer.describeFound("database", List("sample", "other"), List("absent")) shouldBe
        "2 database(s): other, sample"
    }
  }

  // Each round is one pass over the uploaded file (see 'ExportSource'), so which questions go in
  // which round is now worked out from the ids alone, before a single question has been read
  "The rounds the questions are created in" should {

    "be a single one when no question is built on top of another" in {
      Importer.roundsOf(Map(BigInt(1) -> Set.empty, BigInt(2) -> Set.empty)) shouldBe
        List(Set(BigInt(1), BigInt(2)))
    }

    "put a question after the one it uses as a table or as a metric" in {
      val rounds = Importer.roundsOf(Map(
        BigInt(1) -> Set.empty[BigInt],
        BigInt(2) -> Set(BigInt(1)),
        BigInt(3) -> Set(BigInt(2)),
        BigInt(4) -> Set.empty[BigInt]
      ))

      rounds shouldBe List(Set(BigInt(1), BigInt(4)), Set(BigInt(2)), Set(BigInt(3)))
    }

    "wait for every question one depends on and not only for the first" in {
      val rounds = Importer.roundsOf(Map(
        BigInt(1) -> Set.empty[BigInt],
        BigInt(2) -> Set(BigInt(1)),
        BigInt(3) -> Set(BigInt(1), BigInt(2))
      ))

      rounds shouldBe List(Set(BigInt(1)), Set(BigInt(2)), Set(BigInt(3)))
    }

    // 'dependencyTrees' rejects both of these before anything is created, so getting here means the
    // check was missed: what must not happen is going round for ever
    "give up instead of looping when the dependencies cannot be satisfied" in {
      val circular = Map(BigInt(1) -> Set(BigInt(2)), BigInt(2) -> Set(BigInt(1)))

      val error = the [ValidationDataException] thrownBy Importer.roundsOf(circular)
      error.msg should include("impossible to satisfy their card dependencies")
    }

    "give up when a question depends on one the export does not carry" in {
      val missing = Map(BigInt(1) -> Set(BigInt(99)))

      val error = the [ValidationDataException] thrownBy Importer.roundsOf(missing)
      error.msg should include("Cannot create cards '1'")
    }

    "have nothing to do when there are no questions" in {
      Importer.roundsOf(Map.empty) shouldBe empty
    }
  }

  private def exportOf(databases: List[Database], tables: List[Table], fields: List[Field]) =
    ExportedCollection(
      cards = List.empty,
      dashboards = List.empty,
      fields = fields,
      tables = tables,
      databases = databases,
      subscriptions = List.empty,
      snippets = List.empty,
      collection = Collection(1, "a collection", "", None),
      children = List.empty,
      permissionsPerGroup = Map.empty
    )

  // A stub of metabase that answers by path, so that what actually travels to it is checked and not
  // only what we do with the answers. 'com.sun.net.httpserver' comes with the JDK, so this needs no new
  // dependency
  private def withMetabase(responses: Map[String, String])(check: (Importer, () => List[String]) => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val requested = ListBuffer.empty[String]
    server.createContext("/api", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        val path = exchange.getRequestURI.getPath.stripPrefix("/api/")
        requested += path
        val (status, body) = responses.get(path) match {
          case Some(response) => (200, response)
          // metabase answers 404 and not an empty list when a schema has no table we can read
          case None => (404, """{"message": "Not found."}""")
        }
        val bytes = body.getBytes(UTF_8)
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        exchange.getResponseBody.write(bytes)
        exchange.close()
      }
    })
    server.start()
    val api = new ApiClient(s"http://127.0.0.1:${server.getAddress.getPort}", "a-session", timeout = "5s")
    try check(new Importer(api), () => requested.toList)
    finally server.stop(0)
  }
}
