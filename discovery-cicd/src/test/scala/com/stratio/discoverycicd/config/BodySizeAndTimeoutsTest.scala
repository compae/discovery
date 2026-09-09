/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.config

import com.stratio.discoverycicd.config.ConfigFacade.ApiClientConfig
import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.duration._

/**
 * The size and the timeouts an import and an export are given by default.
 *
 * Both are numbers that have to agree with what is configured one and two layers earlier, in the
 * nginx of the pod and in the ingress, and that agreement is the whole point of the values: with the
 * three the same, none of them cuts before another and the error does not depend on where it ran
 * out. Nothing in the code can check the other two layers, so what is pinned down here is this end
 * of it: that the shipped defaults are the ones the documentation promises, and that the settings
 * take the same spelling of a size that nginx does.
 */
@RunWith(classOf[JUnitRunner])
class BodySizeAndTimeoutsTest extends WordSpec with Matchers {

  private val shipped = ConfigFactory.load()

  private def apiClientConfig(conf: com.typesafe.config.Config): ApiClientConfig =
    ConfigSource.fromConfig(conf.getConfig("discovery-cicd-api-client")).loadOrThrow[ApiClientConfig]

  "The body of an import" should {

    // 512M and not 512m on purpose: it is the very string NGINX_CLIENT_MAX_BODY_SIZE is given, and
    // both nginx and this read it as the same number of bytes
    "be capped at the same 512M the nginx in front of it is" in {
      shipped.getBytes("akka.http.server.parsing.max-content-length") shouldBe 512L * 1024 * 1024
    }

    "be capped at whatever DISCOVERY_CICD_API_MAX_BODY_SIZE says instead" in {
      val overridden = ConfigFactory
        .parseString("akka.http.server.parsing.max-content-length: 1G")
        .withFallback(shipped)

      overridden.getBytes("akka.http.server.parsing.max-content-length") shouldBe 1024L * 1024 * 1024
    }

    // what an operator would write in CCT, taken from the units nginx accepts. None of them may
    // leave the service refusing every import because the value did not parse
    "be given in any of the spellings nginx takes" in {
      val sizes = Map("512M" -> 512L * 1024 * 1024, "512m" -> 512L * 1024 * 1024,
                      "1G" -> 1024L * 1024 * 1024, "1024K" -> 1024L * 1024)

      sizes foreach { case (written, bytes) =>
        withClue(s"'$written' did not read as $bytes bytes: ") {
          ConfigFactory.parseString(s"size: $written").getBytes("size") shouldBe bytes
        }
      }
    }
  }

  "The timeouts of an export and an import" should {

    // both the same, and the same 300 seconds the ingress and the nginx of the pod are given by
    // default, so that none of the three is the one that cuts and the error does not depend on
    // where it ran out
    "be five minutes each" in {
      val config = apiClientConfig(shipped)

      config.getTimeoutExport shouldBe (5 minutes)
      config.getTimeoutImport shouldBe (5 minutes)
    }

    // this one bounds a single call to metabase, not a whole export, and it stays short on purpose:
    // a metabase that has stopped answering is then reported as such in seconds, instead of holding
    // the export it belongs to for as long as the export timeout allows
    "keep the timeout of a single metabase request short" in {
      apiClientConfig(shipped).timeoutMetabase shouldBe "20s"
    }

    // this one is not an export or an import timeout: it governs the endpoints that answer in
    // milliseconds, such as asking a job how far it has got, so it is meant to stay short
    "leave the request timeout of the server alone" in {
      shipped.getDuration("akka.http.server.request-timeout").getSeconds shouldBe 20L
    }
  }
}
