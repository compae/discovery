(ns metabase.driver.stratiopostgres
  "Postgres driver that also sets a custom variable with the name or email of the current user"
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [metabase.api.common :as api]
            [metabase.driver :as driver]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.query-processor.store :as qp.store]
            [metabase.util :as u])
  (:import [java.sql Connection]))


(driver/register! :stratiopostgres, :parent :postgres)

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
        (.execute stmt set-user-sql)
        (log/tracef "Successfully set variable %s with the logged user %s" variable user))
      (log/debug "Not setting variable with logged user"))))

(defmethod sql-jdbc.execute/connection-with-timezone :stratiopostgres
  [driver database ^String timezone-id]
  (let [conn ((get-method sql-jdbc.execute/connection-with-timezone :postgres) driver database timezone-id)]
    (try
      (set-user-if-needed! conn)
      conn
      (catch Throwable e
        (log/error e "Failed to set variable with logged user")
        conn))))
