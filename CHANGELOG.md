# Changelog

Only listing significant user-visible, not internal code cleanups and minor bug fixes.

## 0.50.21-0.1.3 (2025-05-12)

* Add env. variable to download extra jars to the plugins folder
* [ROCK-13601] Support filter values from other questions in discovery-cicd
* [ROCK-13616] Make param splicing style in Crossdata MB driver configurable (whether to hex-encode params in sql or not)
* [ROCK-13627] Fix Virtualizer driver: support syncing schema and table names with hyphens (bump virtualizer jdbc to 2.31.0)
* [ROCK-13689] Fix Virtualizer driver: use correct SQL dialect to prettify SQL

## 0.50.21-0.1.2 (2024-11-13)

* [ROCK-13158] Fix: downgrade Crossdata JDBC to 2.27.0-83e371f (fix queries in virtualizer +2k rows)

## 0.50.21-0.1.1 (2024-11-07)

* [ROCK-13154] Environment variable to avoid the statement cancellation that Metabase does in all queries

## 0.50.21-0.1.0 (2024-10-07)

* [ROCK-11848] Update stratio-metabase to 0.50.21-0.1.0
* [ROCK-12171] SSO via KEOS oauth2proxy
* [ROCK-11267] Fix: Improper tenant validation
* [ROCK-13006] Fix vulnerabilities: upgrade oauth-base image to 0.4.1

## 0.43.4-0.3.0 (2024-02-06)

* [ROCK-12172] Subscriptions with dashboard name in subject (bump stratio-metabase to 0.43.4-0.2.0)
* [ROCK-XXXXX] Upgrade oauth-base to 0.4.0

## 0.43.4-0.2.0 (2023-07-21)

* [ROCK-10498] Snippets promotion in discovery CI/CD
* [ROCK-9237] Use percentages to configure the JVM memory in discovery and discovery-cicd
* [ROCK-10170] Handle dashcard links to other questions or dashboards in discovery CI/CD
* [ROCK-10529] CCT placements in Discovery 
* [ROCK-10696] CCT priority class added
* [ROCK-9940] Fix vulnerabilities: Tracing session cookies on DEBUG via jetty
* [ROCK-10759] Fix vulnerabilities: update prometheus java agent to 0.19.0 to resolve the snakeyaml security vulnerability (GHSA-mjmj-j48q-9wg2)
* [ROCK-10613] Fix: avoid errors when configuring Discovery CI/CD timeouts

## 0.43.4-0.1.4 (2023-04-20)

* [ROCK-10071] Take into account cards using other cards as input data in discovery-cicd

## 0.43.4-0.1.3 (2023-02-16)

* [ROCK-8559] Fix: bump Crossdata JDBC to 2.24.0-7394c02 (fix sync of Delta tables)

## 0.43.4-0.1.2 (2022-12-12)

* [ROCK-9193] Fix: Do not return fetchSize error in graphic queries with limit (bump Crossdata JDBC to 2.23.0-b2ae129)

## 0.43.4-0.1.1 (2022-10-25)

* [ROCK-8674] Fix: Sync admin status correctly (bump stratio-metabase to 0.43.4-0.1.1)

## 0.43.4-0.1.0 (2022-10-07)

* [ROCK-7390] Upgrade Metabase 0.43
* [ROCK-6458] Subscription promotion in discovery CI/CD
* [ROCK-7181] Improvements in error messages of discovery CICD API
* [ROCK-8025] Fix 'portal "C_x" does not exist' error in stratiopostgres driver
* [ROCK-8431] [ROCK-8438] Fix security vulnerabilities of H2 embedded database (CVE-2022-23221)
* [ROCK-8559] Fix: bump Crossdata JDBC to 2.22.7-e2c665f (apply limit when user impersonation is enabled)
* Fix vulnerabilities: Update prometheus java agent to 0.17.1 to resolve the org.yaml:snakeyaml security vulnerability (GHSA-3mc7-4q67-w48m)

## Previous development

### 0.42.2-0.1.1 (2022-06-13)

* [ROCK-7358] Add missing sync-scheduling options to our metabase drivers

### 0.42.2-0.1.0 (2022-03-22)

