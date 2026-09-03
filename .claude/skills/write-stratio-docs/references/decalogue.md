# Decalogue: writing style guide

> Illustrative examples that show Spanish documentation text are kept in Spanish on purpose — they demonstrate how the ES pages must read. The rules and explanations are in English.

## 1. Establish a close conversation

Use the 2nd person to address the reader and the 3rd person to refer to the Stratio product:

- ✅ *Stratio Discovery* te ofrece las siguientes funcionalidades.
- ❌ *Stratio Discovery* ofrece al usuario las siguientes funcionalidades.

## 2. Aid comprehension

Use clear, concise, easy-to-understand language. Use bold for emphasis and italics for concepts or foreign-language words. Do not use both at once.

## 3. Simplify the grammar

Use the active voice and the present tense. Do not use the first person plural ("recomendamos", "te acompañamos"); replace it with impersonal constructions ("se recomienda", "es recomendable").

**Instructions to the reader go in the present + imperative — never the future, never a nominalized or passive obligation.** This is the single most frequent correction the docs team applies to drafts, so pre-empt it:

- **Future → present:** `deberás migrarlo` → `debes migrarlo`; `podrás seguir usándolo` → `puedes seguir usándolo`; `tendrás que configurar` → `debes configurar`.
- **Nominalized / passive obligation → direct imperative:** `es necesario configurar el _driver_` / `el _driver_ debe configurarse` / `será necesario desplegar una instancia` → `configura el _driver_`, `despliega una instancia`.
- **EN mirrors this:** `Users … must update their collections` → `update the collections …`; `the database must be configured` → `configure the database`.

This applies **only** to actions the reader performs. Keep the impersonal/passive voice when the subject is the system, not the reader (`los grupos se sincronizan automáticamente` / "the groups are synchronized automatically").

**This repo leaks the first person plural and colloquialisms.** Several pages were written as an informal narrative and still say `no vamos a reinventar la rueda`, `vamos a bromear un poco`, `nuestro trabajo`, `te pedimos que la borres`, `Lamentamos las molestias`, `¡abróchate el cinturón de seguridad!`. All of them break principles 1 and 3: rewrite them impersonally (`se recomienda`, `el equipo de Stratio`, `borra la base de datos`) when you touch the page. Never add new ones. `Por favor` is also present (`TIP: Por favor, lee esta breve guía`) and must go — see R31.

**Nuance on `es necesario` (ES).** The defect is the impersonal obligation aimed at the reader: `es necesario configurar X` → `configura X` / `debes configurar X`. Two uses are correct and must be left alone: the adverbial `si es necesario` ("if needed"), and the negative `no es necesario X`, which informs rather than instructs — prefer `no hace falta X` there. Before running a pass over this, check whether the documentation team has already reviewed those pages and kept the wording on purpose.

## 4. Encourage the reader to interact

You may occasionally include calls to action. Be approachable and avoid stiff formalisms.

## 5. Explain in detail

Add clarifications and examples. Where relevant, add reference links. The link text must be descriptive: never "aquí"/"here" nor "this section".

- ✅ Consulta la xref:operations-guide:installing-and-upgrading/upgrade.adoc[guía de actualización de versión].
- ❌ Consulta xref:operations-guide:installing-and-upgrading/upgrade.adoc[aquí] la documentación.

## 6. Go from general to specific

In introductions and section openings, place the reader within the product or context before diving into the detail.

## 7. Be organized

- Short paragraphs and sentences of no more than 25 words.
- Lists to group items. Each item starts with a capital letter and ends with a period.
- After a colon `:`, lowercase unless it is a proper noun.
- Section titles are lowercase (sentence case) except for proper nouns.
- Sequential instructions go in numbered lists, not in prose.

## 8. Explain better with images

Illustrate ideas with screenshots, diagrams or icons from the tool itself. Images complement the text, they do not replace it. _Stratio Discovery_ docs lean heavily on guided screenshots: the whole _Stratio Command Center_ deployment flow and the _Stratio Virtualizer_ connection form are captured step by step (51 images per language tree). There are **no videos and no attachments** in this repo — see R25.

## 9. Respect the nomenclature

Always use the approved form of each term. When in doubt, consult `term-glossary.md`. Before choosing a literal marker (italics / backticks / double quotes) for a domain term, `grep` it across the other pages and reuse the marker already in use — **unless** that usage conflicts with the taxonomy (R1/R14/R27); the repo is inconsistent in places, so match the taxonomy and normalize the outliers rather than copying them.

## 10. Be foresighted

Include known customer problems in the FAQ, Known issues or Troubleshooting sections. Distinguish between usage incidents and development errors.

---

# Extended formatting rules

## R1. Quotes and inline code

| Element | Format | Example |
|---------|--------|---------|
| Navigation paths, tabs, UI sections | Single quotes | 'Services' → 'Deployments' |
| Calls to action (buttons, links, clickable options) | Double quotes | "Save", "Add user" |
| Clickable buttons and _Stratio Command Center_ option labels | Double quotes | "Deploy", "Use virtualizer jdbc from Extra JARs" |
| Commands, attributes, parameters, inline code | Backticks | `ALLOWED_GROUPS`, `AUTHENTICATOR`, `MB_DB_URL` |
| Form fields or data structures | Italics | _Admin group_, _Analytic image_ |
| File names and filesystem paths | Italics | _/etc/hosts_, _log4j2.xml_, _docker-entrypoint.sh_ |
| Example names of resources or instances | Backticks | `` `s000001-discovery` ``, `` `user01` `` |

