/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.routes

import java.nio.charset.StandardCharsets.UTF_8

import org.json4s.JNull
import org.json4s.native.JsonMethods.parse
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}
import scalaj.http.Http.{apply => request}

/**
 * The asynchronous endpoints, end to end, on the shared harness (see [[RoutesHarness]]).
 *
 * The one assertion everything else hangs from is that an asynchronous export produces exactly the
 * same bytes as the synchronous one. Everything else here is about the part that is new: answering
 * in milliseconds instead of after an hour, and being able to say how far it has got while it runs
 * (see ROCK-15286: the synchronous export of a big collection sends nothing at all until it is over,
 * so no connection through the ingress survives it)
 */
@RunWith(classOf[JUnitRunner])
class AsyncRoutesTest extends WordSpec with Matchers with RoutesHarness {

  "An asynchronous export" should {

    "answer with an id straight away and produce the very same export as the synchronous one" in
      withServer { base =>
        val accepted = parse(post(s"$base/discovery-cicd/async/export/4").body)
        val jobId = (accepted \ "jobId").extract[String]
        // it may already have been picked up by the time the answer is built, and saying so is
        // more useful than pretending it is still waiting
        (accepted \ "status").extract[String] should (be("QUEUED") or be("RUNNING"))
        (accepted \ "statusUrl").extract[String] shouldBe s"/discovery-cicd/async/jobs/$jobId"

        val finished = waitUntilFinished(base, jobId)
        (finished \ "status").extract[String] shouldBe "SUCCEEDED"
        (finished \ "percentage").extract[Int] shouldBe 100

        val asynchronous = get(s"$base/discovery-cicd/async/jobs/$jobId/result").body
        val synchronous = get(s"$base/discovery-cicd/export/4").body

        asynchronous shouldBe synchronous
      }

    // this is what the whole thing is for: with 60.000 questions the export takes tens of minutes,
    // and until now the only thing the caller could do was hold a connection that no proxy survives
    "know how many questions there are before bringing a single one of them" in withServer { base =>
      val jobId = (parse(post(s"$base/discovery-cicd/async/export/4").body) \ "jobId").extract[String]
      val finished = waitUntilFinished(base, jobId)

      val phases = (finished \ "phases").children.map { phase =>
        (phase \ "phase").extract[String] -> (phase \ "total").extractOpt[Long]
      }.toMap

      // the two heaviest phases are measured by listing the items of the collection, which is one
      // cheap request, and not by having already done the work
      phases("questions") shouldBe Some(2L)
      phases("dashboards") shouldBe Some(0L)
      phases("tables") shouldBe Some(1L)
      (finished \ "phases").children.map(p => (p \ "status").extract[String]).distinct shouldBe List("DONE")
    }

    "be listed, and be downloadable both compressed and not" in withServer { base =>
      val jobId = (parse(post(s"$base/discovery-cicd/async/export/4").body) \ "jobId").extract[String]
      waitUntilFinished(base, jobId)

      val listed = parse(get(s"$base/discovery-cicd/async/jobs").body).children
      listed.map(job => (job \ "jobId").extract[String]) shouldBe List(jobId)
      (listed.head \ "type").extract[String] shouldBe "EXPORT"

      // the file on disk is gzipped, which is what makes an export of a couple hundred MB fit on the
      // ephemeral disk of the pod. A client that does not want it that way gets it uncompressed
      val compressed = request(s"$base/discovery-cicd/async/jobs/$jobId/result")
        .header("x-metabase-session", Session).header("Accept-Encoding", "gzip").asBytes
      // scalaj asks for gzip by default, so saying otherwise takes turning that off first
      val plain = request(s"$base/discovery-cicd/async/jobs/$jobId/result")
        .header("x-metabase-session", Session).compress(false)
        .header("Accept-Encoding", "identity").asString

      compressed.header("Content-Encoding") shouldBe Some("gzip")
      plain.header("Content-Encoding") shouldBe None
      // scalaj decompresses what it asked for, so both end up being the same document
      new String(compressed.body, UTF_8) shouldBe plain.body
    }
  }

  // json4s leaves a None out of the json by default, so a client polling the status would see
  // currentPhase disappear the moment the job finished, and would never see the "total": null that
  // says the size of a phase is not known yet
  "The answer about a job" should {

    "say null for what is not known, instead of leaving the field out" in withServer { base =>
      val jobId = (parse(post(s"$base/discovery-cicd/async/export/4").body) \ "jobId").extract[String]

      val running = parse(get(s"$base/discovery-cicd/async/jobs/$jobId").body)
      val tables = (running \ "phases").children.find(p => (p \ "phase").extract[String] == "tables").get
      (tables \ "total") shouldBe JNull

      val finished = waitUntilFinished(base, jobId)
      (finished \ "currentPhase") shouldBe JNull
      (finished \ "error") shouldBe JNull
    }
  }

