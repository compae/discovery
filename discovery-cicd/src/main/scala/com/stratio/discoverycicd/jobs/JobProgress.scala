/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.jobs

import com.stratio.discoverycicd.progress.PhaseProgress

/**
 * What the status endpoint answers about one job.
 *
 * Every phase of the operation is listed from the very first poll, the ones that have not run yet as
 * pending, so that whoever is watching knows what is still to come and not only where it is now. A
 * `total` of `null` means that the size of that phase is not known yet, which is the truth for the
 * tables and the fields of an export until every question has been read.
 */
case class JobProgress(
  jobId: String,
  `type`: String,
  status: String,
  collectionId: Option[BigInt],
  parentId: Option[BigInt],
  owner: String,
  submittedAt: String,
  startedAt: Option[String],
  finishedAt: Option[String],
  elapsedSeconds: Long,
  estimatedSecondsRemaining: Option[Long],
  percentage: Int,
  currentPhase: Option[String],
  message: String,
  phases: List[PhaseProgress],
  warnings: List[String],
  warningsOmitted: Int,
  error: Option[String],
  resultBytes: Option[Long],
  statusUrl: Option[String],
  resultUrl: Option[String]
)

object JobProgress {

  def of(job: Job): JobProgress = {
    val state = job.snapshot
    JobProgress(
      jobId = job.id,
      `type` = job.jobType.entryName,
      status = state.status.entryName,
      collectionId = job.collectionId,
      parentId = job.parentId,
      owner = job.owner,
      submittedAt = job.submittedAt.toString,
      startedAt = state.startedAt.map(_.toString),
      finishedAt = state.finishedAt.map(_.toString),
      elapsedSeconds = job.elapsedSeconds,
      estimatedSecondsRemaining = job.estimatedSecondsRemaining,
      percentage = state.progress.percentage,
      currentPhase = state.progress.current.map(_.id),
      message = state.progress.message,
      phases = state.progress.phaseProgress,
      warnings = state.progress.warnings.toList,
      warningsOmitted = state.progress.warningsOmitted,
      error = state.error,
      resultBytes = state.resultBytes,
      statusUrl = None,
      resultUrl = None
    )
  }
}

/** The answer to the request that starts a job: it comes back before any work has been done. */
case class JobAccepted(jobId: String, status: String, statusUrl: String, resultUrl: Option[String])

object JobAccepted {
  def of(job: Job, statusUrl: String, resultUrl: Option[String]): JobAccepted =
    JobAccepted(job.id, job.status.entryName, statusUrl, resultUrl)
}
