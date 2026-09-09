/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import java.util.concurrent.{Executors, Semaphore}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import com.stratio.discoverycicd.progress.PhaseReporter

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object Concurrently extends Logging {

  // Metabase answers about one table, one field, and one question at a time: no endpoint takes a list
  // of them, and the ones that answer for a whole database or for the whole instance are the ones that
  // do not work with big catalogs ('GET card?f=all' answers 500 with the same 'PreparedStatement can
  // have at most 65,535 parameters' as the rest, see 'ApiClient.getTableMetadata' and ROCK-15286). A
  // collection can use thousands of each, so making those requests one after another is what makes an
  // export or an import of a big collection take ages.
  //
  // How many go at a time is not the same for every endpoint, because they do not cost metabase the
  // same. Both numbers below are measured against a metabase 0.56.4, and neither should be raised
  // without measuring again on the instance that is being slow.

  // 'GET table/:id/query_metadata' and 'GET field/:id' are cheap and scale almost linearly: ~134ms
  // each, and 4 at a time make them 3.7 times faster, 8 make it 6.9 and 16 only 9.7. Past 8 the gain
  // no longer pays for the load put on metabase
  val MetadataRequests = 8

  // 'GET card/:id' is a different animal: ~352ms each, and metabase saturates. Its throughput goes
  // from 2.8 requests/s alone to 4.8 with 4 at a time and back down to 3.8 with 8, while the time of
  // each request climbs from 352ms to 2s. So 4, and no more: past that the export gets slower, not
  // faster
  val CardRequests = 4

  // What an import writes with: 'POST card', 'PUT card/:id', 'POST dashboard', 'PUT dashboard/:id'
  // and the pulse endpoints. It is not a measurement, it is the prudent place to start from: a
  // write costs metabase more than the 'GET card/:id' that already saturates at 4 (see above), and
  // metabase shares the two cores of the pod with us. Raising it means measuring against the
  // instance that is being slow, the same as with the two constants above, and it can be raised
  // without recompiling through 'DISCOVERY_CICD_IMPORT_PARALLEL_WRITES'
  val WriteRequests = 4

  // All those requests together can take a long while, so the log says how far they have got
  private val ItemsPerProgressLog = 100

  /**
   * Runs `f` over every item, with at most `requests` of them at a time, and gives the results back
   * in the order of `items` and not in the order they finished, so that what an export holds does not
   * depend on which request came back first.
   *
   * `f` runs on another thread, so it must not touch anything mutable. The ones we pass only make an
   * http request and build a value out of the answer, which is safe: `ApiClient` keeps no state and
   * builds a fresh request every time.
   *
   * If `f` fails for one of the items its exception comes out of here, just as it came out of the
   * loop this replaces. The requests already in flight are left to finish, and no more are started:
   * `shutdown` alone would not do that, see the `failure` below.
   *
   * @param what     how the items are named in the progress line of the log, e.g. "table(s)"
   * @param requests how many of them are in flight at once, see [[MetadataRequests]] and [[CardRequests]]
   * @param reporter who is told how far this has got, and who says whether it is still wanted. This
   *                 is the one place where nearly every long loop of an export or of an import goes
   *                 through, so instrumenting it covers the questions, the dashboards, the tables and
   *                 the fields at once. It defaults to doing nothing, which is what the synchronous
   *                 endpoints and the shell get
   */
  def map[A, B](
    items: List[A],
    what: String,
    requests: Int = MetadataRequests,
    reporter: PhaseReporter = PhaseReporter.NoOp
  )(f: A => B): List[B] = {
    reporter.start()
    reporter.total(items.size.toLong)

    // not worth a thread pool, and it keeps the caller on its own thread when there is nothing to gain
    if (items.size <= 1) items map reporter.tracking(f)
    else {
      val pool = Executors.newFixedThreadPool(requests min items.size)
      implicit val context: ExecutionContext = ExecutionContext.fromExecutorService(pool)
      val done = new AtomicInteger(0)
      val failure = new AtomicReference[Throwable]()

      // every item gets its own Future right away, but the pool has only so many threads, so no more
      // than 'requests' of them are actually asking metabase anything at any moment
      val results = items map { item =>
        Future {
          try {
            // What went wrong for one item stops the rest from being asked for at all, the same way
            // 'fold' stops reading its iterator. The 'shutdown' below does not do it: it only keeps
            // new tasks out of the pool and still runs every one already queued, so a failure on
            // the fifth question of sixty thousand left the other fifty-nine thousand asking
            // metabase for answers nobody was going to look at, for the best part of an hour after
            // the export had already failed
            Option(failure.get) foreach { error => throw error }

            // before the request and not after it: cancelling has to stop asking metabase things, not
            // only stop looking at the answers. The ones already in flight are left to finish
            reporter.checkCancelled()
            val result = f(item)
            reporter.advance()
            val soFar = done.incrementAndGet()
            if (soFar % ItemsPerProgressLog == 0) log.info(s"Got $soFar of the ${items.size} $what")
            result
          } catch {
            // the first failure is the one kept, and the rethrows above carry that very exception,
            // so what comes out of the 'Await' is always an error that really happened
            case error: Throwable =>
              val _ = failure.compareAndSet(null, error)
              throw error
          }
        }
      }

      // the http client has its own timeout (see 'ApiClient.connectionAndReadTimeout'), so this is not
      // a way of waiting forever: it is bounded by the requests themselves
      try Await.result(Future.sequence(results), Duration.Inf)
      finally pool.shutdown()
    }
  }

  /**
   * The same as [[map]], but over a one-pass iterator whose items must never be held all at once,
   * folding the results as they come back instead of building a list of them.
   *
   * This is what an import needs and [[map]] cannot give it. [[map]] takes a `List` and makes a
   * `Future` per item up front, which for the sixty thousand questions of a big export means both
   * the whole export in the heap and sixty thousand queued tasks. Here the iterator is pulled one
   * item at a time and only when one of the requests in flight has finished, so **at most
   * `requests` items of the export are alive at any moment** and the memory an import needs does
   * not depend on how big the export is. That is the whole point of `ExportSource.withCards` and
   * this is what lets it survive being parallelised.
   *
   * `f` runs on another thread, so it must not touch anything mutable; `combine` runs under a lock,
   * so it can. Results are combined in the order they finish and not in the order of `items`: every
   * caller here builds a `Map` or a `Set`, which do not care.
   *
   * If `f` fails for one of the items, no more are read and the exception comes out of here once
   * the ones in flight have finished, just as it came out of the loop this replaces. The first
   * failure is the one reported. Errors that are not `NonFatal` are reported too, and on purpose:
   * a `Future` whose body throws an `OutOfMemoryError` never completes its promise in scala 2.12,
   * so the `Await` of [[map]] waits for an answer that is never coming and the job hangs instead of
   * failing. Here the permit is given back in a `finally` and the error is the one that comes out.
   *
   * @param what     how the items are named in the progress line of the log, e.g. "question(s)"
   * @param requests how many are in flight at once, see [[WriteRequests]]. One or less runs the
   *                 whole thing on the caller's thread, which is what the shell and the tests get
   * @param reporter who is told how far this has got and whether it is still wanted. It is **not**
   *                 told the size: an iterator does not know it, and every caller already declared
   *                 it through `ProgressReporter.tracked`
   */
  def fold[A, R, B](
    items: Iterator[A],
    what: String,
    requests: Int = WriteRequests,
    reporter: PhaseReporter = PhaseReporter.NoOp
  )(zero: B)(f: A => R)(combine: (B, R) => B): B = {
    reporter.start()

    if (requests <= 1) foldOnThisThread(items, what, reporter)(zero)(f)(combine)
    else {
      val pool = Executors.newFixedThreadPool(requests)
      // the one thing that bounds how far ahead of the requests the iterator is allowed to get
      val slots = new Semaphore(requests)
      val failure = new AtomicReference[Throwable]()
      val done = new AtomicInteger(0)
      val lock = new Object
      var accumulated = zero

      try {
        while (items.hasNext && failure.get == null) {
          // the permit first and the item after it, and not the other way round: waiting with an
          // item already in hand would keep one more of them alive than the caller asked for
          slots.acquire()
          val item = items.next()

          // checked again after waiting: whatever went wrong may have gone wrong while we waited,
          // and there is no point in starting one more request for work nobody wants any more
          if (failure.get != null) slots.release()
          else pool.execute(new Runnable {
            def run(): Unit =
              try {
                // before the request and not after it: cancelling has to stop asking metabase
                // things, not only stop looking at the answers
                reporter.checkCancelled()
                val result = f(item)
                lock.synchronized { accumulated = combine(accumulated, result) }
                reporter.advance()
                val soFar = done.incrementAndGet()
                if (soFar % ItemsPerProgressLog == 0) log.info(s"Done $soFar $what so far")
              } catch {
                case error: Throwable => val _ = failure.compareAndSet(null, error)
              } finally slots.release()
          })
        }

        // every permit back means every request has finished, failed or not: the ones in flight are
        // always left to finish, the same as in 'map'
        slots.acquire(requests)
        Option(failure.get) foreach { error => throw error }
        lock.synchronized(accumulated)
      } catch {
        // whoever interrupts the thread running the import wants it to stop now, and that arrives
        // here as an interrupt of the 'acquire' above. Waiting for the requests in flight is
        // exactly what it is trying to get out of, so they are cut short instead of awaited
        case interrupted: InterruptedException =>
          pool.shutdownNow()
          throw interrupted
      } finally pool.shutdown()
    }
  }

  /** [[fold]] for a loop that only makes requests and keeps nothing. */
  def foreach[A](
    items: Iterator[A],
    what: String,
    requests: Int = WriteRequests,
    reporter: PhaseReporter = PhaseReporter.NoOp
  )(f: A => Unit): Unit =
    fold(items, what, requests, reporter)(())(f)((_, _) => ())

  private def foldOnThisThread[A, R, B](items: Iterator[A], what: String, reporter: PhaseReporter)
                                       (zero: B)(f: A => R)(combine: (B, R) => B): B = {
    var accumulated = zero
    var done = 0L
    while (items.hasNext) {
      val item = items.next()
      reporter.checkCancelled()
      accumulated = combine(accumulated, f(item))
      reporter.advance()
      done += 1
      if (done % ItemsPerProgressLog == 0) log.info(s"Done $done $what so far")
    }
    accumulated
  }
}
