/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.utils

import org.json4s._
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class MBJsonTest extends WordSpec with Matchers {

  "MBJsonHeler" should {

    "extract snippets id from a dataset_query" in {

      val datasetQueryAsJson = parse(
        """
          |{
          |	"dataset_query": {
          |		"type": "native",
          |		"native": {
          |			"query": "select * FROM people WHERE {{snippet: alwaystrue}} {{snippet: limitresults}}",
          |			"template-tags": {
          |				"snippet: limitresults": {
          |					"id": "d6b8c3ea-1cc4-8d86-41ce-243ccc73c038",
          |					"name": "snippet: limitresults",
          |					"display-name": "Snippet: limitresults",
          |					"type": "snippet",
          |					"snippet-name": "limitresults",
          |					"snippet-id": 1
          |				},
          |				"snippet: alwaystrue": {
          |					"id": "b95727fd-ecf5-64bd-8244-d71238d4b6f6",
          |					"name": "snippet: alwaystrue",
          |					"display-name": "Snippet: alwaystrue",
          |					"type": "snippet",
          |					"snippet-name": "alwaystrue",
          |					"snippet-id": 2
          |				}
          |			}
          |		},
          |		"database": 1
          | }
          |}
          |""".stripMargin
      )
      MBJson.getSnippetIds(datasetQueryAsJson) should contain theSameElementsAs Seq(1,2)
    }

    "replace the snippets-id in a dataset_query" in {

      val replacements = Map(BigInt(1) -> BigInt(3), BigInt(2) -> BigInt(6))

      val datasetQueryAsJson = parse(
        """
          |{
          |	"dataset_query": {
          |		"type": "native",
          |		"native": {
          |			"query": "select * FROM people WHERE {{snippet: alwaystrue}} {{snippet: limitresults}}",
          |			"template-tags": {
          |				"snippet: limitresults": {
          |					"id": "d6b8c3ea-1cc4-8d86-41ce-243ccc73c038",
          |					"name": "snippet: limitresults",
          |					"display-name": "Snippet: limitresults",
          |					"type": "snippet",
          |					"snippet-name": "limitresults",
          |					"snippet-id": 1
          |				},
          |				"snippet: alwaystrue": {
          |					"id": "b95727fd-ecf5-64bd-8244-d71238d4b6f6",
          |					"name": "snippet: alwaystrue",
          |					"display-name": "Snippet: alwaystrue",
          |					"type": "snippet",
          |					"snippet-name": "alwaystrue",
          |					"snippet-id": 2
          |				}
          |			}
          |		},
          |		"database": 1
          | }
          |}
          |""".stripMargin
      )

      val expectedNewDatasetQueryJson = parse(
        """
          |{
          |	"dataset_query": {
          |		"type": "native",
          |		"native": {
          |			"query": "select * FROM people WHERE {{snippet: alwaystrue}} {{snippet: limitresults}}",
          |			"template-tags": {
          |				"snippet: limitresults": {
          |					"id": "d6b8c3ea-1cc4-8d86-41ce-243ccc73c038",
          |					"name": "snippet: limitresults",
          |					"display-name": "Snippet: limitresults",
          |					"type": "snippet",
          |					"snippet-name": "limitresults",
          |					"snippet-id": 3
          |				},
          |				"snippet: alwaystrue": {
          |					"id": "b95727fd-ecf5-64bd-8244-d71238d4b6f6",
          |					"name": "snippet: alwaystrue",
          |					"display-name": "Snippet: alwaystrue",
          |					"type": "snippet",
          |					"snippet-name": "alwaystrue",
          |					"snippet-id": 6
          |				}
          |			}
          |		},
          |		"database": 1
          | }
          |}
          |""".stripMargin
      )

      MBJson.replaceSnippets(datasetQueryAsJson, replacements) shouldBe expectedNewDatasetQueryJson
    }

  }

}
