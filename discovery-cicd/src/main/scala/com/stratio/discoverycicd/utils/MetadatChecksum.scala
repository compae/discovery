/*
 * © 2020 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.discoverycicd.utils

import java.security.MessageDigest
import java.util.Base64
import org.json4s.native.Serialization.write
import org.json4s.DefaultFormats
import org.json4s.JsonAST.{ JDouble, JInt, JObject, JValue, JField }

/**  Calculates the checksum of the result_metadata in cards in the same
  *  way that metabase does so when creating cards we won't force metabase
  *  to run the query to obtaind the result_metadata. Metabase way:
  *
  *      (defn- prepare-for-serialization
  *        "Return version of `node` that will hash consistently"
  *        [node]
  *        (cond
  *          (integer? node)
  *          (double node)
  *
  *          (map? node)
  *          (into (sorted-map) node)
  *
  *          (set? node)
  *          (into (sorted-set) node)
  *
  *          :else
  *          node))
  *
  *      (defn- serialize-metadata-for-hashing
  *        [metadata]
  *        (->> metadata
  *             (walk/postwalk prepare-for-serialization)
  *             json/generate-string))
  *
  *      (defn- metadata-checksum
  *        [metadata]
  *        (when metadata
  *          (-> metadata
  *              serialize-metadata-for-hashing
  *              hash/md5
  *              codec/base64-encode
  *              encryption/maybe-encrypt)))
  */
object MetadataChecksum {
  implicit val formats = DefaultFormats

  def apply(metadata: JValue): String = (serializeMetadataForHashing andThen md5 andThen base64Encode)(metadata)

  private val serializeMetadataForHashing = (metadata: JValue) => write(metadata.map(prepareForSerialization))

  private def prepareForSerialization(node: JValue) = node match {
    case JInt(x) => JDouble(x.toDouble)
    case JObject(list) => JObject(list sortBy { case JField(k, _) => k })
    case other =>  other
  }

  private def base64Encode(byteArray: Array[Byte]) = Base64.getEncoder.encodeToString(byteArray)

  private def md5(s: String) = MessageDigest.getInstance("MD5").digest(s.getBytes)

}
