/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.jobs

import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import com.stratio.discoverycicd.exceptions.JobCancelledException
import com.stratio.discoverycicd.progress._
import enumeratum._

sealed trait JobType extends EnumEntry with EnumEntry.Uppercase

object JobType extends Enum[JobType] {
  val values = findValues
  case object Export extends JobType
  case object Import extends JobType

  def phasesOf(jobType: JobType): List[Phase] = jobType match {
    case Export => Phase.ofExport
    case Import => Phase.ofImport
  }
}

sealed abstract class JobStatus(val isTerminal: Boolean) extends EnumEntry with EnumEntry.Uppercase

object JobStatus extends Enum[JobStatus] {
  val values = findValues
  case object Queued    extends JobStatus(false)
  case object Running   extends JobStatus(false)
  case object Succeeded extends JobStatus(true)
  case object Failed    extends JobStatus(true)
  case object Cancelled extends JobStatus(true)
}

/** Everything about a job that changes while it runs, as one value that is swapped atomically. */
private[jobs] case class JobState(
  status: JobStatus,
  progress: ProgressState,
  startedAt: Option[Instant],
  finishedAt: Option[Instant],
  error: Option[String],
  resultBytes: Option[Long]
)

/**
 * One export or one import running in the background.
 *
 * What changes while it runs lives in a single `AtomicReference`, and every update swaps a whole
 * immutable value: the threads making the requests to metabase write to it thousands of times while
 * the status endpoint reads it, and neither of them should wait for the other nor see a state that
 * is half written.
 *
 * Nothing here is persisted. A restart of the pod, or of the process alone, loses the job and its
 * result, and the status endpoint then answers that it does not know that id.
 */
class Job(
  val id: String,
  val jobType: JobType,
  val collectionId: Option[BigInt],
  val parentId: Option[BigInt],
  val owner: String,
  val submittedAt: Instant,
  val resultFile: Option[Path],
  val inputFile: Option[Path]
) {

  private val state =
    new AtomicReference(JobState(
      JobStatus.Queued, ProgressState.empty(JobType.phasesOf(jobType)), None, None, None, None
    ))

  private val cancelled = new AtomicBoolean(false)

  def snapshot: JobState = state.get

  def status: JobStatus = state.get.status

  def isCancelled: Boolean = cancelled.get

  /** Asks the job to stop. What actually stops it is the next [[PhaseReporter.checkCancelled]],
    * which every loop of the exporter and of the importer runs before each item */
  def cancel(): Unit = cancelled.set(true)

  def started(): Unit = update(_.copy(status = JobStatus.Running, startedAt = Some(Instant.now)))

  def succeeded(resultBytes: Option[Long]): Unit = update { current =>
    current.copy(
      status = JobStatus.Succeeded,
      // the only place that says 100: while it runs the percentage is capped below it, because a
      // phase that has not measured itself yet can always add work that is not counted
      progress = current.progress.completed,
      finishedAt = Some(Instant.now),
      resultBytes = resultBytes
    )
  }

  def failed(reason: String): Unit =
    update(_.copy(status = JobStatus.Failed, finishedAt = Some(Instant.now), error = Some(reason)))

  def cancelledNow(): Unit =
    update(_.copy(status = JobStatus.Cancelled, finishedAt = Some(Instant.now)))

  private[jobs] def updateProgress(f: ProgressState => ProgressState): Unit =
    update(current => current.copy(progress = f(current.progress)))

  private def update(f: JobState => JobState): Unit = {
    val _ = state.updateAndGet(new java.util.function.UnaryOperator[JobState] {
      def apply(current: JobState): JobState = f(current)
    })
  }

  /** What the exporter and the importer write their progress into. */
  val reporter: ProgressReporter = new ProgressReporter {

    def warn(msg: String): Unit = updateProgress(_.warn(msg))

    def phase(p: Phase): PhaseReporter = new PhaseReporter {
      def start(): Unit = updateProgress(_.start(p))
      def total(n: Long): Unit = updateProgress(_.withTotal(p, n))
      def addTotal(n: Long): Unit = updateProgress(_.addTotal(p, n))
      def advance(by: Long): Unit = updateProgress(_.advance(p, by))
      def checkCancelled(): Unit = if (cancelled.get) throw JobCancelledException(id)
    }
  }

  def elapsedSeconds: Long = {
    val current = state.get
    val from = current.startedAt getOrElse submittedAt
    val to = current.finishedAt getOrElse Instant.now
    ChronoUnit.SECONDS.between(from, to) max 0L
  }

  /**
   * How long is left, from how long it has taken to get where it is. It is deliberately not offered
   * at the very beginning: the first percent of a job that reads a collection before doing anything
   * says nothing about the hour that may follow it
   */
  def estimatedSecondsRemaining: Option[Long] = {
    val current = state.get
    val percentage = current.progress.percentage
    if (current.status != JobStatus.Running || percentage < Job.MinPercentageForEstimate) None
    else Some(elapsedSeconds * (100 - percentage) / percentage)
  }
}

object Job {

  /** See [[Job.estimatedSecondsRemaining]] */
  val MinPercentageForEstimate = 5
}
