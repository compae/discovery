/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.jobs

import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.util.concurrent.{ConcurrentHashMap, Executors, ScheduledExecutorService, ThreadFactory, TimeUnit}

import com.stratio.discoverycicd.config.ConfigFacade.JobsConfig
import com.stratio.discoverycicd.utils.Logging

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.Try

/**
 * The jobs this process knows about, and the files they left on disk.
 *
 * Nothing here survives a restart, on purpose: there is no volume in the deployment
 * ("volumes": [] in the CCT descriptor) and no store of any kind, so the work directory is emptied
 * on start up. Whatever a previous process left there is unreachable anyway, since the ids that
 * named those files went with it, and a 200MB export nobody can ask for any more is only filling up
 * the ephemeral disk of the pod.
 *
 * Finished jobs are dropped after [[JobsConfig.getRetention]], and only the last
 * [[JobsConfig.maxStoredResults]] of them are kept, for the same reason.
 */
class JobRegistry(config: JobsConfig) extends Logging {

  import JobRegistry.ReapEvery

  val workDir: Path = Paths.get(config.workDir)

  private val jobs = new ConcurrentHashMap[String, Job]()

  private val reaper: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        val thread = new Thread(r, "discovery-cicd-job-reaper")
        thread.setDaemon(true)
        thread
      }
    })

  prepareWorkDir()
  reaper.scheduleWithFixedDelay(
    new Runnable { def run(): Unit = Try(reap()).failed.foreach(ex => log.error("The job reaper failed", ex)) },
    ReapEvery.toSeconds, ReapEvery.toSeconds, TimeUnit.SECONDS
  )

  def register(job: Job): Unit = {
    jobs.put(job.id, job)
    log.info(s"Accepted the ${job.jobType.entryName.toLowerCase} job with id:[${job.id}] " +
      s"requested by '${job.owner}'")
  }

  def get(id: String): Option[Job] = Option(jobs.get(id))

  /** Newest first: whoever lists them is nearly always after the one they just started. */
  def all: List[Job] = jobs.values.asScala.toList.sortBy(_.submittedAt)(Ordering[Instant].reverse)

  /** Forgets the job and deletes whatever it left on disk. A job that is still running is left
    * alone: dropping it would leave its files behind with nobody to clean them up */
  def remove(id: String): Option[Job] = get(id).filter(_.status.isTerminal).flatMap(job => forget(job.id))

  /** The same, for a job that was never handed to the pool and so will never reach a terminal state
    * on its own: one refused because there was no room for it */
  private[jobs] def forget(id: String): Option[Job] =
    get(id).map { job =>
      jobs.remove(id)
      deleteFilesOf(job)
      job
    }

  private[jobs] def reap(): Unit = {
    val finished = all.filter(_.status.isTerminal)
    val expiredAt = Instant.now.minusSeconds(config.getRetention.toSeconds)

    val tooOld = finished.filter(_.snapshot.finishedAt.exists(_.isBefore(expiredAt)))
    val tooMany = finished.drop(config.maxStoredResults)

    (tooOld ++ tooMany).map(_.id).distinct foreach { id =>
      log.info(s"Dropping the finished job with id:[$id] and deleting its files")
      remove(id)
    }
  }

  private def deleteFilesOf(job: Job): Unit =
    (job.resultFile.toList ++ job.inputFile.toList) foreach { file =>
      Try(Files.deleteIfExists(file)).failed.foreach { ex =>
        log.warn(s"Could not delete the file '$file' of the job with id:[${job.id}]", ex)
      }
    }

  private def prepareWorkDir(): Unit = {
    Files.createDirectories(workDir)
    val leftovers =
      Try(Files.list(workDir).iterator.asScala.toList).getOrElse(List.empty)
    if (leftovers.nonEmpty) {
      log.info(s"Deleting ${leftovers.size} file(s) left in '$workDir' by a previous run: the jobs " +
        "they belonged to are gone with it, so nobody can ask for them any more")
      leftovers.foreach(file => Try(Files.deleteIfExists(file)))
    }
  }

  def close(): Unit = {
    reaper.shutdownNow()
    all.foreach(deleteFilesOf)
    jobs.clear()
  }
}

object JobRegistry {

  /** How often the finished jobs are looked at. They are dropped by age and by how many there are,
    * so checking often costs nothing: it walks a handful of entries in a map */
  val ReapEvery: FiniteDuration = 1.minute
}
