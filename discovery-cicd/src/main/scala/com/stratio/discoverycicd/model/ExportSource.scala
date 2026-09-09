/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import com.stratio.discoverycicd.utils.TypeAliases.{GroupName, Permission}

/**
 * Everything an export holds except the three lists that grow with the collection.
 *
 * Measured on the export of a collection with 60.000 questions (203 MB): the questions are 81% of
 * the document and the fields another 16%, so what is left here is a handful of MB even for a
 * collection that big. The fields are the one thing in here that is not small, and they stay: they
 * are needed all at once to resolve the ids against the target metabase (see `Importer.getDbMap`).
 */
case class ExportHeader(
  fields: List[Field],
  tables: List[Table],
  databases: List[Database],
  snippets: List[Snippet],
  collection: Collection,
  children: List[Child],
  permissionsPerGroup: Map[GroupName, Permission]
)

/**
 * Where an import reads the data it has to create from.
 *
 * An export of a big collection does not fit in the heap this process is given: 203 MB of json
 * become upwards of a gigabyte and a half once parsed, and the pod gives discovery-cicd about one
 * (`-XX:MaxRAMPercentage=25.0` of the 4 GB the pod asks for, because metabase takes 70% of the
 * same pod). So the questions, the dashboards and the subscriptions are never a `List`: they are
 * asked for one pass at a time and only one of them is in memory while it is being created.
 *
 * The counts and the dependencies between questions are known up front, without keeping any of
 * them, so the status endpoint can still answer '3400 of 60000' from the very first poll and the
 * questions can still be created in dependency order.
 */
trait ExportSource {

  def header: ExportHeader

  def cardCount: Long
  def dashboardCount: Long
  def subscriptionCount: Long

  /**
   * For every question, the questions it uses as a table or as a metric. This is the whole of what
   * the validation and the ordering need of a question besides its id, and for 60.000 of them it is
   * a few MB against the hundreds the questions themselves are.
   */
  def cardDependencies: Map[BigInt, Set[BigInt]]

  /**
   * A pass over the questions. Only one of them is in memory at a time, so the iterator cannot be
   * kept and read later: it is only valid inside `use`, and whatever it is reading from is closed
   * as soon as `use` returns. Every call is a new pass.
   */
  def withCards[A](use: Iterator[Card] => A): A

  def withDashboards[A](use: Iterator[Dashboard] => A): A

  def withSubscriptions[A](use: Iterator[Subscription] => A): A
}

object ExportSource {

  /** The dependencies of one question, which is all the index keeps of it besides its id. */
  def dependenciesOf(card: Card): Set[BigInt] = card.getCardsUsedAsTablesOrMetrics
}

/**
 * An export that is already in memory, for the paths where it cannot be anything else: the
 * synchronous endpoint, the shell, and the tests. Nothing is streamed here, and nothing needs to
 * be: whoever calls these already paid for the whole document.
 */
class InMemoryExportSource(exported: ExportedCollection) extends ExportSource {

  val header: ExportHeader = exported.header

  val cardCount: Long = exported.cards.size.toLong
  val dashboardCount: Long = exported.dashboards.size.toLong
  val subscriptionCount: Long = exported.subscriptions.size.toLong

  lazy val cardDependencies: Map[BigInt, Set[BigInt]] =
    exported.cards.map(card => card.id -> ExportSource.dependenciesOf(card)).toMap

  def withCards[A](use: Iterator[Card] => A): A = use(exported.cards.iterator)
  def withDashboards[A](use: Iterator[Dashboard] => A): A = use(exported.dashboards.iterator)
  def withSubscriptions[A](use: Iterator[Subscription] => A): A = use(exported.subscriptions.iterator)
}

object InMemoryExportSource {
  def apply(exported: ExportedCollection): InMemoryExportSource = new InMemoryExportSource(exported)
}
