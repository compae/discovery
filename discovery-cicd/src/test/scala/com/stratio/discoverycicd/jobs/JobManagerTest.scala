/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.jobs

import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import com.stratio.discoverycicd.config.ConfigFacade.JobsConfig
import com.stratio.discoverycicd.exceptions.{JobNotFoundException, TooManyJobsException}
import com.stratio.discoverycicd.progress.Phase
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

/**
 * Running the exports and the imports somebody asked for asynchronously. Only one runs at a time by
 * default: the pod is given 2 cores and an export of a big collection already uses 1.8 of them, so a
 * second one at the same time makes both slower and neither finishes any sooner
 */
@RunWith(classOf[JUnitRunner])
class JobManagerTest extends WordSpec with Matchers {

  "A submitted job" should {

    "run, and end up succeeded with the size of what it produced" in withManager() { manager =>
      val job = manager.submit(newJob(manager, "ok"))(_ => Some(1234L))

      waitUntil(job.status.isTerminal)
      job.status shouldBe JobStatus.Succeeded
      job.snapshot.resultBytes shouldBe Some(1234L)
      job.snapshot.progress.percentage shouldBe 100
    }

    // whoever polls the status has to be told why, because nobody is left holding a response
    "end up failed with the reason, instead of taking the process down" in withManager() { manager =>
      val job = manager.submit(newJob(manager, "boom"))(_ => throw new IllegalStateException("no such table"))

      waitUntil(job.status.isTerminal)
      job.status shouldBe JobStatus.Failed
      job.snapshot.error.get should include("no such table")
    }
  }

  "Asking for more work than there is room for" should {

    "make the ones that do not fit wait their turn" in withManager() { manager =>
      val blocked = new CountDownLatch(1)
      val first = manager.submit(newJob(manager, "first")) { _ => blocked.await(); None }
      waitUntil(first.status == JobStatus.Running)

      val second = manager.submit(newJob(manager, "second"))(_ => None)

      second.status shouldBe JobStatus.Queued
      blocked.countDown()
      waitUntil(second.status == JobStatus.Succeeded)
    }

    "be refused once the queue is full, and not leave the refused one registered" in
      withManager(maxQueued = 1) { manager =>
        val blocked = new CountDownLatch(1)
        manager.submit(newJob(manager, "running")) { _ => blocked.await(); None }
        waitUntil(manager.get("running").exists(_.status == JobStatus.Running))
        manager.submit(newJob(manager, "waiting"))(_ => None)

        val refused = the[TooManyJobsException] thrownBy manager.submit(newJob(manager, "refused"))(_ => None)

        refused.msg should include("cannot be taken")
        manager.get("refused") shouldBe None
        blocked.countDown()
      }
  }

  // the server does not abort an export when the client goes away, so before this a failed attempt
  // left an export nobody wanted eating both cores of the pod until it finished
  "Cancelling a job" should {

    "stop one that is running, at the next item it was about to ask for" in withManager() { manager =>
      val started = new CountDownLatch(1)
      val job = manager.submit(newJob(manager, "long")) { running =>
        val phase = running.reporter.tracked(Phase.Questions, 1000)
        started.countDown()
        (1 to 1000) foreach { _ =>
          phase.checkCancelled()
          Thread.sleep(5)
          phase.advance()
        }
        None
      }
      started.await(5, TimeUnit.SECONDS)

      manager.cancel("long")

      waitUntil(job.status.isTerminal)
      job.status shouldBe JobStatus.Cancelled
      job.snapshot.progress.doneOf(Phase.Questions) should be < 1000L
    }

    "stop one that had not started yet without ever running it" in withManager() { manager =>
      val blocked = new CountDownLatch(1)
      manager.submit(newJob(manager, "running")) { _ => blocked.await(); None }
      waitUntil(manager.get("running").exists(_.status == JobStatus.Running))

      val ran = new AtomicBoolean(false)
      val queued = manager.submit(newJob(manager, "queued")) { _ => ran.set(true); None }
      manager.cancel("queued")
      blocked.countDown()

      waitUntil(queued.status.isTerminal)
      queued.status shouldBe JobStatus.Cancelled
      ran.get shouldBe false
    }

    "forget one that had already finished, and delete its result" in withManager() { manager =>
      val job = manager.submit(newJob(manager, "done"))(_ => Some(1L))
      waitUntil(job.status.isTerminal)
      val result = job.resultFile.get
      Files.write(result, Array[Byte](1, 2, 3))

      manager.cancel("done")

      manager.get("done") shouldBe None
      Files.exists(result) shouldBe false
    }

    "say so when there is no such job, mentioning that a restart loses them" in withManager() { manager =>
      val notFound = the[JobNotFoundException] thrownBy manager.cancel("never existed")

      notFound.msg should include("do not survive a restart")
    }
  }

  // --- helpers ----------------------------------------------------------------------------------

  private def withManager(maxConcurrent: Int = 1, maxQueued: Int = 4)(check: JobManager => Unit): Unit = {
    val dir = Files.createTempDirectory("discovery-cicd-jobs-test")
    val config = JobsConfig(maxConcurrent, maxQueued, dir.toString, "24h", 10)
    val manager = JobManager(new JobRegistry(config), maxConcurrent, maxQueued)
    try check(manager)
    finally manager.close()
  }

  private def newJob(manager: JobManager, id: String): Job =
    JobManager.newJob(id, JobType.Export, "1", collectionId = Some(BigInt(4)),
      resultFile = Some(manager.workDir.resolve(s"$id.json.gz")))

  private def waitUntil(condition: => Boolean): Unit = {
    val deadline = System.currentTimeMillis() + 10000
    while (!condition && System.currentTimeMillis() < deadline) Thread.sleep(10)
    if (!condition) fail("the job did not get where it was expected to in 10s")
  }
}
