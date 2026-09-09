/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.routes

import java.io.{BufferedOutputStream, BufferedWriter, OutputStreamWriter}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.GZIPOutputStream

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ContentDispositionTypes, HttpEncodings, Location,
  `Accept-Encoding`, `Content-Disposition`, `Content-Encoding`}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.{Compression, FileIO}
import com.stratio.discoverycicd.exceptions._
import com.stratio.discoverycicd.jobs._
import com.stratio.discoverycicd.model.ExportedCollection
import com.stratio.discoverycicd.progress.Phase
import com.stratio.discoverycicd.config.ConfigFacade.apiClientConfig
import com.stratio.discoverycicd.utils.{ApiClient, Logging}
import com.stratio.discoverycicd.{Exporter, Importer}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{DefaultFormats, Formats}
import org.json4s.native.Serialization

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * The asynchronous half of the api.
 *
 * An export of a collection with tens of thousands of questions takes between twenty minutes and an
 * hour, and the synchronous endpoint sends nothing at all until it is over, so no connection with
 * the ingress and the oauth proxy in front of it survives: it dies after ten minutes with nothing
 * transferred. These endpoints answer with an id in milliseconds and do the work on a thread of
 * their own, and whoever asked polls the status endpoint for as long as it takes.
 *
 * Neither the jobs nor their results survive a restart of the pod or of this process. That is
 * deliberate: there is no volume in the deployment and nowhere to keep them.
 */
