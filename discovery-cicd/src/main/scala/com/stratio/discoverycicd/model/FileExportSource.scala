/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.model

import java.io.{BufferedInputStream, FilterInputStream, InputStream}
import java.nio.file.{Files, Path}
import java.util.zip.GZIPInputStream

import com.fasterxml.jackson.core.{JsonToken, JsonParser => Jackson}
import com.stratio.discoverycicd.exceptions.ValidationDataException
import com.stratio.discoverycicd.progress.PhaseReporter
import com.stratio.discoverycicd.utils.Logging
import com.stratio.discoverycicd.utils.TypeAliases.{GroupName, Permission}
import org.json4s.jackson.JsonMethods
import org.json4s.{Extraction, Formats, JValue}

import scala.collection.mutable

/**
 * An export read straight from the file it was uploaded as, one question at a time.
 *
 * The whole point is that nothing here is ever a `List[Card]`. The file is read once to learn what
 * it holds (see [[FileExportSource.from]]) and then once more per pass the import needs, and a pass
 * only ever has one question, one dashboard or one subscription in memory. The sections a pass does
 * not care about are skipped with `skipChildren`, which walks the tokens without building anything:
 * a pass over the 60.000 questions of the measured export steps over its 32 MB of fields for free.
 *
 * Re-reading the file costs a second or two of local disk against an import that spends tens of
 * minutes making one http request per question, so the passes are not worth avoiding.
 */
class FileExportSource private (
  file: Path,
  val header: ExportHeader,
  val cardCount: Long,
  val dashboardCount: Long,
  val subscriptionCount: Long,
  val cardDependencies: Map[BigInt, Set[BigInt]]
)(implicit formats: Formats) extends ExportSource {

  import FileExportSource._

  def withCards[A](use: Iterator[Card] => A): A = pass[Card, A](file, CardsField)(use)

  def withDashboards[A](use: Iterator[Dashboard] => A): A = pass[Dashboard, A](file, DashboardsField)(use)

  def withSubscriptions[A](use: Iterator[Subscription] => A): A =
    pass[Subscription, A](file, SubscriptionsField)(use)
}

object FileExportSource extends Logging {

  val CardsField = "cards"
  val DashboardsField = "dashboards"
  val SubscriptionsField = "subscriptions"
  val FieldsField = "fields"
  val TablesField = "tables"
  val DatabasesField = "databases"
  val SnippetsField = "snippets"
  val CollectionField = "collection"
  val ChildrenField = "children"
  val PermissionsField = "permissionsPerGroup"

  val BytesPerMiB = 1024L * 1024L
  private val BufferSize = 64 * 1024

  /** The mapper json4s-jackson keeps, which already knows how to give a `JValue` back. */
  private def mapper = JsonMethods.mapper

  /**
   * Reads the file once: takes everything that is not a question, a dashboard or a subscription,
   * counts those three, and notes which questions each question depends on.
   *
   * Every element is parsed and turned into its model here even when only the count is kept, so
   * that a file that cannot be read is refused before a single thing has been created in the target
   * metabase. That is what the old whole-document parse gave for free and it is worth the second
   * pass over the questions: it is cpu against a heap that could not hold them anyway.
   */
  def from(file: Path, phase: PhaseReporter)(implicit formats: Formats): FileExportSource = {
    var fields = List.empty[Field]
    var tables = List.empty[Table]
    var databases = List.empty[Database]
    var snippets = List.empty[Snippet]
    var collection = Option.empty[Collection]
    var children = List.empty[Child]
    var permissions = Map.empty[GroupName, Permission]
    var cards = 0L
    var dashboards = 0L
    var subscriptions = 0L
    val dependencies = mutable.Map.empty[BigInt, Set[BigInt]]

    val counting = new CountingInputStream(open(file), phase)
    val parser = mapper.getFactory.createParser(counting)
    try {
      openObject(parser)
      eachField(parser) {
        case CardsField =>
          cards = eachElement(parser) { json =>
            val card = extractOne[Card](json, CardsField)
            dependencies += card.id -> ExportSource.dependenciesOf(card)
          }

        // parsed and dropped: only the count is kept, but a dashboard that cannot be read has to
        // fail now and not after the questions have been created
        case DashboardsField =>
          dashboards = eachElement(parser)(extractOne[Dashboard](_, DashboardsField))
        case SubscriptionsField =>
          subscriptions = eachElement(parser)(extractOne[Subscription](_, SubscriptionsField))

        // element by element as well, so that the 32 MB of fields of a big export are never an ast
        // of their own on top of the list they become
        case FieldsField => fields = collectElements[Field](parser, FieldsField)
        case TablesField => tables = collectElements[Table](parser, TablesField)
        case DatabasesField => databases = collectElements[Database](parser, DatabasesField)
        case SnippetsField => snippets = collectElements[Snippet](parser, SnippetsField)
        case ChildrenField => children = collectElements[Child](parser, ChildrenField)

        // small enough to take whole
        case CollectionField =>
          collection = Some(extractOne[Collection](valueOf(parser), CollectionField))
        case PermissionsField =>
          permissions = extractOne[Map[GroupName, Permission]](valueOf(parser), PermissionsField)

        // an export written by a later version of this tool: skipped without building anything,
        // the same way the whole-document parse used to ignore what it had no field for
        case other =>
          log.debug(s"Ignoring the unknown field '$other' of the uploaded export")
          parser.skipChildren()
      }
    } finally {
      parser.close()
      counting.finish()
    }

    log.info(s"Read the export to import: ${cards} question(s), ${dashboards} dashboard(s), " +
      s"${subscriptions} subscription(s), ${tables.size} table(s) and ${fields.size} field(s)")

    new FileExportSource(
      file,
      ExportHeader(
        fields, tables, databases, snippets,
        collection getOrElse (throw ValidationDataException(
          s"The uploaded export has no '$CollectionField': there is nothing to import from it"
        )),
        children, permissions
      ),
      cards, dashboards, subscriptions, dependencies.toMap
    )
  }

