/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.progress

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

/**
 * What the status endpoint answers about how far an export or an import has got. The point of these
 * is that the number it reports is honest: an export of a collection with 60.000 questions and
 * 60.000 tables is not half done when the questions are, and it does not know how many tables it
 * needs until it has read every question (see ROCK-15286 and 'Concurrently')
 */
@RunWith(classOf[JUnitRunner])
class ProgressStateTest extends WordSpec with Matchers {

  import Phase._

  private val twoPhases = List(Questions, Tables)

  "The size of a phase" should {

    "be reported as unknown until it is known, instead of being guessed" in {
      val state = ProgressState.empty(twoPhases).advance(Questions, 30)

      state.phaseProgress.find(_.phase == Questions.id).get shouldBe
        PhaseProgress(Questions.id, PhaseProgress.Running, 30, None)
    }

    "be known before the phase runs, which is the whole point of listing the items first" in {
      val state = ProgressState.empty(twoPhases).withTotal(Questions, 60000)

      // declared but not started: an export knows how many questions it has to bring long before it
      // starts bringing them
      state.phaseProgress.find(_.phase == Questions.id).get shouldBe
        PhaseProgress(Questions.id, PhaseProgress.Pending, 0, Some(60000))
      state.current shouldBe None
    }

    "add up when the phase is fed from more than one place" in {
      val state = ProgressState.empty(List(DeletingLeftovers))
        .addTotal(DeletingLeftovers, 4)
        .addTotal(DeletingLeftovers, 6)

      state.totals(DeletingLeftovers) shouldBe 10
    }
  }

  // An enumeratum enum forces its case objects while the object holding them is still being built,
  // so a weight declared next to them reads as zero and every phase silently ends up weighing
  // nothing, and a list of them built too early comes out full of nulls. Both went unnoticed until
  // an asynchronous import answered 500 with a NullPointerException
  "Every phase" should {

    "cost something, so that the phases really are weighed against each other" in {
      Phase.values.filter(_.millisPerItem <= 0) shouldBe empty
    }

    "be in the list of the operation it belongs to, and none of them be null" in {
      val listed = Phase.ofExport ++ Phase.ofImport

      listed should contain noElementsOf List(null)
      listed.size shouldBe Phase.values.size
      listed.toSet shouldBe Phase.values.toSet
    }
  }