class AsyncRoutes(
  jobs: JobManager,
  apiClientFor: String => ApiClient,
  maxBodySize: Long
)(implicit val system: ActorSystem[_]) extends Json4sSupport with Logging {

  /**
   * The answers about a job say `"currentPhase": null` and `"total": null` instead of leaving the
   * field out, which is what json4s does with a `None` by default: a client polling the status would
   * otherwise have to know that a field disappears once the job finishes, or once a phase turns out
   * to have a size after all
   */
  implicit val formats: Formats = DefaultFormats.preservingEmptyValues

  implicit val serialization = Serialization
  implicit val materializer: Materializer = Materializer(system)

  import AsyncRoutes._
  import ServerRoutes.{ExportSegment, ImportSegment, ParentId, authenticatedSuperuser}

  val routes: Route =
    pathPrefix(Segment / AsyncSegment) { base =>
      startExport(base) ~ startImport(base) ~ listJobs(base) ~ jobResult ~ jobStatus(base) ~ cancelJob
    }

  // --- starting the work ----------------------------------------------------------------------

  private def startExport(base: String): Route =
    (path(ExportSegment / Segment) & post) { collectionIdSegment =>
      authenticatedSuperuser(apiClientFor) { api =>
        val collectionId = collectionIdOf(collectionIdSegment)
        val id = jobs.newId()
        val resultFile = jobs.workDir.resolve(s"$id$ResultSuffix")
        val job = JobManager.newJob(
          id, JobType.Export, ownerOf(api), collectionId = Some(collectionId), resultFile = Some(resultFile)
        )

        jobs.submit(job) { running =>
          val exported = new Exporter(api, running.reporter).exportCollection(collectionId)
          Some(writeResult(running, exported, resultFile))
        }

        accepted(base, job)
      }
    }

  private def startImport(base: String): Route =
    (path(ImportSegment) & post) {
      // It seems that unmarshalling directly to BigInt does not work
      parameter(ParentId.as[Long].?) { parentId =>
        authenticatedSuperuser(apiClientFor) { api =>
          ImportUploads.bigUploads(maxBodySize) {
            extractRequestEntity { entity =>
              val id = jobs.newId()
              val inputFile = jobs.workDir.resolve(s"$id${ImportUploads.UploadSuffix}")

              // The body goes straight to disk and the answer comes back as soon as it is there.
              // Parsing it is the job's first phase and not part of this request: an export of a
              // big collection does not fit in the heap this process is given twice over, and the
              // upload is the only part of an import that has to happen while the caller waits
              val uploaded: Future[IOResult] = ImportUploads.receive(entity, inputFile)
              onComplete(uploaded) {
                case Success(_) =>
                  val job = JobManager.newJob(
                    id, JobType.Import, ownerOf(api),
                    parentId = parentId.map(BigInt.apply), inputFile = Some(inputFile)
                  )

                  jobs.submit(job) { running =>
                    val source = ImportUploads.read(inputFile, running.reporter)
                    new Importer(api, running.reporter, apiClientConfig.getParallelWrites)
                      .importCollection(source, parentId.map(BigInt.apply))
                    None
                  }

                  accepted(base, job)

                // The upload itself failed: the caller hung up halfway through it, or the body was
                // over the maximum. No job was created, so neither the registry nor the reaper
                // knows about what was written, and without this every refused upload would leave
                // a piece of a file behind until the process is restarted. The synchronous route
                // has always done this; see 'ServerRoutes'
                case Failure(error) =>
                  ImportUploads.deleteQuietly(inputFile)
                  failWith(error)
              }
            }
          }
        }
      }
    }

  // --- asking about it ------------------------------------------------------------------------

  private def listJobs(base: String): Route =
    (path(JobsSegment) & get) {
      authenticatedSuperuser(apiClientFor) { _ =>
        parameters(StatusParam.?, TypeParam.?) { (status, jobType) =>
          val listed = jobs.all
            .filter(job => status.forall(_ equalsIgnoreCase job.status.entryName))
            .filter(job => jobType.forall(_ equalsIgnoreCase job.jobType.entryName))
          complete(listed map (job => progressOf(base, job)))
        }
      }
    }

  private def jobStatus(base: String): Route =
    (path(JobsSegment / Segment) & get) { jobId =>
      authenticatedSuperuser(apiClientFor) { _ =>
        complete(progressOf(base, jobOrFail(jobId)))
      }
    }

  private def cancelJob: Route =
    (path(JobsSegment / Segment) & delete) { jobId =>
      authenticatedSuperuser(apiClientFor) { _ =>
        val job = jobs.cancel(jobId)
        complete(Cancellation(job.id, job.status.entryName, cancellationNote(job)))
      }
    }

  private def jobResult: Route =
    (path(JobsSegment / Segment / ResultSegment) & get) { jobId =>
      authenticatedSuperuser(apiClientFor) { _ =>
        val job = jobOrFail(jobId)
        val file = resultFileOf(job)
        optionalHeaderValueByType[`Accept-Encoding`](()) { acceptEncoding =>
          complete(resultResponse(job, file, acceptsGzip(acceptEncoding)))
        }
      }
    }

  // --- the work itself ------------------------------------------------------------------------

  /**
   * Writes the export straight into a gzipped file, without ever building the whole json in memory:
   * the marshaller the synchronous endpoint uses turns it into one String first, and an export of a
   * big collection is a couple hundred MB against a heap of about a gigabyte. Compressing it is not
   * an afterthought either, it is what makes it fit on the ephemeral disk of the pod: measured on a
   * real export of 203MB, gzip brings it down to 5.3MB
   *
   * @return how many bytes the file holds
   */
  private def writeResult(job: Job, exported: ExportedCollection, file: Path): Long = {
    val phase = job.reporter.tracked(Phase.WritingResult, 1)
    phase.checkCancelled()
    val writer =
      new BufferedWriter(new OutputStreamWriter(
        new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file))), UTF_8))
    try Serialization.write(exported, writer)(ImportUploads.PayloadFormats)
    finally writer.close()
    phase.advance()
    Files.size(file)
  }

  // --- responses ------------------------------------------------------------------------------

  private def accepted(base: String, job: Job): Route = {
    val status = statusUrl(base, job.id)
    val result = if (job.jobType == JobType.Export) Some(s"$status/$ResultSegment") else None
    respondWithHeader(Location(Uri(status))) {
      complete(StatusCodes.Accepted, JobAccepted.of(job, status, result))
    }
  }

  private def resultResponse(job: Job, file: Path, gzip: Boolean): HttpResponse = {
    val name = s"${job.collectionId.fold("collection")(id => s"collection-$id")}-${job.id}.json"
    val source = FileIO.fromPath(file)
    if (gzip)
      HttpResponse(
        entity = HttpEntity(ContentTypes.`application/json`, Files.size(file), source),
        headers = List(
          `Content-Encoding`(HttpEncodings.gzip),
          `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> s"$name.gz"))
        )
      )
    else
      HttpResponse(
        entity = HttpEntity(ContentTypes.`application/json`, source.via(Compression.gunzip())),
        headers = List(`Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> name)))
      )
  }

  private def progressOf(base: String, job: Job): JobProgress = {
    val status = statusUrl(base, job.id)
    JobProgress.of(job).copy(
      statusUrl = Some(status),
      resultUrl = if (job.jobType == JobType.Export) Some(s"$status/$ResultSegment") else None
    )
  }

  private def statusUrl(base: String, jobId: String): String = s"/$base/$AsyncSegment/$JobsSegment/$jobId"

  // --- checks ---------------------------------------------------------------------------------

  private def jobOrFail(jobId: String): Job =
    jobs.get(jobId) getOrElse (throw JobNotFoundException(jobs.unknown(jobId)))

  private def resultFileOf(job: Job): Path = {
    if (job.jobType != JobType.Export)
      throw JobNotFoundException(s"The job with id:[${job.id}] is an import, and an import has no result to download")

    job.status match {
      case JobStatus.Succeeded =>
        job.resultFile.filter(Files.exists(_)) getOrElse {
          throw JobNotFoundException(s"The result of the job with id:[${job.id}] is no longer on disk")
        }
      case JobStatus.Failed =>
        throw JobNotFinishedException(s"The job with id:[${job.id}] failed, so it produced no result: " +
          job.snapshot.error.getOrElse("no reason was recorded"))
      case JobStatus.Cancelled =>
        throw JobNotFinishedException(s"The job with id:[${job.id}] was cancelled, so it produced no result")
      case other =>
        throw JobNotFinishedException(s"The job with id:[${job.id}] is ${other.entryName.toLowerCase} " +
          s"(${job.snapshot.progress.percentage}%), so there is nothing to download yet")
    }
  }

  private def collectionIdOf(segment: String): BigInt =
    Try(BigInt(segment)).getOrElse(throw ValidationDataException(
      s"'$segment' is not a valid collection id: it has to be a number"
    ))

  /** The metabase user the session belongs to. Recorded so that whoever looks at the list knows who
    * asked for what; it does not restrict anything, because every caller is a superuser already and
    * a CI pipeline may well ask about a job with a different session than the one that started it */
  private def ownerOf(api: ApiClient): String = api.user.id.toString

  private def cancellationNote(job: Job): String = job.jobType match {
    case JobType.Import if job.status == JobStatus.Cancelled || !job.status.isTerminal =>
      "The requests already in flight are left to finish. An import is not transactional, so the " +
        "target collection is left half written and has to be imported again"
    case _ =>
      "The requests already in flight are left to finish"
  }

  private def acceptsGzip(header: Option[`Accept-Encoding`]): Boolean =
    header.exists(_.encodings.exists(_.matches(HttpEncodings.gzip)))
}

object AsyncRoutes {

  val AsyncSegment = "async"
  val JobsSegment = "jobs"
  val ResultSegment = "result"
  val StatusParam = "status"
  val TypeParam = "type"

  val ResultSuffix = ".json.gz"

  case class Cancellation(jobId: String, status: String, note: String)

}
