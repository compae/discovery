/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.jobs

import java.nio.file.{Files, Path}
import java.time.Instant

import com.stratio.discoverycicd.config.ConfigFacade.JobsConfig
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

/**
 * The jobs this process remembers and the files they leave behind. Nothing here is persisted on
 * purpose: there is no volume in the deployment, so what matters is that the ephemeral disk of the
 * pod does not fill up with exports of a couple hundred MB that nobody can ask for any more
 */
@RunWith(classOf[JUnitRunner])
class JobRegistryTest extends WordSpec with Matchers {

  "Registering a job" should {

    "make it findable, and list the newest first" in withRegistry() { (registry, _) =>
      val older = finishedJob(registry, "older", secondsAgo = 60)
      val newer = finishedJob(registry, "newer", secondsAgo = 1)

      registry.get("older").map(_.id) shouldBe Some(older.id)
      registry.all.map(_.id) shouldBe List("newer", "older")
      registry.get("nothing like it") shouldBe None
      newer.status shouldBe JobStatus.Succeeded
    }
  }

  "Removing a job" should {

    "delete the file it left behind" in withRegistry() { (registry, _) =>
      val job = finishedJob(registry, "done", secondsAgo = 1)
      val result = job.resultFile.get
      Files.exists(result) shouldBe true

      registry.remove("done")

      registry.get("done") shouldBe None
      Files.exists(result) shouldBe false
    }

    // dropping it would leave its file behind with nobody left to clean it up
    "leave alone one that is still running" in withRegistry() { (registry, _) =>
      val job = jobWithResult(registry, "running")
      registry.register(job)
      job.started()

      registry.remove("running") shouldBe None
      registry.get("running") shouldBe defined
    }
  }

  "The reaper" should {

    "drop the finished jobs that are too old, and their files" in
      withRegistry(retention = "0s") { (registry, _) =>
        val job = finishedJob(registry, "old", secondsAgo = 5)
        val running = jobWithResult(registry, "still going")
        registry.register(running)
        running.started()

        registry.reap()

        registry.get("old") shouldBe None
        Files.exists(job.resultFile.get) shouldBe false
        // one that has not finished is never dropped, however long it has been going
        registry.get("still going") shouldBe defined
      }

    "keep only the last few, however recent the rest are" in
      withRegistry(maxStoredResults = 2) { (registry, _) =>
        val dropped = (1 to 4).map(i => finishedJob(registry, s"job-$i", secondsAgo = 10 - i))

        registry.reap()

        registry.all.map(_.id) shouldBe List("job-4", "job-3")
        dropped.take(2).foreach(job => Files.exists(job.resultFile.get) shouldBe false)
      }
  }

  "Starting up" should {

    // the ids that named them went with the process that is gone, so nobody can ask for them again
    "empty the work directory of whatever a previous run left there" in {
      val dir = Files.createTempDirectory("discovery-cicd-jobs-test")
      val leftover = Files.write(dir.resolve("leftover.json.gz"), Array[Byte](1, 2, 3))

      val registry = new JobRegistry(configOf(dir))
      try {
        Files.exists(leftover) shouldBe false
        Files.isDirectory(dir) shouldBe true
      } finally registry.close()
    }
  }

  // --- helpers ----------------------------------------------------------------------------------

  private def configOf(dir: Path, retention: String = "24h", maxStoredResults: Int = 10) =
    JobsConfig(maxConcurrent = 1, maxQueued = 4, workDir = dir.toString,
      retention = retention, maxStoredResults = maxStoredResults)

  private def withRegistry(retention: String = "24h", maxStoredResults: Int = 10)
                          (check: (JobRegistry, Path) => Unit): Unit = {
    val dir = Files.createTempDirectory("discovery-cicd-jobs-test")
    val registry = new JobRegistry(configOf(dir, retention, maxStoredResults))
    try check(registry, dir)
    finally registry.close()
  }

  private def jobWithResult(registry: JobRegistry, id: String, secondsAgo: Long = 0): Job = {
    val result = registry.workDir.resolve(s"$id.json.gz")
    Files.write(result, Array[Byte](1, 2, 3))
    new Job(id, JobType.Export, Some(BigInt(4)), None, "1",
      Instant.now.minusSeconds(secondsAgo), Some(result), None)
  }

  private def finishedJob(registry: JobRegistry, id: String, secondsAgo: Long): Job = {
    val job = jobWithResult(registry, id, secondsAgo)
    registry.register(job)
    job.started()
    job.succeeded(Some(3L))
    job
  }
}
