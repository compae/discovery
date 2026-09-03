# Page structure, placement and templates

How pages are built and wired into the Antora site, plus ready-to-paste skeletons per page type. All of it is derived from the real `stratio-docs/` tree of `discovery-cicd` (component `stratio-discovery`; 23 content pages per language, plus `nav.adoc` and the installation-guide partial). Load this when creating a NEW page or moving/renaming one; for pure style/wording use `decalogue.md` and the glossaries.

## Page anatomy (every page)

Every `.adoc` starts the same and carries **no document attributes** (no `:toc:`, `:description:`, `:keywords:`, `:page-*:`, `:sectnums:` — zero pages in the repo use them, verified). Antora renders the right-hand TOC from the `==`/`===` headings and the left tree from `nav.adoc`.

```
= Page title
                              ← one blank line
Intro paragraph that places the reader in the product/context BEFORE any detail
(general → specific, decalogue #6). A one-line NOTE/TIP linking a related page is common here.
                              ← one blank line
== First section
...
```

The `= Title` carries naming italics (`= Actualización de _Stratio Discovery_`), sentence case, no trailing period.

The EN tree used to carry a UTF-8 BOM on all 23 of its content pages (`nav.adoc` and the partial never did); the upgrade-documentation change stripped every one of them, so there is none left in either tree. Never add one to a new page. It used to be a defect that could not be fixed on its own, because stripping it is a one-language-only change on 23 files, which fails the `LanguageCheck` CI gate. Remove it only on the pages you are already editing in **both** trees, never as a standalone sweep. Never add one to a new page.

## Owns the truth (verify against the code)

_Stratio Discovery_ is **Metabase plus Stratio's own layer**, and the two live in different repositories:

| Claim about… | Verify in |
|---|---|
| Container start-up, processes, certificates, environment variables | `docker-entrypoint.sh`, `docker-entrypoint/*.sh` (this repo) |
| The reverse proxy, the audit log, the health endpoint | `nginx/nginx.conf.template`, `service/*/run` (this repo) |
| The `crossdata`, `stratiopostgres` and repackaged `hive-like` Metabase drivers | `metabase/modules/drivers/**` (this repo) |
| The bundled JDBC driver of _Stratio {modulename}_ | `virtualizer-jdbc/pom.xml` (this repo) |
| Collection export/import | `discovery-cicd/src/main/scala/**` (this repo) |
| Metabase log levels and logger names | `log4j2.xml` (this repo) |
| `discovery-cicd` log levels and logger names | `discovery-cicd/src/main/resources/log4j2.xml` (this repo) |
| Metabase behavior, the auth middleware, the DB migrations | the **`Stratio/stratio-metabase`** fork (another repo) — say so instead of guessing |
| Stratio's own DB migrations (the subscription triggers) | `metabase/resources/migrations/100_stratio.yaml` and `subscription_triggers.sql` (this repo) |
| Deployment options, tabs, defaults, probes, resources | `packages/discovery/discovery.json` in **`Stratio/kubernetes-universe`**, on the branch of the target Universe |

**The local `deployment/CCT/k8s-descriptors/discovery.json` is the development copy, not the released descriptor.** For anything an operator will see in _Stratio Command Center_, read the Universe branch:

```bash
gh api -H "Accept: application/vnd.github.raw" \
  "repos/Stratio/kubernetes-universe/contents/packages/discovery/discovery.json?ref=branch-15.1"
```

**Do not guess Metabase behavior.** If a claim depends on the Metabase UI or on its migration engine and you cannot confirm it in the fork, leave it out or mark it `// REVISAR:`. Note the repository names: this repo is `Stratio/discovery`, and the Metabase fork is the separate `Stratio/stratio-metabase`.

## Where content goes (naming & placement)

- **Filenames and folders: kebab-case, always** (`connect-to-postgresql-with-tls.adoc`).
- **Module landing page** = `<module>/pages/<module>.adoc` — a hub of `xref` links with short intros (`operations-guide.adoc`).
- **Hub + sibling folder:** a page with children is `X.adoc` **next to** a folder `X/`; the children live inside `X/`. Live examples: `installing-and-upgrading.adoc` + `installing-and-upgrading/`, `connect-with-other-modules.adoc` + `connect-with-other-modules/`, `what-is-new.adoc` + `what-is-new/`.
- **Images:** `<module>/assets/images/…`, referenced as `image::file.png[]`. Both trees hold their own copy with the same file name.
- **No attachments and no videos** exist in this repo.

