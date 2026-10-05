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

The EN tree used to carry a UTF-8 BOM on all 23 of the content pages it had then (`nav.adoc` and the partial never did); `ea8434b`, the upgrade-documentation change, stripped every one of them. **Measured at the baseline: 0 BOM in either tree**, out of 32 `.adoc` per tree. Never add one to a new page. It used to be a defect that could not be fixed on its own, because stripping a BOM is a one-language-only change, which fails the `LanguageCheck` CI gate. If one ever reappears, remove it only on the pages you are already editing in **both** trees, never as a standalone sweep.

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
- ⚠️ **The block above is byte-for-byte what both `es/antora.yml` and `en/antora.yml` contain at the baseline** — `version: '0.56.4-0.2'` (quoted, two halves, no patch segment) and `start_page: introduction.adoc`. It is reproduced here so nobody "corrects" the real file to match a wrong example: a wrong `start_page` breaks the component's landing page, and a wrong `version` moves the whole component to another segment of the docs portal. If you edit this snippet, diff it against both real files first.

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

`installing-and-upgrading/environment-variables.adoc` holds four `|===` tables, one per area, **59 variable rows per tree**. **Its shape is not the one this guide would choose, but match it anyway** — a row written differently from its 58 siblings is worse than a consistent one:

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

Note what it does **not** do: no `[cols=…]`, no `options="header"` (the first row is simply written as the header), and **the variable name carries no backticks** in the first cell.

⚠️ **The `Obsoleto` / `Deprecated` column is gone — do not put it back.** Three of the four tables used to declare a sixth column that was empty in 44 of its 46 rows, and `fe149fe` removed it: **measured at the baseline, `Obsoleto` has 0 occurrences in the ES tree**, the four header rows are the five-column form above, and the two variables that carried a value state it inside the description cell instead (`Deprecated as of version 0.56.4-0.2.x.` on `USE_DYNAMIC_AUTHENTICATION`, EN line 135, and on `SSO_URI`, EN line 195). That is the pattern to follow for a new deprecation: a sentence in `Descripción`, mirrored in both trees, not a column that is blank for everyone else.

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

