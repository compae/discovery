/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd

import com.stratio.discoverycicd.model.{Database, Field}
import com.stratio.discoverycicd.utils.ApiClient
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.mutable.ListBuffer

/**
 * How the tables and the fields a collection uses are got from metabase. Since ROCK-15286 they are asked
 * for one by one, because neither the catalog of the instance nor the whole list of fields of a database
 * can be brought into memory when the catalog is big. What these tests pin down is that nothing is asked
 * for twice, and that what metabase does not give us leaves the export incomplete instead of failing it,
 * which is what filtering the catalog silently did before
 */
@RunWith(classOf[JUnitRunner])
class ExporterTest extends WordSpec with Matchers {

  private val sourceMetabase = Map(
    "table/10/query_metadata" ->
      """{"id": 10, "name": "ACCOUNTS", "schema": "PUBLIC", "db_id": 1, "active": true,
        | "fields": [{"id": 100, "name": "ID"}, {"id": 101, "name": "EMAIL"}]}""".stripMargin,

    // a table metabase has marked as inactive: it is exported, and warned about, because the import
    // cannot resolve it unless the target metabase has it active
    "table/12/query_metadata" ->
      """{"id": 12, "name": "GONE", "schema": "PUBLIC", "db_id": 1, "active": false,
        | "fields": []}""".stripMargin,

    // 'GET field/:id' is the only endpoint that gives the fields metabase has marked as inactive, and
    // the only one that does not turn a null schema into the empty string
    "field/110" ->
      """{"id": 110, "name": "CODE", "active": false,
        | "table": {"id": 11, "name": "NOSCHEMA", "schema": null, "db_id": 2, "active": true}}""".stripMargin,

    "database/2" -> """{"id": 2, "name": "other"}"""
  )

  "Getting the metadata of the tables a collection uses" should {

    "leave out the ones metabase does not give us, and keep on exporting the rest" in {
      withMetabase { (exporter, _) =>
        exporter.getTablesMetadata(Set(10, 99), List.empty).map(_.id).toSet shouldBe Set(BigInt(10))
      }
    }

    "export a table that is inactive in this metabase, as only the target metabase can tell" in {
      withMetabase { (exporter, _) =>
        val tables = exporter.getTablesMetadata(Set(12), List.empty)

        tables.map(_.name) shouldBe List("GONE")
        tables.head.isActive shouldBe false
      }
    }
  }

  "Getting the fields a collection uses" should {

    // a collection can use fields of tables it does not use itself, through an implicit join by foreign
    // key for instance, and those are the only ones worth a request of their own
    "ask only for the ones that none of the tables of the collection brought" in {
      withMetabase { (exporter, requested) =>
        val tables = exporter.getTablesMetadata(Set(10), List.empty)

        val looseFields = exporter.getLooseFields(Set(100, 101, 110), tables, List.empty)

        looseFields.map(_.id) shouldBe List(BigInt(110))
        requested() should not contain "field/100"
        requested() should not contain "field/101"
      }
    }

    "identify a field by the table it belongs to, whatever way metabase spelled its schema" in {
      withMetabase { (exporter, _) =>
        val field = exporter.getLooseFields(Set(110), List.empty, List.empty).head

        field.toField("other") shouldBe Field(110, "CODE", "NOSCHEMA", None, "other")
        field.toField("other").tableKey shouldBe model.Table.keyOf("other", Some(""), "NOSCHEMA")
        field.isActive shouldBe false
      }
    }
  }

  // Without their names the importer cannot look for those tables and fields in the target metabase, so
  // they go into the export. One database that cannot be read only costs its own tables and fields
  "Getting the databases the tables and fields of a collection belong to" should {

    "give the ones metabase answers with" in {
      withMetabase { (exporter, _) =>
        exporter.getExtraDatabases(Set(2)) shouldBe Set(Database(2, "other"))
      }
    }

    "leave out the ones metabase does not give us, instead of failing the whole export" in {
      withMetabase { (exporter, _) =>
        exporter.getExtraDatabases(Set(2, 3)) shouldBe Set(Database(2, "other"))
      }
    }
  }

  // A stub of metabase that answers by path. 'com.sun.net.httpserver' comes with the JDK, so this needs
  // no new dependency
  private def withMetabase(check: (Exporter, () => List[String]) => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    // the exporter asks for several tables and fields at the same time now (see 'Concurrently'), and
    // this happens to be safe because a server with no executor of its own answers on a single thread,
    // which is not something worth depending on
    val requested = ListBuffer.empty[String]
    server.createContext("/api", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        val path = exchange.getRequestURI.getPath.stripPrefix("/api/")
        requested.synchronized { requested += path }
        val (status, body) = sourceMetabase.get(path) match {
          case Some(response) => (200, response)
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
    try check(new Exporter(api), () => requested.synchronized(requested.toList))
    finally server.stop(0)
  }
}
