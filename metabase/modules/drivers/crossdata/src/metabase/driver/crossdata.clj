(ns metabase.driver.crossdata
  "Driver for Stratio Crossdata databases. Uses the official Stratio JDBC driver under the hood."
  (:require [buddy.core.codecs :as codecs]
            [clojure.core.async :as a]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [honeysql.core :as hsql]
            [honeysql.helpers :as h]
            [medley.core :as m]
            [java-time :as t]
            [metabase.api.common :as api]
            [metabase.driver :as driver]
            [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.execute.legacy-impl :as legacy]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.driver.sql.util :as sql.u]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.mbql.util :as mbql.u]
            [metabase.models.field :refer [Field]]
            [metabase.models.table :refer [Table]]
            [metabase.query-processor.context :as context]
            [metabase.query-processor.interface :as qp.i]
            [metabase.query-processor.store :as qp.store]
            [metabase.query-processor.timezone :as qp.timezone]
            [metabase.query-processor.util :as qputil]
            [metabase.stratio.middleware :as st.mw]
            [metabase.sync.util :as sync-util]
            [metabase.util :as u]
            [metabase.util.date-2 :as u.date]
            [metabase.util.honeysql-extensions :as hx]
            [metabase.util.i18n :refer [trs]]
            [toucan.db :as db])
  (:import [java.sql Connection PreparedStatement ResultSet Types]
           [java.time LocalDate OffsetDateTime ZonedDateTime]))

;; This driver should declare sparksql driver as parent and just override a couple of methods, like those of
;; issues with spark's Thrift server and those that add extra stratio functionality or the ones declaring the
;; types of crossdata. However, since we do not include the sparksql driver due to vulnerabilities, we must
;; implement everything here but it will be almost the same implementations as in sparksql and hive-like
;; except for some changes that we mark with the < STRATIO - /> comments

;; ******************  hive-like methods  ************************************************