**Form field vs. listed option.** A form field named in running prose goes in italics (`el campo _Admin group_`). But when listing a deployment option together with its technical key, the repo puts **both in backticks**: `` * `Use group whitelist` (variable de entorno `USE_GROUP_WHITELIST`): … ``. That is the pattern of `end-user-authentication.adoc` — which contains **no bold at all** — and it is the one to use for every option/settings list. Do not introduce a bold label; `**text**` (double asterisk) does not appear anywhere in the tree either.

**Deployment option labels are quoted, not translated.** The release notes and `what-is-new.adoc` cite a _Stratio Command Center_ option by its exact English label in double quotes, followed by its variable in backticks: `la opcion de despliegue "Use virtualizer jdbc from Extra JARs" (variable de entorno VIRTUALIZER_JDBC_FROM_EXTRA_JARS)`. Keep the label in English in BOTH language trees, because it is what the operator reads on screen; only the surrounding prose is translated.

## R2. Italics and bold

- **Italics** (`_text_`): untranslated English terms (see the thesaurus), file names, paths, form fields and data structures, Stratio product names.
- **Bold** (`*text*`): important emphasis, and the version series in the `what-is-new` opening line (`*0.56.4-0.2.X*`). It is rare in this tree, and there is not a single `**text**` — the double asterisk is never needed.
- **`*_Breaking change_*`** is the one accepted bold-plus-italic combination, and only as the label that opens a release-notes bullet: the italics belong to the untranslated term and the bold makes it a label. Do not extend the combination to any other term.
- Do not use italics and bold at the same time on the same term.

## R3. Escaping special characters inside italics

When `_` or `*` inside italicized text is not meant to produce formatting (for example, an underscore in a file name), escape it with `++`:

- ✅ `_pg++_++hba.conf_`
- ❌ `_pg_hba.conf_` (the inner underscore breaks the italics)

In this repo the case that recurs is an environment variable cited inside an italic span. Prefer backticks for the variable, which need no escaping at all, over italicizing and escaping it.

**Exception:** escaping is only needed inside *italic* `_…_` spans. Inside backticks, code blocks, double-quoted examples and plain prose, `_` needs **no** escaping — a mid-word underscore (`film_actor`, `customer_id`) does not open italics (AsciiDoc constrained formatting requires a word boundary), so do not add `++_++` there. When unsure whether a `_` will italicize, render the page with Asciidoctor and check for a stray `<em>`.

## R4. Asides: em-dashes → parentheses

Asides set off with em-dashes (—…—) must be turned into parentheses:

- ❌ `el _driver_ —si se configuró en el despliegue— cancela la consulta`
- ✅ `el _driver_ (si se configuró en el despliegue) cancela la consulta`

## R5. Temporal formulas

| Incorrect form | Correct form |
|----------------|--------------|
| en el momento del despliegue | durante el despliegue |
| en tiempo de despliegue | durante el despliegue |
| con posterioridad | tras él |
| a posteriori | tras él |

Example: "Esta configuración puede establecerse durante el despliegue o tras él."

## R6. Acronyms with their expansion

**In Spanish**: the acronym expansion goes in italics because it is a foreign-language phrase. The acronym itself does NOT go in italics.

- ❌ `LDAP (Lightweight Directory Access Protocol)`
- ✅ `LDAP (_Lightweight Directory Access Protocol_)`

**Plural of acronyms — ES vs EN.** In Spanish, acronyms are *invariable* in the plural: do NOT add a final `s` (the article carries the plural). In English, acronyms pluralise normally.

- ❌ `las CAs`, `los LLMs`, `las APIs` — ✅ `las CA`, `los LLM`, `las API`, `los ID`
- EN: `CAs`, `LLMs`, `APIs`, `IDs` are correct.

This is the single most frequent per-language correction in review (e.g. the docs team repeatedly changes `CAs` → `CA` in Spanish pages).

**In English**: the expansion is NOT italicized, since it is in the language of the document.

- ✅ `LDAP (Lightweight Directory Access Protocol)`

## R7. Instructions involving the interface

| Incorrect form | Correct form |
|----------------|--------------|
| Pulsa el botón "X" | Haz clic en "X" |
| Clica en "X" | Haz clic en "X" |
| Accede a "X" (button) | Haz clic en "X" |
| Accede a 'X' (navigation) | Ve a 'X' |
| Pulsa sobre el nombre de la colección | Haz clic en el nombre de la colección |

## R8. Code blocks

Replace `[source,ruby]` with the correct language for the content:

| Content | Tag |
|---------|-----|
| Shell commands, `curl` calls, `kubectl` | `[source,bash]` |
| JSON payloads, Vault secrets, API responses | `[source,json]` |
| Log lines and plain output | `[source,text]` |
| SQL statements | `[source,sql]` |
| YAML manifests | `[source,yaml]` |

