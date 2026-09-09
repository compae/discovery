/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.routes

import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.GZIPOutputStream

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.stratio.discoverycicd.config.ConfigFacade.JobsConfig
import com.stratio.discoverycicd.jobs.{JobManager, JobRegistry}
import com.stratio.discoverycicd.utils.ApiClient
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.typesafe.config.ConfigFactory
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse
import org.scalatest.Matchers
import scalaj.http.Http.{apply => request}
import scalaj.http.HttpResponse

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * The world around the routes: a stub of metabase that answers by path, the real routes in front of
 * it, and a client. Shared by the tests of both halves of the api, which need exactly the same
 * setting and have to be driven the same way to be comparable.
 *
 * A real server and not `akka-http-testkit`, and not only because the testkit is not a dependency:
 * `withRequestTimeout` does nothing at all without a `TimeoutAccess`, which only a bound server
 * gives, so the whole matter of which timeout governs the reading of a body cannot be tested any
 * other way.
 */
trait RoutesHarness extends Matchers {

  protected implicit val formats: DefaultFormats.type = DefaultFormats

  protected val Session = "a-session-of-an-admin"

  protected val metabase: Map[String, String] = Map(
    "user/current" -> """{"id": 1, "is_superuser": true}""",

    "collection/4" -> """{"id": 4, "name": "Sales", "description": "", "parent_id": null}""",

    "collection/4/items" ->
      """{"data": [{"id": 1, "name": "Q1", "model": "card"}, {"id": 2, "name": "Q2", "model": "card"}],
        | "total": 2, "limit": 9999}""".stripMargin,

    "card/1" -> card(1, "Q1"),
    "card/2" -> card(2, "Q2"),

    "table/10/query_metadata" ->
      """{"id": 10, "name": "ACCOUNTS", "schema": "PUBLIC", "db_id": 1, "active": true,
        | "fields": [{"id": 100, "name": "ID"}, {"id": 101, "name": "EMAIL"}]}""".stripMargin,

    "database/1" -> """{"id": 1, "name": "sales-db"}""",
    "permissions/group" -> """[{"id": "1", "name": "All Users"}]""",
    "collection/graph" -> """{"revision": 1, "groups": {"1": {"4": "write"}}}""",

    // what a minimal import needs on top of the above
    "database" -> """{"data": []}""",
    "collection/root/items" -> """{"data": [], "total": 0, "limit": 9999}""",
    "collection" -> """{"id": 9, "name": "Empty", "description": "", "parent_id": null}""",
    "native-query-snippet" -> "[]",
    // the stub answers by path and not by method, so this is what 'POST card' gets back
    "card" -> card(99, "created")
  )

  protected val emptyExport: String =
    """{"cards": [], "dashboards": [], "fields": [], "tables": [], "databases": [],
      | "subscriptions": [], "snippets": [],
      | "collection": {"id": 7, "name": "Empty", "description": "", "parent_id": null},
      | "children": [], "permissionsPerGroup": {}}""".stripMargin

  protected def card(id: Int, name: String): String =
    s"""{"id": $id, "name": "$name", "parameters": [], "parameter_mappings": [],
       | "dataset_query": {"type": "query", "database": 1,
       |                   "query": {"source-table": 10, "fields": [["field", 100, null]]}},
       | "type": "question", "display": "table", "description": null,
       | "visualization_settings": {}, "collection_id": 4, "result_metadata": []}""".stripMargin

  /** An export of `cards` questions, each one padded so that the whole thing reaches a given size. */
  protected def exportOf(cards: Int, padding: Int = 0): String = {
    val filler = "x" * padding
    val questions = (1 to cards).map { id =>
      s"""{"id": $id, "name": "Q$id", "parameters": [], "parameter_mappings": [],
         | "dataset_query": {"type": "native", "native": {"query": "select 1"}},
         | "type": "question", "display": "table", "description": "$filler",
         | "visualization_settings": {}, "collection_id": 7, "result_metadata": []}""".stripMargin
    }.mkString(",")

    s"""{"cards": [$questions], "dashboards": [], "fields": [], "tables": [], "databases": [],
       | "subscriptions": [], "snippets": [],
       | "collection": {"id": 7, "name": "Empty", "description": "", "parent_id": null},
       | "children": [], "permissionsPerGroup": {}}""".stripMargin
  }

  // --- the client -----------------------------------------------------------------------------

  protected def get(url: String): HttpResponse[String] =
    request(url).header("x-metabase-session", Session).asString

  protected def post(url: String, body: String = ""): HttpResponse[String] =
    request(url).header("x-metabase-session", Session)
      .header("content-type", "application/json").postData(body).asString