(driver/register! :crossdata :parent #{:sql-jdbc ::legacy/use-legacy-classes-for-read-and-set})

(defmethod driver/db-start-of-week :crossdata
           [_]
           :sunday)

(defmethod sql-jdbc.conn/data-warehouse-connection-pool-properties :crossdata
  [driver database]
  ;; The Hive JDBC driver doesn't support `Connection.isValid()`, so we need to supply a test query for c3p0 to use to
  ;; validate connections upon checkout.
  (merge
   ((get-method sql-jdbc.conn/data-warehouse-connection-pool-properties :sql-jdbc) driver database)
   {"preferredTestQuery" "SELECT 1"}))

;; < STRATIO - we implement this differently from hive-like
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
;; STRATIO />

(defmethod sql.qp/current-datetime-honeysql-form :crossdata [_] :%now)

(defmethod sql.qp/unix-timestamp->honeysql [:crossdata :seconds]
  [_ _ expr]
  (hx/->timestamp (hsql/call :from_unixtime expr)))

(defn- date-format [format-str expr]
  (hsql/call :date_format expr (hx/literal format-str)))

(defn- str-to-date [format-str expr]
  (hx/->timestamp
   (hsql/call :from_unixtime
              (hsql/call :unix_timestamp
                         expr (hx/literal format-str)))))

(defn- trunc-with-format [format-str expr]
  (str-to-date format-str (date-format format-str expr)))

(defmethod sql.qp/date [:crossdata :default]         [_ _ expr] (hx/->timestamp expr))
(defmethod sql.qp/date [:crossdata :minute]          [_ _ expr] (trunc-with-format "yyyy-MM-dd HH:mm" (hx/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :minute-of-hour]  [_ _ expr] (hsql/call :minute (hx/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :hour]            [_ _ expr] (trunc-with-format "yyyy-MM-dd HH" (hx/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :hour-of-day]     [_ _ expr] (hsql/call :hour (hx/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :day]             [_ _ expr] (trunc-with-format "yyyy-MM-dd" (hx/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :day-of-month]    [_ _ expr] (hsql/call :dayofmonth (hx/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :day-of-year]     [_ _ expr] (hx/->integer (date-format "D" (hx/->timestamp expr))))
(defmethod sql.qp/date [:crossdata :month]           [_ _ expr] (hsql/call :trunc (hx/->timestamp expr) (hx/literal :MM)))
(defmethod sql.qp/date [:crossdata :month-of-year]   [_ _ expr] (hsql/call :month (hx/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :quarter-of-year] [_ _ expr] (hsql/call :quarter (hx/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :year]            [_ _ expr] (hsql/call :trunc (hx/->timestamp expr) (hx/literal :year)))

(defmethod sql.qp/date [:crossdata :day-of-week]
  [_ _ expr]
  (sql.qp/adjust-day-of-week :crossdata
                             (hx/->integer (date-format "u"
                                                        (hx/+ (hx/->timestamp expr)
                                                              (hsql/raw "interval '1' day"))))))

(defmethod sql.qp/date [:crossdata :week]
  [driver _ expr]
  (let [week-extract-fn (fn [expr]
                          (hsql/call :date_sub
                            (hx/+ (hx/->timestamp expr)
                                  (hsql/raw "interval '1' day"))
                            (date-format "u" (hx/+ (hx/->timestamp expr)
                                                   (hsql/raw "interval '1' day")))))]
    (sql.qp/adjust-start-of-week driver week-extract-fn expr)))

(defmethod sql.qp/date [:crossdata :quarter]
  [_ _ expr]
  (hsql/call :add_months
    (hsql/call :trunc (hx/->timestamp expr) (hx/literal :year))
    (hx/* (hx/- (hsql/call :quarter (hx/->timestamp expr))
                1)
          3)))

(defmethod sql.qp/->honeysql [:crossdata :replace]
  [driver [_ arg pattern replacement]]
  (hsql/call :regexp_replace
    (sql.qp/->honeysql driver arg)
    (sql.qp/->honeysql driver pattern)
    (sql.qp/->honeysql driver replacement)))

(defmethod sql.qp/->honeysql [:crossdata :regex-match-first]
  [driver [_ arg pattern]]
  (hsql/call :regexp_extract (sql.qp/->honeysql driver arg) (sql.qp/->honeysql driver pattern) 0))

(defmethod sql.qp/->honeysql [:crossdata :median]
  [driver [_ arg]]
  (hsql/call :percentile (sql.qp/->honeysql driver arg) 0.5))

(defmethod sql.qp/->honeysql [:crossdata :percentile]
  [driver [_ arg p]]
  (hsql/call :percentile (sql.qp/->honeysql driver arg) (sql.qp/->honeysql driver p)))

(defmethod sql.qp/add-interval-honeysql-form :crossdata
  [_ hsql-form amount unit]
  (hx/+ (hx/->timestamp hsql-form) (hsql/raw (format "(INTERVAL '%d' %s)" (int amount) (name unit)))))

;; ignore the schema when producing the identifier
(defn qualified-name-components
  "Return the pieces that represent a path to `field`, of the form `[table-name parent-fields-name* field-name]`."
  [{field-name :name, table-id :table_id}]
  [(db/select-one-field :name Table, :id table-id) field-name])

(defmethod sql.qp/field->identifier :crossdata
  [_ field]
  (apply hsql/qualify (qualified-name-components field)))

(def ^:dynamic *param-splice-style*
  "How we should splice params into SQL (i.e. 'unprepare' the SQL). Either `:friendly` (the default) or `:paranoid`.
  `:friendly` makes a best-effort attempt to escape strings and generate SQL that is nice to look at, but should not
  be considered safe against all SQL injection -- use this for 'convert to SQL' functionality. `:paranoid` hex-encodes
  strings so SQL injection is impossible; this isn't nice to look at, so use this for actually running a query."
  :friendly)

(defmethod unprepare/unprepare-value [:crossdata String]
  [_ ^String s]
  ;; Because Spark SQL doesn't support parameterized queries (e.g. `?`) convert the entire String to hex and decode.
  ;; e.g. encode `abc` as `decode(unhex('616263'), 'utf-8')` to prevent SQL injection
  (case *param-splice-style*
    :friendly (str \' (sql.u/escape-sql s :backslashes) \')
    :paranoid (format "decode(unhex('%s'), 'utf-8')" (codecs/bytes->hex (.getBytes s "UTF-8")))))

;; Hive/Spark SQL doesn't seem to like DATEs so convert it to a DATETIME first
(defmethod unprepare/unprepare-value [:crossdata LocalDate]
  [driver t]
  (unprepare/unprepare-value driver (t/local-date-time t (t/local-time 0))))

(defmethod unprepare/unprepare-value [:crossdata OffsetDateTime]
  [_ t]
  (format "to_utc_timestamp('%s', '%s')" (u.date/format-sql (t/local-date-time t)) (t/zone-offset t)))

(defmethod unprepare/unprepare-value [:crossdata ZonedDateTime]
  [_ t]
  (format "to_utc_timestamp('%s', '%s')" (u.date/format-sql (t/local-date-time t)) (t/zone-id t)))

;; Hive/Spark SQL doesn't seem to like DATEs so convert it to a DATETIME first
(defmethod sql-jdbc.execute/set-parameter [:crossdata LocalDate]
  [driver ps i t]
  (sql-jdbc.execute/set-parameter driver ps i (t/local-date-time t (t/local-time 0))))

;; TIMEZONE FIXME — not sure what timezone the results actually come back as
(defmethod sql-jdbc.execute/read-column-thunk [:crossdata Types/TIME]
  [_ ^ResultSet rs rsmeta ^Integer i]
  (fn []
    (when-let [t (.getTimestamp rs i)]
      (t/offset-time (t/local-time t) (t/zone-offset 0)))))

(defmethod sql-jdbc.execute/read-column-thunk [:crossdata Types/DATE]
  [_ ^ResultSet rs rsmeta ^Integer i]
  (fn []
    (when-let [t (.getDate rs i)]
      (t/zoned-date-time (t/local-date t) (t/local-time 0) (t/zone-id "UTC")))))

(defmethod sql-jdbc.execute/read-column-thunk [:crossdata Types/TIMESTAMP]
  [_ ^ResultSet rs rsmeta ^Integer i]
  (fn []
    (when-let [t (.getTimestamp rs i)]
      (t/zoned-date-time (t/local-date-time t) (t/zone-id "UTC")))))



;; ******************  sparksql methods  ************************************************

;;; ------------------------------------------ Custom HoneySQL Clause Impls ------------------------------------------

(def ^:private source-table-alias
  "Default alias for all source tables. (Not for source queries; those still use the default SQL QP alias of `source`.)"
  "t1")

;; use `source-table-alias` for the source Table, e.g. `t1.field` instead of the normal `schema.table.field`
(defmethod sql.qp/->honeysql [:crossdata (class Field)]
  [driver field]
  (binding [sql.qp/*table-alias* (or sql.qp/*table-alias* source-table-alias)]
    ;; < STRATIO - we do not have :hive-like driver in our build
    ((get-method sql.qp/->honeysql [:sql (class Field)]) driver field)
    ;; ((get-method sql.qp/->honeysql [:hive-like (class Field)]) driver field)
    ;; STRATIO />
    ))


(defmethod sql.qp/apply-top-level-clause [:crossdata :page] [_ _ honeysql-form {{:keys [items page]} :page}]
  (let [offset (* (dec page) items)]
    (if (zero? offset)
      ;; if there's no offset we can simply use limit
      (h/limit honeysql-form items)
      ;; if we need to do an offset we have to do nesting to generate a row number and where on that
      (let [over-clause (format "row_number() OVER (%s)"
                                (first (hsql/format (select-keys honeysql-form [:order-by])
                                                    :allow-dashed-names? true
                                                    :quoting :mysql)))]
        (-> (apply h/select (map last (:select honeysql-form)))
            (h/from (h/merge-select honeysql-form [(hsql/raw over-clause) :__rownum__]))
            (h/where [:> :__rownum__ offset])
            (h/limit items))))))

(defmethod sql.qp/apply-top-level-clause [:crossdata :source-table]
  [driver _ honeysql-form {source-table-id :source-table}]
  (let [{table-name :name, schema :schema} (qp.store/table source-table-id)]
    (h/from honeysql-form [(sql.qp/->honeysql driver (hx/identifier :table schema table-name))
                           (sql.qp/->honeysql driver (hx/identifier :table-alias source-table-alias))])))


;;; ------------------------------------------- Other Driver Method Impls --------------------------------------------

;; < STRATIO - connection details for crossdata
(defmethod sql-jdbc.conn/connection-details->spec :crossdata
  [_ {:keys [host port dbname user ssl-option impersonate]
      :or {host "localhost", port 13422, dbname "", user "crossdata-1"}
      :as details}]
  (log/debug "Crossdata conection details. host:" host "port:" port "details:" details "impersonate:" impersonate)
  (-> (merge {:classname "com.stratio.jdbc.core.jdbc4.StratioDriver"
              :subprotocol "crossdata"
              :subname (if ssl-option
                         (str "//Server=" host ":" port ";UID=" user ";SSL=true;LogLevel=3;LogPath=/tmp/crossdata-jdbc-logs")
                         (str "//Server=" host ":" port ";UID=" user ";LogLevel=3;LogPath=/tmp/crossdata-jdbc-logs"))}
             (dissoc details :host :port :user :ssl :db :ssl-option))
      (sql-jdbc.common/handle-additional-options details, :seperator-style :semicolon)))
;; STRATIO />


;; < STRATIO - sparksql driver implements describe-databases and describe-table because there is som problem
;; with the Thrift server but in croossdata we do not have this problem so we do not need to implement them.
;; However, we do have to implement describe-table in order to
;;   - do a refresh table
;;   - control possible errors that would stop the sync process, leaving some tables without syncing
(defn- table-name-for-logging  ;; this is defined in sync-util, but is private
  [{schema :schema, id :id, table-name :name}]
  (trs "Table {0} ''{1}''" (or id "") (str (when (seq schema) (str schema ".")) table-name)))

(defn- refresh-table!
  [database {schema :schema, id :id, table-name :name}]
  (with-open [conn (jdbc/get-connection (sql-jdbc.conn/db->pooled-connection-spec database))]
    (jdbc/query {:connection conn} [(format "refresh table %s.%s" schema table-name)])))

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
;; STRATIO />

;; < STRATIO - Crossdata jdbc does not handle comments well, until they do, we need to
;; override the default sql-jdbc execute-query-method so it does not add comments to the sql.
;; This is a copy/paste of the sql-jdbc execute-reducible-query method but removing the sql comments
;; and qualifying some symbols in the sql-jdbc.execute namespace. Note that we only need to copy/paste
;; the arity implementation that we need to modify, the other one we leave as is
(defn- execute-reducible-query
  "Default impl of `execute-reducible-query` for sql-jdbc drivers."
  {:added "0.35.0", :arglists '([driver query context respond] [driver sql params max-rows context respond])}
  ([driver {{sql :query, params :params} :native, :as outer-query} context respond]
   {:pre [(string? sql) (seq sql)]}
   (let [remark   (qputil/query->remark driver outer-query)
         ;; < STATIO - do not add line with comment to the sql, crossdata does not handle newlines well
         ;; sql      (str "-- " remark "\n" sql)
         ;; STRATIO />
         max-rows (or (mbql.u/query->max-rows-limit outer-query)
                      qp.i/absolute-max-results)]
     ;; we do not copy/paste the other arity implementation and just call the sql-jdbc.excute one directly
     (sql-jdbc.execute/execute-reducible-query driver sql params max-rows context respond))))
;; STRATIO />

;; < STRATIO - We modify execute-reducible-query to add impersonation, and to join all lines in
;; the sql query (removing comments) so we can have multiline queries with the crossdata driver.
;; Ideally this should be handled by the crossdata jdbc, but as of now it interpret newlines
;; as separate queries (and it only return the results of the first query/line ¯\_(ツ)_/¯)
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
  (and (get-in (qp.store/database) [:details :impersonate])
       (not st.mw/*is-sync-request?*)))

(defn- current-user []
  (:first_name @api/*current-user*))

;; bound variables are not supported in Spark SQL (maybe not Hive either, haven't checked)
(defmethod driver/execute-reducible-query :crossdata
  [driver {:keys [database settings], {sql :query, :keys [params], :as inner-query} :native, :as outer-query} context respond]
  (let [inner-query (-> (assoc inner-query
                               :remark (qputil/query->remark :crossdata outer-query)
                               :query  (if (seq params)
                                         (binding [*param-splice-style* :paranoid]
                                           (unprepare/unprepare driver (cons sql params)))
                                         sql)
                               :max-rows (mbql.u/query->max-rows-limit outer-query))
                        ;; < STRATIO - impersonate logged user in Crossdata
                        (update :query (if (and (impersonate?) (current-user))
                                         (comp (partial format "EXECUTE AS %s %s" (current-user))
                                               join-lines-and-remove-comments)
                                         join-lines-and-remove-comments))
                        ;; STRATIO />
                        (dissoc :params))
        query       (assoc outer-query :native inner-query)]
    ;; < STRATIO - crossdata jdbc does not handle newlines well, until they do, we need to
    ;; override this method so we do not add a remark comment in a newline (see impl. above)
    ;; we also add some debug logs
    ;; ((get-method driver/execute-reducible-query :sql-jdbc) driver query context respond)
    (log/debug "Executing crossdata query:" query)
    (execute-reducible-query driver query context respond)
    ;; STRATIO />
    ))

;; 1.  SparkSQL doesn't support `.supportsTransactionIsolationLevel`
;; 2.  SparkSQL doesn't support session timezones (at least our driver doesn't support it)
;; 3.  SparkSQL doesn't support making connections read-only
;; 4.  SparkSQL doesn't support setting the default result set holdability
(defmethod sql-jdbc.execute/connection-with-timezone :crossdata
  [driver database ^String timezone-id]
  (let [conn (.getConnection (sql-jdbc.execute/datasource-with-diagnostic-info! driver database))]
    (try
      (.setTransactionIsolation conn Connection/TRANSACTION_READ_UNCOMMITTED)
      conn
      (catch Throwable e
        (.close conn)
        (throw e)))))

;; 1.  SparkSQL doesn't support setting holdability type to `CLOSE_CURSORS_AT_COMMIT`
(defmethod sql-jdbc.execute/prepared-statement :crossdata
  [driver ^Connection conn ^String sql params]
  (let [stmt (.prepareStatement conn sql
                                ResultSet/TYPE_FORWARD_ONLY
                                ResultSet/CONCUR_READ_ONLY)]
    (try
      (.setFetchDirection stmt ResultSet/FETCH_FORWARD)
      (sql-jdbc.execute/set-parameters! driver stmt params)
      stmt
      (catch Throwable e
        (.close stmt)
        (throw e)))))

;; the current HiveConnection doesn't support .createStatement
(defmethod sql-jdbc.execute/statement-supported? :sparksql [_] false)

(doseq [feature [:basic-aggregations
                 :binning
                 :expression-aggregations
                 :expressions
                 :native-parameters
                 :nested-queries
                 :standard-deviation-aggregations]]
  (defmethod driver/supports? [:crossdata feature] [_ _] true))

;; only define an implementation for `:foreign-keys` if none exists already. In test extensions we define an alternate
;; implementation, and we don't want to stomp over that if it was loaded already
(when-not (get (methods driver/supports?) [:crossdata :foreign-keys])
  (defmethod driver/supports? [:crossdata :foreign-keys] [_ _] true))

(defmethod sql.qp/quote-style :crossdata [_] :mysql)