⚠️ **`[source,logs]` is not a language** (Rouge does not know it). It was used twice, in `create-a-new-stratio-virtualizer-connection.adoc` (both trees), and is now `[source,text]`. The frequent defect was worse: **every `[source,bash]` block in the tree contained a log excerpt or a JSON snippet, not shell** — six per tree, all corrected. Check the content before you pick the tag. See R26 for the full source-language inventory.

## R9. Admonition blocks

Use the standard AsciiDoc syntax:

```
NOTE:
TIP:
WARNING:
IMPORTANT:
```

All four are in use, `NOTE` and `WARNING` most often. **`CAUTION` is not used** — it survived in `key-concepts.adoc` until it was normalized to `WARNING`, and it is the one admonition the rest of the Stratio docs repos do not use either. Do not introduce it. Prefer inline admonitions. Use a delimited `====` admonition only when it spans several paragraphs or contains an image or a code block.

**Titled blocks are rare and specific.** The only one in the legacy tree is `.Diferencia entre pulsos y suscripciones` / `.Difference between pulses and subscriptions` above a block in `export-and-import-collections.adoc`; the upgrade fact sheets add `.Mensaje para transmitir` / `.Message to convey` with `[caption=]`. Use a `.Title` for a titled example or aside; do not wrap a single plain paragraph in `====` when a normal paragraph suffices.

## R10. Passwords and sensitive data

Replace any plaintext password or sensitive data with a _placeholder_ in angle brackets:

- `<keystore-password>`
- `<cluster-id>`
- `<token-value>`

Placeholders `<...>` are not only for secrets: use them for **any** user-substituted value, always lowercase-with-hyphens (`<release-name>`, `<user-name>`, `<namespace>`, `<path>`). In this repo the convention that actually appears is the shell form `${…}` inside a `+…+` passthrough (`+userland/certificates/${DISCOVERY_INSTANCE_NAME}+`), so prefer it for consistency; `<...>` stays available for secrets and for values with no environment variable behind them.

## R11. Numerals

Numbers below ten are written as words (two, four, eight), unless they are technical values, measurements, versions or coordinates.

## R12. Orthography (Spanish)

- "sólo" → "solo" (no accent, current RAE rule)
- Demonstratives without accent: "este", "ese", "aquel"
- Remove "dicho/a" as an anaphoric determiner: replace it with "este/a", "el/la" or the specific noun.

---

# Extended rules verified against Stratio docs reviews (R13–R28)

Added from the documentation team's reviews of the `stratio-docs/` repos. They complement the rules above; R25–R26 and R30 capture discovery-specific facts, and R30 deliberately contradicts what the sibling `intelligence` repo does.

## R13. Italics recalibrate per language (EN ≠ ES)

The ES/EN mirror is **not a literal copy**: the italics on English terms depend on the language of the document.

- **In ES**, generic infrastructure anglicisms go in italics: `_driver_`, `_endpoint_`, `_cluster_`, `_namespace_`, `_pod_`, `_backend_`, `_dashboard_`, `_log_`/`_logs_`, `_token_`, `_timeout_`, `_plugin_`, `_whitelist_`, `_cookie_`, `_placement_`, `_troubleshooting_`, `_streaming_`, `_snippet_`, `_trigger_`.
- **In EN**, those same generic terms **drop** the italics (they are already in their own language): `driver`, `endpoint`, `cluster`, `namespace`, `pod`, `backend`, `dashboard`, `logs`, `token`, `timeout`, `plugin`, `whitelist`, `cookie`, `placement`, `troubleshooting`, `streaming`, `snippet`, `trigger`.
- **Italic in BOTH languages**: every `_Stratio X_` product name, plus file names and paths.
- **Metabase object names are the exception to the "do not translate" habit.** The ES tree translates `collection` → `colección`, `question` → `pregunta`, `subscription` → `suscripción`, `pulse` → `pulso` and `model` → `modelo`, but keeps `_dashboard_`, `_snippet_` and `_x-ray_` in English and in italics. Follow the split; do not italicize the translated ones.
- Third-party products are **never** italicized in either language: `PostgreSQL®`, `Metabase`, `Oracle`, `MongoDB`, `BigQuery`.

⚠️ **Discovery-specific trap: `driver`.** It is the single most frequent generic term in these docs (Metabase drivers, the JDBC driver, the _Stratio Virtualizer_ driver). It is italic in ES (`_driver_`, `_drivers_`) and roman in EN (`driver`, `drivers`) — and `key-concepts.adoc` currently breaks the rule in both trees by writing it in backticks (`` `driver` ``, `` `JDBC` ``) as if it were a literal. It is a common noun, not a literal: fix it to the per-language italics rule when you touch that page.

Rule of thumb when writing EN: if the term is in `translation-glossary.md` as a "word that is NOT translated" and it names a Metabase/Stratio object type (`_Question_`, `_Collection_`), italicize it; if it is generic infrastructure or an anglicism (`driver`, `endpoint`, `cluster`, `dashboard`, `cookie`), no italics.

## R14. Literal taxonomy (refines R1)