  /**
   * How many bytes of the file the `reading` phase has to get through, in MiB.
   *
   * Rounded up, because the counter reports the tail of the file as one more MiB: rounded down, a
   * file of nine and a half would be left saying 'reading: 10 of 9'.
   */
  def sizeInMiB(file: Path): Long = {
    val bytes = uncompressedSize(file)
    math.max(1L, (bytes + BytesPerMiB - 1) / BytesPerMiB)
  }

  // --- the passes -----------------------------------------------------------------------------

  /**
   * One pass over one of the three lists. The iterator is only valid while `use` runs: it reads
   * from the file, and the file is closed as soon as `use` gives back its answer.
   */
  private def pass[A: Manifest, R](file: Path, field: String)(use: Iterator[A] => R)
    (implicit formats: Formats): R = {
    val parser = mapper.getFactory.createParser(open(file))
    try {
      openObject(parser)
      if (findArray(parser, field)) use(elementsOf[A](parser, field))
      // a section the export does not carry at all is an empty one, which is what the whole-document
      // parse did with it as well
      else use(Iterator.empty: Iterator[A])
    } finally parser.close()
  }

  /** Pulls one element out of the array the parser is inside of, and not a token more. */
  private def elementsOf[A: Manifest](parser: Jackson, field: String)
    (implicit formats: Formats): Iterator[A] =
    new Iterator[A] {
      private var checked = false
      private var ended = false

      private def peek(): Unit =
        if (!checked && !ended) {
          ended = parser.nextToken() == JsonToken.END_ARRAY
          checked = true
        }

      def hasNext: Boolean = { peek(); !ended }

      def next(): A = {
        peek()
        if (ended) throw new NoSuchElementException(s"There are no more elements in '$field'")
        checked = false
        extractOne[A](valueOf(parser), field)
      }
    }

  // --- walking the json -----------------------------------------------------------------------

  private def openObject(parser: Jackson): Unit =
    if (parser.nextToken() != JsonToken.START_OBJECT)
      throw ValidationDataException(
        "The uploaded export is not a json object. An import expects the document an export gives, " +
          "which starts with '{'"
      )

  /** Every field of the object the parser has just opened, with the parser left on its value. */
  private def eachField(parser: Jackson)(onField: String => Unit): Unit =
    while (parser.nextToken() != JsonToken.END_OBJECT) {
      val name = parser.currentName()
      parser.nextToken()
      onField(name)
    }

  /**
   * Leaves the parser inside the array of `field`, skipping whatever comes before it without
   * building any of it. False when the export has no such field, or spells it as a null.
   */
  private def findArray(parser: Jackson, field: String): Boolean = {
    var found = false
    var done = false
    while (!done && parser.nextToken() != JsonToken.END_OBJECT) {
      val name = parser.currentName()
      val token = parser.nextToken()
      if (name != field) parser.skipChildren()
      else {
        done = true
        found = token == JsonToken.START_ARRAY
        if (!found && token != JsonToken.VALUE_NULL) throw notAnArray(field)
      }
    }
    found
  }

  /** Runs `onElement` on every element of the array the parser is on, keeping none of them. */
  private def eachElement(parser: Jackson)(onElement: JValue => Unit): Long = {
    if (parser.currentToken == JsonToken.VALUE_NULL) return 0L
    if (parser.currentToken != JsonToken.START_ARRAY) throw notAnArray(parser.currentName())
    var count = 0L
    while (parser.nextToken() != JsonToken.END_ARRAY) {
      onElement(valueOf(parser))
      count += 1
    }
    count
  }