The canonical list, grouped by version, newest first. One `==` per version with its release date and an explicit anchor above it. **A line that only shipped in a Universe that never reached production gets no section of its own:** 0.56.4-0.1.x (Universe 15.0) is folded into the 0.56.4-0.2.0 section, and its anchor stays inline (`[[v0564-01x]]`) on its first entry so the inbound links from the release notes keep resolving (#436). A sentence that relies on the fold says which origin it applies to ("Si vienes de la 0.50.21-0.2.x, …"). Each entry is a single bullet with a bold title, then the impact and the action, and what happens to what already exists:

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

A **decision index, not a procedure**: it lists the available upgrade paths in a table and sends the reader to one fact sheet. Every Stratio module uses the same page title (`Planificación de la actualización` / `Upgrade planning`) and the same three sections, so an operator recognizes the structure across modules. Measured at the baseline: **34 lines in each tree**.

```
= Planificación de la actualización          |  = Upgrade planning

<párrafo: es un índice de decisión, no un procedimiento; enlaza a upgrade.adoc>
<párrafo: qué contiene cada ficha y para quién está escrita>

TIP: Empieza por el <resumen de la ficha>. Responde, de un vistazo, a todo lo
     que necesitas para decidir la ventana de mantenimiento.

== Rutas de actualización disponibles

[cols="1,1,1,2", options="header"]
|===
| Universo de origen | Versión de origen | Versión de destino | Ficha
…una fila por ruta; la última columna enlaza la ficha o la guía que la cubre…
|===

NOTE: <lo que queda fuera de alcance y por qué; si no queda nada fuera, se omite. Nunca nombra el Universo 15.0>

== Cómo se lee una ficha

* *Las respuestas son breves.* …
* *Ningún campo queda vacío.* …
* *El detalle se queda en las páginas de procedimiento.* …
```

The `TIP` and the three closing bullets are meant to be **shared verbatim across the implementing modules**, and they are the first thing that drifts. This repo's wording is the one to keep: ES «ventana de mantenimiento» and EN *maintenance window* (the glossary forbids *intervention window*), and the third ES bullet in the feminine concordance — «la ficha **las** enlaza en lugar de reproducir**las**». Do not paraphrase them when you add a route; only the table grows.

## Upgrade fact sheet — `…/upgrade-overview/from-<x>-to-<y>.adoc`

One page per upgrade path, named by product version in kebab-case so new paths stack without renaming anything: `from-0-50-21-to-0-56-4.adoc` today, and there is exactly one live route (see the Universe correlation above).

This is **not a page type this repo invented, and not free text.** It is a **cross-product standard** implemented in `rocket-core` (which shipped it first and set it), `genai-core`, `intelligence-multiuser-environment`, `dlc-entity` and here, so that an operator who opens any module's sheet finds the same answer in the same place. What is fixed: the page title, the anchors, the section order, the table headers, the **closed vocabularies of the answer cells** and four conventions. Do not restructure it, do not rename its sections and do not drop a section because it would be short — what standardizes the sheets is the structure, not the length.

Measured at the baseline, in each tree: **569 lines, 18 `[#slug]` anchors at identical line numbers, 13 `[cols="…", options="header"]` tables, 3 `====` headings and 1 `====` example block.**

### Section order

Fixed, each section preceded by its explicit anchor and one blank line (R16). The line numbers are this sheet's, identical in both trees:

```
= Actualización de 0.50.21-0.2.x a 0.56.4-0.2.x     (1)
[#summary]         == Resumen                                    (7/9)    IMPORTANT + tabla de 9 filas + bloque `[caption=]`
[#scope]           == Alcance de la actualización                (62/64)
[#components]      === Componentes y orden de actualización      (72/74)
[#modules]         === Relación con otros módulos                (100/102)
[#coexistence]     === Convivencia de versiones                  (138/140)
[#execution]       == Ejecución de la actualización              (163/165)
[#before]          === Antes de la ventana                       (167/169)
[#phases]          === Pasos de principio a fin                  (220/222)
[#data]            === Datos y consistencia                      (266/268)
[#no-return]       === Punto de no retorno                       (276/278)
[#outage]          === Parada y disponibilidad                   (284/286)
[#work-in-flight]  === Trabajo en curso durante la ventana       (294/296)
[#post-upgrade]    == Después de la actualización                (335/337)
[#regressions]     === Regresiones que hay que esperar           (339/341)
                   ==== Bloqueantes o de acción obligatoria antes de la ventana  (349)
                   ==== Requieren acción tras la actualización                   (396)
                   ==== Cambios de comportamiento a tener en cuenta              (433)
[#checks]          === Comprobaciones posteriores                (462/464)
[#problems]        == Si algo va mal                             (498/500)
[#rollback]        === _Rollback_                                (502/504)   ES italic; EN `=== Rollback`, roman
[#known-issues]    === Avisos e incidencias conocidas            (530/532)
```

Notes on the order, all verified here:

- **The three `====` subheadings of `[#regressions]`** are the standard's grouping-by-priority form, used once the section passes about eight rows. Keep the three of them and the order (blocking → action afterwards → behaviour change), because the reader budgets the window from the first group.
- **`[#sizes]` (`==== Parada por <magnitud>`) is part of the standard but optional**, and this sheet does not carry it: the downtime here is not dimensionable, it is capped by the `startup probe` deadline. Do not add an empty one. `[#data]`, `[#no-return]` and `[#work-in-flight]` are conditional in the standard and all three apply here, because the module keeps a metadata database, applies a schema migration and can be running queries when the window opens.
- **The anchors are identical across the six published sheets** — measured: all six carry the same 18, in the same order, and `rocket-core` adds one more (`[#sizes]`) that the rest omit. So nothing external depends on the heading wording. That matters because **the EN heading text is not standardized and has drifted between modules**; this repo uses `Known issues` and `Post-upgrade checks`, which is what `rocket-core` uses. Mirror the Spanish heading rather than picking a favourite, and never change an anchor.
- ⚠️ **The anchors of this sheet are the one place in the repo where an anchor is NOT per language** in effect: they are explicit `[#slug]`, in English, identical in both trees. That does not contradict R30 — R30 is about the ids Antora *generates* from translated headings. Explicit slugs are exactly the way out of that problem, and the direction new content should take.

### The 14 table headers

**They are part of the standard, not free text**: an operator comparing two modules has to be comparing the same columns. Fourteen header rows across the two pages of the implementation — thirteen in the sheet, one in the hub. Reproduce them exactly rather than rewriting, and mirror each into EN as the sheet already does:

| Section | `[cols=…]` | ES header row |
|---|---|---|
| hub, `== Rutas…` | `1,1,1,2` | `\| Universo de origen \| Versión de origen \| Versión de destino \| Ficha` |
| `[#summary]` | `2,3,1` | `\| Pregunta \| Respuesta \| Detalle` |
| `[#components]` | `1,2,2,3` | `\| # \| Proceso \| Dónde guarda el estado \| Por qué no necesita ventana propia` |
| `[#modules]` | `2,2,4,4` | `\| Módulo relacionado \| Qué relación hay \| Al actualizar _Stratio Discovery_ \| Al actualizar el módulo relacionado` |
| `[#coexistence]` | `2,1,3` | `\| Escenario \| ¿Admitido? \| Limitaciones` |
| `[#before]` | `3,1,3` | `\| Qué tiene que estar listo \| Cuándo \| Cómo se comprueba que está hecho` |
| `[#phases]` | `4%,17%,49%,30%` | `\| # \| Paso \| Qué ocurre y qué deja de funcionar \| Duración y parada` |
| `[#work-in-flight]` | `2,4,2,4` | `\| Qué puede estar ejecutándose \| Qué le ocurre \| Qué hacer antes \| Cómo se recupera después` |
| `[#regressions]` ×3 | `1,1,2,1` | `\| Qué deja de funcionar o cambia \| Causa \| Cómo resolverlo \| Mientras tanto` |
| `[#checks]` | `2,3,2` | `\| Tarea \| Qué hay que hacer \| Qué no funciona hasta que se haga` |
| `[#rollback]` | `2,1,4,2` | `\| Momento del proceso \| ¿Se puede? \| Qué se hace \| Cuánto tarda y si se pierden datos` |
| `[#known-issues]` | `2,2,3,2` | `\| Aviso o incidencia \| Cuándo se manifiesta \| Qué hacer \| Referencia` |

Two of them are **this repo's own divergence from the shared wording**, and both are deliberate: `[#components]` says `Proceso` and drops the `¿Ventana propia?` column because _Stratio Discovery_ is a single container with three supervised processes and no internal upgrade order, so the answer would be "no" in every row; and `[#modules]` names the module (`Al actualizar _Stratio Discovery_`) where the shared form says `Al actualizar este módulo`. Leave both as they are — realigning them is a one-language-per-cell churn with no reader benefit — but know that they are divergences, not the spec.

The `[#phases]` table uses the **four-column** form with `| Duración y parada` fused, and carries no `TOTAL` row. Any empty fact-sheet template that asks for five columns (`Duración` and `Parada` split) plus a `TOTAL` row is out of date: **where a template and the published sheets disagree, the sheets win**, unanimously across the five implementations.

### Closed vocabularies (the part that drifts first)

An answer cell is not prose. It opens with a value from a closed list, and only then explains. This is what makes two modules comparable, and it is exactly what `Stratio/discovery` #384 had to repair in this sheet:

| Campo | Valores admitidos |
|---|---|
| Respuestas de sí o no | `Sí`, `No` o `Parcialmente` — y siempre con la parte que sí aplica detrás |
| Nivel de exigencia | `Obligatorio`, `Recomendado` u `Opcional`, **uno solo por celda** |
| Duraciones | Seguidas de `(medida)` o `(estimada)`, nunca una cifra a secas; si es medida, en qué entorno. El marcador **concuerda con el sustantivo al que sigue**: `Unos minutos (estimados)`, `Semanas (estimadas)`, `La más larga de la intervención (estimada)`. Es el criterio del equipo de documentación en sus dos revisiones de fichas (Stratio/discovery#409 y Stratio/genai-core#854); la ficha de este repositorio ya lo sigue |
| Riesgo global | `Bajo` (reversible sin coste y sin cambios en el modelo de datos), `Medio` (reversible mediante acción manual o de configuración) o `Alto` (irreversible tras un punto de no retorno o con riesgo de pérdida de datos), con una justificación de una línea |
| Pérdida de datos | `Ninguna` o la cosa concreta que se pierde |

This sheet answers `Alto` for the overall risk and marks every duration `(estimada)`: _Stratio Discovery_ has no measured migration figures, so `(medida)` is a value of the vocabulary that this repo cannot yet use. `Medio` and `Opcional` were likewise unused until #384 introduced the first `Opcional` row.

⚠️ **The four defects #384 fixed, so nobody writes them again.** Every one of them is a cell that read perfectly well as Spanish and still broke the standard:

| Defecto | Corrección |
|---|---|
| `\| *No está validada.* Cada Universo declara…` — an explanation where the vocabulary wants a value | `\| *No.* No está validada: cada Universo declara…`. The value comes first, in bold; the reason follows after a colon. Same in the `[#coexistence]` cell, `\| No está validada` → `\| No` |
| Three `(estimado)` in the masculine | `(estimada)`. The mark agrees with *duración*, feminine, not with *tiempo* or with the number: `unos minutos (estimada)`, `Depende del tamaño de la copia (estimada)`. EN uses `(estimated)`, which hides the slip |
| `\| Inmediato. No se pierden datos.` — a duration with no mark at all | `\| Inmediato (estimada). No se pierden datos.` **`Inmediato` is a duration.** So is `Unos minutos`, and so is `Días o semanas`. If a cell of a duration column says how long something takes in any words at all, it carries the mark |
| `\| Lo primero es obligatorio; lo demás, opcional, en el mismo cambio de versión` — two requirement levels in one cell, over a row that bundled two unrelated decisions | Split into **two rows**, one `Obligatorio` (`_Instances_` to `1`) and one `Opcional` (`_Metabase log level_` to `"INFO"`). When a cell needs two levels, the row is really two rows |

### Four conventions a sheet breaks with ease

- **Ninguna celda queda vacía.** Un `No aplica` con su motivo se lee como una respuesta; una celda vacía, como un descuido. This is one of the hub's own three bullets, so a blank cell contradicts the page that sent the reader here.
- **La columna `Detalle` del resumen enlaza a una sección de la propia ficha** con `<<ancla, texto>>`, nunca a una página externa: all nine rows of `[#summary]` do, pointing at eight distinct sections of the sheet — `<<outage>>`, `<<data>>` (twice), `<<phases>>`, `<<before>>`, `<<modules>>`, `<<rollback>>`, `<<coexistence>>` and `<<known-issues>>`. The rest of the sheet uses `xref:…[texto descriptivo]`. **With a space after the comma**, as decalogue R30 states: it is the form the documentation team asked for in the review of PR #409.
- **El detalle se queda en las páginas de procedimiento.** When a cell needs more than three lines, link instead of explaining; if the target page has no anchor for it, add one **in both trees**. The sheet summarizes and links, it never becomes the only home: `[#rollback]` keeps the table of what can be done at each moment and sends the case-by-case detail to `upgrade/rollback.adoc`, which links back to `#known-issues` for the diagnosis.
- **Toda duración indica si es medida o estimada**, and in which environment when it is measured. A figure measured on a local disk with no load does not transfer to network storage. Grep the sheet for a bare number in a duration column before you push.

⚠️ **The relay-message block: copy this shape exactly.** It lives at the end of `[#summary]`, right after the closing `|===` of the question table — lines 56–60 of both trees:

```
|===

.Mensaje para transmitir
[caption=]
====
Mensaje breve, sin terminología interna, que se pueda copiar y transmitir tal cual.
====
```

Three things about it, all learned the hard way in `rocket-core` after its sheet had already been merged:

1. **The `[caption=]` attribute is mandatory.** Without it Asciidoctor numbers the example block and it renders as *"Example 1. Mensaje para transmitir"*. The `rocket-core` sheet shipped without it and had to be fixed after merge.
2. **The order is `.Título` first, then `[caption=]`.** This is the form the documentation lead settled on after checking the published Antora output, and it is what four of the five published sheets carry. `crossdata-spark2` is the exception: it puts `[caption=]` **above** the title. **Both orders render identically** — verified by rendering each with Asciidoctor 2.x: what suppresses the numbering is the presence of the attribute, not its position, and the same block without `[caption=]` renders as *"Example 1. Mensaje para transmitir"*. So write a new sheet in the canonical order, but do **not** reorder a published sheet to converge, and do not read `crossdata-spark2`'s as defective. **It makes the `EmptyLines` sub-check report `- <line> - a line break is missing before this line` on the caption line** — line 57 here, in both trees. That report is a **false positive on a correct document**: the sub-check reads the caption as a block attribute needing a blank line in front, when it belongs to the block the title above already opened. `Docs Checks` concludes `neutral` for it, so it does not block the merge. `crossdata-spark2`'s order is, incidentally, the only one the sub-check does not flag — which is precisely what makes reordering tempting. Take the `neutral` instead: consistency across the sheets is worth more than a clean linter line, and the render is the same either way. **Do not "fix" it by moving the attribute above the title, and do not insert a blank line between the two** — either change alters what the documentation team validated in the portal.
3. **In English the title is `.Message to communicate`**, following `rocket-core`, the repo that set the standard. **This sheet says `.Message to convey`** (line 56 of the EN tree) and it is not alone: three of the five sheets diverge the same way. The label is what an operator copies and pastes, so it should be one thing across the modules — but **do not change it here as a drive-by**: it is a pending unification for the documentation team to settle, and flipping it in one repo only makes the divergence harder to see. Raise it; do not patch it.

### Read the post-merge fixes of the repo that shipped the standard first

`rocket-core` merged its sheet in PR #5274 and then needed six more PRs to settle it. **None of those defects is Rocket's fault — they are the standard's blind spots**, and each is a mistake the next module repeats unless it reads them first. This is a precedent to learn from, not this repo's history:

| Fix in `rocket-core` | The lesson |
|---|---|
| #5294 — the missing `[caption=]` | An example block without it renders as *"Example 1. …"*. See the relay-message note above. |
| #5288 — cite the **full** descriptor path | `'Probes'` became `'General' → 'Probes'`, in 10 files. A section name alone is not enough: the operator navigates a tab *and* an accordion, so chain both (R28). The same chain exists in this repo's descriptor and goes one level deeper in places. |
| #5294 — the summary `IMPORTANT` rewritten | The first version said what set the date of the upgrade in the abstract ("the calendar, not the migration"). The rewrite says which concrete work takes weeks and links `<<before>>`. If the `IMPORTANT` does not tell the reader what to go and do, it is not finished. |
| #5294 — a checklist row marked conditional | `Obligatorio, semanas antes` became `Obligatorio si has extendido <el módulo>. Semanas antes.` A row that only applies to some deployments must say so, or every reader budgets weeks they may not need. This sheet already does it in three of its ten `[#before]` rows (`Obligatorio si utilizas…`, `Obligatorio si aplica…`, `Obligatorio si has personalizado…`) — keep that habit. |
| #5295 — the template extracted, then deleted | An empty fact-sheet template does not belong in a product's published docs. The standard is kept **here**, in this skill, not as an `.adoc` under `stratio-docs/`. |
| 53 files — a per-module lexical asymmetry in EN | Rocket had to rename its workflow nodes. That entry belongs to the module that owns the concept; **it does not apply here**, because this repo documents no _Stratio Rocket_ workflow (a case-insensitive grep for `rocket` over both trees returns nothing). Do not import another module's glossary rows into `translation-glossary.md`. |

**Before publishing or reviewing a sheet, read those, then re-read the four conventions above.** All six of those follow-up PRs would have been unnecessary if the standard had been written down anywhere; that is what this section is for. When you change the standard, change it here first, and re-measure every figure in this section against the tree you are in — several statements above are measurements of *these* trees, not properties of the standard.

## Anatomy of a CCT descriptor (read this before documenting any configuration)

**The module owns the descriptor of its version and uploads it to `Stratio/kubernetes-universe` to be published across the whole Universe** — so the copy in the module's repository is the authoritative one, versioned with the product, and the Universe one is the published copy, versioned with the platform. **Look in the repo first, at the product tag.**

Measured across the clones here, September 2026 — it is the usual arrangement but not universal, so check rather than assume:

| Repo | Its own descriptor |
|---|---|
| `rocket-core` | `deployment/CCT/k8s-descriptors/{rocket,rocketbase,rocket-catalog,mlmodel-server,rockets3}.json` |
| `intelligence-multiuser-environment` | `deployment/CCT/kubernetes/intelligence-k8s-default.json` |
| `discovery-cicd` | `deployment/CCT/k8s-descriptors/discovery.json` |
| `dlc-entity` | `saas-universe/k8s/dlc-entity.json` (the sibling `dlc-entity-external-mtls.json` declares no parameters) |
| `eureka-bdl` | `bdl-agent/cct-definition/{bdl-default,bdl-operational}.json` |
| `virtualizer` | `kubernetes-universe/virtualizer-default.json` — a directory of that name **inside the repo**; `virtualizer-monitor` and `virtualizer-ui` carry theirs at `cct-k8s-descriptor/` |
| `genai-api` / `genai-litellm` / `genai-ui` / `genai-gateway` | `saas-universe/<piece>-default.json`, one per piece (`genai-api` also carries `genai-developer-proxy-default.json`) |
| `stratio-spark` | **not found**: no such directory and no JSON carrying `internalName` on any `upstream` branch |

⚠️ **Two traps in that table.** The repo that holds the docs is not always the repo that holds the descriptor: `genai-core` carries the documentation of a suite whose descriptors live in the **piece repos**, and `crossdata-spark2` is the engine while the product descriptor lives in `virtualizer`. And the directory names are all different — `deployment/CCT/`, `saas-universe/`, `cct-definition/`, `cct-k8s-descriptor/`, even one literally called `kubernetes-universe/` — so **search by content, not by path**: `grep -rl '"internalName"' --include='*.json'` finds it whatever it is called.

Where the repo has none, the published copy is what you have, and **say so in the page or the skill instead of sending the reader to a path that does not exist**. Either way, **read it at a tag, never at a branch head** (`git show <tag>:<path>`); for the Universe copy take the most recent tag by date (`git tag --list '15.1*' --sort=-creatordate | head -1`), because a platform version carries several `<version>-<sha>` tags that do not all agree.

For **this** repo the descriptor is `deployment/CCT/k8s-descriptors/discovery.json`, read at the product tag — and mind the two-part numbering, `<product>-<packaging>`, as in `0.50.21-0.2.0`. **Measured for the 0.50.21 → 0.56.4 path**: 83 keys → 94, only **5 removed** (one of them level 1, *Skip Metabase's Statement final cancellation*) and 21 same-key renames; measuring from `0.50.21-0.1.4` or from `0.50.21-0.2.0` gives the identical answer, so the packaging suffix does not affect it.

⚠️ **This repo has the clearest case of "live key, dead effect".** Three of the renames point the `internalName` at **`NOT_USED`**: `postgresDatabaseCreation`, `cpuResourceType` and `startupProbePeriodSeconds`. The key survives, so the value is carried, but it no longer does anything — the field is still on the form and has stopped mattering. That is neither "the value is lost" nor "nothing changes": for the operator it means **a setting they had in place has silently stopped applying**, and a probe period or a resource type is visible enough that somebody may have set it. Document it when the setting was visible; and note that this is the one case where the reassuring reading is the false one, so the habit of preferring the answer that gives the reader less work does not apply.

Everything an operator sets in _Stratio Command Center_ comes from there, so the descriptor — not the code, not the previous version of the page — is the authority for how a configuration page is written.

### The four names of one property, and what each is for

A property is a leaf of the `parameters.properties` tree, and it carries four identifiers that are routinely confused:

| | What it is | What it is for | In the docs |
|---|---|---|---|
| **JSON key** | The property's name in the tree (`virtualizerStoragePath`, `historyServerLogDir`) | The **identity**: it is what _Stratio Command Center_ matches a stored value against across an upgrade | Never written. Used only when reasoning about what survives an upgrade |
| **`internalName`** | The environment variable injected into the container (`HISTORY_SERVER_LOG_DIR`) | Runtime plumbing | In backticks, **beside** the title, only where the reader needs it to script or verify |
| **`title`** | The label the operator reads on screen (_Logs path_) | The form | **This is how you name the property**: in italics, verbatim, including its capitalization (R28) |
| **`description`** | The help text under the field | The form | Not quoted; write your own prose |

The consequence that matters most: **a release can rename the `internalName` without the operator noticing anything, and can keep the `title` identical while changing the JSON key.** The first is invisible; the second costs them the value. Only a diff of the **keys** tells them apart.

### The fields that change what you write

- **`level`** — `1` is what the operator sees by default, `2` is advanced. A `level: 2` property is usually computed or plumbing: **document level 1 and leave level 2 alone** unless the reader has to touch it. When two properties share a `title`, the level almost always disambiguates them.
- **`required`** — required *of the form*, not of the operator: a `required: true` with a `default` needs no action, and saying otherwise sends people looking for a field to fill in.
- **`default`** — cite it in backticks: it is a configuration value (`` `true` ``, `` `/tmp/logs/history` ``). A default that interpolates (`${...}`) marks a computed field.
  ⚠️ **The descriptor's `default` is not the code's default, and confusing them misinforms the reader about a deployment nobody touched.** In this repo `_Files to retain after compaction_` carries `default: 5` while the property it drives, `spark.history.fs.eventLog.rolling.maxFilesToRetain`, is `createWithDefault(Integer.MAX_VALUE)` in `History.scala` — "all event log files will be retained". And the descriptor only injects its 5 when `_History Server v2 compaction enable_` is `"true"`, which itself defaults to `False`. So out of the box the **code** default governs, and writing "the default is 5" is wrong. `eureka-bdl` has the same shape with `'Force table recreation on partition schema changes'`: `default: true` in the descriptor against `false` in `env.scala`. When the two disagree, say which one applies and under what condition.
- **`enum`** — the selectable values. Copy them **verbatim**, in double quotes, including odd casing (R27): the operator has to match them on screen.
- **`ui.visible`** — the condition under which the field appears, usually another boolean's key. This is what makes a branch of the form conditional, and it is why "the field is not there" is often correct rather than a defect.
- **`ui.updatable: false`** / **`readOnly`** — set once, or computed. Do not tell the reader to change it.
- **`${...}` interpolation** — a value that references another property's key or a platform variable (`${Application.id}`). Treat it as a computed field.

### Tabs, accordions and the path you must cite

The first level of `parameters.properties` are the **tabs**; a nested object with a `title` is an **accordion**; the leaves are the properties. Cite the whole chain in single quotes with ` → ` — `'General' → 'Datastores for Event Logs'` — because an accordion name alone does not tell the operator where to click (R28). Two traps recur: **sibling branches share accordion and property titles**, so disambiguate by the selector value that governs them; and **an accordion's title can be a leftover** that no longer describes what it holds.

### Lifecycles: read them across versions before writing what the upgrade does

Below the parameters, `lifecycles` declares `install`, `update`, `upgrade`, `restart` and `uninstall`, each a list of steps with a `type`, often an `enabled` expression and sometimes `continueOnFail`. **Two symmetric mistakes are easy here, and both produce false documentation.**

**Mistake 1 — treating an `install`-only step as an upgrade behaviour.** A step declared only under `install` never runs on an upgrade. In `stratio-spark`, `gosec/policies/create` (the HDFS ACL on the event-log path) is in `install` and in **no** other lifecycle, at 14.10.1 and at 15.1 alike. The fact sheet said the default flip of _Execute HDFS prerequisites_ meant "the upgrade no longer creates that policy" — describing a behaviour change that never existed, because the upgrade never created it.

**Mistake 2 — treating a permanent safety net as something this jump does.** Most of what an `upgrade` block declares is there **on every operation**, to re-create or refresh registrations that may have drifted, not because this version changes them. Three signals identify one, and any of them is enough:

- the same step also appears in `install` and/or `update`;
- it carries **`continueOnFail: true`**, i.e. it is allowed to fail because it is a best-effort net;
- **its `lifecycles` block is unchanged between the two versions you are documenting.**

Measured, so you can calibrate: `rocket-core`'s upgrade registers the CN of `${Application.id}` and of `${executionIdentity}` in a GoSec group and registers the application in _Stratio Data Governance_ — all three with `continueOnFail: true`, all three present at **3.6.13 and 4.1.0 alike**, and the Governance one also in `install` and `update`. `dlc-entity`'s upgrade creates the Rocket API ACL policies and registers the CN, and its `lifecycles` are **byte-identical across 4.0.0, 4.0.1, 4.0.2 and 4.0.3**. `eureka-bdl`'s four upgrade steps are all in `install` or `update` too, and its lifecycles do not change between 14.10 and 15.1. **None of that belongs in a fact sheet as something the upgrade does to the operator**, and the sheets of those repos are right to answer "ningún impacto".

So: **diff the `lifecycles` block between the two versions first.** If it is unchanged, the upgrade steps are the same ones that always ran and there is nothing to report. If it changed, look at what changed and only then decide whether the operator needs to know — `rocket-core` 4.1.0 does add one step to its upgrade (`gosec/policies/create`, "Updating Rocket Metadata policy"), and even that carries `continueOnFail: true`. Say what the operator must *do*, never what the machinery *runs*.

**Cite the step, not the step type.** A descriptor can declare several steps of the same `type` acting on different objects, and matching by `type` alone is how you end up contradicting a sentence that was right. `dlc-entity`'s `upgrade` does run a `gosec/policies/create` — but it creates the Rocket API ACL policy (`${Application.id}-rocketacl`), while the PostgreSQL® one (`${...}-pgacl`), which is the policy its fact sheet's Gosec row actually describes, is created **only in `install`**. So its "las políticas ya existen y no se vuelven a crear" is correct, and a PR to "fix" it would have broken working documentation. Before contradicting a sentence, check that the step you found is the one that sentence describes — by its `label` and the object it names, never by its `type`.

Three questions cover every case of this kind, and all three are cheap:

1. **Who does it affect?** Is there anyone downstream who consumes this? Sometimes the answer is nobody, and then there is nothing to write.
2. **Is it new?** Was it already there in the version you are coming from? The three signals above answer this.
3. **Is it exactly this?** Is the thing you found the same thing the sentence is about? This is the one that catches the rest.

### What survives an upgrade

**Reverting to the previous descriptor is native to _Stratio Command Center_, and it preserves a service's configuration across an upgrade**, matching each stored value by its JSON key. *(Source: confirmed by the platform team. It has **not** been read from the _Stratio Command Center_ source, which is not in these repositories; it is consistent with every key diff measured below. If you ever get to confirm it against that code, record the source here.)* So:

| What changed between the two tags | What the operator has to do |
|---|---|
| Same JSON key, `internalName` renamed | **Nothing.** The value is carried; the rename is invisible in the form. |
| Same JSON key, moved to another tab or accordion | **Nothing**, beyond knowing where to look. |
| JSON key removed or renamed | **Note the value down before the upgrade**, because there is no key for it to land on. |
| Key gone with no replacement | The setting no longer exists: document the withdrawal, not a workaround. |

**A key disappears because a feature was withdrawn, and the withdrawal is documented at the feature level — not key by key.** Searching the docs for the property name will find nothing, and that is correct: nobody writes an entry for a descriptor knob. The right question is *which feature owned this knob, and is that feature's withdrawal documented?* Worked example, verified in `rocket-core` 3.6.13 → 4.1.0, where 24 level-1 keys vanish and **every one of them maps to a documented removal**:

| Keys that disappear | The feature that owned them | Where it is documented |
|---|---|---|
| `GIT_API_PROXY_*` (6: proxy host, port, domains, SSH auth, credentials path) | `MLProject` assets of type *Git Clone* | "Eliminación de los _assets_ de tipo _GitClone_ asociados a _MLProject_", plus a regressions row in the sheet |
| `INTELLIGENCE_MLMODELS_DEFAULT_MLEAP_DOCKER_IMAGE` | MLeap packaging and serving | "Eliminación completa de la compatibilidad con MLeap" |
| `ENABLE_CONNECTOR_TFRECORD` | The internally packaged connectors | "Se han eliminado varios conectores que _Stratio Rocket_ empaquetaba internamente" |
| `SPARTA_CONFIG_…_AUTOML_ENABLED` | AutoML on TransmogrifAI | "Eliminación completa de la funcionalidad de _AutoML_", with its own regressions row and a rollback warning |
| 18 × `GENAI_CHAIN_*` / `GENAI_GATEWAY_*` | The GenAI chain stack moving to 0.8 | The sheet's GenAI coverage (14 mentions) |

So the sequence is: diff the keys → for each removed key find the feature → check that feature's withdrawal is documented. Stopping at "the property is not mentioned anywhere" produces a false gap, which is the same error as every other row of the table below.

Never ask for a blanket "configuration inventory", never write that values "revert to their defaults", and never describe a rollback as requiring the configuration to be re-entered by hand. **Measure the key diff** instead of inferring it:

```python
def keys(path):
    d, out = json.load(open(path)), {}
    def w(n, k=None):
        if isinstance(n, dict):
            if n.get('internalName') and k: out[k] = n['internalName']
            for kk, v in n.items(): w(v, kk)
        elif isinstance(n, list):
            for v in n: w(v, k)
    w(d); return out
# same key, different internalName -> carried    |    key only in the old one -> note it down
```

⚠️ **With more than one descriptor, diff the union and enumerate the directory at both tags.** Per-file counting inflates the removals for **two different reasons**, and the same aggregate fixes both: a key that **moves** from one descriptor to another is counted as gone from the first — in `rocket-core` 58 file-level disappearances collapse to **32** real ones, 11 of them movements — and a key that lives in **several descriptors at once** is counted once per file, as in `eureka-bdl`, where the same key sits in both installables and 2 becomes 1. The set of files is not stable either: `rocket-core` gains a whole descriptor (`mlmodel-server.json`) between 3.6.13 and 4.1.0, and `kubernetes-universe` **stopped publishing** `genai-gateway-default.json` partway through the 15.1 series, so a measurement against an intermediate tag reproduces a catalog that no longer exists. List the directory at both tags first, and take the totals over the union: for `rocket-core` that is 557 → 594 keys, not the 537 → 548 of its largest file.


⚠️ **The answer is per module, and it is not the intuitive one.** In `stratio-spark`, 14.10.1 → 15.1 removes or renames **zero** of 44 keys, so there is nothing to note down. In `crossdata-spark2`, the same diff over `virtualizer-default.json` (0.11.7 → 1.1.0) removes **24 of 311**, eleven of them level-1 LDAP and HTTP transport fields whose keys change with the Akka → Apache Pekko™ move — while _Default storage path_ and _Default provider_, whose `internalName` is renamed, keep their key and are carried. Its docs had the two halves the wrong way round.

## Verify the datum, and then verify the consequence

Every false claim this family of skills has had to remove followed the same shape: **a correct fact, an invented consequence.** Both sessions writing these docs hit it repeatedly in one day, and the failure is not sloppiness about facts — it is stopping one step too early.

| The datum, correct | The consequence, invented | What it cost the reader |
|---|---|---|
| The streaming plugin was removed in 3.5.7 | "So the `/streaming/…` REST endpoints stopped answering" | A 404 blamed on an upgrade it predates |
| `Java` goes from 8 to 17 | "So everything has to be recompiled" | Work that was not needed |
| The `lifecycles` block does not change between the two versions | "So it has nothing to tell the reader" | An upgrade whose real steps went undocumented |
| The `upgrade` block declares four steps | "So this jump performs them" | Machinery presented as the operator's job |
| A default flips from `true` to `false` | "So the upgrade stops doing what the step did" | A behaviour change that never existed |
| A property is renamed in the descriptor | "So its value is lost and must be re-entered" | An inventory ritual against a tool that carries the value |

The datum is cheap to check and everyone checks it. **The consequence is where the work is**, and it needs its own evidence: read the deleted code rather than assuming its reach, read the consumer rather than the declaration, read the other lifecycles rather than the one in front of you, diff the keys rather than the names.

Two habits catch almost all of it:

- **Ask what would have to be true.** "The endpoints stopped answering" requires that the removed code served them — so go and look at what it constructed. "The value is lost" requires that nothing matches it on the other side — so go and diff the keys.
- **Prefer the answer that gives the reader less work.** Not because it is nicer, but because the alarming answer is the one that needs the stronger evidence: it is the one that will make somebody spend an afternoon. If the evidence is not there, the honest answer is usually "nothing to do", and in every row above it was.

And the formulation that decides the fact-sheet cases: **say what the operator has to do, never what the machinery runs.**
