(ns metabase.driver.stratiopostgres
  "Postgres driver that also sets a custom variable/postgres role with the name or email of the current user"
  (:require
   [clojure.string :as str]
   [metabase.driver :as driver]
   [metabase.driver-api.core :as driver-api]
   [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
   [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute :refer [inject-remark
                                                                  do-with-connection-with-options
                                                                  statement-or-prepared-statement
                                                                  column-metadata
                                                                  reducible-rows]]
   #_{:clj-kondo/ignore [:metabase/modules]}
   [metabase.stratio.middleware :as st.mw]
   [metabase.util.i18n :refer [tru]]
   [metabase.util.log :as log])
  (:import [java.sql Connection ResultSet SQLFeatureNotSupportedException]))

(set! *warn-on-reflection* true)

(driver/register! :stratiopostgres, :parent :postgres)

(def ^:private ssl-options
  (str
   "ssl=true&"
   "sslmode=verify-full&"
   "sslcert=/etc/pki/discovery.pem&"
   "sslkey=/etc/pki/discovery.pk8&"
   "sslrootcert=/etc/pki/ca-bundle.pem&"))

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

(defn- database-spec []
  (driver-api/database (driver-api/metadata-provider)))

(defn- user-variable-name []
  (let [details       (:details (database-spec))
        add-user-var? (:add-user-var details)
        variable-name (:user-var-name details)]
    (and add-user-var? variable-name)))

(defn- current-api-user []
  #_{:clj-kondo/ignore [:deprecated-var]}
  (:first_name @(driver-api/current-user)))

(defn- impersonate? []
  (and (get-in (database-spec) [:details :impersonate])
       (not st.mw/*is-sync-request?*)))

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
;; We also wrap some sql-jdbc.execute private names with #' so to be able to access them from here
#_{:clj-kondo/ignore [:missing-docstring]}
(do
  (def download? #'sql-jdbc.execute/download?)
  (def execute-statement-or-prepared-statement! #'sql-jdbc.execute/execute-statement-or-prepared-statement!))

(defn execute-reducible-query
  "Default impl of [[metabase.driver/execute-reducible-query]] for sql-jdbc drivers."
  {:added "0.35.0", :arglists '([driver query context respond])}
  [driver {{sql :query, params :params} :native, :as outer-query} _context respond]
  {:pre [(string? sql) (seq sql)]}
  (let [database (driver-api/database (driver-api/metadata-provider))
        sql      (if (get-in database [:details :include-user-id-and-hash] true)
                   (->> (driver-api/query->remark driver outer-query)
                        (inject-remark driver sql))
                   sql)
        max-rows (driver-api/determine-query-max-rows outer-query)]
    (do-with-connection-with-options
     driver
     (driver-api/database (driver-api/metadata-provider))
     {:session-timezone (driver-api/report-timezone-id-if-supported driver (driver-api/database (driver-api/metadata-provider)))
      :download? (download? (-> outer-query :info :context))}
     (fn [^Connection conn]
       (with-open [stmt          (statement-or-prepared-statement driver conn sql params (driver-api/canceled-chan))
                   ^ResultSet rs (try
                                   ;; < STRATIO - impersonate
                                   ;; We used to get the current value from the conn and then set it back in
                                   ;; the finally block just in case, but since some changes in Metabase (I
                                   ;; think it was from https://github.com/metabase/metabase/pull/22166 but
                                   ;; i am not sure) when we setAutoCommit in the finally block we get a
                                   ;; "portal C_X does not exist" error so we do not do that any more.
                                   ;;
                                   ;; The autoCommit false is needed so that pgbouncer does not switch the
                                   ;; connection where we have set the role/uservar just after executiong
                                   ;; the set role/uservar statement, so that then the query itself is executed
                                   ;; in another postgres connection without the role/uservar set (for us it
                                   ;; is the same connection we have with the bouncer, but the connection the
                                   ;; bouncer has with postgres may change, and it is in that connection
                                   ;; where the role/use is set
                                   (.setAutoCommit conn false)
                                   (set-user-if-needed! conn)
                                   (set-role-if-needed! conn)
                                   ;; STRATIO >
                                   (execute-statement-or-prepared-statement! driver stmt max-rows params sql)
                                   (catch Throwable e
                                     (throw (ex-info (tru "Error executing query: {0}" (ex-message e))
                                                     {:driver driver
                                                      :sql (str/split-lines (driver/prettify-native-form driver sql))
                                                      :params params
                                                      :type driver-api/qp.error-type.invalid-query}
                                                     e)))
                                   ;; < STRATIO - impersonate
                                   (finally
                                     (reset-role! conn)))]
                                   ;;  STRATIO >

         (let [rsmeta           (.getMetaData rs)
               results-metadata {:cols (column-metadata driver rsmeta)}]
           (try (respond results-metadata (reducible-rows driver rs rsmeta (driver-api/canceled-chan)))
                ;; Following cancels the statment on the dbms side.
                ;; It avoids blocking `.close` call, in case we reduced the results subset eg. by means of
                ;; [[metabase.query-processor.middleware.limit/limit-xform]] middleware, while statment is still
                ;; in progress. This problem was encountered on Redshift. For details see the issue #39018.
                ;; It also handles situation where query is canceled through [[driver-api/canceled-chan]] (#41448).
                (finally
                  ;; TODO: Following `when` is in place just to find out if vertica is flaking because of cancelations.
                  ;;       It should be removed afterwards!
                  (when-not (= :vertica driver)
                    (try (.cancel stmt)
                         (catch SQLFeatureNotSupportedException _
                           (log/warnf "Statemet's `.cancel` method is not supported by the `%s` driver."
                                      (name driver)))
                         (catch Throwable _
                           (log/warn "Statement cancelation failed."))))))))))))


(defmethod driver/execute-reducible-query :stratiopostgres
  [driver query chans respond]
  (execute-reducible-query driver query chans respond))
