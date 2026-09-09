/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.model

import java.io.{BufferedWriter, OutputStreamWriter}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.GZIPOutputStream

import com.stratio.discoverycicd.exceptions.ValidationDataException
import com.stratio.discoverycicd.progress.PhaseReporter
import org.json4s.{DefaultFormats, Formats, JArray, JObject, JString}
import org.json4s.native.Serialization
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

/**
 * Reading an export out of its file without ever holding it whole.
 *
 * What every one of these pins down is that the streaming reader and the old whole-document parse
 * see exactly the same export: the file source is checked against an [[InMemoryExportSource]] over
 * the very same [[ExportedCollection]], so a difference between the two is a failure and not
 * something a reader of the test has to spot by eye.
 */
@RunWith(classOf[JUnitRunner])
class FileExportSourceTest extends WordSpec with Matchers {

  private implicit val formats: Formats = DefaultFormats

  "Reading an export from its file" should {

    "see the same thing the whole document parsed in memory sees" in withExport(anExport) { source =>
      val inMemory = InMemoryExportSource(anExport)

      source.header shouldBe inMemory.header
      source.cardCount shouldBe inMemory.cardCount
      source.dashboardCount shouldBe inMemory.dashboardCount
      source.subscriptionCount shouldBe inMemory.subscriptionCount
      source.cardDependencies shouldBe inMemory.cardDependencies

      source.withCards(_.toList) shouldBe inMemory.withCards(_.toList)
      source.withDashboards(_.toList) shouldBe inMemory.withDashboards(_.toList)
      source.withSubscriptions(_.toList) shouldBe inMemory.withSubscriptions(_.toList)
    }

    // this is the whole point: the uploaded file is stored gzipped, because the measured export is
    // 203MB and 5.3MB packed, and the pod keeps up to ten of them on an ephemeral filesystem
    "read a gzipped file the same as a plain one" in withExport(anExport, gzipped = true) { source =>
      source.header shouldBe anExport.header
      source.cardCount shouldBe 3
      source.withCards(_.map(_.name).toList) shouldBe List("plain", "on top of plain", "unrelated")
    }

    "give a fresh pass every time, so that the questions can be read once per round" in
      withExport(anExport) { source =>
        source.withCards(_.map(_.id).toList) shouldBe List(BigInt(1), BigInt(2), BigInt(3))
        source.withCards(_.map(_.id).toList) shouldBe List(BigInt(1), BigInt(2), BigInt(3))
      }

    // the sections are walked in whatever order the document has them, and the reader must not
    // depend on the order json4s happens to write them in
    "not care in which order the export writes its sections" in {
      withExportJson(reorderRoot(Serialization.write(anExport))) { source =>
        source.header shouldBe anExport.header
        source.cardCount shouldBe 3
        source.withCards(_.map(_.name).toList) shouldBe List("plain", "on top of plain", "unrelated")
      }
    }

    "note which questions each question is built on top of, and nothing else of them" in
      withExport(anExport) { source =>
        source.cardDependencies shouldBe Map(
          BigInt(1) -> Set.empty[BigInt],
          BigInt(2) -> Set(BigInt(1)),
          BigInt(3) -> Set.empty[BigInt]
        )
      }

    "take a section the export does not carry at all as an empty one" in {
      withExportJson("""{"collection": {"id": 1, "name": "c", "description": "", "parent_id": null}}""") { source =>
        source.cardCount shouldBe 0
        source.dashboardCount shouldBe 0
        source.subscriptionCount shouldBe 0
        source.header.fields shouldBe empty
        source.withCards(_.toList) shouldBe empty
      }
    }

    "ignore a section written by a later version of this tool" in {
      val withExtra =
        """{"collection": {"id": 1, "name": "c", "description": "", "parent_id": null},
          | "somethingNewer": [{"a": 1}, {"b": 2}], "cards": []}""".stripMargin
      withExportJson(withExtra) { source =>
        source.header.collection.name shouldBe "c"
        source.cardCount shouldBe 0
      }
    }
  }

  // a file that cannot be read has to be refused while it is being read, before a single thing has
  // been created in the target metabase, which is what the whole-document parse gave for free
  "An export that cannot be read" should {

    "be refused when it is not a json object at all" in {
      val error = the [ValidationDataException] thrownBy readingOf("""["not", "an", "export"]""")
      error.msg should include("is not a json object")
    }

    "be refused when it carries no collection" in {
      val error = the [ValidationDataException] thrownBy readingOf("""{"cards": []}""")
      error.msg should include("has no 'collection'")
    }

    "be refused when one of its questions cannot be read, and say which section it was in" in {
      val broken =
        """{"collection": {"id": 1, "name": "c", "description": "", "parent_id": null},
          | "cards": [{"id": 1}]}""".stripMargin
      val error = the [ValidationDataException] thrownBy readingOf(broken)
      error.msg should include("An element of 'cards'")
    }

    "be refused when a section that should be a list is not one" in {
      val error = the [ValidationDataException] thrownBy readingOf("""{"cards": {"not": "a list"}}""")
      error.msg should include("'cards' of the uploaded export is not a list")
    }

    // what a client that uploads the '.json.gz' of an export without saying 'Content-Encoding: gzip'
    // leaves on disk: the body was gzipped already and the upload packed it once more
    "say what happened when it was gzipped twice, instead of failing to parse it" in {
      val file = Files.createTempFile("packed-twice", ".json.gz")
      val out = new GZIPOutputStream(Files.newOutputStream(file))
      try out.write(Files.readAllBytes(write(Serialization.write(anExport), gzipped = true)))
      finally out.close()

      val error = the [ValidationDataException] thrownBy FileExportSource.from(file, PhaseReporter.NoOp)
      error.msg should include("Content-Encoding: gzip")
      Files.deleteIfExists(file)
    }
  }

