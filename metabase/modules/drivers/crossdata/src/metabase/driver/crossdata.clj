(ns metabase.driver.crossdata
  "Driver for Stratio Crossdata databases. Uses the official Stratio JDBC driver under the hood."
  (:require
   [buddy.core.codecs :as codecs]
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]
   [honey.sql :as sql]
   [honey.sql.helpers :as sql.helpers]
   [java-time :as t]
   [metabase.api.common :as api]
   [metabase.driver :as driver]
   [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
   [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
   [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
   [metabase.driver.sql-jdbc.execute.legacy-impl :as sql-jdbc.legacy]
   [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
   [metabase.driver.sql.parameters.substitution :as sql.params.substitution]
   [metabase.driver.sql.query-processor :as sql.qp]
   [metabase.driver.sql.util :as sql.u]
   [metabase.driver.sql.util.unprepare :as unprepare]
   [metabase.legacy-mbql.util :as mbql.u]
   [metabase.lib.metadata :as lib.metadata]
   [metabase.query-processor.middleware.limit :as limit]
   [metabase.query-processor.store :as qp.store]
   [metabase.query-processor.util :as qp.util]
   [metabase.query-processor.util.add-alias-info :as add]
   [metabase.stratio.middleware :as st.mw]
   [metabase.sync.util :as sync-util]
   [metabase.util.date-2 :as u.date]
   [metabase.util.honey-sql-2 :as h2x]
   [metabase.util.i18n :refer [trs]]
   [metabase.util.log :as log])
  (:import
   (java.sql Connection ResultSet Types)
   (java.time LocalDate OffsetDateTime ZonedDateTime)))

;; This driver should declare sparksql driver as parent and just override a couple of methods, like those of
;; issues with spark's Thrift server and those that add extra stratio functionality or the ones declaring the
;; types of crossdata. However, since we do not include the sparksql driver due to vulnerabilities, we must
;; implement everything here but it will be almost the same implementations as in sparksql and hive-like
;; except for some changes that we mark with the < STRATIO - /> comments

;; ******************  hive-like methods  ************************************************

(set! *warn-on-reflection* true)

(driver/register! :crossdata :parent #{:sql-jdbc ::sql-jdbc.legacy/use-legacy-classes-for-read-and-set})

(doseq [[feature supported?] {:now           true
                              :datetime-diff true}]
  (defmethod driver/database-supports? [:hive-like feature] [_driver _feature _db] supported?))

(defmethod driver/escape-alias :crossdata
  [driver s]
  ;; replace question marks inside aliases with `_QMARK_`, otherwise Spark SQL will interpret them as JDBC parameter
  ;; placeholder (yes, even if the identifier is quoted... (:unamused:)
  ;;
  ;; `_QMARK_` is kind of arbitrary but that's what [[munge]] does and it seems like it would lead to less potential
  ;; name clashes than if we just used underscores.
  (let [s (str/replace s #"\?" "_QMARK_")]
    ((get-method driver/escape-alias :sql) driver s)))

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

(defmethod sql.qp/current-datetime-honeysql-form :crossdata
  [_]
  (h2x/with-database-type-info :%now "timestamp"))

(defmethod sql.qp/unix-timestamp->honeysql [:crossdata :seconds]
  [_ _ expr]
  (h2x/->timestamp [:from_unixtime expr]))

(defn- date-format [format-str expr]
  [:date_format expr (h2x/literal format-str)])

(defn- str-to-date [format-str expr]
  (h2x/->timestamp [:from_unixtime [:unix_timestamp expr (h2x/literal format-str)]]))

(defn- trunc-with-format [format-str expr]
  (str-to-date format-str (date-format format-str expr)))

(defmethod sql.qp/date [:crossdata :default]         [_ _ expr] expr)
(defmethod sql.qp/date [:crossdata :minute]          [_ _ expr] (trunc-with-format "yyyy-MM-dd HH:mm" (h2x/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :minute-of-hour]  [_ _ expr] [:minute (h2x/->timestamp expr)])
(defmethod sql.qp/date [:crossdata :hour]            [_ _ expr] (trunc-with-format "yyyy-MM-dd HH" (h2x/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :hour-of-day]     [_ _ expr] [:hour (h2x/->timestamp expr)])
(defmethod sql.qp/date [:crossdata :day]             [_ _ expr] (trunc-with-format "yyyy-MM-dd" (h2x/->timestamp expr)))
(defmethod sql.qp/date [:crossdata :day-of-month]    [_ _ expr] [:dayofmonth (h2x/->timestamp expr)])
(defmethod sql.qp/date [:crossdata :day-of-year]     [_ _ expr] (h2x/->integer (date-format "D" (h2x/->timestamp expr))))
(defmethod sql.qp/date [:crossdata :month]           [_ _ expr] [:trunc (h2x/->timestamp expr) (h2x/literal :MM)])
(defmethod sql.qp/date [:crossdata :month-of-year]   [_ _ expr] [:month (h2x/->timestamp expr)])
(defmethod sql.qp/date [:crossdata :quarter-of-year] [_ _ expr] [:quarter (h2x/->timestamp expr)])
(defmethod sql.qp/date [:crossdata :year]            [_ _ expr] [:trunc (h2x/->timestamp expr) (h2x/literal :year)])

(def ^:private date-extract-units
  "See https://spark.apache.org/docs/3.3.0/api/sql/#extract"
  #{:year :y :years :yr :yrs
    :yearofweek
    :quarter :qtr
    :month :mon :mons :months
    :week :w :weeks
    :day :d :days
    :dayofweek :dow
    :dayofweek_iso :dow_iso
    :doy
    :hour :h :hours :hr :hrs
    :minute :m :min :mins :minutes
    :second :s :sec :seconds :secs})

(defn- format-date-extract
  [_fn [unit expr]]
  {:pre [(contains? date-extract-units unit)]}
  (let [[expr-sql & expr-args] (sql/format-expr expr {:nested true})]
    (into [(format "extract(%s FROM %s)" (name unit) expr-sql)]
          expr-args)))

(sql/register-fn! ::date-extract #'format-date-extract)

(defn- format-interval
  "Interval actually supports more than just plain numbers, but that's all we currently need. See
  https://spark.apache.org/docs/latest/sql-ref-literals.html#interval-literal"
  [_fn [amount unit]]
  {:pre [(number? amount)
         ;; other units are supported too but we're not currently supporting them.
         (#{:year :month :week :day :hour :minute :second :millisecond} unit)]}
  [(format "(interval '%d' %s)" (long amount) (name unit))])

(sql/register-fn! ::interval #'format-interval)

(defmethod sql.qp/date [:crossdata :day-of-week]
  [driver _unit expr]
  (sql.qp/adjust-day-of-week driver (-> [::date-extract :dow (h2x/->timestamp expr)]
                                        (h2x/with-database-type-info "integer"))))

(defmethod sql.qp/date [:crossdata :week]
  [driver _unit expr]
  (let [week-extract-fn (fn [expr]
                          (-> [:date_sub
                               (h2x/+ (h2x/->timestamp expr)
                                      [::interval 1 :day])
                               [::date-extract :dow (h2x/->timestamp expr)]]
                              (h2x/with-database-type-info "timestamp")))]
    (sql.qp/adjust-start-of-week driver week-extract-fn expr)))


(defmethod sql.qp/date [:crossdata :week-of-year-iso]
  [_driver _unit expr]
  [:weekofyear (h2x/->timestamp expr)])

(defmethod sql.qp/date [:crossdata :quarter]
  [_driver _unit expr]
  [:add_months
   [:trunc (h2x/->timestamp expr) (h2x/literal :year)]
   (h2x/* (h2x/- [:quarter (h2x/->timestamp expr)]
                 1)
          3)])

(defmethod sql.qp/->honeysql [:crossdata :replace]
  [driver [_ arg pattern replacement]]
  [:regexp_replace
   (sql.qp/->honeysql driver arg)
   (sql.qp/->honeysql driver pattern)
   (sql.qp/->honeysql driver replacement)])

(defmethod sql.qp/->honeysql [:crossdata :regex-match-first]
  [driver [_ arg pattern]]
  [:regexp_extract (sql.qp/->honeysql driver arg) (sql.qp/->honeysql driver pattern) 0])

(defmethod sql.qp/->honeysql [:crossdata :median]
  [driver [_ arg]]
  [:percentile (sql.qp/->honeysql driver arg) 0.5])

(defmethod sql.qp/->honeysql [:crossdata :percentile]
  [driver [_ arg p]]
  [:percentile (sql.qp/->honeysql driver arg) (sql.qp/->honeysql driver p)])

(defmethod sql.qp/add-interval-honeysql-form :crossdata
  [driver hsql-form amount unit]
  (if (= unit :quarter)
    (recur driver hsql-form (* amount 3) :month)
    (h2x/+ (h2x/->timestamp hsql-form)
           [::interval amount unit])))

(defmethod sql.qp/datetime-diff [:crossdata :year]
  [driver _unit x y]
  [:div (sql.qp/datetime-diff driver :month x y) 12])

(defmethod sql.qp/datetime-diff [:crossdata :quarter]
  [driver _unit x y]
  [:div (sql.qp/datetime-diff driver :month x y) 3])

(defmethod sql.qp/datetime-diff [:crossdata :month]
  [_driver _unit x y]
  (h2x/->integer [:months_between y x]))

(defmethod sql.qp/datetime-diff [:crossdata :week]
  [_driver _unit x y]
  [:div [:datediff y x] 7])

(defmethod sql.qp/datetime-diff [:crossdata :day]
  [_driver _unit x y]
  [:datediff y x])

(defmethod sql.qp/datetime-diff [:crossdata :hour]
  [driver _unit x y]
  [:div (sql.qp/datetime-diff driver :second x y) 3600])

(defmethod sql.qp/datetime-diff [:crossdata :minute]
  [driver _unit x y]
  [:div (sql.qp/datetime-diff driver :second x y) 60])

(defmethod sql.qp/datetime-diff [:crossdata :second]
  [_driver _unit x y]
  [:- [:unix_timestamp y] [:unix_timestamp x]])

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
;;
;; Also, pretty sure Spark SQL doesn't have a TIME type anyway.
;; https://spark.apache.org/docs/latest/sql-ref-datatypes.html
(defmethod sql-jdbc.execute/read-column-thunk [:crossdata Types/TIME]
  [_ ^ResultSet rs _rsmeta ^Integer i]
  (fn []
    (when-let [t (.getTimestamp rs i)]
      (t/offset-time (t/local-time t) (t/zone-offset 0)))))

(defmethod sql-jdbc.execute/read-column-thunk [:crossdata Types/DATE]
  [_ ^ResultSet rs _rsmeta ^Integer i]
  (fn []
    (when-let [s (.getString rs i)]
      (u.date/parse s))))

(defmethod sql-jdbc.execute/read-column-thunk [:crossdata Types/TIMESTAMP]
  [_ ^ResultSet rs _rsmeta ^Integer i]
  (fn []
    (when-let [t (.getTimestamp rs i)]
      (t/zoned-date-time (t/local-date-time t) (t/zone-id "UTC")))))


;; ******************  sparksql methods  ************************************************

;;; ------------------------------------------ Custom HoneySQL Clause Impls ------------------------------------------

(def ^:private source-table-alias
  "Default alias for all source tables. (Not for source queries; those still use the default SQL QP alias of `source`.)"
  "t1")

(defmethod sql.qp/->honeysql [:crossdata :field]
  [driver [_ _ {::sql.params.substitution/keys [compiling-field-filter?]} :as field-clause]]
  ;; use [[source-table-alias]] instead of the usual `schema.table` to qualify fields e.g. `t1.field` instead of the
  ;; normal `schema.table.field`
  ;; < STRATIO - we do not have :hive-like driver in our build
  ;; (let parent-method (get-method sql.qp/->honeysql [:hive-like :field])
  (let [parent-method (get-method sql.qp/->honeysql [:sql-jdbc :field])
        ;; STRATIO >
        field-clause  (mbql.u/update-field-options field-clause
                                                   update
                                                   ::add/source-table
                                                   (fn [source-table]
                                                     (cond
                                                       ;; DO NOT qualify fields from field filters with `t1`, that won't
                                                       ;; work unless the user-written SQL query is doing the same
                                                       ;; thing.
                                                       compiling-field-filter? ::add/none
                                                       ;; for all other fields from the source table qualify them with
                                                       ;; `t1`
                                                       (integer? source-table) source-table-alias
                                                       ;; no changes for anyone else.
                                                       :else                   source-table)))]
    (parent-method driver field-clause)))

(defn- format-over
  "e.g. ROW_NUMBER() OVER (ORDER BY field DESC) AS __rownum__"
  [_fn [expr partition]]
  (let [[expr-sql & expr-args]           (sql/format-expr expr      {:nested true})
        [partition-sql & partition-args] (sql/format-expr partition {:nested true})]
    (into [(format "%s OVER %s" expr-sql partition-sql)]
          cat
          [expr-args
           partition-args])))

(sql/register-fn! ::over #'format-over)

(defmethod sql.qp/apply-top-level-clause [:crossdata :page]
  [_driver _clause honeysql-form {{:keys [items page]} :page}]
  (let [offset (* (dec page) items)]
    (if (zero? offset)
      ;; if there's no offset we can simply use limit
      (sql.helpers/limit honeysql-form items)
      ;; if we need to do an offset we have to do nesting to generate a row number and where on that
      (let [over-clause [::over :%row_number (select-keys honeysql-form [:order-by])]]
        (-> (apply sql.helpers/select (map last (:select honeysql-form)))
            (sql.helpers/from (sql.helpers/select honeysql-form [over-clause :__rownum__]))
            (sql.helpers/where [:> :__rownum__ [:inline offset]])
            (sql.helpers/limit [:inline items]))))))

(defmethod sql.qp/apply-top-level-clause [:crossdata :source-table]
  [driver _ honeysql-form {source-table-id :source-table}]
  (let [{table-name :name, schema :schema} (lib.metadata/table (qp.store/metadata-provider) source-table-id)]
    (sql.helpers/from honeysql-form [(sql.qp/->honeysql driver (h2x/identifier :table schema table-name))
                                     [(sql.qp/->honeysql driver (h2x/identifier :table-alias source-table-alias))]])))


;;; ------------------------------------------- Other Driver Method Impls --------------------------------------------

;; < STRATIO - connection details for crossdata
(defmethod sql-jdbc.conn/connection-details->spec :crossdata
  [_ {:keys [host port user ssl-option impersonate]
      :or {host "localhost", port 13422, user "discovery"}
      :as details}]
  (log/debug "Crossdata conection details. host:" host "port:" port "details:" details "impersonate:" impersonate)
  (-> (merge {:classname "com.stratio.jdbc.core.jdbc4.StratioDriver"
              :subprotocol "crossdata"
              :subname (if ssl-option
                         (str "//Server=" host ":" port ";UID=" user ";SSL=true;;LogOutputType=stdout;LogLevel=DEBUG")
                         (str "//Server=" host ":" port ";UID=" user ";LogOutputType=stdout;LogLevel=DEBUG"))}
             (dissoc details :host :port :user :ssl :db :ssl-option))
      (sql-jdbc.common/handle-additional-options details, :seperator-style :semicolon)))
;; STRATIO />


;; < STRATIO - sparksql driver implements describe-database and describe-table because there is some problem
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
     (jdbc/query {:connection conn} [(format "refresh table %s.%s" schema table-name)]))))

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

;; < STRATIO - Crossdata jdbc does not handle comments well, until they do, we need to override the default
;; sql-jdbc execute-query-method so it does not add comments to the sql. This is a copy/paste of the sql-jdbc
;; execute-reducible-query method but providing 'false' as the default for the :include-user-id-and-hash
;; setting and qualifying some symbols in the sql-jdbc.execute namespace. Note that we only need to copy/paste
;; the arity implementation that we need to modify, the other one we leave as is.
;; Althoug we have included the setting :include-user-id-and-hash setting in our driver manifest, if users
;; upgrade (from a version without that setting in the manifest) and do not edit any setting of the crossdata
;; conection, the setting will not be present when we recover the database metadata so it will use the default,
;; which in metabase is true, we just change that to false :/.
(defn execute-reducible-query
  "Default impl of [[metabase.driver/execute-reducible-query]] for sql-jdbc drivers."
  {:added "0.35.0", :arglists '([driver query context respond] [driver sql params max-rows context respond])}
  ([driver {{sql :query, params :params} :native, :as outer-query} context respond]
   {:pre [(string? sql) (seq sql)]}
   (let [database (lib.metadata/database (qp.store/metadata-provider))
         sql      (if (get-in database [:details :include-user-id-and-hash] false) ;; here is the change
                    (->> (qp.util/query->remark driver outer-query)
                         (sql-jdbc.execute/inject-remark driver sql))
                    sql)
         max-rows (limit/determine-query-max-rows outer-query)]
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
  (and (get-in (lib.metadata/database (qp.store/metadata-provider)) [:details :impersonate])
       (not st.mw/*is-sync-request?*)))

(defn- current-user []
  (:first_name @api/*current-user*))

;; bound variables are not supported in Spark SQL (maybe not Hive either, haven't checked)
(defmethod driver/execute-reducible-query :crossdata
  [driver {{sql :query, :keys [params], :as inner-query} :native, :as outer-query} context respond]
  (let [inner-query (-> (assoc inner-query
                               :remark (qp.util/query->remark :crossdata outer-query)
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
(defmethod sql-jdbc.execute/do-with-connection-with-options :crossdata
  [driver db-or-id-or-spec options f]
  (sql-jdbc.execute/do-with-resolved-connection
   driver
   db-or-id-or-spec
   options
   (fn [^Connection conn]
     (when-not (sql-jdbc.execute/recursive-connection?)
       (.setTransactionIsolation conn Connection/TRANSACTION_READ_UNCOMMITTED))
     (f conn))))

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

(doseq [[feature supported?] {:basic-aggregations              true
                              :binning                         true
                              :expression-aggregations         true
                              :expressions                     true
                              :native-parameters               true
                              :nested-queries                  true
                              :standard-deviation-aggregations true
                              :metadata/key-constraints        false
                              :test/jvm-timezone-setting       false
                              ;; disabled for now, see issue #40991 to fix this.
                              :window-functions/cumulative false}]
  (defmethod driver/database-supports? [:crossdata feature] [_driver _feature _db] supported?))

;; only define an implementation for `:foreign-keys` if none exists already. In test extensions we define an alternate
;; implementation, and we don't want to stomp over that if it was loaded already
(when-not (get (methods driver/database-supports?) [:crossdata :foreign-keys])
  (defmethod driver/database-supports? [:crossdata :foreign-keys] [_driver _feature _db] true))

(defmethod sql.qp/quote-style :crossdata
  [_driver]
  :mysql)
