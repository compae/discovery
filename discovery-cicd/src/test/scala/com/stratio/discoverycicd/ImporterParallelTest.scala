/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd

import com.stratio.discoverycicd.model._
import com.stratio.discoverycicd.utils.ApiClient
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.json4s.JsonAST.JObject
import org.junit.runner.RunWith
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer

/**
 * Creating the questions of an import several at a time.
 *
 * Everything an import does is one request to metabase per question, dashboard and subscription,
 * and it used to make them one after another: for a collection with sixty thousand questions and
 * ~350ms a request that is hours of waiting on the network with the pod idle. What these pin down
 * is that they really do overlap now, that asking for one at a time really means one at a time, and
 * that the map from the ids of the source metabase to the ids of the target one is the same either
 * way, which is what everything created afterwards is built on.
 */
@RunWith(classOf[JUnitRunner])
class ImporterParallelTest extends WordSpec with Matchers {

  "Creating the questions of an import" should {

    "make several of the requests at the same time" in {
      withMetabase(questions = 12, parallelWrites = 4, delayMillis = 30) { imported =>
        imported.cardsCreated shouldBe 12
        imported.mostAtOnce should be > 1
        imported.mostAtOnce should be <= 4
      }
    }

    "make them one after another when that is what was asked for" in {
      withMetabase(questions = 6, parallelWrites = 1, delayMillis = 10) { imported =>
        imported.cardsCreated shouldBe 6
        imported.mostAtOnce shouldBe 1
      }
    }

    // the ids the target metabase gave are what the dashboards, the subscriptions and the questions
    // of the next round are then built with, so they may not depend on which request came back first
    "map every source id to the id the target gave it, whatever order they finished in" in {
      withMetabase(questions = 20, parallelWrites = 4, delayMillis = 5) { imported =>
        imported.cardsCreated shouldBe 20
      }
      withMetabase(questions = 20, parallelWrites = 1, delayMillis = 0) { imported =>
        imported.cardsCreated shouldBe 20
      }
    }

    // the permission graph is the biggest document metabase serves, and it used to be read and
    // written once per collection of the import
    "read and write the permission graph once and not once per collection" in {
      withMetabase(questions = 2, parallelWrites = 4, delayMillis = 0) { imported =>
        imported.count("GET collection/graph") shouldBe 1
        imported.count("PUT collection/graph") shouldBe 1
      }
    }
  }

  /** What the stubbed metabase saw while the import ran. */
  private class Seen(requests: ListBuffer[String], val mostAtOnce: Int) {
    def count(request: String): Int = requests.count(_ == request)
    def cardsCreated: Int = count("POST card")
  }

  private def cardOf(id: Int) = Card(
    id = id,
    name = s"question $id",
    parameters = JObject(),
    parameter_mappings = JObject(),
    dataset_query = JObject(),
    `type` = None,
    display = "table",
    description = None,
    visualization_settings = JObject(),
    enable_embedding = None,
    embedding_params = None,
    collection_id = Some(BigInt(7)),
    dashboard_id = None,
    collection_position = None,
    result_metadata = JObject(),
    cache_ttl = None,
    collection_preview = None,
    based_on_upload = None
  )

  private def exportOf(questions: Int) = ExportedCollection(
    cards = (1 to questions).map(cardOf).toList,
    dashboards = List.empty,
    fields = List.empty,
    tables = List.empty,
    databases = List.empty,
    subscriptions = List.empty,
    snippets = List.empty,
    collection = Collection(7, "a collection", "", None),
    children = List.empty,
    permissionsPerGroup = Map.empty
  )

  private val NoItems = """{"data": [], "total": 0, "limit": 9999}"""

  // A stub of metabase that answers by method and path, counts how many requests it is serving at
  // the same time, and takes its time over each one so that requests that really overlap show up as
  // overlapping. 'com.sun.net.httpserver' comes with the JDK, so this needs no new dependency
  private def withMetabase(questions: Int, parallelWrites: Int, delayMillis: Long)
                          (check: Seen => Unit): Unit = {
    val responses = Map(
      "GET database" -> """{"data": []}""",
      "GET collection/root/items" -> NoItems,
      "GET collection/70/items" -> NoItems,
      "POST collection" -> """{"id": 70, "name": "a collection", "description": "", "parent_id": null}""",
      "GET permissions/group" -> """[{"id": "1", "name": "All Users"}]""",
      "GET collection/graph" -> """{"revision": 3, "groups": {"1": {"70": "none"}}}""",
      "PUT collection/graph" -> """{"revision": 4, "groups": {"1": {"70": "none"}}}"""
    )

    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    // the stub has to be able to serve them at the same time, or nothing would ever overlap and the
    // test would pass for the wrong reason
    server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8))

    val requests = ListBuffer.empty[String]
    val inFlight = new AtomicInteger(0)
    val mostAtOnce = new AtomicInteger(0)
    val nextId = new AtomicInteger(1000)

    server.createContext("/api", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        val path = exchange.getRequestURI.getPath.stripPrefix("/api/")
        val key = s"${exchange.getRequestMethod} $path"
        requests.synchronized { requests += key }

        val now = inFlight.incrementAndGet()
        mostAtOnce.synchronized { if (now > mostAtOnce.get) mostAtOnce.set(now) }
        if (delayMillis > 0) Thread.sleep(delayMillis)
        inFlight.decrementAndGet()

        val (status, body) = responses.get(key) match {
          case Some(response) => (200, response)
          // every write of a question answers with the id the target metabase gave it, which is all
          // the importer reads back (see 'ApiClient.createCard')
          case None if key == "POST card" => (200, s"""{"id": ${nextId.incrementAndGet()}}""")
          case None => (404, """{"message": "Not found."}""")
        }
        val bytes = body.getBytes(UTF_8)
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        exchange.getResponseBody.write(bytes)
        exchange.close()
      }
    })
    server.start()

    val api = new ApiClient(s"http://127.0.0.1:${server.getAddress.getPort}", "a-session", timeout = "10s")
    try {
      new Importer(api, parallelWrites = parallelWrites).importCollection(exportOf(questions), None)
      // the requests that are not questions are made one at a time, so the peak is the questions'
      check(new Seen(requests, mostAtOnce.get))
    } finally server.stop(0)
  }
}
