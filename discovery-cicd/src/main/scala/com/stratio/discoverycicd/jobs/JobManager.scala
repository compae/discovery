/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.jobs

import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

import com.stratio.discoverycicd.exceptions.CustomException.causeOf
import com.stratio.discoverycicd.exceptions.{JobCancelledException, JobNotFoundException, TooManyJobsException}
import com.stratio.discoverycicd.utils.Logging

/**
 * Runs the exports and the imports that were asked for asynchronously, a few at a time.
 *
 * By default only one runs at a time. The pod is given 2 cores and an export of a big collection
 * already uses 1.8 of them, with tens of thousands of throttling events: a second one at the same
 * time makes both slower and neither of them finishes any sooner. Whoever has the room for more can
 * raise it, see `discovery-cicd-jobs.max-concurrent`.
 */
class JobManager(val registry: JobRegistry, maxConcurrent: Int, maxQueued: Int) extends Logging {

  private val threads = new AtomicInteger(0)

  private val executor =
    new ThreadPoolExecutor(
      maxConcurrent, maxConcurrent, 0L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue[Runnable](maxQueued),
      new ThreadFactory {
        def newThread(r: Runnable): Thread =
          new Thread(r, s"discovery-cicd-job-${threads.incrementAndGet()}")
      }
    )

  def workDir: Path = registry.workDir

  def get(id: String): Option[Job] = registry.get(id)

  def all: List[Job] = registry.all

  def newId(): String = UUID.randomUUID().toString

  /**
   * Takes the job, answers straight away, and runs `work` on one of the job threads.
   *
   * `work` gets the job so that it can hand `job.reporter` to the exporter or to the importer, which
   * is what makes the progress of a run show up in the status endpoint.
   */
  def submit(job: Job)(work: Job => Option[Long]): Job = {
    registry.register(job)
    try {
      executor.execute(new Runnable { def run(): Unit = runNow(job, work) })
      job
    } catch {
      case _: RejectedExecutionException =>
        // it never reached the pool, so it will never finish on its own and 'remove' would not
        // touch it: it has to be forgotten outright, or it would sit in the list for ever
        registry.forget(job.id)
        throw TooManyJobsException(
          s"There are already $maxConcurrent job(s) running and $maxQueued waiting, so this one " +
            "cannot be taken. Wait for one of them to finish, or cancel one you no longer need"
        )
    }
  }

  private def runNow(job: Job, work: Job => Option[Long]): Unit = {
    // it may have been cancelled while it was waiting its turn
    if (job.isCancelled) {
      log.info(s"The job with id:[${job.id}] was cancelled before it started")
      job.cancelledNow()
    } else {
      job.started()
      log.info(s"Starting the ${job.jobType.entryName.toLowerCase} job with id:[${job.id}]")
      try {
        val resultBytes = work(job)
        job.succeeded(resultBytes)
        log.info(s"The job with id:[${job.id}] finished in ${job.elapsedSeconds}s")
      } catch {
        case _: JobCancelledException =>
          job.cancelledNow()
          log.info(s"The job with id:[${job.id}] was cancelled after ${job.elapsedSeconds}s")
        case ex: Throwable =>
          job.failed(causeOf(ex))
          log.error(s"The job with id:[${job.id}] failed after ${job.elapsedSeconds}s", ex)
      }
    }
  }

  /**
   * Asks a job to stop, or forgets one that had already finished and deletes its result.
   *
   * The requests already in flight are left to finish; what stops is asking metabase for anything
   * else. Cancelling an import leaves the target collection half written, because an import is not
   * transactional and deletes from the target whatever the export does not carry.
   */
  def cancel(id: String): Job =
    registry.get(id) match {
      case None => throw JobNotFoundException(unknown(id))
      case Some(job) if job.status.isTerminal =>
        registry.remove(id)
        job
      case Some(job) =>
        job.cancel()
        log.info(s"The job with id:[$id] was asked to stop")
        job
    }

  def unknown(id: String): String =
    s"There is no job with id:[$id]. Either it never existed, or it was dropped after finishing, or " +
      "this process was restarted: jobs and their results are kept in this process only and do not " +
      "survive a restart"

  def close(): Unit = {
    executor.shutdownNow()
    registry.close()
  }
}

object JobManager {

  def apply(registry: JobRegistry, maxConcurrent: Int, maxQueued: Int): JobManager =
    new JobManager(registry, maxConcurrent, maxQueued)

  /** A job whose only trace is the one the caller was given: the id, and where to ask about it. */
  def newJob(
    id: String,
    jobType: JobType,
    owner: String,
    collectionId: Option[BigInt] = None,
    parentId: Option[BigInt] = None,
    resultFile: Option[Path] = None,
    inputFile: Option[Path] = None
  ): Job =
    new Job(id, jobType, collectionId, parentId, owner, Instant.now, resultFile, inputFile)
}
