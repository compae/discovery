# Changelog

Only listing significant user-visible, not internal code cleanups and minor bug fixes.

## 0.40.2-0.1.0 (September 16, 2021)

* [ROCK-4531] Discovery in KEOS adaptations and descriptors
* [ROCK-4939] Discovery CICD adaptations to metabase 0.40.2 (breaking changes!)
* [ROCK-NA] Allow to execute multiline native queries in crossdata driver
* [ROCK-5174] Add missing db-start-of-the-week method to Stratio Crossdata driver
* [ROCK-5164] Disable auto-commit in stratiopostgres driver so PgBouncer can be used

## 0.38.1-0.2.4 (August 02, 2021)

* [ROCK-4906] Discovery 0.38.1-0.2.3 descriptor compatible with EOS 0.25 and Gosec 1.x
* [ROCK-5011] Fix: escape points in jwt regex in nginx conf

## 0.38.1-0.2.3 (July 12, 2021)

* [ROCK-NA] Update metabse to 0.38.1-0.2.2 (Fix: Do not try to auto-login in public api endpoints)

## 0.38.1-0.2.2 (June 16, 2021)

* [ROCK-4767] Update metabase to 0.38.1-0.2.1 (emails allowed as user header and automatic session renewal)

## 0.38.1-0.2.1 (June 09, 2021)

* [ROCK-4561] Update Crossdata jdbc driver to 2.22.1-d9034f2 (Fix for closed session when synchronizing)
* [ROCK-4708] Make nginx parameters configurable: worker_processes, worker_connections and client_max_body_size
* [ROCK-4778] Allow to have "discovery" as instance name (error while renaming jks)

## 0.38.1-0.2.0 (May 14, 2021)

* [ROCK-4533] Add impersonation via SET ROLE in stratiopostgres driver

## 0.38.1-0.1.1 (April 29, 2021)

* Update metabase-builder to 0.38.1-0.1.1 (fix uppercase names and delete old session cookies)

## 0.38.1-0.1.0 (April 12, 2021)

* New arrangement of the modules: The discovery docker image is now built in this repository

## 1.3.0-5ad831f (Built: November 20, 2020 | Released: November 23, 2020)

* Fix: Cannot pass parentId query param to import endpoint
* Fix: Handled exceptions via API
* Add configurable akka and metabase timeout variables
* Fix: Clean empty-string descriptions in cards

## 1.2.0-e81eb29 (Built: June 23, 2020 | Released: June 29, 2020)

* Fix visualization settings in dahscards (Fix from 1.1.1)
* Fix text cards in exported dashboards (Fix from 1.1.1)
* Add superuser check (Fix from 1.1.1)
* More informative errors (Fix from 1.1.2)
* Log steps of import process (Fix from 1.1.2)
* Do not fetch all fields while importing if humanization of DB names is disabled
* Create cards sending results_metadata and correct metadata_checksum

## 1.1.0-c05ce24 (Built: May 20, 2020 | Released: May 20, 2020)

* Application API
* Migrate sub-collections
* Change logback to Stratio log

## 1.0.0-38d0608 (Built: May 06, 2020 | Released: May 20, 2020)

* First version