## The modules (Antora component `stratio-discovery`)

Only three, and the split is not the one other Stratio repos use — **there is no `user-guide` module**:

| Module | Content |
|--------|---------|
| `ROOT` | Entry and end-user pages: `introduction` (start page), `what-is-new` (+ `what-is-new/release-notes`), `architecture`, `quick-start-guide`, `user-guide`, `known-issues`. Note that `user-guide.adoc` and `quick-start-guide.adoc` are **pages of ROOT**, so they are referenced `xref:ROOT:user-guide.adoc[…]`. |
| `operations-guide` | Everything an operator does: `operations-guide.adoc` (landing), `key-concepts`, `installing-and-upgrading/` (prerequisites, deployment, environment-variables, verify-installation, upgrade, upgrade-overview), `connect-with-other-modules/`, `basic-operations`, `configure-postgresql-acl`, `end-user-authentication`, `export-and-import-collections`, `troubleshooting`. |
| `installation-guide` | **Partial only**: `partials/installing-discovery.adoc`. It is not in the nav and is not `include::`d anywhere in this repo — it exists to be included from another component. |

## Registering a page in the navigation (do not skip)

A new page is **invisible** on the site until it is added to the navigation, and the nav is an **ES/EN mirror** just like the pages:

```
en/modules/ROOT/nav.adoc   AND   es/modules/ROOT/nav.adoc     ← single nav per language, in ROOT
```

- Entry format is a **2-part xref with the module prefix always explicit**, including ROOT: `** xref:<module>:<path>.adoc[]`.
- **The two navs disagree by design**: the ES nav writes an explicit Spanish label on almost every entry, the EN nav leaves `[]` (title reuse) on almost every one. Add your entry in the style of the file you are editing — an explicit label in ES, `[]` in EN — so the diff stays minimal, and keep both files at the same number of lines.
- Nesting (`*` / `**` / `***` / `****`) mirrors the hub+folder depth.
- Add the entry to **both** navs at the **same** position.

## Antora component (`antora.yml`)

Two mirrored files, `es/antora.yml` and `en/antora.yml`:

```yaml
name: stratio-discovery
title: Stratio Discovery
version: '0.56.4-0.2'
nav:
- modules/ROOT/nav.adoc
start_page: introduction.adoc
```

- `name: stratio-discovery` is the component that feeds the 3-part cross-component `xref`.
- **Do not change `version` when documenting a feature.** It is the docs-portal version segment, not a claim about the code.
- Note `start_page: introduction.adoc` has **no module prefix** here, unlike the `xref`s in the pages. Leave it as it is.

## Attribute references `{…}` and literal braces

- **`{modulename}`** resolves to Crossdata/Virtualizer at build time and is set by the Antora **playbook, outside this repo** — `antora.yml` declares no `asciidoc.attributes`. Write `_Stratio {modulename}_` and do not define the attribute here. `{modulenamexdata}` is used once, in `create-a-new-stratio-virtualizer-connection.adoc`, inside an `xref` component name.
- **`${VAR}`** placeholders shown to the reader (`${DISCOVERY_INSTANCE_NAME}`, `${haproxyhost}`, `${external_domain}`…) are values the user substitutes — tie them to R10. Wrap a URL containing one in the `+…+` passthrough so AsciiDoc does not mangle it, **with no space inside the passthrough**.
- To show a literal brace in prose, escape it: `$\{VAR}` (see R24). Not needed inside code blocks or backticks.

## Includes / partials

`installation-guide/partials/installing-discovery.adoc` is the only partial. **R17 exception, and it applies to images too:** inside it, both `xref` and `image::` are fully qualified with 3 parts even to its own component (`xref:stratio-discovery:operations-guide:…`, `image::stratio-discovery:installation-guide:discovery-installation-1.png[]`), because the partial is included from another component. Do not "fix" either of them to the short form — the images would stop resolving wherever the partial is included.

