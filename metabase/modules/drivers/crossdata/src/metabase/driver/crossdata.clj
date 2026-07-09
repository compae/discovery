(ns metabase.driver.crossdata
  "Driver for Stratio Crossdata databases. Uses the official Stratio JDBC driver under the hood."
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]
   [medley.core :as m]
   [metabase.config.core :as config]
   [metabase.driver :as driver]
   [metabase.driver-api.core :as driver-api]
   [metabase.driver.hive-like :as hive-like]
   [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
   [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
   [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
   [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
   [metabase.driver.sql-jdbc.sync.interface :as sql-jdbc.sync.interface]
   [metabase.driver.sql.query-processor :as sql.qp]
   [metabase.driver.sql.util :as sql.u]
   [metabase.stratio.middleware :as st.mw]
   [metabase.sync.util :as sync-util]
   [metabase.util.i18n :refer [trs]]
   [metabase.util.log :as log]))

(set! *warn-on-reflection* true)

(driver/register! :crossdata :parent :sparksql)

;; ******************  hive-like overrides  ************************************************

;; STRATIO: this maps the JDBC type names reported by the ResultSet at query time
;; (`.getColumnTypeName` in `column-metadata`, e.g. `SQL_INTEGER`, `VARCHAR`, `DECIMAL`), so it
;; matches uppercase JDBC names by substring (via `pattern-based-database-type->base-type`, which
;; uses `re-find`).
;;
;; It is NOT used by the schema sync: our `describe-table` does not go through the JDBC metadata
;; methods (`.getColumns`), it runs a `DESCRIBE` by hand, so there we get Spark's *native* type
;; names (lowercase `int`, `string`, `decimal(10,2)`, ...) and map them with the `:hive-like`
;; matcher instead (see `describe-results->fields`). hive-like uses `re-matches` (full match on the
;; native names) and would not match these JDBC names, hence the two separate matchers.
;;
;; This split is specific to us: the Stratio Simba JDBC driver reports these JDBC-canonical names
;; (`SQL_INTEGER`, ...) in the ResultSet, whereas the :sparksql/:hive-like driver's Hive JDBC driver
;; reports the native names both at query time and at sync time, so it gets by with a single
;; (hive-like) matcher.
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

;; ****************** schema sync optimizations (STRATIO) **********************************
;;
;; With many schemas/tables the sync used to take up to ~24h. The inherited :sql-jdbc path costs
;; ~5-6 round-trips per table against Virtualizer. The overrides below cut it to ~1 DESCRIBE
;; per table: we skip the per-table SELECT probe and replace JDBC .getColumns with a direct
;; DESCRIBE. See the plan/PR for the full round-trip breakdown.

;; STRATIO: the :sql-jdbc active-tables impl (fast-active-tables) calls have-select-privilege? on
;; EVERY table, running a `SELECT TRUE FROM s.t WHERE 1<>1 LIMIT 0` probe per table. Against
;; Virtualizer each probe is a full query round-trip, so this is an N+1 over all tables and the
;; single worst bottleneck of the sync. Crossdata is a virtualization layer with a single
;; connection user, and impersonation is disabled during sync (see impersonate?), so if a table
;; can be listed it can be queried: the probe adds nothing. Skip it.
(defmethod sql-jdbc.sync.interface/have-select-privilege? :crossdata
  [_driver _conn _schema _table & _]
  true)

;; do not use the sparksql implementation (which does a bare `SHOW TABLES` for the current DB only,
;; ignoring schema filters) and just use the sql-jdbc one. Its fast-active-tables lists tables only
;; for the *included* schemas, so it stays cheap even for connections with lots of schemas.
(defmethod driver/describe-database :crossdata
  [driver database]
  ((get-method driver/describe-database :sql-jdbc) driver database))

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

;; The describe-table flow is: describe-table -> describe-table-fields (opens the connection and
;; runs the `DESCRIBE`) -> describe-results->fields (parses the rows) -> valid-describe-table-row?
;; (keeps only real column rows). These parsing helpers are based on :sparksql's describe-table
;; (sparksql.clj:125-152): valid-describe-table-row? is copied verbatim; describe-results->fields is
;; pulled into its own fn (so it can be unit-tested without a live Virtualizer, see
;; crossdata_test.clj) and additionally captures the column comment (:field-comment), which
;; sparksql's parsing drops. The other STRATIO tweaks are in describe-table-fields.

;; Virtualizer's `DESCRIBE` appends extra rows besides the columns: partition-info markers
;; (`# Partition Information`, `# col_name`) and blank rows. Keep only rows whose col_name and
;; data_type are non-blank and don't start with `#`.
(defn- valid-describe-table-row?
  [{:keys [col_name data_type]}]
  (every? (every-pred (complement str/blank?)
                      (complement #(str/starts-with? % "#")))
          [col_name data_type]))

;; `DESCRIBE` returns Spark-native type names (lowercase `string`, `bigint`, `array<...>`,
;; `decimal(p,s)`), so base-type is resolved with the :hive-like matcher (not our own
;; database-type->base-type above, which matches the uppercase JDBC type names rather than
;; `DESCRIBE` output). We also read the third `DESCRIBE` column — the column comment — into
;; :field-comment so Metabase syncs it as the field description; sparksql's parsing drops it.
(defn- describe-results->fields
  [results]
  (set
   (for [[idx {col-name :col_name, data-type :data_type, col-comment :comment, :as result}] (m/indexed results)
         :when (valid-describe-table-row? result)]
     (cond-> {:name              col-name
              :database-type     data-type
              :base-type         (sql-jdbc.sync/database-type->base-type :hive-like (keyword data-type))
              :database-position idx}
       (not (str/blank? col-comment)) (assoc :field-comment col-comment)))))

;; STRATIO: fetch a table's columns with ONE `DESCRIBE` round-trip. Copied from :sparksql's
;; describe-table (sparksql.clj:131-152) with two changes:
;;   1. we do NOT reuse it via get-method / its dash-to-underscore name mangling — that is a Spark
;;      Thrift workaround and would corrupt crossdata schema/table names that legitimately contain
;;      '-';
;;   2. we quote with backticks (mysql quote-style), same as refresh-table! above.
;; This replaces the inherited :sql-jdbc describe-table (JDBC .getColumns), which on the Simba driver
;; re-lists the whole DB (`SHOW DATABASES` + `SHOW TABLES` + `DESCRIBE`) plus a getPrimaryKeys call
;; for EVERY table. Crossdata has no INFORMATION_SCHEMA (no bulk column source), but a plain
;; `DESCRIBE` returns all columns of a table in a single round-trip.
(defn- describe-table-fields
  [database schema table-name]
  (sql-jdbc.execute/do-with-connection-with-options
   :crossdata
   database
   nil
   (fn [conn]
     (describe-results->fields
      (jdbc/query {:connection conn}
                  [(format "describe `%s`.`%s`" schema table-name)])))))

;; Virtualizer lists tables the connection user may not be able to read: `SHOW TABLES` filters
;; only at the database level (per-table filtering is off unless legacy-show-tables-permissions is
;; enabled), so an accessible database exposes all its tables. `DESCRIBE` (needs the Read action)
;; and `refresh table` (needs Cache) then throw "Operation not authorized" for tables the user has
;; no grant on. We detect that to sync those tables with no fields and a one-line warning, rather
;; than using a per-table permission probe like the sql-jdbc driver.
(defn- authorization-error?
  [^Throwable e]
  (->> (iterate #(.getCause ^Throwable %) e)
       (take-while some?)
       (some (fn [^Throwable t]
               (some-> (.getMessage t) (str/includes? "Operation not authorized"))))
       boolean))

(defn- describe-table
  [_driver database {schema :schema, table-name :name, :as table}]
  (sync-util/with-error-handling
   (format "Describe-table error in XD driver for %s. Will return empty metadata." (table-name-for-logging table))
    (try
      (log/tracef "Describing %s" (table-name-for-logging table))
      (refresh-table! database table)
      (let [fields (describe-table-fields database schema table-name)]
        (log/debugf "Described %s: %d field(s)" (table-name-for-logging table) (count fields))
        {:name   table-name
         :schema schema
         :fields fields})
      ;; An unauthorized table is expected (see above): warn without a stacktrace and sync it empty.
      ;; Any other error is rethrown so with-error-handling logs it in full and applies its
      ;; retry/abort policy.
      (catch Throwable e
        (if (authorization-error? e)
          (do
            (log/warnf "Not authorized on table %s; syncing it with empty metadata" (table-name-for-logging table))
            (empty-table-metadata table))
          (throw e))))))

(defmethod driver/describe-table :crossdata
  [driver database table]
  (or (describe-table driver database table)
      (empty-table-metadata table)))

;; STRATIO: disable fingerprinting. The analyze phase samples up to 10k rows with one query per
;; table, which is costly when there are many tables and doesn't give much value for our use case
;; (auto semantic-typing, min/max, x-rays).
(defmethod driver/database-supports? [:crossdata :fingerprint]
  [_driver _feature _database]
  false)

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
;; (the :paranoid one gives problems with Virtualizer integration with cdata)
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
;; cases where we should not paranoid-escape params, since it breaks cdata integration in Virtualizer.
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
