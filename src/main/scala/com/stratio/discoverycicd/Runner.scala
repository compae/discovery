package com.stratio.discoverycicd

import com.stratio.discoverycicd.model.ExportedCollection
import com.stratio.discoverycicd.utils.ApiClient
import java.io.{File, FileWriter, BufferedWriter}
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.{read, write}

object Runner {

  import Config._
  implicit val formats = DefaultFormats

  def main(args: Array[String]): Unit = {

    val initialConf = Config()
    val config = parser.parse(args, Config()).getOrElse{ System.exit(1); initialConf }
    assert(config.isValid, "Provided parameters are not valid")

    val api = new ApiClient(config.metabaseUrl, config.user, config.password)

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
        val importer = new Importer(api)
        importer.importCollection(collection, config.parentId)
        println("...done")
        System.exit(0)

      case None =>
        println("Must provide an action")
        Config.parser.showUsage()
        System.exit(1)
    }
  }
}
