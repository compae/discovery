/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.progress

/**
 * How far one phase has got, as the status endpoint reports it.
 *
 * `total` is `None` while it is not known yet, and it is reported as such instead of being guessed:
 * an export only learns how many tables and fields it needs after it has read every question, so
 * saying '1234 of ?' is the truth and saying '1234 of 1234' is not.
 */
case class PhaseProgress(phase: String, status: String, done: Long, total: Option[Long])

object PhaseProgress {
  val Pending = "PENDING"
  val Running = "RUNNING"
  val Done    = "DONE"
}

/**
 * Everything an export or an import has told about itself so far, as one immutable value.
 *
 * It is immutable on purpose: the job keeps it in an `AtomicReference` and updates it thousands of
 * times from the threads that are making the requests, while the status endpoint reads it. Whoever
 * reads never sees a half-written state and never makes the work wait.
 */
case class ProgressState(
  expected: List[Phase],
  started: Vector[Phase],
  totals: Map[Phase, Long],
  done: Map[Phase, Long],
  percentage: Int,
  weighedTotal: Long,
  weighedDone: Long,
  warnings: Vector[String],
  warningsOmitted: Int,
  finished: Boolean
) {

  import ProgressState._

  def current: Option[Phase] = if (finished) None else started.lastOption

  /** The work is over and it went well. This is the only place that says 100: while it runs the
    * percentage is capped below it, because a phase that has not measured itself yet can always add
    * work that nothing has counted. A phase that never ran is done too: it had nothing to do */
  def completed: ProgressState = copy(percentage = 100, finished = true)

  def doneOf(p: Phase): Long = done.getOrElse(p, 0L)

  def start(p: Phase): ProgressState =
    if (started contains p) this else recomputed(copy(started = started :+ p))

  /** Only the size, not that the phase has begun: totals are often known well in advance */
  def withTotal(p: Phase, n: Long): ProgressState =
    recomputed(copy(totals = totals + (p -> n)))

  /** For a phase that is fed from more than one place and only knows its size a piece at a time,
    * such as the one that deletes from the target whatever the export does not carry */
  def addTotal(p: Phase, n: Long): ProgressState =
    withTotal(p, totals.getOrElse(p, 0L) + n)

  /**
   * The one method of this class that is called per item, so the only one that may not walk over
   * every phase.
   *
   * It used to go through [[recomputed]] like the rest, which sums over the twelve phases twice.
   * That is sixty thousand times twenty-four map lookups for a big import, all of it inside the
   * `AtomicReference.updateAndGet` of the job and now with several threads contending for it. What
   * an item adds to the weighed work done is known without looking at any other phase: its own
   * weight, unless this phase has already reported more items than it said it had, and then
   * nothing, because [[recomputed]] never counted those either.
   */
  def advance(p: Phase, by: Long): ProgressState = {
    val running = start(p)
    // a phase nobody has measured yet is assumed to hold a single item, the same as in 'recomputed'
    val assumed = running.totals.getOrElse(p, 1L)
    val doneSoFar = running.doneOf(p)
    val counted = math.min(doneSoFar + by, assumed) - math.min(doneSoFar, assumed)
    val advanced = running.weighedDone + counted * p.millisPerItem

    running.copy(
      done = running.done + (p -> (doneSoFar + by)),
      weighedDone = advanced,
      percentage = math.max(running.percentage, percentageOf(advanced, running.weighedTotal))
    )
  }

  /** Only the first [[MaxWarnings]] are kept: the rest are counted. A collection can reference
    * thousands of tables that are all inactive, and one warning for each of them would make the
    * status response bigger than the export (see 'Exporter.warnAboutInactive') */
  def warn(msg: String): ProgressState =
    if (warnings.size < MaxWarnings) copy(warnings = warnings :+ msg)
    else copy(warningsOmitted = warningsOmitted + 1)

  /** A phase is done once a later one has started: they run strictly one after another. */
  def phaseProgress: List[PhaseProgress] = {
    val currentPhase = current
    expected map { p =>
      val status =
        if (finished) PhaseProgress.Done
        else if (currentPhase contains p) PhaseProgress.Running
        else if (started contains p) PhaseProgress.Done
        else PhaseProgress.Pending
      PhaseProgress(p.id, status, doneOf(p), totals.get(p))
    }
  }

  def message: String = if (finished) "Done" else current.fold("Starting") { p =>
    totals.get(p) match {
      case Some(total) => s"${p.id}: ${doneOf(p)} of $total"
      case None        => s"${p.id}: ${doneOf(p)}"
    }
  }

  /**
   * Weighed by what an item of each phase costs, over **every** phase of the operation and not only
   * the ones that have already been measured: a phase whose size is not known yet counts as one
   * item, which is enough to keep the first single-request phase of an export from being the whole
   * of a denominator and reporting 99% before any real work has been done.
   *
   * It does not go down while the same work is in sight, but it is **not** monotonic overall: when a
   * phase turns out to hold sixty thousand items instead of the one that was assumed, the answer
   * that came before it was based on work nobody knew about, so the floor is dropped and the number
   * is recomputed. That is a step backwards on the bar, and it is the truthful one; a bar frozen at
   * 99% for the forty minutes an export of a big collection takes is worse. The exact figures are
   * always the `done` and `total` of each phase, which are never guessed.
   *
   * It is capped below 100 while there is anything left to do: 100 means finished, and the job says
   * that itself when it ends.
   */
  private def recomputed(state: ProgressState): ProgressState = {
    // a phase nobody has measured yet is assumed to hold a single item
    val assumed = (p: Phase) => state.totals.getOrElse(p, 1L)
    val weight = (p: Phase) => p.millisPerItem.toLong

    val pending = state.expected.map(p => assumed(p) * weight(p)).sum
    val advanced = state.expected.map(p => math.min(state.doneOf(p), assumed(p)) * weight(p)).sum

    // work that has just come to light makes whatever was reported before it meaningless
    val floor = if (pending == state.weighedTotal) state.percentage else 0

    state.copy(
      percentage = math.max(floor, percentageOf(advanced, pending)),
      weighedTotal = pending,
      weighedDone = advanced
    )
  }

  private def percentageOf(advanced: Long, pending: Long): Int =
    if (pending <= 0) 0 else math.min(((advanced * 100) / pending).toInt, MaxWhileRunning)
}

object ProgressState {

  /** See [[ProgressState.warn]] */
  val MaxWarnings = 100

  /** See [[ProgressState.recomputed]] */
  val MaxWhileRunning = 99

  def empty(expected: List[Phase]): ProgressState =
    ProgressState(expected, Vector.empty, Map.empty, Map.empty, 0, 0, 0, Vector.empty, 0, finished = false)
}
