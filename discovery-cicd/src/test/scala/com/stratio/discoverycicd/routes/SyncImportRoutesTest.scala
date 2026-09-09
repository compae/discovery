/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.routes

import java.nio.charset.StandardCharsets.UTF_8

import org.junit.runner.RunWith
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner

/**
 * The synchronous import, which used to answer a big body with a 503 and import it anyway.
 *
 * Measured against a real collection of 70.000 questions: a body of 126MB was answered with a 503
 * after 21s -- the 20s default of the server, because `withRequestTimeout` was applied inside
 * `entity(as[ExportedCollection])` and so did not govern the reading of the body -- and the import
 * went on to finish 34 minutes later, with nobody left to tell. It also held the whole document in
 * the heap, 2480MiB of rss against a heap of about 2048MiB.
 *
 * So what is pinned down here is that the configured timeout is the one that governs, that a body
 * over the default cap gets in either packed or not, and that the body goes to a file that is always
 * deleted -- which is also the proof that it is not being parsed whole into a `String`.
 */
@RunWith(classOf[JUnitRunner])
class SyncImportRoutesTest extends WordSpec with Matchers with RoutesHarness {

  /** A server that gives up on a request in a tenth of a second, as the default one does in twenty. */
  private val ImpatientServer = "akka.http.server.request-timeout = 100ms"

  /** A server that takes a body of at most 1MiB, as the shipped one takes 512MiB. */
  private val SmallBodyServer = "akka.http.server.parsing.max-content-length = 1M"

  /** ~3MB of json, well over the 1MiB above and tiny once gzipped: the padding is all one letter. */
  private def overOneMiB: String = exportOf(cards = 100, padding = 30000)

  "The synchronous import" should {

    // The test for the bug itself, and the reason the body has to be this big: the request timeout
    // of the server does not start running when the request arrives but once its body has been
    // received, so what used to run out of it was not the upload -- it was turning those bytes into
    // a document. Against the real 126MB export that took well over the 20s default; here a server
    // that allows a tenth of a second is given a body that cannot be parsed in a tenth of a second.
    //
    // The configured import timeout is the 200s of 'application.conf', so the import has all the
    // time it needs as long as the directive that sets it is applied before the body is read, which
    // is the whole change. Checked against the route as it was: answered '503 The server was not
    // able to produce a timely response'
    "let the configured import timeout govern, not the default of the server" in
      withRoutes(ImpatientServer) { (base, _) =>
        val big = exportOf(cards = 200, padding = 50000) // ~10MB of json

        val response = post(s"$base/discovery-cicd/import", big)

        withClue(s"the import was answered ${response.code}: ${response.body.take(300)}\n") {
          response.code shouldBe 200
        }
      }

    // akka http caps a body at 8MiB by default and applies the cap again to what a gzipped one
    // unpacks to, through a setting of its own ('routing.decode-max-size'), so this is what pins
    // down that the configured 512MiB is what governs on both sides and not either of those two
    "take an export bigger than the default limit of the server, packed and not" in withServer { base =>
      val big = exportOf(cards = 200, padding = 50000) // ~10MB of json, over the 8MiB default
      big.getBytes(UTF_8).length should be > (8 * 1024 * 1024)

      val plain = post(s"$base/discovery-cicd/import", big)
      withClue(s"the plain import of a big export answered ${plain.code}: ${plain.body.take(300)}\n") {
        plain.code shouldBe 200
      }

      val packed = postGzipped(s"$base/discovery-cicd/import", big)
      withClue(s"the gzipped import of a big export answered ${packed.code}: ${packed.body.take(300)}\n") {
        packed.code shouldBe 200
      }
    }

    // The other half of the same thing: the limit is raised to what is configured and not lifted
    // altogether, so 'DISCOVERY_CICD_API_MAX_BODY_SIZE' is a number that does something. Without
    // this the route answered 200 to a body of any size whatsoever, while the setting, the
    // descriptor and the documentation all promised a cap that was not there.
    //
    // Gzipped on purpose, and that is the whole point: packed it is a few KB and gets past every
    // check on the way in, so the only thing that can refuse it is the limit being applied again to
    // what it unpacks to -- which is exactly the side that used to be lifted. It also keeps the
    // test honest about what a client sees: a body that is already too big on the way in is cut
    // while it is still being written, and what curl reports of that is not a status code
    "refuse a body whose unpacked size is over the configured maximum" in
      withRoutes(SmallBodyServer) { (base, workDir) =>
        val overTheLimit = overOneMiB
        overTheLimit.getBytes(UTF_8).length should be > (1024 * 1024)

        val response = postGzipped(s"$base/discovery-cicd/import", overTheLimit)

        withClue(s"the gzipped import over the limit answered ${response.code}: ${response.body.take(300)}\n") {
          response.code shouldBe 413
        }
        response.body should include ("DISCOVERY_CICD_API_MAX_BODY_SIZE")
        // and what the refused upload had already written does not stay behind
        eventually(leftBehindIn(workDir) shouldBe empty)
      }

    // the same cap must not get in the way of a body that fits, which is what says the number being
    // applied is the configured one and not something smaller
    "still take a body under the configured maximum" in withRoutes(SmallBodyServer) { (base, _) =>
      post(s"$base/discovery-cicd/import", emptyExport).code shouldBe 200
      postGzipped(s"$base/discovery-cicd/import", emptyExport).code shouldBe 200
    }

    // a body that is not compressed is how every client has always sent one, and a gzipped one is
    // what the result of an export already is: it can now be uploaded back exactly as downloaded
    "take an export uncompressed, as it always did, and gzipped, as it could not" in withServer { base =>
      post(s"$base/discovery-cicd/import", emptyExport).code shouldBe 200
      postGzipped(s"$base/discovery-cicd/import", emptyExport).code shouldBe 200
    }

    "still need the session of a superuser" in withServer { base =>
      // no session header at all
      scalaj.http.Http(s"$base/discovery-cicd/import").postData(emptyExport).asString.code shouldBe 400
    }
  }