Not everything "code-like" goes in backticks. Distinguish:

| What it is | Format | Example |
|------------|--------|---------|
| Property, env var, package, **endpoint path**, and **every configuration value** (numbers included) | Backticks | `` `ALLOWED_GROUPS` ``, `` `/api/health` ``, `` `true` ``, `` `3009` ``, `` `gosec-sso` `` |
| **JDBC connection string / URL parameters** | Backticks | `` `ssl=true&sslmode=verify-full&prepareThreshold=0` `` |
| **File name, filesystem path** | **Italics** | _/etc/pki/discovery.pem_, _log4j2.xml_, _docker-entrypoint.sh_ |
| **Execution / lifecycle state, conceptual enum value** | **Italics** | `_Running_`, `_Ready_`, `_Hard_` (the resource-type value of the descriptor) |
| **String the system emits**: log line, error message, page title | **Double quotes** | `"error 503"`, `"You are not allowed to access Metabase"` |
| **Clickable button, and the exact label of a _Stratio Command Center_ deployment option** | **Double quotes** | `"Deploy"`, `"Customize deployment"`, `"Use virtualizer jdbc from Extra JARs"` |
| Example resource/instance name | Backticks | `` `s000001-discovery` ``, `` `user01` ``, `` `stratio-stratio-owners` `` |

**Configuration values are NOT a textual citation**: a default, a value the reader must set, or a value shown exactly as it appears all take **backticks**, in prose and in tables alike (`` establece `USE_GROUP_WHITELIST` en `true` ``, `` `AUTHENTICATOR` con el valor `gosec-sso` ``). Double quotes are reserved for what the *system* prints back — log lines, error messages, a page title — and for clickable buttons and _Stratio Command Center_ option labels. In Spanish a value is set **`en`**, never «a»: `` establece `USE_GROUP_WHITELIST` en `false` ``.

**Every new environment variable goes in `installing-and-upgrading/environment-variables.adoc`**, not only in a release note — a variable documented only there is a variable the operator will not find. Match the shape of that page exactly rather than the general table rule: four `|===` tables, **no `[cols]` and no `options="header"`**, a five- or six-column header row written as an ordinary row (`| Propiedad | Descripción | Posible/por defecto | Obligatorio | Tipo`, plus `| Obsoleto` in three of them), and **the variable name written without backticks** in the first cell. It is not the convention this guide would choose, but it is the one the page uses, and a new row in a different shape is worse than a consistent one.

**API request/response field names** in prose follow the data-structure rule (italics with escaped underscores, per R1/R3). A reference page that already backticks its identifiers may keep field names in backticks for local consistency — pick one convention per page and apply it to every sibling field rather than mixing both on the same page.

## R15. Bold: single asterisk and full phrase

- Use `*text*` (one asterisk), not `**text**`. In AsciiDoc, `**` is only needed for bold in the middle of a word.
- Bold covers the **whole** phrase, not a single word: `*tiene precedencia sobre la detección automática*`, not `tiene *precedencia* sobre…`.

## R16. Blank line after an anchor

Between a block anchor (`[#slug]`) and the `==`/`===` that follows it there must be **one blank line**. This is a recurring omission.

```
[#summary]

== Resumen
```

⚠️ **Explicit anchors are the exception, not the rule.** The upgrade fact sheet declares 18 `[#slug]` at the same lines in both trees; `what-is-new/breaking-changes.adoc`, `upgrade.adoc`, `upgrade/details-upgrade.adoc` and `troubleshooting.adoc` declare a few each. Every other page relies on the id Antora auto-generates from the heading text, which is why two references were broken (see R17 and R30). **New content must declare its anchors explicitly**, and every new section another page links to has to do it too.

## R17. Cross-references: intra-component vs cross-component

The Antora component of these docs is **`stratio-discovery`** (see `antora.yml`).

- **Within these docs** (to another page of the same component): `xref:<module>:<path>.adoc[text]` (**2 parts**, no component). The module prefix is **always** written, including `ROOT`. Count them with `grep -rho 'xref:[a-z-]*:' es en --include='*.adoc' | sort | uniq -c` when you need the current figure. E.g. `xref:operations-guide:installing-and-upgrading/upgrade.adoc[…]`, `xref:ROOT:what-is-new/release-notes.adoc[notas de _release_]`.
- **To another product/component**: `xref:<component>:<module>:<path>.adoc[text]` (**3 parts**). Components referenced in this repo: `stratio-gosec` (4) and `stratio-virtualizer` (2), plus the self-reference `stratio-discovery` (6, only from the installation-guide partial — see the partial rule in `page-templates.md`).
- **Modules:** only three exist — `ROOT`, `operations-guide` and `installation-guide` (partial-only). There is no `user-guide`/`developer-guide` module: `user-guide.adoc` and `quick-start-guide.adoc` are pages of `ROOT`, so they are referenced as `xref:ROOT:user-guide.adoc[…]`, never `xref:user-guide:…`.
- **Auto-generated section ids.** A `== Heading` gets its id by lowercasing, turning each run of non-alphanumerics into `_`, stripping the leading/trailing `_`, **keeping accents** and **dropping `™`/`®`**: `== JDBC y drivers en Metabase` → `#_jdbc_y_drivers_en_metabase`; `== Configuraciones específicas` → `#_configuraciones_específicas` (with the accent). If unsure of the exact id, render the page with Asciidoctor and read the generated `id=`.
- **Auto-generated ids differ between ES and EN**, because the headings are translated: `#_jdbc_y_drivers_en_metabase` vs `#_jdbc_and_drivers_in_metabase`. This repo relies on that (unlike the intelligence repo, which forces a shared Spanish slug), so a cross-reference to a heading has to be written **per language** and both have to be checked. See R30.
- **Two references were broken, and were fixed; they are the reason this rule is spelled out**: the installation-guide partial pointed at a `#_configuraciones_especificas` / `#_specific_configurations` section of `deployment.adoc` that has never existed (the real ids are `#_configuración` / `#_configuration`), and the EN `create-a-new-stratio-virtualizer-connection.adoc` wrote `xref:…#configuration-attributes [text]` with a space before the `[`, which stops it being a link at all. Render the page and read the generated `id=` rather than guessing the slug.
- The link text carries the naming italics as appropriate and is **never** "aquí"/"here"/"this section". Wrap it naturally: `consulta la xref:…[guía de actualización de versión]`.