  "The percentage" should {

    // 'GET card/:id' costs about five times what 'GET table/:id/query_metadata' costs, so counting
    // items instead of weighing them would say 50% when the slow half is what is left
    "weigh each phase by what one of its items costs" in {
      val state = ProgressState.empty(twoPhases)
        .withTotal(Questions, 100)
        .withTotal(Tables, 100)
        .advance(Tables, 100)

      // 100 tables of 17 out of 100 questions of 88 plus 100 tables of 17
      state.percentage shouldBe (100 * Tables.millisPerItem * 100) / (100 * Questions.millisPerItem + 100 * Tables.millisPerItem)
      state.percentage should be < 20
    }

    // The first phase of an export is a single request. Counting only the phases that had already
    // been measured made it the whole of the denominator, so an export of an hour reported 99% after
    // a tenth of a second and, being monotonic, stayed there
    "not report a job as nearly done because its first one-request phase finished" in {
      val barelyStarted = ProgressState.empty(Phase.ofExport)
        .withTotal(Collection, 1)
        .advance(Collection, 1)

      barelyStarted.percentage should be < 10
    }

    "count a phase nobody has measured yet as a single item" in {
      val onlyQuestionsMeasured = ProgressState.empty(twoPhases)
        .withTotal(Questions, 100)
        .advance(Questions, 50)

      // 50 questions of 88, out of 100 questions of 88 plus the one table it assumes
      onlyQuestionsMeasured.percentage shouldBe
        (50 * Questions.millisPerItem * 100) / (100 * Questions.millisPerItem + Tables.millisPerItem)
    }

    "not go down while the work in sight is the same" in {
      val advancing = (1 to 20).scanLeft(ProgressState.empty(twoPhases).withTotal(Questions, 20)) {
        (state, _) => state.advance(Questions, 1)
      }

      advancing.map(_.percentage) shouldBe sorted
    }

    // a bar frozen at 99% for the forty minutes an export takes is worse than one that admits it
    // has just found sixty thousand items nobody had counted
    "start again from the real number when a phase turns out to be far bigger than assumed" in {
      val questionsDone = ProgressState.empty(twoPhases).withTotal(Questions, 10).advance(Questions, 10)
      questionsDone.percentage should be > 90

      val tablesTurnedUp = questionsDone.withTotal(Tables, 1000)

      tablesTurnedUp.percentage should be < questionsDone.percentage
      tablesTurnedUp.percentage shouldBe
        (10 * Questions.millisPerItem * 100) / (10 * Questions.millisPerItem + 1000 * Tables.millisPerItem)
    }

    "stay below a hundred while there is anything left, because a phase can still add work" in {
      val everythingKnownDone = ProgressState.empty(twoPhases)
        .withTotal(Questions, 10).advance(Questions, 10)
        .withTotal(Tables, 10).advance(Tables, 10)

      everythingKnownDone.percentage shouldBe ProgressState.MaxWhileRunning
    }

    "reach a hundred only when the job says it finished" in {
      val finished = ProgressState.empty(twoPhases).withTotal(Questions, 10).advance(Questions, 3).completed

      finished.percentage shouldBe 100
      finished.current shouldBe None
      // a phase that never ran had nothing to do, and the job is over
      finished.phaseProgress.map(_.status).distinct shouldBe List(PhaseProgress.Done)
    }

    "not be thrown off by a phase that reports more items than it said it had" in {
      val state = ProgressState.empty(List(Questions)).withTotal(Questions, 10).advance(Questions, 25)

      state.percentage shouldBe ProgressState.MaxWhileRunning
    }

    // 'advance' is the one method called per item, sixty thousand times for a big import and now
    // from several threads at once inside the job's compare-and-set, so it works the number out
    // from the item alone instead of summing over the twelve phases twice like the rest do. It has
    // to land on exactly the same number as summing would
    "be the same whether it is worked out item by item or over every phase at once" in {
      val everyPhase = Phase.ofImport
      val steps: List[ProgressState => ProgressState] = List(
        _.withTotal(CreatingQuestions, 60000),
        _.withTotal(CreatingDashboards, 40),
        _.advance(CreatingDashboards, 10),
        _.advance(CreatingQuestions, 1),
        _.advance(CreatingQuestions, 24999),
        _.addTotal(DeletingLeftovers, 7),
        _.advance(DeletingLeftovers, 3),
        _.advance(CreatingQuestions, 35000),
        // past what the phase said it had, which counts for nothing either way
        _.advance(CreatingQuestions, 500),
        _.withTotal(UpdatingDashboards, 40),
        _.advance(UpdatingDashboards, 40)
      )

      // the empty state is left out: nothing has been summed over it yet, and it reports zero
      val walked = steps.foldLeft(List(ProgressState.empty(everyPhase))) {
        (states, step) => step(states.head) :: states
      }.dropRight(1)

      walked.size shouldBe steps.size
      walked.foreach { state =>
        val assumed = (p: Phase) => state.totals.getOrElse(p, 1L)
        val summedTotal = everyPhase.map(p => assumed(p) * p.millisPerItem).sum
        val summedDone =
          everyPhase.map(p => math.min(state.doneOf(p), assumed(p)) * p.millisPerItem).sum

        state.weighedTotal shouldBe summedTotal
        state.weighedDone shouldBe summedDone
        state.percentage shouldBe
          math.min(((summedDone * 100) / summedTotal).toInt, ProgressState.MaxWhileRunning)
      }
    }
  }

  "The phases" should {

    "be listed from the start, so that what is still to come is visible too" in {
      ProgressState.empty(Phase.ofExport).phaseProgress.map(_.phase) shouldBe Phase.ofExport.map(_.id)
      ProgressState.empty(Phase.ofExport).phaseProgress.map(_.status).distinct shouldBe List(PhaseProgress.Pending)
    }

    "leave behind as done the ones an earlier step already went through" in {
      val state = ProgressState.empty(twoPhases).advance(Questions, 1).advance(Tables, 1)

      state.phaseProgress.map(p => p.phase -> p.status).toMap shouldBe
        Map(Questions.id -> PhaseProgress.Done, Tables.id -> PhaseProgress.Running)
    }
  }

  // a collection can reference thousands of tables that are all inactive, and one warning for each
  // of them would make the status response bigger than the export itself
  "The warnings" should {

    "be kept up to a limit, and the rest counted" in {
      val state = (1 to ProgressState.MaxWarnings + 5)
        .foldLeft(ProgressState.empty(twoPhases))((acc, i) => acc.warn(s"warning $i"))

      state.warnings.size shouldBe ProgressState.MaxWarnings
      state.warningsOmitted shouldBe 5
      state.warnings.head shouldBe "warning 1"
    }
  }
}
