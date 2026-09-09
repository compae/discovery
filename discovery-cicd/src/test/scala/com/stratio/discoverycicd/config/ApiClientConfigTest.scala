/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.config

import com.stratio.discoverycicd.config.ConfigFacade.ApiClientConfig
import com.stratio.discoverycicd.utils.Concurrently
import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.junit.JUnitRunner
import pureconfig.ConfigSource
import pureconfig.generic.auto._

/**
 * How many questions an import creates at a time is the one setting that decides how long it takes,
 * and it is meant to be turned by whoever is watching an import be slow against their own metabase.
 * So it has to survive being absent, and it has to refuse a value that would make the import do
 * nothing at all rather than obey it.
 */
@RunWith(classOf[JUnitRunner])
class ApiClientConfigTest extends WordSpec with Matchers {

  private val WithoutIt =
    """metabase-url: "http://localhost:3000"
      |allow-unsafe-ssl: true
      |timeout-metabase: 20s
      |timeout-export: 60s
      |timeout-import: 200s
      |""".stripMargin

  private def load(conf: String): ApiClientConfig =
    ConfigSource.fromConfig(ConfigFactory.parseString(conf)).loadOrThrow[ApiClientConfig]

  "How many writes an import makes at a time" should {

    "be the measured default when the setting is not there at all" in {
      load(WithoutIt).getParallelWrites shouldBe Concurrently.WriteRequests
    }

    "be what was asked for" in {
      load(WithoutIt + "parallel-writes: 8").getParallelWrites shouldBe 8
    }

    // the old sequential import, and a legitimate thing to ask for against a metabase that cannot
    // take anything else
    "be allowed to be one, which is one question after another" in {
      load(WithoutIt + "parallel-writes: 1").getParallelWrites shouldBe 1
    }

    "never be less than one, which would create nothing at all" in {
      load(WithoutIt + "parallel-writes: 0").getParallelWrites shouldBe 1
      load(WithoutIt + "parallel-writes: -4").getParallelWrites shouldBe 1
    }

    // metabase saturates at four reads at a time and shares the two cores of the pod with us, so a
    // number in the hundreds is a typo and obeying it would only make the import slower
    "be capped instead of taken literally when it is absurd" in {
      load(WithoutIt + "parallel-writes: 5000").getParallelWrites shouldBe ConfigFacade.MaxParallelWrites
    }
  }

  "The application.conf shipped with the service" should {

    "carry a parallelism the importer can use without anything being set" in {
      ConfigFacade.apiClientConfig.getParallelWrites shouldBe Concurrently.WriteRequests
    }
  }
}