## R18. Asides and dashes (reinforces R4)

Beyond turning `—…—` asides into parentheses, take the chance to rewrite toward shorter sentences: `stops responding —because it is being upgraded—` → `(whether due to an upgrade or an outage)`.

## R19. English-language conventions

- **Oxford comma**: yes in EN (`collections, questions, and dashboards`); no in ES.
- **Dates**: full month name (`January 19, 2025`, not `Jan 19, 2025`).
- **Headings in sentence case, not Title Case**: capitalize only the first word and proper nouns (`Status monitoring`, not `Status Monitoring`). This is the same rule as principle 7 for ES, restated because Title Case is the English default an author may fall back to.

## R20. File hygiene

- No BOM at the start of the file. (Some older files carry a BOM by inheritance; it is a defect the docs team removes — do not add one.)
- No trailing whitespace at the end of lines.
- Exactly one blank line between block elements (neither zero nor two).
- Straight quotes only (`"…"`), never curly (`“…”`), and straight apostrophes (`'`), never `’`. Preventive for the quotes (there are none in the tree); the three typographic apostrophes that did exist have been corrected.
- No non-breaking spaces (U+00A0) or other Unicode spaces — especially after a heading marker (`==`/`===`) or in place of a normal space. They render as literal text, silently break heading parsing (and the heading's anchor), and slip past a naive `wc -l` parity check. Scan touched pages with `grep -nP '\xc2\xa0'`, and compare ES/EN heading profiles with `grep -nE '^=+ '` (ASCII space required, so a stray U+00A0 after `===` surfaces as a missing heading).

## R21. Version formats

**The version of _Stratio Discovery_ has two halves and they are not interchangeable.** The scheme is `${Metabase version}-${Stratio version}` (documented in `key-concepts.adoc`): in `0.56.4-0.2.0`, `0.56.4` is the **Metabase version** bundled in the image and `0.2.0` is the version of Stratio's own changes. It has been in force since `0.38.1-0.1`.

- **Always write the full version, both halves.** `0.56.4-0.2.0`, never `0.2.0` and never a bare `0.56.4` — a bare `0.56.4` reads as the Metabase version, which is a different thing.
- **Series (all patches):** `0.56.4-0.2.X` with a literal trailing `X` — the form `what-is-new.adoc` uses. Exact version: `0.56.4-0.2.0`.
- **Lexicographic order is misleading and the docs must not rely on it.** `0.40.0` (old scheme) sorts above `0.38.1-0.2` even though it is older, and the two release lines `0.50.21-0.2.x` and `0.56.4-0.1.x` are maintained in parallel from different branches. When you cite a version, cite the Universe it ships in as well.
- Third-party versions keep their full form (`Metabase 0.56.4`, the JDBC driver of _Stratio {modulename}_ `4.0.1` in the 0.56.4-0.2.0 release). Check the version in `virtualizer-jdbc/pom.xml` of the tag you are documenting, not of the branch head, which may already carry the next one.
- **Platform "Universo".** Unlike other Stratio modules, the version of _Stratio Discovery_ has **no** one-to-one correlation with the Universe: `0.50.21-0.2.0` ships in Universes 14.8, 14.9 and 14.10, and `0.56.4-0.2.0` in 15.1. Write the correlation explicitly wherever it matters (`0.56.4-0.2.0 (Universo 15.1)`). `Universo` is capitalized, no italics; in EN it is `Universe`.
- **The docs-portal version is a third, separate thing.** `antora.yml` carries `version: '0.56.4-0.2'` — the segment of the documentation site, not a claim about the code. Do not change it when you document a feature (see `page-templates.md`).

## R22. Date formats

- EN: full month name, `Month DD, YYYY` (`January 19, 2025`).
- ES: `D de <mes en minúscula> de AAAA` (`8 de abril de 2026`) — month lowercase, no leading zero.

## R23. Parenthetical examples

- ES: `(p. ej. …)` **with a space** after the dot — never `p.ej.`; in running prose use `por ejemplo`.
- EN: `(e.g. …)` in parentheses; in running prose use `for example`.

## R24. Navigation paths and literal braces

- Navigation paths: single-quoted segments chained with ` → ` (U+2192, with spaces). **Never `>` nor the ASCII `->`** (the docs team rewrites both to `→`). Any length: `'Services' → 'Deploy service'`, `'General' → 'User Authentication' → 'Admin group'`. When you name the element type, it goes first: `la sección 'General'`, `la pestaña 'Settings'`. Any `->` inside a navigation path is a defect, so grep both trees (`grep -rn '\->' es en --include='*.adoc'`) before claiming the tree is clean. The three that survived until the upgrade-documentation change were on `operations-guide/pages/installing-and-upgrading/deployment.adoc` (two) and `installation-guide/partials/installing-discovery.adoc` (one).
- To show a literal `{…}` in prose (e.g. a `${VAR}` the reader must substitute), escape the opening brace: `$\{VAR}`, so AsciiDoc does not resolve it as an attribute reference. Inside `----` blocks or backticks no escaping is needed.

## R25. Images and screenshots (discovery)

- **Screenshots carry the guided flows.** Always the block form `image::file.png[]` — the inline `image:icon.png[]` is not used here. Assets live per module in `modules/<module>/assets/images/` (51 references per language tree). The `_Stratio Command Center_` deployment flow is captured step by step, with numbered file names that encode the order: `cct_services_list_discovery_deployment_0001.png`, `cct_deploy_service_discovery_deployment_0002.png`, … Keep that convention when you add a screenshot to a numbered flow, and renumber the rest rather than appending out of order.
- **The two language trees hold their own copy of every image**, under `es/modules/<module>/assets/images/` and `en/modules/<module>/assets/images/`, with the same file name. Add and delete in both.
- **There are no videos and no attachments in this repo.** Do not introduce `video::` or `attachment$` without a real asset: the `OrphanAssets` and `AssetsLinks` CI checks look at both directions (see the CI gate in `SKILL.md`).

## R26. Source-language inventory (discovery)

Languages present in `[source,…]` blocks today: `json` and `text`; `bash` is legitimate for a real shell command but no block uses it any more. **`logs` is not a valid language** — the two occurrences in `create-a-new-stratio-virtualizer-connection.adoc` are now `text`. And check the *content*, not the habit: every `[source,bash]` in the tree used to hold a log excerpt or a JSON snippet, six per tree, all now retagged. `sql` and `yaml` are legitimate for their content but are not in use today. Never leave a placeholder `[source,ruby]` nor an empty `[source,]`.

## R27. UI option values: the user-facing label, not the backend enum

When you document the value of a UI-selectable option (a dropdown or a checkbox the operator picks in _Stratio Command Center_, or a field of the Metabase database form), use the **label the user sees**, NOT the backend enum value.

- Put the option label in **double quotes** (R1 — it is a selectable value); put the section or tab name in **single quotes**; use backticks for the environment variable and for the raw value when you must name it as such: `` la opción "Use group whitelist" ('User Authentication'), variable de entorno `USE_GROUP_WHITELIST` ``.
- **The labels are English in both trees.** _Stratio Command Center_ is not translated, so `'User Authentication' → 'Admin group'` reads the same in the ES and the EN page; only the prose around it changes.
- A descriptor value may never have been exposed in the UI, or may have moved to the advanced view — do not tell the reader to look for it in the interface unless you have confirmed it in the descriptor of the target Universe. Two live examples: the `authenticator` option did not disappear in 15.1, it moved from `level: 1` to `level: 2` (the advanced view); and the option "Skip Metabase's Statement final cancellation in Virtualizer driver" (`level: 1` in 14.10) really is gone from the 15.1 descriptor, while the 'Probes' and most of the 'Resources' fields kept their titles and levels but lost their `internalName`, which is now `NOT_USED` — they stopped being injected into the container without being removed from the interface. Documenting any of these as "removed" or "added" would be wrong.

## R28. Config set in _Stratio Command Center_: name the descriptor tab

**Name the option by its descriptor `title`, not by its environment variable.** The operator does not see variable names in _Stratio Command Center_: they see a labelled field. So the option is cited by its exact `title`, in double quotes, and the variable comes second — in backticks, in parentheses — and only when it adds something: when the reader has to set it outside CCT, read it from inside the container, or find it in a script.

- ✅ `la opción "Use group whitelist" ('General' → 'User Authentication')`
- ✅ `la opción "Skip Metabase's Statement final cancellation in Virtualizer driver" (variable de entorno VIRTUALIZER_DRIVER_SKIP_STMT_CANCELLATION)`, in backticks
- ❌ naming the variable when the reader is working in the CCT form
- ❌ citing the option's `description` as if it were its `title`: the `title` of `AUTHENTICATOR` is `Authentication method`, while `Authenticator method to use (gosec-sso, headers, jwt, metabase-native)` is its `description`. The docs carried that mistake for a while.

**A variable with no `title` has no option behind it**, so it is cited by its name alone, in backticks: `LOG_ACCESS_DATA`, `DATA_AUDIT_ENCRYPTION_KEY_PASS`, `TENANT_NAME`, `CURL_CUSTOM_SECURITY_OPTS`, `USE_GOSEC_SSO_AUTH`, `VIRTUALIZER_DRIVER_LOG_LEVEL`. Check the descriptor before assuming either way — and check the descriptor of the **target Universe**, because an option can exist in one and not in another.

**Values picked from the form also take the user-facing form.** A log level chosen from a dropdown is `"DEBUG"` / `"INFO"` in double quotes (R27); backticks are for the literal value of a variable set outside CCT.

Also name the descriptor tab/section, in single quotes chained with `→` (`en _Stratio Command Center_, en 'General' → 'User Authentication'`).

The tabs of the _Stratio Discovery_ descriptor, and their groups, are fixed and worth knowing by heart:

- **'General'** → 'Networking', 'PostgreSQL', 'User Authentication' (+ 'Gosec SSO Authenticator Settings'), 'Resources', 'Probes', 'Metrics', 'Log levels'.
- **'Settings'** → 'Import/Export service', 'Nginx', 'Metabase'.
- **'Environment'** → mostly prefilled from the cluster's global variables, but **not** read-only: only the Vault host, port and enable flag, the two `oauth2proxy` fields and the service account are `readOnly: true`, all of them `level: 2`. Three fields are editable and visible in the basic view: "Secret Role" (prefilled, but not updatable after the deploy) and the two that 15.1 adds, "KEOS CA secret name" (default `keos-ca`) and "KEOS CA name (key inside secret)" (default `ca.crt`), both required and updatable — these are the ones the 15.1 upgrade asks the operator to confirm.

**Find the tab in the descriptor of the target Universe, not in this repository.** The authoritative descriptor is `packages/discovery/discovery.json` in the `Stratio/kubernetes-universe` repo, on the branch of the Universe (`branch-15.1`, `branch-14.10`); the local `deployment/CCT/k8s-descriptors/discovery.json` is the development copy and can differ. Read it with `gh api -H "Accept: application/vnd.github.raw" "repos/Stratio/kubernetes-universe/contents/packages/discovery/discovery.json?ref=branch-15.1"`.

## R29. The EN page mirrors the ES one; it is not an autonomous English text

The most frequent correction in editorial review. Translate sentence by sentence, keeping:

- **Order and sentence segmentation.** Do not merge two Spanish sentences into one English one, nor split one in two.
- **Voice.** If the ES uses the impersonal/reflexive passive, the EN uses the passive. Do not turn it into an active English sentence.
- **The definite articles of the ES**, even where generic English would drop them: `the groups the user belongs to`, `all the pending collections`.
- **Do not stack more than two nouns.** `the platform's PostgreSQL® metadata store configuration` is wrong twice over. But there is **no** blanket preference for `X of Y`: choose per sentence for readability, and use the genitive when `X of Y of Z` would chain.
- **The fixed lexical equivalent**, even when a more idiomatic English turn exists: `arrancar` → `start up` (not `start`), `cerrar la sesión` → `close the session` (not `log out`), `recorrer` → `traverse`, `retirada` → `retirement`, `desregistrar` → `deregister`.
- **Nominal headings and table headers**, not gerunds or ellipsis: `Recovery from a failed upgrade` (not `Recovering from…`), `| Element | State after the rollback` (not `| Item | Status…`).

**`^[English]^` is ES-only.** Every link in the Spanish tree that points at documentation available only in English carries the marker right after the link text (zero in `en/`; count the ES ones with `grep -ro '\^\[English\]\^' es --include='*.adoc' | wc -l`). It is a deliberate divergence: add it when you write an ES link to Metabase's documentation, and drop it when you mirror the line into EN.

**Industry-term exception.** A few concepts take their standard English term while the ES keeps its own: `ventana de intervención` → *maintenance window*; `parada` → *downtime* (not *outage*); `interfaz de administración` → *administration interface*; `aviso de obsolescencia` → *deprecation notice*.

**`upgrade` vs `update` (EN).** `upgrade` is only moving the product or the Universe to a higher version. Refreshing an image, a driver, a dependency or the bundled Metabase is an `update` — Spanish says "actualización" for both, which is where the error comes from: `Update of the Virtualizer JDBC driver`, but `Upgrade from 0.50.21-0.2.0 to 0.56.4-0.2.0`.

⚠️ **This repo's EN tree drifted away from its ES pair in places** (the pages were written years apart and edited separately): `troubleshooting.adoc` and `key-concepts.adoc` carry English asides and jokes that the Spanish does not, and vice versa. When you touch a paragraph, bring the two back into alignment — but only that paragraph, and check the line counts afterwards (the CI gate compares them).

## R30. Anchors are per language in this repo

**This is the opposite of the `intelligence` repo, so do not copy that rule here.** In _Stratio Discovery_, every legacy cross-reference relies on the id Antora generates from the heading text, and because headings are translated, the id differs between trees:

- ES `== JDBC y drivers en Metabase` → `#_jdbc_y_drivers_en_metabase`
- EN `== JDBC and drivers in Metabase` → `#_jdbc_and_drivers_in_metabase`

Consequences you must respect:

1. **Never copy an anchor from one tree to the other.** The ES page links the ES id, the EN page the EN id. A copied fragment resolves nowhere and Antora reports nothing.
2. **Renaming a heading silently breaks every inbound link to it**, in that language only. Before renaming, grep both trees for the old id and repoint every hit.
3. **New sections that another page links to must declare an explicit `[#slug]`**, identical in both trees, with a blank line after it. That is what the upgrade fact sheets do, and it is the direction to move in: an explicit anchor survives a heading rewrite and a translation. Do not retrofit anchors onto old pages as a standalone change, though — it would touch pages in one tree only and fail `LanguageCheck`.
4. **Accents survive in the generated id** (`#_configuraciones_específicas`), and `™`/`®` are dropped. If unsure, render the page and read the generated `id=`.

Internal references carry **no** space after the comma: `<<summary,resumen>>`. Every one of the internal references in the tree follows it. Before the upgrade-documentation change there were only two, both in `troubleshooting.adoc` and both spaced, and they were corrected. rocket-core mostly does the same, while `intelligence` and `genai` lean on the spaced form, so do not copy an internal reference across repos.

## R31. Spanish grammar the review corrects most often

- **Subjunctive** after prospective `hasta que` (`hasta que un reintento **tenga** éxito`), after prescriptive `asegúrate de que` / `verifica que` (`asegúrate de que la versión de _Stratio Virtualizer_ **sea** 0.11.0 o superior`), and in relative clauses with a hypothetical antecedent (`una actualización que **introduzca** variables nuevas`).
- **Future tense for hypothetical consequences**, not the timeless present: `el servicio no **estará** disponible hasta que…`, `las conexiones JDBC **quedarán** bloqueadas si…`.
- **`nuevo` before the noun**: `la nueva versión`, `un nuevo campo`, `la nueva imagen`.
- **Trademark + noun takes `de`**: `el _driver_ **de** Metabase`, `las colecciones **de** Metabase` (in EN, `the Metabase driver` is correct). This is one of the most frequent slips in these pages, which are full of `driver Metabase` and `servidores Stratio Virtualizer`.
- **Government**: `aplicar**se** a`, `informar **sobre**`, `compartir **con**`, `establecer … **en**`.
- **Preferences**: `Desde la versión X` over `A partir de la versión X`; `por lo que` over `así que` in formal prose; `automáticamente` over `de forma automática`; `indefinidamente` over `de forma indefinida`.
- **Punctuation**: comma after an opening connector (`Así,`, `En ambos casos,`, `No obstante,`); **no** comma before `y` when it coordinates predicates of the same subject; no comma before a purpose clause with `para`. In EN, **do** use a comma before `and` when it joins two independent clauses.
- **Never `Por favor` in ES.** In EN, `Please + imperative` is acceptable to soften an operational recommendation. `key-concepts.adoc` opens with `TIP: Por favor, lee esta breve guía` — that is the pattern to remove, not to imitate.
- **No first person plural and no jokes.** See principle 3: this repo carries a narrative voice (`no vamos a reinventar la rueda`, `¡abróchate el cinturón de seguridad!`, `Lamentamos las molestias`) that the docs team removes on sight.

## R32. Lexical choices confirmed in editorial review

**Spanish**

- `requerir` over `exigir` and over `necesitar`: `una conexión que **requiere** autenticación`, `no **requiere** configuración adicional`.
- `hasta que` + subjunctive over `mientras no`: `falla **hasta que configures** la autenticación`.
- `no … sino que` for contrast: `Metabase no ejecuta la consulta, **sino que** espera los resultados de la base de datos`.
- `vincular` over `enlazar` for logical relations; `en el disco`, not `en disco`; `el _driver_ de Metabase`, not `driver Metabase` (see R31).
- **`disponibilizar` does not exist.** Rewrite: `para que el _driver_ esté disponible`, `que puedes usar como alternativa`.
- **`soportado` / `soporte` → `compatible` / `compatibilidad`.** The pages are full of `Soporte para _placements_`, `siempre que sean soportados por Metabase`, `Soporte para usar preguntas como valores en filtros`. The canonical form is `compatibilidad con` and `es compatible con`.

**English**

- `on the platform`, not `in the platform`.
- `username`, not `user name` nor a bare `user`, for a login identifier.
- Xref link text prefers the gerund: `[connecting to PostgreSQL® with TLS]`, not `[the connection to PostgreSQL® with TLS]`, even when the Spanish nominalizes.
- `authenticate with`, not `authenticate against`.
- `collection`, `question` and `dashboard` are the Metabase object names — use them, do not invent `panel` or `report` where the product says `dashboard`.

## R33. Do not generalize a rule from a single correction

Editorial review corrects sentence by sentence, and two passes can legitimately reach opposite conclusions on the same kind of element. A path in prose was moved from italics to backticks in one review and from backticks to italics in the next.

Before turning a correction into a rule: `grep` the term or the marker across the rest of the tree (decalogue principle 9) and follow the majority usage. If the correction contradicts what the tree already does, it is a candidate for a question, not for a new rule.
