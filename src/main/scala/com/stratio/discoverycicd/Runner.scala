package com.stratio.discoverycicd

import com.stratio.discoverycicd.Config.Action
import com.stratio.discoverycicd.runners.{ServerRunner, ShellRunner}

object Runner {

  def main(args: Array[String]): Unit = args.headOption match {
    case Some(value) if value == Action.Server.toString.toLowerCase =>
      ServerRunner.run(args)
    case _ =>
      ShellRunner.run(args)
  }
}
