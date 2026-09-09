/*
 * © 2026 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.utils.ImplicitExtensions.ReportedBody
import com.stratio.discoverycicd.utils.ImplicitExtensions.ReportedBody.{DefaultMaxChars, maxCharsOf}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

/**
 * When metabase answers something we cannot deserialize, the body goes into the message of the
 * exception and into the log, and such a body can be as big as the one we asked for. How much of it is
 * reported is configurable with an environment variable, so what matters is that a value nobody can
 * use never leaves the operator with no diagnostic at all
 */
@RunWith(classOf[JUnitRunner])
class ImplicitExtensionsTest extends WordSpec with Matchers {

  "The limit of how much of an unreadable metabase response is reported" should {

    "be a whole megabyte by default, so that in practice nothing is ever truncated" in {
      DefaultMaxChars shouldBe 1024 * 1024
      ReportedBody.MaxCharsEnvVar shouldBe "DISCOVERY_CICD_API_MAX_REPORTED_BODY_CHARS"
    }

    "be the default when the environment variable is not set, or is set to nothing" in {
      maxCharsOf(None) shouldBe DefaultMaxChars
      maxCharsOf(Some("")) shouldBe DefaultMaxChars
      maxCharsOf(Some("   ")) shouldBe DefaultMaxChars
    }

    "be what the environment variable says, ignoring the blanks around it" in {
      maxCharsOf(Some("4000")) shouldBe 4000
      maxCharsOf(Some(" 4000 ")) shouldBe 4000
    }

    // zero or less would report nothing at all, and anything that is not a plain number of characters
    // is a typo: in both cases the default is more useful than what was asked for
    "fall back to the default when what it says is not a number of characters greater than zero" in {
      maxCharsOf(Some("0")) shouldBe DefaultMaxChars
      maxCharsOf(Some("-1")) shouldBe DefaultMaxChars
      maxCharsOf(Some("1MB")) shouldBe DefaultMaxChars
      maxCharsOf(Some("1048576.0")) shouldBe DefaultMaxChars
      maxCharsOf(Some("99999999999999")) shouldBe DefaultMaxChars
    }
  }
}
