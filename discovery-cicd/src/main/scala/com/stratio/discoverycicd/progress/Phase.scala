/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.progress

import enumeratum._

/**
 * What one item of each kind of phase costs, in milliseconds.
 *
 * These live outside [[Phase]] on purpose. The case objects of an enumeratum enum are forced while
 * the object that holds them is still being initialized, so a constant declared next to them is
 * still zero when they read it, and every phase silently ends up weighing nothing. Out here there is
 * no cycle to fall into.
 *
 * The numbers come from the same measurements that decided how many requests go at a time (see
 * [[com.stratio.discoverycicd.utils.Concurrently]]): 'GET card/:id' takes ~352ms and 4 of them go at
 * once, so ~88ms each; 'GET table/:id/query_metadata' and 'GET field/:id' take ~134ms with 8 at
 * once, so ~17ms each. They are an estimate for a progress bar and nothing else, and they should be
 * revisited whenever those constants are.
 */
private[progress] object PhaseCost {

  /** A phase that makes a single request and is done. It does not matter much which number this is,
    * as long as it is small next to the ones that repeat thousands of times */
  val OneRequest = 20

  /** 'GET card/:id', and 'GET dashboard/:id', which costs metabase about the same */
  val Card = 88

  /** 'GET table/:id/query_metadata' and 'GET field/:id' */
  val Metadata = 17

  /** Serializing and compressing the export into its file */
  val Writing = 40

  /** Parsing one MiB of an uploaded export */
  val ReadMiB = 300
}

/**
 * One step of an export or of an import, as the one asking about its progress sees it.
 *
 * `millisPerItem` is only used to weigh the phases against each other when working out a percentage:
 * a collection with 60.000 questions and 60.000 tables is not half done when the questions are,
 * because a question costs about five times what a table costs.
 *
 * @param id            how the phase is named in the json of the status endpoint
 * @param millisPerItem what one item of this phase costs, see [[PhaseCost]]
 */
sealed abstract class Phase(val id: String, val millisPerItem: Int) extends EnumEntry {
  override def toString: String = id
}

object Phase extends Enum[Phase] {

  import PhaseCost._

  // --- export ------------------------------------------------------------------------------

  case object Collection      extends Phase("collection", OneRequest)
  case object CollectionItems extends Phase("collection-items", OneRequest)
  case object Questions       extends Phase("questions", Card)
  case object Dashboards      extends Phase("dashboards", Card)
  case object SubCollections  extends Phase("subcollections", OneRequest)
  case object Subscriptions   extends Phase("subscriptions", OneRequest)
  case object Snippets        extends Phase("snippets", OneRequest)
  case object Databases       extends Phase("databases", OneRequest)
  case object Tables          extends Phase("tables", Metadata)
  case object Fields          extends Phase("fields", Metadata)
  case object Permissions     extends Phase("permissions", OneRequest)
  case object WritingResult   extends Phase("writing-result", Writing)

  // --- import ------------------------------------------------------------------------------

  // The only phase whose items are not requests: it counts MiB of the file being read, because
  // counting bytes would give this one phase a weight of two hundred million against the tens of
  // thousands of every other one
  case object Reading extends Phase("reading", ReadMiB)

  case object ResolvingTables       extends Phase("resolving-tables", OneRequest)
  case object ResolvingFields       extends Phase("resolving-fields", Metadata)
  case object Validating            extends Phase("validating", OneRequest)
  case object CreatingCollections   extends Phase("creating-collections", OneRequest)
  case object CreatingSnippets      extends Phase("creating-snippets", OneRequest)
  case object CreatingDashboards    extends Phase("creating-dashboards", Card)
  case object CreatingQuestions     extends Phase("creating-questions", Card)
  case object UpdatingDashboards    extends Phase("updating-dashboards", Card)
  case object DeletingLeftovers     extends Phase("deleting-leftovers", OneRequest)
  case object CreatingSubscriptions extends Phase("creating-subscriptions", OneRequest)
  case object UpdatingPermissions   extends Phase("updating-permissions", OneRequest)

  /**
   * Every phase of an export and of an import, in the order they run. The status endpoint reports
   * all of them from the start, the ones that have not run yet as pending, so that whoever polls it
   * knows what is still to come and not only where it is now.
   *
   * Lazy, and declared after every case object: read while this object is still being initialized,
   * an eager list of them can come out full of nulls
   */
  lazy val ofExport: List[Phase] = List(
    Collection, CollectionItems, Questions, Dashboards, SubCollections, Subscriptions, Snippets,
    Databases, Tables, Fields, Permissions, WritingResult
  )

  lazy val ofImport: List[Phase] = List(
    Reading, ResolvingTables, ResolvingFields, Validating, CreatingCollections, CreatingSnippets,
    CreatingDashboards, CreatingQuestions, UpdatingDashboards, DeletingLeftovers,
    CreatingSubscriptions, UpdatingPermissions
  )

  // last, so that forcing the case objects cannot happen before anything they need is in place
  val values = findValues
}
