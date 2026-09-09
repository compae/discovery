/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.routes

import java.nio.file.{Files, Path}

import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.{Compression, FileIO}
import com.stratio.discoverycicd.config.ConfigFacade.apiClientConfig
import com.stratio.discoverycicd.model.{ExportSource, FileExportSource}
import com.stratio.discoverycicd.progress.{Phase, ProgressReporter}
import com.stratio.discoverycicd.utils.Logging
import com.typesafe.config.Config
import org.json4s.{DefaultFormats, Formats}

import scala.concurrent.Future
import scala.util.Try

/**
 * How the body of an import gets in, for both halves of the api.
 *
 * The two routes take the very same body and do the very same thing with it, and the difference
 * between them is only who waits for the import: the asynchronous one answers with an id, the
 * synchronous one holds the connection. So the upload lives here, in one place, and neither route
 * can drift from the other on the two things that were wrong about it for as long as the
 * synchronous endpoint has existed: the timeout and the heap.
 */
object ImportUploads extends Logging {

  val UploadSuffix = ".import.json.gz"

  /**
   * The formats the exported document is written and read with.
   *
   * Deliberately not the `preservingEmptyValues` the asynchronous route uses for its answers about
   * jobs: that one leaves a `None` in as a null, and the export has to come out byte for byte the
   * same document the synchronous endpoint has always produced. In one place so that a route cannot
   * pick the wrong one by having it in scope.
   */
  val PayloadFormats: Formats = DefaultFormats

  /**
   * What it takes for the body of a real import to get in at all.
   *
   * The size limit has to be set again **after** `decodeRequest`, and that is not a slip: akka
   * applies its own `routing.decode-max-size`, 8MiB by default, to whatever a gzipped body unpacks
   * to, so an export of a couple hundred MB uploaded packed -- which is how a big one is meant to
   * travel -- was answered with a 500 no matter how high the limit on the way in was. Both sides
   * are held to the configured `max-content-length` instead of being lifted altogether, so that the
   * number in the descriptor is the number that applies and the three layers of a promotion really
   * do agree: past it the answer is a 413 here, the same as from the nginx in front.
   * `decodeRequest` is what lets the gzipped result of an export be uploaded back exactly as it was
   * downloaded.
   *
   * Beware that `extractParserSettings` is **not** where that number is: it reads the common
   * `akka.http.parsing`, whose `max-content-length` is akka's own 8MiB, and not the
   * `akka.http.server.parsing` the server itself is given and `DISCOVERY_CICD_API_MAX_BODY_SIZE`
   * overrides. Taking it from there capped every import at 8MiB.
   *
   * And the timeout, which is the whole reason this is a directive and not three lines inside each
   * route. `withRequestTimeout` only touches the timeout of the request when the directive is
   * evaluated, so wrapping it around the route *inside* `entity(as[ExportedCollection])` -- which is
   * what the synchronous route used to do -- leaves the reading of the body governed by the 20s
   * default of the server, and the reading of a couple hundred megabytes is not something that
   * happens in twenty seconds: measured, a 126MB import was answered with a 503 after 21s while the
   * import itself went on for another 34 minutes. It has to be applied before anything reads the
   * body.
   *
   * Note that the timeout of a request does not start running when the request arrives but once its
   * body has been received, so what ran out of it was never the transfer: it was turning those bytes
   * into a document.
   */
  def bigUploads(maxBodySize: Long): Directive0 =
    decodeRequest &
      withSizeLimit(maxBodySize) &
      withRequestTimeout(
        apiClientConfig.getTimeoutImport,
        ServerRoutes.configurableTimeoutHandler(ServerRoutes.ImportSegment)
      )

  /** Where the maximum size of a body is configured. `DISCOVERY_CICD_API_MAX_BODY_SIZE` overrides
    * it, and `NGINX_CLIENT_MAX_BODY_SIZE` caps the same body one layer earlier */
  val MaxBodySizePath = "akka.http.server.parsing.max-content-length"

  /**
   * The configured maximum size of a body, in bytes.
   *
   * Taken from the configuration of the actor system the routes were built with, which is the very
   * one the server reads its own settings from, so that the limit the routes apply and the one the
   * parser applies cannot say different things. `infinite`, which akka takes as no limit at all,
   * does not read as a number of bytes: it becomes the largest one, which amounts to the same.
   */
  def maxBodySizeOf(config: Config): Long =
    Try(config.getBytes(MaxBodySizePath).toLong) getOrElse Long.MaxValue

  /**
   * The body, straight to disk, without ever holding it in memory.
   *
   * It is packed on the way in for the same reason the result of an export is: the measured export
   * is 203MB and 5.3MB gzipped, and the pod keeps up to ten of them on a filesystem that is
   * ephemeral and shared with everything else.
   *
   * @return how the write went, once the whole body is on disk
   */
  def receive(entity: RequestEntity, file: Path)(implicit mat: Materializer): Future[IOResult] =
    entity.dataBytes.via(Compression.gzip).runWith(FileIO.toPath(file))

  /**
   * The uploaded export, read one question at a time (see [[FileExportSource]]).
   *
   * This is what keeps an import of a big collection inside the heap: parsed whole, the measured
   * 126MB export leaves this process at 2480MiB of rss against a heap of about 2048MiB, in
   * continuous gc; read like this, at 1504MiB.
   */
  def read(file: Path, progress: ProgressReporter): ExportSource =
    FileExportSource.from(file, progress.tracked(Phase.Reading, FileExportSource.sizeInMiB(file)))(PayloadFormats)

  /** Deletes what an import left behind, saying so if it cannot: there is nothing to be done about
    * it at that point, and it is not a reason to fail an import that went well */
  def deleteQuietly(file: Path): Unit =
    Try(Files.deleteIfExists(file)).failed foreach { ex =>
      log.warn(s"The uploaded export in '$file' could not be deleted: ${ex.getMessage}")
    }
}
