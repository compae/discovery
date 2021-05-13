# Changelog

Only listing significant user-visible, not internal code cleanups and minor bug fixes.

## 0.38.1-0.2.0 (upcoming)

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