  "Asking about a job" should {

    "say it does not know it, mentioning that a restart loses them" in withServer { base =>
      val response = get(s"$base/discovery-cicd/async/jobs/not-a-job")

      response.code shouldBe 404
      response.body should include("do not survive a restart")
    }

    "say that an import has no result to download" in withServer { base =>
      val jobId = (parse(post(s"$base/discovery-cicd/async/import", emptyExport).body) \ "jobId").extract[String]

      val response = get(s"$base/discovery-cicd/async/jobs/$jobId/result")

      response.code shouldBe 404
      response.body should include("an import has no result")
    }

    "say why there is nothing to download when the export failed" in withServer { base =>
      // there is no collection 5 in this metabase
      val jobId = (parse(post(s"$base/discovery-cicd/async/export/5").body) \ "jobId").extract[String]
      val finished = waitUntilFinished(base, jobId)
      (finished \ "status").extract[String] shouldBe "FAILED"

      val response = get(s"$base/discovery-cicd/async/jobs/$jobId/result")

      response.code shouldBe 409
      response.body should include("failed")
      (finished \ "error").extract[String] should include("collection with id:[5]")
    }

    "refuse an id that is not a number for the collection" in withServer { base =>
      post(s"$base/discovery-cicd/async/export/not-a-number").code shouldBe 400
    }

    "still need the session of a superuser" in withServer { base =>
      request(s"$base/discovery-cicd/async/jobs").asString.code shouldBe 400 // no session header at all
    }
  }

  "An asynchronous import" should {

    // the body of a real import is hundreds of MB, so it goes straight to disk and is parsed inside
    // the job: the upload is the only part that has to happen while the caller waits
    "take the uploaded export, answer at once, and report the reading of the file as a phase" in
      withServer { base =>
        val accepted = parse(post(s"$base/discovery-cicd/async/import", emptyExport).body)
        val jobId = (accepted \ "jobId").extract[String]
        (accepted \ "resultUrl").extractOpt[String] shouldBe None

        val finished = waitUntilFinished(base, jobId)

        (finished \ "status").extract[String] shouldBe "SUCCEEDED"
        (finished \ "type").extract[String] shouldBe "IMPORT"
        (finished \ "phases").children.map(p => (p \ "phase").extract[String]) should contain("reading")
        (finished \ "resultUrl").extractOpt[String] shouldBe None
      }

    // the result of an export is served gzipped, and this is what lets it be uploaded back exactly
    // as it was downloaded instead of being unpacked first: 5.3MB travel instead of 203MB
    "take an export that was uploaded still gzipped" in withServer { base =>
      val accepted = parse(postGzipped(s"$base/discovery-cicd/async/import", emptyExport).body)
      val jobId = (accepted \ "jobId").extract[String]

      val finished = waitUntilFinished(base, jobId)

      withClue(s"the gzipped import did not succeed: $finished\n") {
        (finished \ "status").extract[String] shouldBe "SUCCEEDED"
      }
    }

    // akka http caps a body at 8MiB by default and applies the cap again to what a gzipped one
    // unpacks to, so this is what pins down that neither of the two limits is left in place: a real
    // export is hundreds of MB and would be answered with a 413 before reaching the job
    "take an export bigger than the default limit of the server, packed and not" in withServer { base =>
      val big = exportOf(cards = 200, padding = 50000) // ~10MB of json, over the 8MiB default
      big.getBytes(UTF_8).length should be > (8 * 1024 * 1024)

      val plain = waitUntilFinished(base, jobIdOf(post(s"$base/discovery-cicd/async/import", big)))
      withClue(s"the plain import of a big export did not succeed: $plain\n") {
        (plain \ "status").extract[String] shouldBe "SUCCEEDED"
      }

      val packed = waitUntilFinished(base, jobIdOf(postGzipped(s"$base/discovery-cicd/async/import", big)))
      withClue(s"the gzipped import of a big export did not succeed: $packed\n") {
        (packed \ "status").extract[String] shouldBe "SUCCEEDED"
      }
    }

    // The other half of the same limit: it is raised to what is configured and not lifted, so a
    // body over it is refused with the very 413 the nginx in front gives, and not with the 500 the
    // catch-all of the exception handler used to make of it. Gzipped, because that is the side the
    // limit used to be lifted on: packed it is a few KB and only its unpacked size can refuse it
    "refuse a body whose unpacked size is over the configured maximum, without starting a job" in
      withRoutes("akka.http.server.parsing.max-content-length = 1M") { (base, workDir) =>
        val overTheLimit = exportOf(cards = 100, padding = 30000) // ~3MB of json, over the 1MiB above

        val response = postGzipped(s"$base/discovery-cicd/async/import", overTheLimit)

        withClue(s"the import over the limit answered ${response.code}: ${response.body.take(300)}\n") {
          response.code shouldBe 413
        }
        (parse(response.body) \ "message").extract[String] should include ("DISCOVERY_CICD_API_MAX_BODY_SIZE")
        // no job was created, so nothing would ever come back for what the refused upload wrote:
        // the route has to delete it itself
        eventually(leftBehindIn(workDir) shouldBe empty)
      }

    // a question at a time is read out of the file, and the count comes from the pass that indexes
    // it, so what the status reports has to be what the file really holds
    "report as many questions as the uploaded file holds" in withServer { base =>
      val jobId = jobIdOf(post(s"$base/discovery-cicd/async/import", exportOf(cards = 50)))

      val finished = waitUntilFinished(base, jobId)

      val phases = (finished \ "phases").children.map { phase =>
        (phase \ "phase").extract[String] -> (phase \ "total").extractOpt[Long]
      }.toMap
      phases("creating-questions") shouldBe Some(50L)
    }
  }

  "Cancelling a job" should {

    "forget one that already finished and delete what it left on disk" in withServer { base =>
      val jobId = (parse(post(s"$base/discovery-cicd/async/export/4").body) \ "jobId").extract[String]
      waitUntilFinished(base, jobId)

      val cancelled = request(s"$base/discovery-cicd/async/jobs/$jobId")
        .header("x-metabase-session", Session).method("DELETE").asString

      cancelled.code shouldBe 200
      get(s"$base/discovery-cicd/async/jobs/$jobId").code shouldBe 404
    }
  }
}
