package com.stratio.discoverycicd.runners

import java.io.{BufferedWriter, File, FileWriter}

import com.stratio.discoverycicd.Config.{Action, parser}
import com.stratio.discoverycicd.{Config, Exporter, Importer}
import com.stratio.discoverycicd.model.ExportedCollection
import com.stratio.discoverycicd.utils.ApiClient
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.{read, write}

object ShellRunner {

  implicit val formats = DefaultFormats

  def run(args: Array[String]): Unit = {
    val initialConf = Config()
    val config = parser.parse(args, Config()).getOrElse{ System.exit(1); initialConf }
    assert(config.isValid, "Provided parameters are not valid")

    val api = new ApiClient(config.metabaseUrl, Right((config.user, config.password)), config.allowUnsafeSsl)

    config.action match {
      case Some(Action.Export) =>
        println("Exporting...")
        val exporter = new Exporter(api)
        val exportData = exporter.exportCollection(config.collId)
        val file = new File(config.filePath)
        val bw = new BufferedWriter(new FileWriter(file))
        write(exportData, bw)
        bw.close()
        println(s"...done. Export file written in ${config.filePath}")
        System.exit(0)

      case Some(Action.Import) =>
        println("Importing...")
        val source = scala.io.Source.fromFile(config.filePath)
        val collection = read[ExportedCollection](source.reader())
        val importer = new Importer(api, config.strictTableNames)
        importer.importCollection(collection, config.parentId)
        println("...done")
        System.exit(0)

      case _ =>
        println("Must provide an action")
        Config.parser.showUsage()
        System.exit(1)
    }
  }
}
