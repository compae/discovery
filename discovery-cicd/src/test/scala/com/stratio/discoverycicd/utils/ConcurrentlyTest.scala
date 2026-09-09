/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.utils

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

import com.stratio.discoverycicd.progress.PhaseReporter

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

/**
 * Metabase answers about one table, and about one field, at a time, and a collection can use thousands
 * of them, so making those requests one after another is what makes an export or an import of a big
 * collection take ages (see ROCK-15286)
 */
@RunWith(classOf[JUnitRunner])
class ConcurrentlyTest extends WordSpec with Matchers {

  "Running something over a list of items" should {

    // this is the whole point: what the caller passes is an http request to metabase
    "run several of them at the same time, and never more than the ones asked for" in {
      val inFlight = new AtomicInteger(0)
      val maxInFlight = new AtomicInteger(0)

      Concurrently.map((1 to Concurrently.MetadataRequests * 2).toList, "thing(s)") { item =>
        val now = inFlight.incrementAndGet()
        maxInFlight.synchronized { if (now > maxInFlight.get) maxInFlight.set(now) }
        Thread.sleep(50) // long enough that they have to overlap if they really run at the same time
        inFlight.decrementAndGet()
        item
      }

      maxInFlight.get should be > 1
      maxInFlight.get should be <= Concurrently.MetadataRequests
    }

    // metabase saturates with some endpoints and not with others, so how many go at a time is chosen
    // per endpoint (see 'Concurrently.CardRequests'): asking for fewer has to really mean fewer
    "never run more than the number asked for when it is not the default" in {
      val inFlight = new AtomicInteger(0)
      val maxInFlight = new AtomicInteger(0)

      Concurrently.map((1 to 16).toList, "thing(s)", requests = 2) { item =>
        val now = inFlight.incrementAndGet()
        maxInFlight.synchronized { if (now > maxInFlight.get) maxInFlight.set(now) }
        Thread.sleep(30)
        inFlight.decrementAndGet()
        item
      }

      maxInFlight.get should be <= 2
    }

    // the export would otherwise hold its tables and its fields in whatever order metabase happened to
    // answer, so the same collection would produce a different file every time it is exported
    "give the results back in the order of the items and not in the order they finished" in {
      // the first one takes the longest, so it finishes last
      val results = Concurrently.map(List(5, 1, 3), "thing(s)") { item =>
        Thread.sleep(item * 40L)
        item * 2
      }

      results shouldBe List(10, 2, 6)
    }

    // the importer throws when it cannot read a table of the target metabase, and that has to keep
    // coming out just as it did when this was a plain loop (see 'Importer.getFieldsOfTable')
    "let the failure of one of the items come out" in {
      val thrown = the [IllegalStateException] thrownBy {
        Concurrently.map((1 to Concurrently.MetadataRequests * 2).toList, "thing(s)") { item =>
          if (item == 3) throw new IllegalStateException("could not read the table")
          item
        }
      }

      thrown.getMessage shouldBe "could not read the table"
    }

    // The same as 'fold' does with its iterator, and it matters far more here: 'map' makes a
    // 'Future' per item up front, and 'shutdown' does not drop the ones already queued, it runs
    // every one of them. So a failure on the fifth question of sixty thousand left the other
    // fifty-nine thousand asking metabase for answers nobody was going to look at -- an hour of
    // load on an instance whose export had already failed, and with the job slot already given to
    // whatever was next in the queue
    "give up on the first failure and not run the items that had not started" in {
      val Items = 400
      val ran = new AtomicInteger(0)

      val thrown = the [IllegalStateException] thrownBy {
        Concurrently.map((1 to Items).toList, "thing(s)", requests = 2) { item =>
          ran.incrementAndGet()
          if (item == 5) throw new IllegalStateException("could not read the table")
          // every item takes a moment, as a request to metabase does: without it the whole queue
          // drains before this thread can look at the counter, and the test would pass either way
          Thread.sleep(2)
          item
        }
      }

      thrown.getMessage shouldBe "could not read the table"
      // long enough for the rest of the queue to have run if it were going to: at two items every
      // two milliseconds that is a couple of hundred of them
      Thread.sleep(300)
      // the ones in flight are left to finish, so a couple past the failure is fine; what may not
      // happen is the other three hundred and ninety running anyway
      withClue(s"${ran.get} of the $Items items ran: ") { ran.get should be < 50 }
    }

    "do nothing at all when there is nothing to do" in {
      Concurrently.map(List.empty[Int], "thing(s)")(_ => fail("should not have been called")) shouldBe empty
    }

    // a single item is not worth a thread pool, but it still has to give the same answer
    "work with a single item" in {
      Concurrently.map(List(21), "thing(s)")(_ * 2) shouldBe List(42)
    }
  }

