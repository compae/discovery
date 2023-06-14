/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.discoverycicd.utils

import com.stratio.discoverycicd.model.LinkType
import org.json4s._
import org.json4s.native.JsonMethods._
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class MBJsonTest extends WordSpec with Matchers {

  "Snippet helpers" should {

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
      MBJson.getSnippetIds(datasetQueryAsJson) should contain theSameElementsAs Seq(1, 2)
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

  "Link helpers" should {

    val visualizationSettings = parse(
      """
        |{
        |  "visualization_settings": {
        |    "column_settings": {
        |      "[\"ref\",[\"field\",6,null]]": {
        |        "click_behavior": {
        |          "type": "link",
        |          "linkType": "dashboard",
        |          "parameterMapping": {},
        |          "targetId": 11,
        |          "linkTextTemplate": "link absurdo ({{SUBTOTAL}})"
        |        }
        |      },
        |      "[\"ref\",[\"field\",3,null]]": {
        |        "click_behavior": {
        |          "targetId": 81,
        |          "parameterMapping": {
        |            "[\"dimension\",[\"field\",22,null]]": {
        |              "source": {"type": "column", "id": "USER_ID", "name": "USER_ID"},
        |              "target": {"type": "dimension", "id": "[\"dimension\",[\"field\",22,null]]", "dimension": ["dimension", ["field", 22, null]]},
        |              "id": "[\"dimension\",[\"field\",22,null]]"
        |            }
        |          },
        |          "linkType": "question",
        |          "type": "link",
        |          "linkTextTemplate": "Details of user {{USER_ID}}"
        |        }
        |      },
        |      "[\"ref\",[\"field\",9,null]]": {}
        |    }
        |  }
        |}
        |""".stripMargin
    )

    "extract fieldIs from visualization_settings (keys and values with an array-like string like [...,\"field\",...])" in {
      val expected = Set(6, 3, 22, 9).map(BigInt.apply)
      MBJson.getFieldIds(visualizationSettings) shouldBe expected
    }

    "extract cardIds from visualization_settings of dashcards with clickbehavior linking other questions" in {
      val expected = Set(81).map(BigInt.apply)
      MBJson.getItemIdsInLinks(LinkType.Question)(visualizationSettings) shouldBe expected
    }

    "extract dashboardIds from visualization_settings of dashcards with clickbehavior linking other dashboards" in {
      val expected = Set(11).map(BigInt.apply)
      MBJson.getItemIdsInLinks(LinkType.Dashboard)(visualizationSettings) shouldBe expected
    }

    "replace dashboard, question, and field ids in dashcard visualization_settings" +
      " with columns with clickbehavior configured to link to other dashboard" in {

      val fieldMap     = Map(6 -> 60, 3 -> 30, 22 -> 220, 9 -> 90) map { case (k, v) => BigInt(k) -> BigInt(v)}
      val cardMap      = Map(81 -> 810)                            map { case (k, v) => BigInt(k) -> BigInt(v)}
      val dashboardMap = Map(11 -> 110)                            map { case (k, v) => BigInt(k) -> BigInt(v)}

      val expected = parse(
        """
          |{
          |  "visualization_settings": {
          |    "column_settings": {
          |      "[\"ref\",[\"field\",60,null]]": {
          |        "click_behavior": {
          |          "type": "link",
          |          "linkType": "dashboard",
          |          "parameterMapping": {},
          |          "targetId": 110,
          |          "linkTextTemplate": "link absurdo ({{SUBTOTAL}})"
          |        }
          |      },
          |      "[\"ref\",[\"field\",30,null]]": {
          |        "click_behavior": {
          |          "targetId": 810,
          |          "parameterMapping": {
          |            "[\"dimension\",[\"field\",220,null]]": {
          |              "source": {"type": "column", "id": "USER_ID", "name": "USER_ID"},
          |              "target": {"type": "dimension", "id": "[\"dimension\",[\"field\",220,null]]", "dimension": ["dimension", ["field", 220, null]]},
          |              "id": "[\"dimension\",[\"field\",220,null]]"
          |            }
          |          },
          |          "linkType": "question",
          |          "type": "link",
          |          "linkTextTemplate": "Details of user {{USER_ID}}"
          |        }
          |      },
          |      "[\"ref\",[\"field\",90,null]]": {}
          |    }
          |  }
          |}
          |""".stripMargin
      )
      val result = (MBJson.replaceItemIdsInDashCardLinks(LinkType.Dashboard)(_, dashboardMap))
        .andThen(MBJson.replaceItemIdsInDashCardLinks(LinkType.Question)(_, cardMap))
        .andThen(MBJson.replaceFieldIds(_, fieldMap))
        .apply(visualizationSettings)

      result shouldBe expected
    }
  }
}