---

# Page skeletons by type

Reuse the closest skeleton; every one is mirrored in ES and EN.

## Hub / landing page — `<module>.adoc` or `X.adoc` beside `X/`

```
= <Title>

<One or two sentences saying what this part of the docs covers.>

<Optional WARNING/NOTE with the one thing the reader must know first.>

* xref:<module>:<child>.adoc[<Descriptive label>]
* xref:<module>:<child>.adoc[<Descriptive label>]
```

Keep the bullet list in reading order. `installing-and-upgrading.adoc` is the model, and note two details of it: the `WARNING` goes **before** the intro paragraph, not after; and `operations-guide.adoc`, the module landing page, nests its list with `**`/`***` to mirror the folder depth instead of keeping it flat.

## Configuration / procedure page

```
= <Configurar X>

<Intro: what this achieves and when you need it.>

== <Área de configuración o paso>
=== <Sub-área>
== <Siguiente área>
```

There is **no** `== Prerequisites` section inside a procedure page: prerequisites are a page of their own (`installing-and-upgrading/prerequisites.adoc`). `deployment.adoc` is the model for a screenshot-driven procedure — `== Configuración` with four `===` sub-areas, then `== Despliegue`. Screenshots go inline after the paragraph they illustrate, with a continuation `+` when they hang off a numbered step.

## Reference page (environment variables)

`installing-and-upgrading/environment-variables.adoc` holds four `|===` tables, one per area. **Its shape is not the one this guide would choose, but match it anyway** — a row written differently from its 40 siblings is worse than a consistent one:

```
= Variables de entorno

<Intro.>

== <Área>

|===
| Propiedad | Descripción | Posible/por defecto | Obligatorio | Tipo

| VARIABLE_NAME
| <Qué hace, una o dos frases.>
| `valor-por-defecto`
| Sí
| String
|===
```

Note what it does **not** do: no `[cols=…]`, no `options="header"` (the first row is simply written as the header), and **the variable name carries no backticks** in the first cell. Three of the four tables add a sixth column, `Obsoleto`, which is empty in 44 of its 46 rows (only `USE_DYNAMIC_AUTHENTICATION` and `SSO_URI` carry a value) — do not add rows to it, and do not remove the column as a drive-by (it changes the line count).

**Every new environment variable goes here**, not only in a release note. When you add one, add it in both trees at the same position.

## Troubleshooting entry — `operations-guide/pages/troubleshooting.adoc`

One `===` per symptom, grouped under exactly two `==` sections — `Problemas de instalación y configuración` and `Problemas de uso`, both linked from the intro by anchor. The heading is the symptom as the user experiences it (a noun phrase or a first-person sentence: `=== No puedo acceder`, `=== La sincronización no termina`), and the body is **plain prose**, not a cause/solution bullet pair:

```
=== <Síntoma, tal y como lo vive el usuario>

<Qué lo produce, en prosa.>

[source,text]
----
la línea de log literal, cuando ayuda
----

<Qué hacer, en prosa, con un xref a la página que lo detalla.>
```

The `* *Causa*:` / `* *Solución*:` bullet pair belongs to `known-issues.adoc`, not here — do not mix the two shapes.

**Known issue entry** — `ROOT/pages/known-issues.adoc`: one `==` per item, the heading stating the symptom, then cause and workaround in prose. When the issue is fixed, move it to the release notes instead of leaving a `NOTE: Arreglado en…` behind.

## "What is new" page — `ROOT/pages/what-is-new.adoc`

Novelties only: the breaking changes have their own page, `ROOT/pages/what-is-new/breaking-changes.adoc`, and this page links to it. Two sections, because the upgrade jumps straight from Universe 14.10 to 15.1 and the previous line never reached production:

```
= Novedades

Descubre las principales mejoras de _Stratio Discovery_ <version>, la versión del Universo <N.N>, lanzada el D de <mes> de AAAA. Como la actualización se realiza directamente desde el Universo <origen>, esta página recoge también las novedades incorporadas en la línea <anterior>. Antes de actualizar, revisa los xref:ROOT:what-is-new/breaking-changes.adoc#<ancla>[_breaking changes_], ya que pueden afectar a la compatibilidad. Para planificar la intervención, consulta la xref:operations-guide:installing-and-upgrading/upgrade-overview.adoc[planificación de la actualización].

TIP: Consulta el detalle completo por versión en las xref:ROOT:what-is-new/release-notes.adoc[notas de _release_].

== Novedades de <version>

* *<Título de la novedad>*: <una línea>.

== Novedades incorporadas desde <anterior>

* *<Título de la novedad>*: <una línea>.

* xref:ROOT:what-is-new/breaking-changes.adoc[_Breaking changes_]: revisa todos los cambios que pueden afectar a la compatibilidad antes de actualizar.
```

## Breaking-changes page — `ROOT/pages/what-is-new/breaking-changes.adoc`

The canonical list, grouped by version, newest first. One `==` per version with its release date and an explicit anchor above it. Each entry is a single bullet with a bold title, then the impact and the action, and what happens to what already exists:

```
= _Breaking changes_

<One paragraph: what this page is, and a link to the upgrade planning.>

[#v<version>]

== <version> (D de <mes> de AAAA)

NOTE: <Only in the newest section, when the path is a direct jump: says that the breaking changes of the previous version also apply, linking them with <<v<previous>,…>>.>

* *<Título del cambio>*: <qué cambia>. Impacto: <qué deja de funcionar o qué se nota>. Acción: <qué hacer, o "ninguna" con el motivo>. <Qué le pasa a lo que ya existe.>

[#v<previous>]

== <previous> (D de <mes> de AAAA)

* *<Título del cambio>*: …
```

In EN the page title is `= Breaking changes`, roman (R13), and the labels are `Impact:` and `Action:`.

## Release-notes entry — `ROOT/pages/what-is-new/release-notes.adoc`

Newest version first, one `==` per version. The date must match `CHANGELOG.md`:

```
== X.Y.Z-A.B.C (D de <mes en minúscula> de AAAA)     // EN: (Month DD, YYYY)

* <Change>.
* *_Breaking change_*: <change>.                      // the one accepted bold+italic label
```

## Upgrade content lives in seven places — pick the right one

Shared with rocket-core, intelligence, genai and virtualizer, which all adopted this layout under the same ticket. **Do not create a page per upgrade path or per platform release**, and do not let the procedure page accumulate a section per source version — that is what Discovery used to do and it is the anomaly the layout fixes.

| What you are writing | Where it goes |
|---|---|
| The version-agnostic procedure: what to prepare, the steps in _Stratio Command Center_, the downtime, what to do if it fails, what to check afterwards | `installing-and-upgrading/upgrade.adoc` |
| A decision index of the available paths, one row per source Universe | `installing-and-upgrading/upgrade-overview.adoc` |
| Everything about **one** path: downtime, order, coexistence, preparation, regressions, checks, rollback | `upgrade-overview/from-<origin>-to-<target>.adoc`, one sheet per live path |
| An adjustment specific to a **target version** (a renamed option, a breaking change, a corrective step) | a `== Actualización a _Stratio Discovery_ <version>` section of `upgrade/details-upgrade.adoc`, with an explicit anchor |
| A path that starts from a version no longer present in any supported Universe | a section of `upgrade/special-upgrades.adoc` |
| The procedure to go back to the previous version, case by case, and what to review afterwards | `upgrade/rollback.adoc` |
| How to diagnose an upgrade that fails or leaves the service unable to start, what each symptom means, and how to retry it | `upgrade/migration-recovery.adoc` — how the migration runs, how to tell where it stopped, and a symptom/cause/action table. rocket-core and `intelligence` carry it under this name; Virtualizer calls it `upgrade-failure-recovery.adoc` because it has no migration |

Two rules keep it from rotting:

- **`upgrade-overview.adoc` is not the parent of `upgrade.adoc`.** They are two sibling trees in the nav at the same `***` level — planning versus executing — each with its own children at `****`. Copying rocket's nav shape is the point.
- **`details-upgrade.adoc` is pruned, not accumulated.** It keeps only the target versions that some supported Universe can reach: one section today, `[#v0564-02x]`, because the 0.56.4-0.1.x line belongs to Universe 15.0, which never reached production, so nobody upgrades *to* it. When a version stops being reachable, its section moves to `special-upgrades.adoc`, whose intro states that those paths are kept for very outdated installations and tells the reader to confirm the route with the product team. Never duplicate the environment-variable inventory there — `installing-and-upgrading/environment-variables.adoc` is the only reference for that.

To know which source versions are still live, read the descriptor of each Universe branch in `Stratio/kubernetes-universe`. Today: 14.8, 14.9 and 14.10 all carry `0.50.21-0.2.0`, 15.0 carries `0.56.4-0.1.3` and 15.1 carries `0.56.4-0.2.0` — so there is exactly one live route, and one sheet.

## Upgrade planning hub — `operations-guide/pages/installing-and-upgrading/upgrade-overview.adoc`

A **decision index, not a procedure**: it lists the available upgrade paths in a table and sends the reader to one fact sheet. Every Stratio module uses the same page title (`Planificación de la actualización` / `Upgrade planning`) and the same three sections, so an operator recognizes the structure across modules:

```
= Planificación de la actualización

<Two paragraphs: this is a decision index; each path has its own sheet.>

TIP: <Where to start reading.>

== Rutas de actualización disponibles

[cols="1,1,1,2", options="header"]
|===
| Universo de origen | Versión de origen | Versión de destino | Ficha
|===

== Cómo se lee una ficha

* *<Convention 1>.*
```

## Upgrade fact sheet — `…/upgrade-overview/from-<x>-to-<y>.adoc`

One page per upgrade path, with a fixed section order and an explicit `[#slug]` anchor above every section, so the "Detalle" column of the summary table can link into it with `<<slug,text>>``. The order is the same in every Stratio module:

```
= Actualización de <origen> a <destino>

[#summary]      == Resumen                              (IMPORTANT + Q/A table + "Mensaje para transmitir" block)
[#scope]        == Alcance de la actualización
[#components]     === Componentes y orden de actualización
[#modules]        === Relación con otros módulos
[#coexistence]    === Convivencia de versiones
[#execution]    == Ejecución de la actualización
[#before]         === Antes de la ventana
[#phases]         === Pasos de principio a fin
[#data]           === Datos y consistencia
[#no-return]      === Punto de no retorno
[#outage]         === Parada y disponibilidad
[#work-in-flight] === Trabajo en curso durante la ventana
[#post-upgrade] == Después de la actualización
[#regressions]    === Regresiones que hay que esperar
                  ==== Bloqueantes o de acción obligatoria antes de la ventana
                  ==== Requieren acción tras la actualización
                  ==== Cambios de comportamiento a tener en cuenta
[#checks]         === Comprobaciones posteriores
[#problems]      == Si algo va mal
[#rollback]       === _Rollback_
[#known-issues]   === Avisos e incidencias conocidas
```

Rules that a fact sheet breaks easily:

- **The sheet summarizes and links; it does not become the only home.** When a topic has an auxiliary page of its own (`upgrade/rollback.adoc` today), the sheet keeps its summary — the table of what can be done at each moment of the process — and links to that page for the case-by-case detail, exactly as rocket does. The reverse also holds: the auxiliary page links back to `#known-issues` for the diagnosis.

- **Every duration says whether it is measured or estimated**, and in which environment. `_Stratio Discovery_ has no measured migration figures, so every duration on its sheet is `(estimada)`.
- **No cell is left empty.** "No aplica: …" with its reason is an answer; a blank cell reads as an oversight.
- **Two link patterns.** In the "Detalle" column, internal `<<slug,text>>` (no space after the comma, per R30) to another section of the same sheet. Everywhere else, `xref:…[descriptive text]`.
- **Closed vocabularies**, so two modules can be compared: yes/no answers are "Sí"/"No"/"Parcialmente" (with the part that does apply); requirement levels are "Obligatorio"/"Recomendado"/"Opcional"; overall risk is "Bajo"/"Medio"/"Alto" with a one-line justification.
- **Detail stays on the procedure pages.** When a cell needs more than three lines, link instead of explaining; if the target has no anchor for it, add one.