  "The size the reading phase counts in" should {

    // gzip keeps the size of what it packed in the last four bytes of the file, and it is that and
    // not the size of the file that says how much there is to read
    "be what the file holds once unpacked, and not what it weighs" in {
      // big enough that the two answers cannot both round to the same MiB by accident: this is the
      // whole difference between a progress bar that ends at 100% and one that ends at 4000%
      val json = Serialization.write(anExport.copy(cards = List.fill(10000)(anExport.cards).flatten))
      val packed = write(json, gzipped = true)
      val plain = write(json, gzipped = false)

      val bytes = Files.size(plain)
      val unpackedMiB = (bytes + FileExportSource.BytesPerMiB - 1) / FileExportSource.BytesPerMiB
      unpackedMiB should be > 1L
      Files.size(packed) / FileExportSource.BytesPerMiB shouldBe 0L

      FileExportSource.sizeInMiB(packed) shouldBe unpackedMiB
      FileExportSource.sizeInMiB(plain) shouldBe unpackedMiB
    }
  }

  // --- the export these run against -------------------------------------------------------------

  // three questions, one of them built on top of another one, so that the dependency index has
  // something to say
  private def anExport = ExportedCollection(
    cards = List(
      aCard(1, "plain", """{"type": "query", "query": {"source-table": 10}}"""),
      aCard(2, "on top of plain", """{"type": "query", "query": {"source-table": "card__1"}}"""),
      aCard(3, "unrelated", """{"type": "query", "query": {"source-table": 11}}""")
    ),
    dashboards = List.empty,
    fields = List(Field(100, "NAME", "ACCOUNTS", Some("PUBLIC"), "sample")),
    tables = List(Table(10, "ACCOUNTS", Database(1, "sample"), Some("PUBLIC"))),
    databases = List(Database(1, "sample")),
    subscriptions = List.empty,
    snippets = List(Snippet(7, "a snippet", "where 1 = 1", "")),
    collection = Collection(1, "a collection", "", None),
    children = List(Child(Collection(2, "a child", "", Some(1)), Map("All Users" -> "read"))),
    permissionsPerGroup = Map("All Users" -> "write")
  )

  private def aCard(id: BigInt, name: String, datasetQuery: String) = Card(
    id = id,
    name = name,
    parameters = JArray(List.empty),
    parameter_mappings = JArray(List.empty),
    dataset_query = Serialization.read[JObject](datasetQuery),
    `type` = Some("question"),
    display = "table",
    description = None,
    visualization_settings = JObject(),
    enable_embedding = Some(false),
    embedding_params = None,
    collection_id = Some(1),
    dashboard_id = None,
    collection_position = None,
    result_metadata = JArray(List(JObject("name" -> JString("NAME")))),
    cache_ttl = None,
    collection_preview = Some(false),
    based_on_upload = None
  )

  // --- writing it to a file ---------------------------------------------------------------------

  private def withExport(exported: ExportedCollection, gzipped: Boolean = false)
    (check: FileExportSource => Unit): Unit =
    withExportJson(Serialization.write(exported), gzipped)(check)

  private def withExportJson(json: String, gzipped: Boolean = false)
    (check: FileExportSource => Unit): Unit = {
    val file = write(json, gzipped)
    try check(FileExportSource.from(file, PhaseReporter.NoOp))
    finally Files.deleteIfExists(file)
  }

  private def readingOf(json: String): FileExportSource = {
    val file = write(json, gzipped = false)
    try FileExportSource.from(file, PhaseReporter.NoOp)
    finally Files.deleteIfExists(file)
  }

  private def write(json: String, gzipped: Boolean): Path = {
    val file = Files.createTempFile("an-export", if (gzipped) ".json.gz" else ".json")
    val out = Files.newOutputStream(file)
    val writer =
      new BufferedWriter(new OutputStreamWriter(if (gzipped) new GZIPOutputStream(out) else out, UTF_8))
    try writer.write(json) finally writer.close()
    file
  }

  /** The same document with its root fields the other way round. */
  private def reorderRoot(json: String): String = {
    val root = Serialization.read[JObject](json)
    Serialization.write(JObject(root.obj.reverse))
  }
}
