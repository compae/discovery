/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.progress

/**
 * How far one phase has got. This is all [[com.stratio.discoverycicd.utils.Concurrently]] and the
 * exporter and the importer get to see, so that none of them has to know anything about jobs: the
 * synchronous endpoints and the shell pass [[PhaseReporter.NoOp]] and nothing changes for them.
 *
 * Every method can be called from several threads at once, because that is exactly what
 * `Concurrently.map` does with it.
 */
trait PhaseReporter {

  /** This phase is the one running now. Declaring its size does not start it: an export knows how
    * many questions it has to bring long before it starts bringing them */
  def start(): Unit

  /** How many items this phase has, as soon as it is known. */
  def total(n: Long): Unit

  /** How many more items this phase has, for one that is fed from more than one place. */
  def addTotal(n: Long): Unit

  /** One more item of this phase is done. */
  def advance(by: Long = 1): Unit

  /**
   * Throws [[com.stratio.discoverycicd.exceptions.JobCancelledException]] if whoever asked for this
   * work no longer wants it. Called before every item, so that cancelling really stops making
   * requests to metabase instead of only stopping to look at the answers.
   */
  def checkCancelled(): Unit

  /**
   * The same `f`, counting one item every time it is called and giving up if the job was cancelled.
   * Meant to be handed straight to a `map` or a `flatMap`, so that instrumenting a loop does not
   * mean rewriting it: `items map reporter.tracking(api.getThing)`.
   */
  def tracking[A, B](f: A => B): A => B = { a =>
    checkCancelled()
    val b = f(a)
    advance()
    b
  }

  /** One item of this phase that is not part of a loop. */
  def one[A](f: => A): A = {
    checkCancelled()
    val a = f
    advance()
    a
  }
}

object PhaseReporter {

  /** What the synchronous endpoints, the shell and the tests use: the work is not being watched. */
  object NoOp extends PhaseReporter {
    def start(): Unit = ()
    def total(n: Long): Unit = ()
    def addTotal(n: Long): Unit = ()
    def advance(by: Long): Unit = ()
    def checkCancelled(): Unit = ()
  }
}

/**
 * What an export or an import tells about itself while it runs. The synchronous paths pass
 * [[ProgressReporter.NoOp]], so `Exporter` and `Importer` are the same code whether anybody is
 * watching or not.
 */
trait ProgressReporter {

  /** The reporter of a phase, without saying that it has started. */
  def phase(p: Phase): PhaseReporter

  /**
   * Something worth telling whoever asked for this work, that did not stop it. The exporter already
   * logs these (a table it could not read, a field that is inactive); this puts them where the one
   * polling the status can see them without going through the log of the pod.
   */
  def warn(msg: String): Unit

  /**
   * How many items a phase will have, known before the phase runs. This is what lets the status
   * endpoint answer '28100 of 60000' instead of '28100 so far' from the very first poll: listing the
   * items of a collection costs one request and already says how many questions and how many
   * dashboards are coming.
   */
  def declare(p: Phase, total: Long): Unit = phase(p).total(total)

  /** Starts a phase that is fed from more than one place, adding to whatever size it already had. */
  def growing(p: Phase, extra: Long): PhaseReporter = {
    val reporter = phase(p)
    reporter.start()
    reporter.addTotal(extra)
    reporter
  }

  /** Starts a phase whose size is already known and gives back its reporter. */
  def tracked(p: Phase, total: Long): PhaseReporter = {
    val reporter = phase(p)
    reporter.start()
    reporter.total(total)
    reporter
  }

  /** A phase that is one single step, such as reading the collection or the permission graph. */
  def step[A](p: Phase)(f: => A): A = tracked(p, 1).one(f)
}

object ProgressReporter {

  object NoOp extends ProgressReporter {
    def phase(p: Phase): PhaseReporter = PhaseReporter.NoOp
    def warn(msg: String): Unit = ()
  }
}
