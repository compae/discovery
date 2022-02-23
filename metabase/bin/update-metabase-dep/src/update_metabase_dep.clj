(ns update-metabase-dep
  "Entrypoint for `bin/update-metabase-dep`. Reads the deps.edn file in the `bin/build-drivers`
   module and changes the dependency on metabase (that points to the source code) to make it point
   to the metabase jar so we do not have to compile metabase namespaces. Also, the compilation of
   metabse namespaces fails if we do not have front files (╯°□°)╯︵ ┻━┻:
   https://github.com/Stratio/stratio-metabase/blob/106a835063d892605b6fba47870134466b42ad3a/src/metabase/pulse/render/color.clj#L17)"
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def source-file       "/app/source/bin/build-drivers/deps.edn")
(def destination-file  "/app/source/bin/build-drivers/deps.edn")
(def metabase-jar-path "/target/uberjar/metabase.jar")

(defn- read-deps []
  (with-open [reader (io/reader source-file)]
    (-> reader
        (java.io.PushbackReader.)
        (edn/read)
        (update-in [:deps 'metabase/metabase-core :local/root]
                   str metabase-jar-path))))

(defn -main []
  (spit destination-file (pr-str (read-deps))))