* [ROCK-6000] Upgrade to metabase 0.42
* [ROCK-6000] Add setting to add all needed ssl options with a simple click in stratiopostgres driver
* [ROCK-6269] Fix prometheus metrics

### 0.40.7-0.1.1 (2022-02-02)

* [ROCK-5792] Fix: do not compare names with display_names while filtering fields
* [ROCK-5951] Fix vulnerabilities: Embedded nginx offers insecure TLS protocols
* [ROCK-5913] Fix vulnerabilities: Broken Auth when using gosec-sso authenticator

### Branched to branch-0.40.7-0.1 (2021-12-14)

* [ROCK-5607] Discovery CICD: preserve dashboard and question IDs when importing 
* [ROCK-5578] Upgrade to metabase 0.40.7 (builder image 0.40.7-0.1.0)
* [ROCK-5578] Fix vulnerabilities: Update to Java 11
* [ROCK-5578] Fix vulnerabilities: Update prometheus java agent to 0.14.0
* [ROCK-5578] Fix vulnerabilities: Update metabase-builder to 0.40.7-0.1.0

### Branched to branch-0.40.5-0.1 (2021-11-23)

* [ROCK-5548] Update to metabase 0.40.5 (builder image 0.40.5-0.1.0)

### Branched to branch-0.40.2-0.1 (2021-09-16)

* [ROCK-4531] Discovery in KEOS adaptations and descriptors
* [ROCK-4939] Discovery CICD adaptations to metabase 0.40.2 (breaking changes!)
* [ROCK-NA] Allow to execute multiline native queries in crossdata driver
* [ROCK-5174] Add missing db-start-of-the-week method to Stratio Crossdata driver
* [ROCK-5164] Disable auto-commit in stratiopostgres driver so PgBouncer can be used

### Branched to branch-0.38.1-0.2 (2021-08-02)

* [ROCK-4906] Discovery 0.38.1-0.2.3 descriptor compatible with EOS 0.25 and Gosec 1.x
* [ROCK-5011] Fix: escape points in jwt regex in nginx conf

### Branched to branch-0.38.1-0.2 (2021-07-12)

* [ROCK-NA] Update metabse to 0.38.1-0.2.2 (Fix: Do not try to auto-login in public api endpoints)

### Branched to branch-0.38.1-0.2 (2021-06-16)

* [ROCK-4767] Update metabase to 0.38.1-0.2.1 (emails allowed as user header and automatic session renewal)

### Branched to branch-0.38.1-0.2 (2021-06-09)

* [ROCK-4561] Update Crossdata jdbc driver to 2.22.1-d9034f2 (Fix for closed session when synchronizing)
* [ROCK-4708] Make nginx parameters configurable: worker_processes, worker_connections and client_max_body_size
* [ROCK-4778] Allow to have "discovery" as instance name (error while renaming jks)

### Branched to branch-0.38.1-0.2 (2021-05-14)

* [ROCK-4533] Add impersonation via SET ROLE in stratiopostgres driver

### Branched to branch-0.38.1-0.1 (2021-04-29)

* Update metabase-builder to 0.38.1-0.1.1 (fix uppercase names and delete old session cookies)

### Branched to branch-0.38.1-0.1 (2021-04-12)

* New arrangement of the modules: The discovery docker image is now built in this repository

### Branched to branch-1.3 (2020-11-20)

* Fix: Cannot pass parentId query param to import endpoint
* Fix: Handled exceptions via API
* Add configurable akka and metabase timeout variables
* Fix: Clean empty-string descriptions in cards

### Branched to branch-1.2 (2020-06-23)

* Fix visualization settings in dahscards (Fix from 1.1.1)
* Fix text cards in exported dashboards (Fix from 1.1.1)
* Add superuser check (Fix from 1.1.1)
* More informative errors (Fix from 1.1.2)
* Log steps of import process (Fix from 1.1.2)
* Do not fetch all fields while importing if humanization of DB names is disabled
* Create cards sending results_metadata and correct metadata_checksum

### Branched to branch-1.1 (2020-05-20)

* Application API
* Migrate sub-collections
* Change logback to Stratio log

### Branched to branch-1.0 (2020-05-06)

* First version