  // the uploaded body is a file on disk, on a filesystem that is ephemeral and shared with metabase,
  // so nothing may be left behind whichever way the import ends. It is also what says the body was
  // never a document in the heap
  "The uploaded body" should {

    "be deleted after an import that succeeded" in withRoutes("") { (base, workDir) =>
      post(s"$base/discovery-cicd/import", emptyExport).code shouldBe 200

      leftBehindIn(workDir) shouldBe empty
    }

    "be deleted after an import that was refused" in withRoutes("") { (base, workDir) =>
      // a json object that is not an export at all: it has no collection
      val response = post(s"$base/discovery-cicd/import", """{"cards": []}""")

      response.code shouldBe 400
      leftBehindIn(workDir) shouldBe empty
    }

    "be deleted when the caller hangs up in the middle of the upload" in
      withRoutes("") { (base, workDir) =>
        halfAnUpload(s"$base/discovery-cicd/import", emptyExport)

        // the upload fails on the server as soon as the socket is closed, but the response it was
        // building is on its way out at that point, so give the cleanup a moment to happen
        eventually(leftBehindIn(workDir) shouldBe empty)
      }
  }

  /** Starts an upload announcing more than it sends, and closes the socket halfway through. */
  private def halfAnUpload(url: String, body: String): Unit = {
    val uri = new java.net.URI(url)
    val bytes = body.getBytes(UTF_8)
    val socket = new java.net.Socket(uri.getHost, uri.getPort)
    try {
      val out = socket.getOutputStream
      out.write(
        (s"POST ${uri.getPath} HTTP/1.1\r\n" +
          s"Host: ${uri.getHost}:${uri.getPort}\r\n" +
          s"x-metabase-session: $Session\r\n" +
          "Content-Type: application/json\r\n" +
          s"Content-Length: ${bytes.length * 2}\r\n\r\n").getBytes(UTF_8)
      )
      out.write(bytes)
      out.flush()
    } finally socket.close()
  }

}
