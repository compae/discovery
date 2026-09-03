# Stratio Discovery

This file provides guidance to Claude Code (claude.ai/code) and to other AI coding agents when working with code in this repository.

## How to work in this repo

Four rules, in priority order. They exist because this repository is a **thin, opinionated layer on top of a large upstream product**, and the usual instinct — improve what you see — is wrong here.

1. **Understand before you change.** Most of what runs inside the image is Metabase, whose source is in a *different* repository (`Stratio/stratio-metabase`, the fork of `metabase/metabase`). Before editing, work out which codebase owns the behavior (see [Where each fact lives](#where-each-fact-lives)). State the assumption you are working under; if two readings of the request lead to different code, ask instead of guessing.
2. **Smallest change that works.** The whole point of this repo is to keep the delta over upstream Metabase minimal, because every line of delta is a line to re-apply on the next Metabase bump. Do not add abstractions, configuration knobs or error handling nobody asked for. A new environment variable is a permanent cost: it has to be added to the CCT descriptor, to `environment-variables.adoc` in both language trees, and carried forward forever.
3. **Surgical edits.** Do not reformat, refactor or "fix" code you were not asked to touch, and do not delete pre-existing dead code as a drive-by. `docker-entrypoint/sql/*.sql` and the `--XX:+UnlockExperimentalVMOptions` line in `metabase-environment.sh` are dead and known to be dead — leave them unless removing them *is* the task. Every changed line must trace to the request.
4. **Make the goal verifiable.** Turn "fix the sync" into "this query against a Virtualizer catalog with N schemas completes without the connection-pool timeout". If the change is behavioral, say how you confirmed it — a build, a log line, a descriptor diff. If you could not verify it, say so plainly instead of implying you did.

**Language:** all code, comments, log messages, commit messages and CHANGELOG entries in English. Only `stratio-docs/es/**` is in Spanish.

## What this is

_Stratio Discovery_ is Stratio's data-exploration and visualization tool, part of the _Stratio Generative AI Data Fabric_. It is **Metabase integrated into the Stratio ecosystem**: this repository builds the `discovery` Docker image, which bundles the Stratio-modified Metabase, Stratio's own Metabase drivers, the collection export/import service, and an nginx front end.

This repository is **`Stratio/discovery`** (`upstream`). The Metabase fork is a *different* repository, **`Stratio/stratio-metabase`** — only changes to the Metabase core go there — and its build produces the `stratio-metabase-builder` image that stage 1 of this `Dockerfile` starts from. Do not confuse the two.

## Where each fact lives

| Question about… | Answer lives in |
|---|---|
| Container start-up, certificates, environment preparation | `docker-entrypoint.sh`, `docker-entrypoint/*.sh` (here) |
| The reverse proxy, the audit log, the health endpoint | `nginx/nginx.conf.template`, `service/*/run` (here) |
| The `crossdata`, `stratiopostgres` and repackaged `hive-like` Metabase drivers | `metabase/modules/drivers/**` (here) |
| The bundled JDBC driver of Stratio Virtualizer | `virtualizer-jdbc/pom.xml` (here) |
| Collection export/import | `discovery-cicd/src/main/scala/**` (here) |
| Metabase logger names and log levels | `log4j2.xml` (here) — Metabase only |
| `discovery-cicd` logger names and log levels | `discovery-cicd/src/main/resources/log4j2.xml` (here) |
| Stratio's DB migrations (subscription triggers) | `metabase/resources/migrations/` (here) |
| Metabase behavior, the auth middleware, the schema migrations, the exclusion of the `sparksql` driver | the **`Stratio/stratio-metabase`** fork (elsewhere) |
| Deployment options an operator sees, probes, resources | `packages/discovery/discovery.json` in **`Stratio/kubernetes-universe`**, on the branch of the target Universe (elsewhere) |

**The descriptor in `deployment/CCT/k8s-descriptors/discovery.json` is the development copy; the released one is in `kubernetes-universe`.** They happen to be identical today, but nothing keeps them in sync, and only the released one tells you what an operator on a given Universe actually sees. Read it with:

```bash
gh api -H "Accept: application/vnd.github.raw" \
  "repos/Stratio/kubernetes-universe/contents/packages/discovery/discovery.json?ref=branch-15.1"
```

## Repository layout

```
Dockerfile              # 2 stages: driver builder (from stratio-metabase-builder) → ubuntu:24.04 runner
docker-entrypoint.sh    # container entrypoint; sources the scripts below, then exec s6-svscan
docker-entrypoint/      # nginx-conf, secrets, crossdata, extra-jars, cicd-conf, metabase-environment, cert-utils
service/                # s6 service definitions: nginx, metabase, discovery-cicd (copied to /etc/sv)
nginx/                  # nginx.conf.template (envsubst'd at start-up)
metabase/modules/       # Clojure source of three Metabase drivers: crossdata, stratiopostgres, hive-like
metabase/resources/     # Stratio's Liquibase changesets, appended into the Metabase uberjar at build time
discovery-cicd/         # Scala/Akka-HTTP collection export-import service (Maven module)
virtualizer-jdbc/       # Maven module that shades the Stratio Virtualizer JDBC driver
deployment/CCT/         # k8s descriptor (development copy)
testPF/                 # Gatling performance tests (not a Maven module, not run in CI)
stratio-docs/           # product documentation (see below)
.claude/skills/         # write-stratio-docs: the documentation style guide
log4j2.xml              # logging for the METABASE JVM only (discovery-cicd has its own, inside its jar)
Jenkinsfile             # CI; carries UPSTREAM_VERSION (the Metabase half of the version)
pom.xml                 # thin aggregator over discovery-cicd and virtualizer-jdbc
CHANGELOG.md            # user-visible changes only, [ROCK-NNNNN] Jira refs
```

`pom.xml` is a thin aggregator (`discovery-parent`) over two modules: `discovery-cicd` and `virtualizer-jdbc`. The Clojure drivers are **not** built by Maven — they are built inside stage 1 of the `Dockerfile`.

## Common commands

```bash
# Build the two Maven modules (needed before the Docker build: it COPYs their target/ jars)
mvn clean install -DskipTests

# Build a single module (-pl takes the DIRECTORY; the artifactId is `discoverycicd`, no hyphen)
mvn -pl discovery-cicd clean package
mvn -pl virtualizer-jdbc clean package

# Run the only test suite there is (discovery-cicd)
mvn -pl discovery-cicd test

# Build the image (needs the Maven artifacts AND access to qa.int.stratio.com for the stage-1 base image)
docker build -t discovery:local .
```

Both Maven commands need Stratio's Maven repository in `~/.m2/settings.xml`: the root POM inherits from `com.stratio:parent:0.14.0`, which is not on Central, so without it the build fails immediately on parent resolution. `discovery-cicd` is Scala 2.12.15 and its compiler cannot read JDK 21 class files: on a JDK 21 default, `mvn clean install`, `mvn -pl discovery-cicd package` and `mvn -pl discovery-cicd test` all die inside `scala-maven-plugin:compile` with a `scala.reflect.internal.FatalError` from `ClassfileParser` that names neither Scala nor the JDK. Build it with `JAVA_HOME` pointing at a JDK 17 (`virtualizer-jdbc` builds fine on 21, so the failure looks module-specific when it is really a JDK problem). **The Clojure drivers cannot be built locally** — they are only built inside stage 1 of the `Dockerfile`, with the `bin/build-driver.sh` of the builder image.

CI is Jenkins (`Jenkinsfile`): `doPackage` → publish the CCT descriptors over SSH → `doDockers`. `GRYPE_TEST = true`, so a new vulnerable dependency fails the build. The `DOC` stage runs `doStratioDocsChecks` over `stratio-docs/`.

⚠️ The `Jenkinsfile` publishes three descriptor directories, but only `deployment/CCT/k8s-descriptors/*` still exists. `descriptors/` held six files: `836e236` (Dec 2022) removed the four `*-compatible*` ones and left `rules/` untouched, and `8517b20` ([ROCK-13536], March 2025) removed the remaining `discovery-default.json` and `discovery-governance.json` plus `rules/discovery-secrets.drl`, emptying both directories. The `doSsh` calls date from `83ff27a` (April 2021) and were never cleaned up. Harmless leftover — do not "fix" it without checking with the build owners.

## Container architecture

One container, no sidecars (plus a Vault-injector init container added by the descriptor). `docker-entrypoint.sh` runs the preparation scripts **in order**, then hands over to `s6-svscan /etc/sv`, which starts three services **in parallel** — there is no ordering or dependency between them, so nginx can be accepting connections before Metabase is ready.

| Process | Port | Bind |
|---|---|---|
| nginx (TLS, the only public entry point) | 443 | all |
| nginx (health endpoint `/api/health`, plain HTTP) | 3009 | all |
| Metabase (Jetty) | 3000 | `127.0.0.1` |
| `discovery-cicd` | 8087 | localhost |
| jmx_prometheus agent (if `PROMETHEUS_METRICS_ENABLE=true`) | 6080 | — |

The descriptor also declares `containerPort: 80`; **nothing listens there** — it is residual. Metabase's `3000` is its own default — `MB_JETTY_PORT` is set nowhere — but the number is hardcoded in four places: `nginx/nginx.conf.template` twice (both `proxy_pass`) and `discovery-cicd`'s own client defaults, in `src/main/resources/application.conf` (overridable at runtime with `DISCOVERY_CICD_APICLIENT_METABASE_URL`) and in `src/main/scala/com/stratio/discoverycicd/Config.scala` (the CLI default). Changing Metabase's port means changing nginx *and* pointing `discovery-cicd` at the new one, or collection export/import silently keeps calling the old one. The jmx_prometheus agent is attached only to the Metabase JVM.

**s6 + `/env_vars` is the mechanism to know, and it is where a new environment variable is won or lost.** `docker-entrypoint/metabase-environment.sh` writes one file per variable into `/env_vars/`, and `s6-envdir /env_vars` turns them back into the environment of each service. `/env_vars` is how the entry point hands **computed, generated or forced** values to the supervised services: the assembled `MB_DB_CONNECTION_URI`, the key-store passwords generated in `secrets.sh`, `JAVA_OPTS`, the hardcoded Metabase defaults (`MB_PLUGINS_DIR`, `MB_HUMANIZATION_STRATEGY=none`, …) and the `gosec-sso`-conditional JWT settings — 25 names in total: 21 written unconditionally with `echo … > /env_vars/X` and 4 through `write_to_file_if_exists`. A plain pass-through variable declared in the descriptor needs **no** `/env_vars` write: `docker-entrypoint.sh` ends in `exec /usr/bin/s6-svscan /etc/sv`, so the whole container environment is inherited by every `service/*/run` and by the JVMs they exec, and `s6-envdir` does not remove anything it has no file for. Watch the reverse, though: for the 21 unconditional names the `echo` also *overrides* the inherited value, and an unset variable leaves a file holding just a newline, so the service receives an empty value instead of the inherited one. `SERVICE_LOG_LEVEL` is the proof: it is never written to `/env_vars` and `log4j2.xml` still reads it. Two writing patterns coexist in that script — an unconditional `echo … > /env_vars/X`, which creates the file even when the value is empty, and `write_to_file_if_exists`, which only writes when the variable has a value. Pick the one that matches whether an empty value is meaningful.

Three exceptions to the `s6-envdir` rule: `JAVA_OPTS` and `DISCOVERY_CICD_MEMORY_OPTS` are read from `/env_vars` with `cat` in `service/*/run` and passed on the command line, because `s6-envdir` only applies the environment to the *spawned* process; and `DISCOVERY_CICD_JAVA_OPTIONS` is read straight from the container environment and never written to `/env_vars` at all.

**Adding an environment variable end to end:** declare it in the CCT descriptor (in `kubernetes-universe`, with its `internalName`, `title`, `default` and `level`) → write it to `/env_vars` from the right `docker-entrypoint/` script **only if its value has to be computed, generated or forced**, since a pass-through value already reaches the services through the inherited environment → document it in `installing-and-upgrading/environment-variables.adoc` in **both** language trees.

## Database and migrations

State lives **only in PostgreSQL** — the Metabase application database, reached over mutual TLS (`sslmode=verify-full`, certificates from `/etc/pki`). `"volumes": []`: everything else in the container is regenerated at each start.

Schema migrations are Metabase's (Liquibase, inside the fork). **Stratio's own changesets are injected into the Metabase uberjar at build time**, not run by a separate script: `Dockerfile` extracts `migrations/000_legacy_migrations.yaml` and `migrations/initialization/metabase_postgres.sql` from the jar, appends `metabase/resources/migrations/100_stratio.yaml` and `subscription_triggers.sql`, and zips them back in. The two files cover two **disjoint** cases, and neither reaches an existing 0.45+ database: `subscription_triggers.sql` is appended to the 0.44 baseline SQL, whose changeset `v00.00-000` carries `preConditions: not tableExists core_user` with `onFail: MARK_RAN`, so it runs on **fresh installs only**; and `100_stratio.yaml` is appended to `000_legacy_migrations.yaml`, which Liquibase loads only when the last applied changeset is older than v45 — that is, upgrades from Metabase 0.44 or earlier. A Stratio changeset that must reach current installations would have to be appended to `migrations/001_update_migrations.yaml` too. See `metabase/resources/migrations/Readme.md`.

What Stratio's migrations do today: create the `stratio_insert_subscriptions` / `stratio_update_subscriptions` triggers that keep every dashboard subscription's name unique and different from its dashboard's, so collection export/import can round-trip them.

`docker-entrypoint/sql/create_triggers_subscription.sql` and `drop_triggers_subscription.sql` are **dead and misleading** — nothing references them, and the first creates `stratio_update_subscription` (singular), which is not the real trigger name. Worse, the second does `DROP FUNCTION … CASCADE` on functions that *do* exist in production. Never run them by hand and do not use them as a reference.

**How the migration actually behaves** (verified in the fork and in Liquibase 4.33.0, and counter-intuitive enough to be worth writing down):

- **It is not atomic.** `app_db/setup.clj` wraps it in a connection with `autoCommit=false` and its own docstring claims migrations "run inside a transaction", but Liquibase commits after **every changeset** (`runInTransaction` defaults to true, per changeset). A failure halfway leaves the schema at an intermediate point, with the successful changesets already recorded in `DATABASECHANGELOG`. s6 restarts the JVM and the retry resumes from the changeset that failed.
- **`MB_DB_AUTOMIGRATE` defaults to true and Stratio never sets it**, so the migration always runs by itself at start-up.
- **Jetty comes up before the migration finishes.** While migrating, `/api/health` answers `503 {"status":"initializing","progress":0.3}` — that is how to tell a migration in progress from a failed start-up.
- **The Liquibase log is silenced by default.** The Root logger in `log4j2.xml` is `${env:SERVICE_LOG_LEVEL:-ERROR}` and there is no explicit `<Logger>` for `liquibase.*`, so only Metabase's own start/end messages appear. Start with `SERVICE_LOG_LEVEL=INFO` to see per-changeset progress.
- **The lock changed between the two release lines.** 0.50.21 used Liquibase's `DATABASECHANGELOGLOCK` table, which could stay held if the process died; 0.56.4 bundles `liquibase-sessionlock` and uses a PostgreSQL advisory lock, which is released automatically. Consequence: on 0.56.4 `migrate release-locks` **throws** on PostgreSQL, so the advice in Metabase's own error message and troubleshooting docs no longer applies here.
- **`migrate down` exists but only goes back one major version**, and the CLI does not accept a target, so it cannot take a 0.56.4 schema to 0.50.21. Metabase refuses to start on a newer schema (`ERROR: Downgrade detected`). There is **no rollback path** other than restoring a dump of the metadata database and reverting the service version in _Stratio Command Center_.
- Metabase's own launcher passes `--add-opens java.base/java.nio=ALL-UNNAMED`; **this image does not**, and `-XX:+IgnoreUnrecognizedVMOptions` does not cover it. It matters for the `migrate` subcommands.

## Authentication flow

1. The KEOS ingress delegates authentication to the **`oauth2proxy` of Stratio KEOS** (`nginx.ingress.kubernetes.io/auth-url` / `auth-signin` annotations in the descriptor).
2. The proxy sets the **`stratio-cookie`** (name configurable via `JWT_COOKIE_NAME`), carrying a JWT.
3. The nginx **inside** the container no longer does OAuth: it is a TLS proxy plus the health server (with `DOWNLOAD_SECRETS=false` it serves 443 without TLS — a development-only path). It only reads the `Metabase-User` response header to write the audit access log.
4. Metabase validates the JWT with the public key that `docker-entrypoint/secrets.sh` copies from `/vault/secrets/jwt-public-key.pem` — **only when `AUTHENTICATOR = "gosec-sso"`**.
5. Metabase creates/synchronizes the user and groups (`ALLOWED_GROUPS`, `ADMIN_GROUP`, `USE_GROUP_WHITELIST`, `CREATE_AND_SYNC_GROUPS`) and returns the `metabase.SESSION` cookie. That logic is in the fork.

**`AUTHENTICATOR` is the one to be careful with.** The CCT descriptor still offers four values (`gosec-sso`, `metabase-native`, `headers`, `jwt`), but **the image's scripts only special-case `gosec-sso`**: with any other value the JWT public key is not copied, `JWT_PUBLIC_KEY_LOCATION` is not set, and `JWT_USERNAME_CLAIM` loses its `uid` default. `USE_GOSEC_SSO_AUTH`, which used to force the value, disappeared with `rc.local` — a legacy deployment that only set that variable now loses the forcing silently.

## Drivers

- **`crossdata`** — display name "Stratio Virtualizer". A **subclass** of Metabase's `sparksql` driver (`(driver/register! :crossdata :parent :sparksql)`), which itself inherits from `hive-like`; individual methods are overridden or delegated, so a Metabase bump can change `crossdata` behaviour through its parent. JDBC class `com.stratio.jdbc.core.jdbc4.StratioDriver`; runtime config generated by `docker-entrypoint/crossdata.sh` into `/etc/sds/crossdata/shell/driver-application.conf` (Pekko-based since 0.56.4; it was Akka before).
- **`stratiopostgres`** — Metabase's `postgres` driver plus impersonation: it sets a custom PostgreSQL variable / `SET ROLE` to the logged-in user before each query.
- The JDBC driver of Virtualizer is shaded by the `virtualizer-jdbc` module into `/crossdata-jdbc-shaded.jar` and copied into `/plugins` at start-up **unless** `VIRTUALIZER_JDBC_FROM_EXTRA_JARS=true`, in which case it is expected to arrive through `EXTRA_JARS`.
- **`hive-like`** is Stratio's own repackaging of Metabase's `hive-like` driver with the vulnerable dependency removed. It exists only so that `crossdata` can inherit from it: the `Dockerfile` deletes the upstream `modules/hive-like.metabase-driver.jar` from the uberjar and ships Stratio's build instead. `sparksql` stays, which is what makes the inheritance chain work; as the `Dockerfile` comment says, Spark SQL and the other hive-like drivers stop working as user-facing drivers, but they are only needed to be inherited from.
- **`databricks`, `athena` and the upstream `hive-like`** are deleted from the uberjar at build time (vulnerabilities). Connections to Databricks or Athena do not work — that is deliberate.

**Working on a driver:** each one is `metabase/modules/drivers/<name>/` with a `deps.edn` and a `resources/metabase-plugin.yaml`; `metabase/modules/drivers/stratio-repo.edn` is copied to `/root/.clojure/deps.edn` in stage 1 so the Stratio Maven repo is reachable from Clojure. There is **no local build loop** — the recommended flow is to develop them inside the Metabase fork and bring the result back here. When you bump the Virtualizer JDBC version in `virtualizer-jdbc/pom.xml`, also check `crossdata/deps.edn`, which warns that the driver's `reference.conf` must be updated with it.

## Versioning and branches

The version is **`${Metabase version}-${Stratio version}`**: in `0.56.4-0.2.0`, `0.56.4` is the bundled Metabase and `0.2.0` is Stratio's own. Always write both halves — a bare `0.56.4` reads as the Metabase version. `Jenkinsfile` carries `UPSTREAM_VERSION` (the Metabase half) and `VERSIONING_TYPE = "stratioVersion-3-3"`.

**Bumping the Metabase version touches seven places**, and missing one breaks something silently: `Dockerfile` (the `stratio-metabase-builder` tag of stage 1, and the `METABASE_VERSION` env var that feeds the `MANIFEST.MF` rewrite and therefore the CVE scan), `Jenkinsfile` (`UPSTREAM_VERSION`), both `stratio-docs/*/antora.yml` (`version`), `CHANGELOG.md`, the git tag, and `deployment/CCT/k8s-descriptors/discovery.json` (the container `image` tag, which carries the full `${Metabase version}-${Stratio version}` string and is hand-edited on every version move — the released copy in `kubernetes-universe` needs the same edit). The bump itself happens first in `Stratio/stratio-metabase`; `metabase/bin/update-metabase-dep.sh` is the helper that updates the `metabase-core` dependency, and it runs inside the builder image.

**Two release lines are maintained in parallel**, so "the latest" depends on which one you mean:

| Version | Metabase | Universes |
|---|---|---|
| `0.50.21-0.2.x` (`upstream/branch-0.50.21-0.2`) | 0.50.21 | 14.8, 14.9, 14.10 |
| `0.56.4-0.1.x` (`upstream/branch-0.56.4-0.1`) | 0.56.4 | 15.0 |
| `0.56.4-0.2.x` (`upstream/branch-0.56.4-0.2`) | 0.56.4 | 15.1 |

Before claiming a change is or is not released, read the other branch's CHANGELOG (`git show upstream/branch-0.50.21-0.2:CHANGELOG.md`) — a fix often has to be ported, and the 0.50.21 line carries releases that this branch's CHANGELOG does not list. Sorting by version string is misleading too: `0.56.4-0.1.3` is newer than `0.50.21-0.2.0` in the first half and older in the second.

**Git flow (Stratio):** `upstream` is `Stratio/discovery`, `origin` is your fork. Branch from `upstream/<release branch>`, open the PR from the fork to `upstream`. CHANGELOG entries reference Jira as `[ROCK-NNNNN]` and list only user-visible changes.

## Documentation (stratio-docs)

Product documentation lives under `stratio-docs/` (AsciiDoc + Antora; component `stratio-discovery`; mirrored `es/` and `en/` trees). **Before writing, editing, OR reviewing any `.adoc` page, load the `write-stratio-docs` skill** (`.claude/skills/write-stratio-docs/`): it and its four reference files are the documentation team's single source of editorial truth, and this file deliberately does not restate them.

Four things worth knowing *before* you decide whether the change is a documentation change at all:

- **Every page exists twice and both copies must change together, with the same line count.** The `LanguageCheck` CI gate compares line and structure parity between `es/` and `en/`, so a single-language fix fails the build. That constraint shapes what a "small doc fix" can be: correcting a BOM or a typo in one tree only is not an option.
- **The mirror is not a literal translation.** Italics recalibrate per language, and anchors are per language too (headings are translated, so their generated ids differ). Never copy an anchor across trees.
- **Facts in the docs are verified against three different places:** versions and dates against `CHANGELOG.md` and the git tag; environment variables and logger names against `docker-entrypoint/` and `log4j2.xml`; deployment options against the descriptor of the **target Universe** in `kubernetes-universe`. An option that moved to the advanced view has not been removed.
- **Upgrade planning has its own shape.** `installing-and-upgrading/upgrade-overview.adoc` is the decision index and `upgrade-overview/from-<x>-to-<y>.adoc` is one fact sheet per path; the page title and section order are shared across every Stratio module, so follow the skeleton in the skill's `page-templates.md` rather than inventing one.

## Critical gotchas

- **The image build needs the Maven artifacts first.** `Dockerfile` `COPY`s `discovery-cicd/target/discoverycicd-*-uber.jar` and `virtualizer-jdbc/target/crossdata-jdbc-shaded.jar`; run `mvn install` before `docker build` or the build fails late and confusingly.
- **The Metabase uberjar is patched, not just used.** Stage 2 rewrites its `MANIFEST.MF` (so vulnerability scanners attribute Metabase's CVEs correctly), appends the Stratio migrations, and deletes three driver modules. Any change to that block affects both security scanning and the migration path.
- **A hard failure in `docker-entrypoint/` kills the container silently from the operator's point of view** — the pod just never becomes ready. The `exit 1` paths are: PKCS8 conversion of the private key, the two JKS conversions, and `MB_DB_SSL=true` with `DOWNLOAD_SECRETS=false`. If certificates do not arrive at `/vault/secrets/`, the first `cp` in `secrets.sh` already fails.
- **The start-up budget is the startup probe: 5 s × 60 ≈ 300 s.** A Metabase schema migration that takes longer gets the pod killed and restarted, which restarts the migration. There is no configuration in this repo that changes that other than the probe values in the descriptor.
- **`MB_JDBC_DATA_WAREHOUSE_UNRETURNED_CONNECTION_TIMEOUT_SECONDS` defaults to `0`** (no timeout) since 0.56.4-0.2.0. It fixed sync failures on large Virtualizer collections, but a leaked connection is now never reclaimed.
- **`MB_HUMANIZATION_STRATEGY` is forced to `none` unconditionally** in `metabase-environment.sh`. Any advice about toggling friendly table names in the UI is inapplicable.
- **No high availability.** `highAvailability: false`, one replica, no leader election and no shared session state in the container — so nothing may assume a second instance, and every upgrade is a full outage.
