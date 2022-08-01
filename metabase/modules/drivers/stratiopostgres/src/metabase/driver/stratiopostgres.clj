(ns metabase.driver.stratiopostgres
  "Postgres driver that also sets a custom variable/postgres role with the name or email of the current user"
  (:require [clojure.core.async :as a]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [metabase.api.common :as api]
            [metabase.db.spec :as db.spec]
            [metabase.driver :as driver]
            [metabase.driver.sql-jdbc.common :as sql-jdbc.common]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.sync.common :as common]
            [metabase.driver.sql-jdbc.sync.describe-table :as sync.describe-table]
            [metabase.driver.sql-jdbc.sync.interface :as i]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.mbql.util :as mbql.u]
            [metabase.query-processor.context :as context]
            [metabase.query-processor.error-type :as qp.error-type]
            [metabase.query-processor.interface :as qp.i]
            [metabase.query-processor.middleware.limit :as limit]
            [metabase.query-processor.store :as qp.store]
            [metabase.query-processor.timezone :as qp.timezone]
            [metabase.query-processor.util :as qputil]
            [metabase.driver.postgres :as postgres]
            [metabase.stratio.auth :as st.auth]
            [metabase.stratio.middleware :as st.mw]
            [metabase.util :as u]
            [metabase.util.i18n :refer [trs tru]])
  (:import [java.sql Connection PreparedStatement ResultSet]))


(driver/register! :stratiopostgres, :parent :postgres)

(def ^:private ssl-options
  (str
   "ssl=true&"
   "sslmode=verify-full&"
   "sslcert=/etc/pki/discovery.pem&"
   "sslkey=/etc/pki/discovery.pk8&"
   "sslrootcert=/etc/pki/ca-bundle.pem&"))

;; Implementing our driver (:stratiopostgres) method for the describe-nested-field-columns multimethod
;; Since the method in sync.describe-table/describe-nested-field-columns does pattern matching only
;; wrt the following types (case driver :postgres :ansi :mysql :mysql), we will
;; explicitly pass as driver type the keyword :postgres to use the postgres method implementation.
(defmethod sql-jdbc.sync/describe-nested-field-columns :stratiopostgres
           [driver database table]
           (let [spec   (sql-jdbc.conn/db->pooled-connection-spec database)]
                (sync.describe-table/describe-nested-field-columns :postgres spec table)))

;; We override this method to make it easier to inject the "default" ssl properties
;; before calling the parent method so that users do not need to include
;; all these paths and params as "additional JDBC options" and just by
;; checking an option all the ssl things are configured for them
(defmethod sql-jdbc.conn/connection-details->spec :stratiopostgres
  [driver {use-ssl-authentication? :use-ssl-authentication, :as details-map}]
  (let [details-map-with-ssl (if use-ssl-authentication?
                               (update details-map :additional-options (partial str ssl-options))
                               details-map)]
    ((get-method sql-jdbc.conn/connection-details->spec :postgres) driver details-map-with-ssl)))

(defn- user-variable-name []
  (let [details       (:details (qp.store/database))
        add-user-var? (:add-user-var details)
        variable-name (:user-var-name details)]
    (and add-user-var? variable-name)))

(defn- current-api-user []
  (:first_name @api/*current-user*))

(defn- set-user-if-needed!
  [^Connection conn]
  (let [api-user     (current-api-user)
        user         (or api-user (str (java.util.UUID/randomUUID)))
        variable     (user-variable-name)
        set-user-sql (format "SET %s='%s';" variable user)]
    (if variable
      (with-open [stmt (.createStatement conn)]
        (if api-user
          (log/debug "Setting postgres variable with logged user with statement:" set-user-sql)
          (log/debug "*current-user* is not binded, will use random generated str for user value"))
        (try
          (.execute stmt set-user-sql)
          (catch Throwable e
            (log/error e "Failed to set variable with logged user")))
        (log/tracef "Successfully set variable %s with the logged user %s" variable user))
      (log/debug "Not setting variable with logged user"))))

(defn- impersonate? []
  (and (get-in (qp.store/database) [:details :impersonate])
       (not st.mw/*is-sync-request?*)))

(defn- set-role-if-needed!
  [^Connection conn]
  (let [user         (current-api-user)
        set-role-sql (format "SET ROLE '%s';" user)]
    (if (and (impersonate?) user)
      (with-open [stmt (.createStatement conn)]
        (log/debug "Setting ROLE as current logged user with sql:" set-role-sql)
        ;; Here we do not wrap in try because we want it to fail if we cannot set the role
        (.execute stmt set-role-sql)
        (log/tracef "Successfully set ROLE as the current logged user %s" user))
      (log/debug "Not setting ROLE with logged user"))))

(defn- reset-role!
  [^Connection conn]
  (with-open [stmt (.createStatement conn)]
    (.execute stmt "RESET ROLE")))

;; Copy paste of the sql-jdbc method but setting the role in the connection and reseting it aferwards,
;; and qualifying some simbols of the sql-jbcd.execute namespace
(defn execute-reducible-query
  "Default impl of `execute-reducible-query` for sql-jdbc drivers."
  {:added "0.35.0", :arglists '([driver query context respond] [driver sql params max-rows context respond])}
  ([driver {{sql :query, params :params} :native, :as outer-query} context respond]
   {:pre [(string? sql) (seq sql)]}
   (let [remark   (qputil/query->remark driver outer-query)
         sql      (str "-- " remark "\n" sql)
         max-rows (limit/determine-query-max-rows outer-query)]
        (execute-reducible-query driver sql params max-rows context respond)))

  ([driver sql params max-rows context respond]
   (with-open [conn          (sql-jdbc.execute/connection-with-timezone driver (qp.store/database) (qp.timezone/report-timezone-id-if-supported))
                             ;; the #' is a hack to be able to access the private sql-jdbc.execute/statement-or-prepared-statement var
               stmt          (#'sql-jdbc.execute/statement-or-prepared-statement driver conn sql params (context/canceled-chan context))
               ^ResultSet rs (let [auto-commit (.getAutoCommit conn)]
                               (try
                                 ;; < STRATIO - impersonate
                                 (.setAutoCommit conn false)
                                 (set-user-if-needed! conn)
                                 (set-role-if-needed! conn)
                                 ;;  STRATIO >
                                 (#'sql-jdbc.execute/execute-statement-or-prepared-statement! driver stmt max-rows params sql)
                                 (catch Throwable e
                                   (throw (ex-info (tru "Error executing query: {0}" (ex-message e))
                                                   {:sql sql, :params params, :type qp.error-type/invalid-query}
                                                   e)))
                                 ;; < STRATIO - impersonate
                                 (finally
                                   (reset-role! conn)
                                   (.rollback conn)
                                   (.setAutoCommit conn auto-commit))))]
                                 ;;  STRATIO >
     (let [rsmeta           (.getMetaData rs)
           results-metadata {:cols (sql-jdbc.execute/column-metadata driver rsmeta)}]
       (respond results-metadata (sql-jdbc.execute/reducible-rows driver rs rsmeta (context/canceled-chan context)))))))

(defmethod driver/execute-reducible-query :stratiopostgres
  [driver query chans respond]
  (execute-reducible-query driver query chans respond))