  // This is the one place nearly every long loop of an export and of an import goes through, so what
  // it reports is what the status endpoint of an asynchronous job shows
  "Reporting how far it has got" should {

    "say how many items there are before running any of them, and count every one" in {
      val reporter = new RecordingReporter

      Concurrently.map((1 to 20).toList, "thing(s)", requests = 4, reporter)(identity)

      reporter.started.get shouldBe true
      reporter.total.get shouldBe 20
      reporter.advanced.get shouldBe 20
    }

    "count the single item too, which does not go through the pool" in {
      val reporter = new RecordingReporter

      Concurrently.map(List(21), "thing(s)", reporter = reporter)(_ * 2) shouldBe List(42)

      reporter.total.get shouldBe 1
      reporter.advanced.get shouldBe 1
    }

    // the server does not abort an export when the client disconnects, so cancelling has to really
    // stop asking metabase for things and not only stop looking at the answers
    "stop asking for anything else once it is cancelled" in {
      val reporter = new RecordingReporter
      val called = new AtomicInteger(0)
      reporter.cancelAfter(3)

      a[IllegalStateException] should be thrownBy {
        Concurrently.map((1 to 200).toList, "thing(s)", requests = 2, reporter) { item =>
          called.incrementAndGet()
          item
        }
      }

      // the ones already in flight are left to finish, so a couple more than the cut-off is fine,
      // but nothing like the two hundred that were asked for
      called.get should be < 20
    }
  }

