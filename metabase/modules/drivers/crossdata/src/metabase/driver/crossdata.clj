(ns metabase.driver.crossdata
  "Driver for Stratio Crossdata databases. Uses the official Stratio JDBC driver under the hood."
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]
   [metabase.config.core :as config]
   [metabase.driver :as driver]
   [metabase.driver-api.core :as driver-api]
   [metabase.driver.hive-like :as hive-like]
   [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
   [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
   [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
   [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
   [metabase.driver.sql.query-processor :as sql.qp]
   [metabase.driver.sql.util :as sql.u]
   [metabase.stratio.middleware :as st.mw]
   [metabase.sync.util :as sync-util]
   [metabase.util.i18n :refer [trs]]
   [metabase.util.log :as log]))

(set! *warn-on-reflection* true)

(driver/register! :crossdata :parent :sparksql)

;; ******************  hive-like overrides  ************************************************

;; STRATIO: we implement this differently from hive-like
(def ^:private database-type->base-type
  (sql-jdbc.sync/pattern-based-database-type->base-type
   [[#"BIGINT"    :type/BigInteger]
    [#"BIG INT"   :type/BigInteger]
    [#"INT"       :type/Integer]
    [#"TINYINT"   :type/Integer]
    [#"SMALLINT"  :type/Integer]
    [#"CHAR"      :type/Text]
    [#"TEXT"      :type/Text]
    [#"CLOB"      :type/Text]
    [#"BLOB"      :type/*]
    [#"REAL"      :type/Float]
    [#"DOUB"      :type/Float]
    [#"FLOA"      :type/Float]
    [#"NUMERIC"   :type/Float]
    [#"DECIMAL"   :type/Decimal]
    [#"BOOLEAN"   :type/Boolean]
    [#"DATETIME"  :type/DateTime]
    [#"TIMESTAMP" :type/DateTime]
    [#"DATE"      :type/Date]
    [#"TIME"      :type/Time]
    [#"BINARY"    :type/*]
    [#"ARRAY"     :type/Array]
    [#"MAP"       :type/Dictionary]
    [#"STRUCT"    :type/*]]))

(defmethod sql-jdbc.sync/database-type->base-type :crossdata [_ database-type]
    (database-type->base-type database-type))


;; ******************  sparksql overrides  ************************************************

;; connection details for crossdata
(defmethod sql-jdbc.conn/connection-details->spec :crossdata
  [_ {:keys [host port user ssl-option impersonate]
      :or {host "localhost", port 13422, user "discovery"}
      :as details}]
  (log/trace "Crossdata conection details. host:" host "port:" port "details:" details "impersonate:" impersonate)
  (-> (merge {:classname "com.stratio.jdbc.core.jdbc4.StratioDriver"
              :subprotocol "crossdata"
              :subname (if ssl-option
                         (str "//Server=" host ":" port ";UID=" user ";SSL=true;;LogOutputType=stdout;LogLevel=DEBUG")
                         (str "//Server=" host ":" port ";UID=" user ";LogOutputType=stdout;LogLevel=DEBUG"))}
             (dissoc details :host :port :user :ssl :db :ssl-option))
      (sql-jdbc.common/handle-additional-options details, :seperator-style :semicolon)))


;; STRATIO: sparksql driver implements describe-database and describe-table because there is some problem
;; with the Thrift server but in croossdata we do not have this problem so we do not need to implement them.
;; However, we do have to implement describe-table in order to
;;   - do a refresh table
;;   - control possible errors that would stop the sync process, leaving some tables without syncing
(defn- table-name-for-logging  ;; this is defined in sync-util, but is private
  [{schema :schema, id :id, table-name :name}]
  (trs "Table {0} ''{1}''" (or id "") (str (when (seq schema) (str schema ".")) table-name)))

(defn- refresh-table!
  [database {schema :schema, table-name :name}]
  (sql-jdbc.execute/do-with-connection-with-options
   :crossdata
   database
   nil
   (fn [conn]
     (jdbc/query {:connection conn} [(format "refresh table `%s`.`%s`" schema table-name)]))))

(defn- empty-table-metadata
  [table]
  (assoc (select-keys table [:name :schema]) :fields #{}))

(defn- describe-table
  [driver database table]
  (sync-util/with-error-handling
    (format "Describe-table error in XD driver for %s. Will return empty metadata." (table-name-for-logging table))
    (refresh-table! database table)
    ((get-method driver/describe-table :sql-jdbc) driver database table)))

(defmethod driver/describe-table :crossdata
  [driver database table]
  (or (describe-table driver database table)
      (empty-table-metadata table)))

;; do not use the sparksql implementation and just use the sql-jdbc one
(defmethod driver/describe-database :crossdata
  [driver database]
  ((get-method driver/describe-database :sql-jdbc) driver database))

;; Crossdata jdbc does not handle comments well, until they do, we need to override this so
;; that no remark is added to the sql
(defmethod sql-jdbc.execute/inject-remark :crossdata
  [driver sql remark]
  sql)


;; We modify execute-reducible-query to add impersonation, and to join all lines in
;; the sql query (removing comments) so we can have multiline queries with the crossdata driver.
;; Ideally this should be handled by the crossdata jdbc, but as of now it interpret newlines
;; as separate queries (and it only return the results of the first query/line ¯\_(ツ)_/¯)
;;
;; also, we make the splicing style configurable via environment variable
;; (the :paranoid one gives problems with the virtualizer integration with cdata)
(defn- is-comment?
  [line]
  (str/starts-with? (str/trim line) "--"))

(defn- join-lines-and-remove-comments
  [sql]
  (->> sql
       str/split-lines
       (remove is-comment?)
       (str/join " ")))

(defn- impersonate? []
  (and (get-in (driver-api/database (driver-api/metadata-provider)) [:details :impersonate])
       (not st.mw/*is-sync-request?*)))

(defn- current-user []
  #_{:clj-kondo/ignore [:deprecated-var]}
  (:first_name @(driver-api/current-user)))

;; TODO: este método lo tienen implementado dos veces repetido en el driver de sparksql...
;; tiene pinta de que el "buieno" no es el que tenemos aquí copiado. REVISAR
;; bound variables are not supported in Spark SQL (maybe not Hive either, haven't checked)
;; STRATIO: impersonate users, handle comments and add debug logs
(defmethod driver/execute-reducible-query :crossdata
  [driver {inner-query :native, :as outer-query} context respond]
  (let [inner-query (-> inner-query
                        (update :query (if (and (impersonate?) (current-user))
                                           (comp (partial format "EXECUTE AS %s %s" (current-user))
                                                 join-lines-and-remove-comments)
                                         join-lines-and-remove-comments)))
        query       (assoc outer-query :native inner-query)]
    (log/debug "Executing virtualizer query:" query)
    ((get-method driver/execute-reducible-query :sparksql) driver query context respond)))

;; STRATIO: take into account our enviroment variable VIRTUALIZER_DRIVER_HEX_ENCODE_SQL_PARAMS for
;; cases where we should not paranoid-escape params, since it breaks cdata integration in virtualizer.
(defmethod sql.qp/format-honeysql :crossdata
  [driver honeysql-form]
  ;; we're compiling a query for one of two reasons:
  ;;
  ;; 1. compiling a query to be executed, in which case [[driver/*compile-with-inline-parameters*]] will be falsely
  ;;
  ;; 2. compiling a query to power the "view the SQL" feature, which should be human-friendly with inlined parameters
  ;;    and what not
  ;;
  ;; Spark SQL/Hive JDBC doesn't support JDBC parameterization and always need to compiled with inline parameters, but
  ;; we want those parameters to be friendly like
  ;;
  ;;    WHERE bird_type = 'cockatiel'
  ;;
  ;; in human-friendly compilation for "view the SQL" and paranoid e.g.
  ;;
  ;;    WHERE bird_type = decode(unhex('776f77'), 'utf-8')
  ;;
  ;; if we're compiling the query for execution.
  ;;
  ;; Look at the value of [[driver/*compile-with-inline-parameters*]] to determine the type of compilation we're doing.
  (let [compiling-for-execution? (not driver/*compile-with-inline-parameters*)]
    (binding [driver/*compile-with-inline-parameters* true
              hive-like/*inline-param-style*          (if (and compiling-for-execution?
                                                               ;; < STRATIO - add this extra condition to be paranoid based on env. varible >
                                                               (config/config-bool :virtualizer-driver-hex-encode-sql-params))
                                                        :paranoid
                                                        :friendly)]
      ((get-method sql.qp/format-honeysql :hive-like) driver honeysql-form))))


;; ******** things not in sparksql or hive_like driver but that we need to implement ***************

;; so that SQL gets prettyfied correctly (we need to use sparksql dialect)
;; without this, with the default dialect, when prettyfing it introduces spaces in the backquoted entity names:
;;   "select * from `table`" -> "selct * from ` table `"
(defmethod driver/prettify-native-form :crossdata
  [driver native-form]
  (sql.u/format-sql-and-fix-params :sparksql native-form))
