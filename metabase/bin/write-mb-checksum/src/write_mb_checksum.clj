(ns write-mb-checksum
  "Entrypoint for `bin/write-mb-checksum`. Writes the checksum of the metabase source files
   so that when building the drivers we do not need to rebuild metabase"
  (:require [build-drivers.checksum :as checksum]
            [build-drivers.common :as c]))


(def ^String uberjar-checksum-path
  (str c/metabase-uberjar-path  ".md5"))

(defn -main []
  (spit uberjar-checksum-path (checksum/metabase-source-checksum)))