  // An import reads its questions one at a time out of a pass over the uploaded file, because the
  // whole of them does not fit in the heap. Parallelising it may not undo that, which is what
  // 'fold' is for and what 'map' could not have done
  "Running something over a one-pass iterator" should {

    "run several of them at the same time, and never more than the ones asked for" in {
      val inFlight = new AtomicInteger(0)
      val maxInFlight = new AtomicInteger(0)

      Concurrently.fold((1 to 40).iterator, "thing(s)", requests = 4)(0) { item =>
        val now = inFlight.incrementAndGet()
        maxInFlight.synchronized { if (now > maxInFlight.get) maxInFlight.set(now) }
        Thread.sleep(20)
        inFlight.decrementAndGet()
        item
      }(_ + _) shouldBe (1 to 40).sum

      maxInFlight.get should be <= 4
      maxInFlight.get should be > 1
    }

    // the reason this exists: the iterator must not be drained ahead of the requests, or the whole
    // export ends up in the heap and the import dies of exactly what it was rewritten to avoid
    "never read further ahead of the requests than the ones in flight" in {
      val read = new AtomicInteger(0)
      val finished = new AtomicInteger(0)
      val maxOutstanding = new AtomicInteger(0)

      val counted = (1 to 60).iterator.map { item => read.incrementAndGet(); item }

      Concurrently.fold(counted, "thing(s)", requests = 3)(0) { item =>
        val outstanding = read.get - finished.get
        maxOutstanding.synchronized { if (outstanding > maxOutstanding.get) maxOutstanding.set(outstanding) }
        Thread.sleep(5)
        finished.incrementAndGet()
        item
      }(_ + _)

      // the ones in flight and not one more, never the sixty the iterator holds
      maxOutstanding.get should be <= 3
    }

    "give back what the caller folds, whatever order the answers come in" in {
      val ids = Concurrently.fold(List(5, 1, 3).iterator, "thing(s)", requests = 3)(Set.empty[Int]) {
        item => Thread.sleep((10 - item) * 10L); item * 2
      }((set, item) => set + item)

      ids shouldBe Set(10, 2, 6)
    }

    "run on the caller's thread when only one is asked for at a time" in {
      val threads = collection.mutable.Set.empty[Long]

      Concurrently.fold((1 to 5).iterator, "thing(s)", requests = 1)(0) { item =>
        threads += Thread.currentThread().getId
        item
      }(_ + _) shouldBe 15

      threads shouldBe Set(Thread.currentThread().getId)
    }

    "have nothing to do with an empty iterator" in {
      Concurrently.fold[Int, Int, Int](Iterator.empty, "thing(s)")(0)(
        _ => fail("should not have been called")
      )(_ + _) shouldBe 0
    }

    // the loop this replaces let the exception out, and so does this one
    "give up on the first failure and not read anything else" in {
      val read = new AtomicInteger(0)
      val counted = (1 to 500).iterator.map { item => read.incrementAndGet(); item }

      val thrown = the[IllegalArgumentException] thrownBy {
        Concurrently.fold(counted, "thing(s)", requests = 2)(0) { item =>
          if (item == 5) throw new IllegalArgumentException("metabase said no")
          item
        }(_ + _)
      }

      thrown.getMessage shouldBe "metabase said no"
      // the ones in flight are left to finish, so a couple past the failure is fine
      read.get should be < 20
    }

    // 'map' cannot do this: a 'Future' whose body throws an 'OutOfMemoryError' never completes, so
    // its 'Await' waits forever and the job hangs instead of failing
    "let an error that is not NonFatal out instead of hanging on it" in {
      val thrown = the[OutOfMemoryError] thrownBy {
        Concurrently.fold((1 to 10).iterator, "thing(s)", requests = 2)(0) { item =>
          if (item == 3) throw new OutOfMemoryError("no heap left")
          item
        }(_ + _)
      }

      thrown.getMessage shouldBe "no heap left"
    }

    "stop asking for anything else once it is cancelled" in {
      val reporter = new RecordingReporter
      val called = new AtomicInteger(0)
      reporter.cancelAfter(3)

      a[IllegalStateException] should be thrownBy {
        Concurrently.fold((1 to 200).iterator, "thing(s)", 2, reporter)(0) { item =>
          called.incrementAndGet()
          item
        }(_ + _)
      }

      called.get should be < 20
    }

    // the size is not told, unlike in 'map': an iterator does not know it, and every caller has
    // already declared it out of the counts the uploaded export carries
    "count every item it runs, without being able to say how many there are" in {
      val reporter = new RecordingReporter

      Concurrently.foreach((1 to 20).iterator, "thing(s)", 4, reporter)(_ => ())

      reporter.started.get shouldBe true
      reporter.advanced.get shouldBe 20
      reporter.total.get shouldBe 0
    }

    "count the items it runs on the caller's thread too" in {
      val reporter = new RecordingReporter

      Concurrently.foreach((1 to 7).iterator, "thing(s)", 1, reporter)(_ => ())

      reporter.advanced.get shouldBe 7
    }
  }

  /** Counts what [[Concurrently]] tells it, and can be told to start refusing at some point. */
  private class RecordingReporter extends PhaseReporter {
    val started = new AtomicBoolean(false)
    val total = new AtomicInteger(0)
    val advanced = new AtomicInteger(0)
    private val cancelAt = new AtomicInteger(Int.MaxValue)
    private val checks = new AtomicInteger(0)

    def cancelAfter(items: Int): Unit = cancelAt.set(items)

    def start(): Unit = started.set(true)
    def total(n: Long): Unit = total.set(n.toInt)
    def addTotal(n: Long): Unit = total.addAndGet(n.toInt)
    def advance(by: Long): Unit = advanced.addAndGet(by.toInt)
    def checkCancelled(): Unit =
      if (checks.incrementAndGet() > cancelAt.get) throw new IllegalStateException("cancelled")
  }
}