  /** The same, keeping them: for the sections that are small enough to hold. */
  private def collectElements[A: Manifest](parser: Jackson, field: String)
    (implicit formats: Formats): List[A] = {
    val collected = List.newBuilder[A]
    eachElement(parser)(json => collected += extractOne[A](json, field))
    collected.result()
  }

  /** The value the parser is on, as a `JValue`, and nothing else of the document. */
  private def valueOf(parser: Jackson): JValue = mapper.readValue(parser, classOf[JValue])

  private def extractOne[A: Manifest](json: JValue, field: String)(implicit formats: Formats): A =
    try Extraction.extract[A](json)
    catch {
      case e: Exception =>
        throw ValidationDataException(
          s"An element of '$field' of the uploaded export could not be read: ${e.getMessage}"
        )
    }

  private def notAnArray(field: String) =
    ValidationDataException(s"The '$field' of the uploaded export is not a list")

  // --- the file -------------------------------------------------------------------------------

  /**
   * The uploaded export, unpacked if it is gzipped.
   *
   * It is stored gzipped (see `AsyncRoutes.startImport`), which is what keeps ten of them on the
   * ephemeral disk of the pod: the measured export is 203 MB and 5.3 MB packed. A plain file still
   * reads, so an upload left over from an earlier version, and the files the tests write, work too.
   */
  private def open(file: Path): InputStream = {
    val raw = new BufferedInputStream(Files.newInputStream(file), BufferSize)
    if (!isGzipped(raw)) raw
    else {
      val unpacked = new BufferedInputStream(new GZIPInputStream(raw, BufferSize), BufferSize)
      // Two layers means the body was already gzipped when it arrived and it was packed again on the
      // way in, which is what happens to a client that uploads the '.json.gz' of an export without
      // saying so. Told plainly here, because the alternative is a parse error that says the export
      // is not a json object and gives no hint of why
      if (!isGzipped(unpacked)) unpacked
      else throw ValidationDataException(
        "The uploaded export is gzipped but the request did not say so, so it was stored packed " +
          "twice and cannot be read. Upload it with the header 'Content-Encoding: gzip', or unpack " +
          "it first"
      )
    }
  }

  private def isGzipped(raw: BufferedInputStream): Boolean = {
    raw.mark(2)
    val first = raw.read()
    val second = raw.read()
    raw.reset()
    first == 0x1f && second == 0x8b
  }

  /**
   * How much there is to read, which is not what the file weighs when it is gzipped.
   *
   * gzip writes the size of what it packed in the last four bytes of the file (RFC 1952, ISIZE),
   * and that is exact below 4 GiB, which an export always is. Should it ever say something absurd,
   * the size of the file itself is used: this only feeds a progress bar.
   */
  private def uncompressedSize(file: Path): Long = {
    val packed = Files.size(file)
    val stream = new BufferedInputStream(Files.newInputStream(file), BufferSize)
    val gzipped = try isGzipped(stream) finally stream.close()
    if (!gzipped) packed
    else {
      val trailer = new Array[Byte](4)
      val channel = Files.newByteChannel(file)
      try {
        channel.position(packed - 4)
        channel.read(java.nio.ByteBuffer.wrap(trailer))
      } finally channel.close()
      val size =
        (trailer(0) & 0xffL) | ((trailer(1) & 0xffL) << 8) |
          ((trailer(2) & 0xffL) << 16) | ((trailer(3) & 0xffL) << 24)
      if (size > 0) size else packed
    }
  }

  /**
   * Counts what has been read so that parsing a two hundred megabyte upload is not a phase that
   * sits at zero for a minute, and gives up as soon as the job is cancelled.
   *
   * Only the thread doing the parsing reads from it, so the plain var is safe.
   */
  private class CountingInputStream(underlying: InputStream, phase: PhaseReporter)
    extends FilterInputStream(underlying) {

    private var pending = 0L

    override def read(): Int = {
      val b = super.read()
      if (b >= 0) counted(1)
      b
    }

    override def read(buffer: Array[Byte], offset: Int, length: Int): Int = {
      val read = super.read(buffer, offset, length)
      if (read > 0) counted(read)
      read
    }

    private def counted(bytes: Int): Unit = {
      pending += bytes
      if (pending >= BytesPerMiB) {
        phase.checkCancelled()
        phase.advance(pending / BytesPerMiB)
        pending = pending % BytesPerMiB
      }
    }

    /** Counts the tail of the file, which is less than the MiB this counts in. */
    def finish(): Unit = {
      if (pending > 0) phase.advance(1)
      pending = 0
    }
  }
}