  /** The same, with the body gzipped and said to be, which is how an export travels back as it is. */
  protected def postGzipped(url: String, body: String): HttpResponse[String] = {
    val packed = new ByteArrayOutputStream()
    val gzip = new GZIPOutputStream(packed)
    try gzip.write(body.getBytes(UTF_8)) finally gzip.close()

    request(url).header("x-metabase-session", Session)
      .header("content-type", "application/json").header("content-encoding", "gzip")
      .postData(packed.toByteArray).asString
  }

  // --- what the work directory holds ----------------------------------------------------------

  /**
   * What is left in the directory the uploads and the results are written to.
   *
   * Both halves of the api write the body of an import to a file there, and neither may leave a
   * piece of one behind whichever way the request ends: the filesystem of the pod is ephemeral and
   * shared with metabase, and nothing cleans up a file that belongs to no job.
   */
  protected def leftBehindIn(workDir: Path): List[String] = {
    val listed = Files.list(workDir)
    try listed.iterator().asScala.map(_.getFileName.toString).toList
    finally listed.close()
  }

  /** Retries an assertion for a couple of seconds, for what happens just after a response. */
  protected def eventually(assertion: => Unit): Unit = {
    val deadline = System.currentTimeMillis() + 5000
    var last: Option[Throwable] = None
    var passed = false
    while (!passed && System.currentTimeMillis() < deadline) {
      try { assertion; passed = true }
      catch { case error: Throwable => last = Some(error); Thread.sleep(50) }
    }
    if (!passed) last foreach (error => throw error)
  }

  // --- jobs -----------------------------------------------------------------------------------

  protected def jobIdOf(response: HttpResponse[String]): String =
    withClue(s"starting the import answered ${response.code}: ${response.body.take(300)}\n") {
      response.code shouldBe 202
      (parse(response.body) \ "jobId").extract[String]
    }

  protected def waitUntilFinished(base: String, jobId: String) = {
    val deadline = System.currentTimeMillis() + 20000
    def poll() = {
      val response = get(s"$base/discovery-cicd/async/jobs/$jobId")
      withClue(s"asking about the job answered ${response.code}: ${response.body}\n") {
        val body = parse(response.body)
        (body \ "status").extractOpt[String] shouldBe defined
        body
      }
    }
    var status = poll()
    while (!Set("SUCCEEDED", "FAILED", "CANCELLED").contains((status \ "status").extract[String]) &&
           System.currentTimeMillis() < deadline) {
      Thread.sleep(20)
      status = poll()
    }
    withClue(s"the job did not finish: $status") {
      (status \ "status").extract[String] should not be "RUNNING"
    }
    status
  }

  // --- the server -----------------------------------------------------------------------------

  /** A stub of metabase that answers by path, the real routes in front of it, and a client. */
  protected def withServer(check: String => Unit): Unit = withRoutes("")((base, _) => check(base))

  /**
   * The same, saying what to override in the configuration of the server and handing over the
   * directory the jobs write to, so that a test can check that nothing was left behind in it.
   */
  protected def withRoutes(akkaOverrides: String)(check: (String, Path) => Unit): Unit = {
    val stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    stub.createContext("/api", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        val path = exchange.getRequestURI.getPath.stripPrefix("/api/")
        val (status, body) = metabase.get(path) match {
          case Some(response) => (200, response)
          case None => (404, """{"message": "Not found."}""")
        }
        val bytes = body.getBytes(UTF_8)
        exchange.sendResponseHeaders(status, bytes.length.toLong)
        exchange.getResponseBody.write(bytes)
        exchange.close()
      }
    })
    stub.start()
    val metabaseUrl = s"http://127.0.0.1:${stub.getAddress.getPort}"

    val dir = Files.createTempDirectory("discovery-cicd-routes-test")
    val jobs = JobManager(new JobRegistry(JobsConfig(1, 4, dir.toString, "24h", 10)), 1, 4)

    val config = ConfigFactory.parseString(akkaOverrides).withFallback(ConfigFactory.load())
    // the typed system is passed explicitly and not left implicit: with both it and the classic one
    // in scope, the conversion from a Route to a Flow cannot pick a materializer
    val system: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "RoutesHarness", config)
    val routes = new ServerRoutes(jobs, sessionId => new ApiClient(metabaseUrl, sessionId, false, "20s"))(system)
    implicit val classicSystem: akka.actor.ActorSystem = system.classicSystem
    import classicSystem.dispatcher
    val binding = Await.result(Http().bindAndHandle(routes.apiRoutes, "127.0.0.1", 0), 10.seconds)

    try check(s"http://127.0.0.1:${binding.localAddress.getPort}", jobs.workDir)
    finally {
      Await.ready(binding.unbind(), 10.seconds)
      system.terminate()
      jobs.close()
      stub.stop(0)
    }
  }
}
