/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd

import scopt.OptionParser

import Config.Action._

case class Config(action: Option[Action] = None,
                  collId: BigInt = -1,
                  parentId: Option[BigInt] = None,
                  filePath: String = "",
                  session: String = "",
                  metabaseUrl: String = "http://localhost:3000",
                  allowUnsafeSsl: Boolean = false,
                  timeout: String = "20s") {

  def isValid: Boolean = {
    val validCollId = action match {
      case Some(Export) => collId >= 0
      case Some(Import) => true
      case _ =>
        println("Must provide an action")
        Config.parser.showUsage()
        System.exit(1)
        false
    }
    filePath.nonEmpty && validCollId && session.nonEmpty && metabaseUrl.nonEmpty
  }
}


object Config {

  val parser = new OptionParser[Config]("discovery-cicd") {
    head("Export / Import metabase collections")

    opt[String]("file")
      .text("File to write the exported collection or file to read the collection to import")
      .required()
      .action((path, cfg) => cfg.copy(filePath = path))

    opt[String]("session")
      .text("Id of a metabase session (cookie) for an admin user")
      .required()
      .action((session, cfg) => cfg.copy(session = session))

    opt[String]("metabase-url")
      .text("URL of the metabase instance")
      .optional()
      .action((url, cfg) => cfg.copy(metabaseUrl = url))

    opt[String]("timeout")
      .text("Connection timeout of the metabase api")
      .optional()
      .action((timeout, cfg) => cfg.copy(timeout = timeout))

    opt[Unit]("allow-unsafe-ssl")
      .text("Allow to make requests to Metabase wihtout verifying SSL certificates")
      .action((_, cfg) => cfg.copy(allowUnsafeSsl = true))

    help('h', "help").text("Prints this message")

    cmd("export").text("Export a metabase collection.").action((_, c) => c.copy(action = Some(Export))).children(
      opt[BigInt]("collection-id")
        .text("Id of the metabase collection to be exported")
        .required()
        .validate(id => if (id <= 0) Left("Collection Id must be positive") else Right())
        .action((id, cfg) => cfg.copy(collId = id))
    )

    cmd("import").text("Import a metabase collection.").action((_, c) => c.copy(action = Some(Import))).children(
      opt[BigInt]("parent-id")
        .text("Id of the metabase collection where to put the imported collection into (not provided will put the imported collection in the root colelction)")
        .optional()
        .validate(id => if (id < 0) Left("Collection Id must be positive or zero") else Right())
        .action((id, cfg) => cfg.copy(parentId = if (id == 0) None else Option(id)))
    )

  }

  object Action {
    sealed trait Action
    final case object Import extends Action
    final case object Export extends Action
    final case object Server extends Action
  }
}
