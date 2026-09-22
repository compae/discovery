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
| _Stratio Command Center_ option and field labels | Italics | _Use virtualizer jdbc from Extra JARs_, _Metabase log level_ |
| Values the operator picks for an option | Double quotes | "INFO", "gosec-sso", "Guaranteed" |
| Commands, attributes, parameters, inline code | Backticks | `ALLOWED_GROUPS`, `AUTHENTICATOR`, `MB_DB_URL` |
| Form fields or data structures | Italics | _Admin group_, _Analytic image_ |
| Clickable buttons | Double quotes | "Deploy", "Save", "Customize deployment" |
| File names and filesystem paths | Italics | _/etc/hosts_, _log4j2.xml_, _docker-entrypoint.sh_ |
| Example names of resources or instances | Backticks | `` `s000001-discovery` ``, `` `user01` `` |

**Form field, option label and value.** The rule is the same one rocket-core, `genai` and `intelligence` carry, so do not invent a local variant: the **name** of a _Stratio Command Center_ option or field goes in **italics** (`el campo _Admin group_`, `la opción _Metabase log level_`), never in bold nor bold+italic; the **values** the operator picks for it go in **double quotes** (`"INFO"`, `"gosec-sso"`); and the environment variable stays in backticks, in parentheses, only when the reader needs it outside the form. Buttons keep their double quotes, because they are clicked, not filled in: `"Deploy"`, `"Customize deployment"`. Do not introduce a bold label; `**text**` (double asterisk) does not appear anywhere in the tree either.

**Deployment option labels stay in English and go in italics, not in quotes.** The release notes and `what-is-new.adoc` cite a _Stratio Command Center_ option by its exact English label in italics, followed by its variable in backticks: `la opcion de despliegue _Use virtualizer jdbc from Extra JARs_ (variable de entorno VIRTUALIZER_JDBC_FROM_EXTRA_JARS)`. Keep the label in English in BOTH language trees, because it is what the operator reads on screen; only the surrounding prose is translated.

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
| **Clickable button** | **Double quotes** | `"Deploy"`, `"Customize deployment"` |
| **Label of a _Stratio Command Center_ option or field** | **Italics** | `_Use virtualizer jdbc from Extra JARs_`, `_Metabase log level_` |
| Example resource/instance name | Backticks | `` `s000001-discovery` ``, `` `user01` ``, `` `stratio-stratio-owners` `` |

**Configuration values are NOT a textual citation**: a default, a value the reader must set, or a value shown exactly as it appears all take **backticks**, in prose and in tables alike (`` establece `USE_GROUP_WHITELIST` en `true` ``, `` `AUTHENTICATOR` con el valor `gosec-sso` ``). Double quotes are reserved for what the *system* prints back — log lines, error messages, a page title —, for clickable buttons and for the values an option takes. In Spanish a value is set **`en`**, never «a»: `` establece `USE_GROUP_WHITELIST` en `false` ``.

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

- Put the option label in **italics** (R1 — it is a form field); put the **value** it takes in double quotes; put the section or tab name in **single quotes**; use backticks for the environment variable and for the raw value when you must name it as such: `` la opción _Use group whitelist_ ('User Authentication'), variable de entorno `USE_GROUP_WHITELIST` ``.
- **The labels are English in both trees.** _Stratio Command Center_ is not translated, so `'User Authentication' → 'Admin group'` reads the same in the ES and the EN page; only the prose around it changes.
- A descriptor value may never have been exposed in the UI, or may have moved to the advanced view — do not tell the reader to look for it in the interface unless you have confirmed it in the descriptor of the target Universe. Two live examples: the `authenticator` option did not disappear in 15.1, it moved from `level: 1` to `level: 2` (the advanced view); and the option _Skip Metabase's Statement final cancellation in Virtualizer driver_ (`level: 1` in 14.10) really is gone from the 15.1 descriptor, while the 'Probes' and most of the 'Resources' fields kept their titles and levels but lost their `internalName`, which is now `NOT_USED` — they stopped being injected into the container without being removed from the interface. Documenting any of these as "removed" or "added" would be wrong.

## R28. Config set in _Stratio Command Center_: name the descriptor tab, and give the full path

**Name the option by its descriptor `title`, not by its environment variable.** The operator does not see variable names in _Stratio Command Center_: they see a labelled field. So the option is cited by its exact `title`, in italics (R1: it is a form field, and this is what rocket-core, `genai` and `intelligence` all do), and the variable comes second — in backticks, in parentheses — and only when it adds something: when the reader has to set it outside CCT, read it from inside the container, or find it in a script.

⚠️ **Citing only the leaf is a defect, not a shortcut.** The reader has to *find* the field before setting it, and in this descriptor a field can be three levels deep. So every citation carries the **full navigation path** — tab → accordion → property — in single quotes chained with `→`, and the property name in italics:

- ✅ `la opción _Use group whitelist_ ('General' → 'User Authentication' → 'Gosec SSO Authenticator Settings')`
- ✅ `la opción _Use virtualizer jdbc from Extra JARs_ ('Settings' → 'Metabase')`
- ✅ `la opción _Authentication method_ ('General' → 'User Authentication')`
- ❌ `la opción _Use group whitelist_ ('User Authentication')` — the tab is missing, so the reader does not know where to start
- ❌ `la opción _Use group whitelist_ ('General' → 'User Authentication')` — one accordion short: this field lives inside `'Gosec SSO Authenticator Settings'`, nested in `'User Authentication'`. This exact truncation is what `rocket-core` had to repair after merge, across 10 files (`'Probes'` → `'General' → 'Probes'`), and it is the reason this rule exists
- ❌ naming the variable when the reader is working in the CCT form
- ❌ citing the option's `description` as if it were its `title`: the `title` of `AUTHENTICATOR` is `Authentication method`, while `Authenticator method to use (gosec-sso, headers, jwt, metabase-native)` is its `description`. The docs carried that mistake for a while

**Copy the `title` literally, with its own capitalization and its own typos.** Take it from the descriptor, not from your sense of English: it is `'User Authentication'` (both capitals) but `'Log levels'` and `'Import/Export service'` (lowercase second word); `_Nginx log level_`, not `_NGINX log level_`. The 15.1 descriptor also carries real typos in some titles — `'oauth2proxy cooke name'`, `'JWT puclic key Vault tamplate'`, `'CRT Vault tamplate'`, and a `'SERVICE URL '` with a trailing space. If you have to cite one of those, quote it as it is and say the label is misspelled in the descriptor; never silently fix it in the docs, or the reader will search the form for a string that is not there.

**One exception, and only one: a first-level tab cited alone.** `'Environment'`, `'General'` and `'Settings'` **are** the top of the path, so citing one of them on its own is complete and correct — there is nothing to chain it to. `'Environment'` in particular has no accordions at all: all of its fields sit directly under the tab, so `('Environment')` is the full path for every one of them. Do not invent an intermediate group to make such a citation look longer.

**Verify the path, not just the label, and verify it in the descriptor of the target Universe.** `'Probes'` exists under `'General'` here, but the word also names Kubernetes' own liveness/readiness/startup probes, which are a different thing in prose — do not let a grep for the concept stand in for reading the descriptor.

**A variable with no `title` has no option behind it**, so it is cited by its name alone, in backticks: `LOG_ACCESS_DATA`, `DATA_AUDIT_ENCRYPTION_KEY_PASS`, `TENANT_NAME`, `CURL_CUSTOM_SECURITY_OPTS`, `USE_GOSEC_SSO_AUTH`, `VIRTUALIZER_DRIVER_LOG_LEVEL`. Check the descriptor before assuming either way — and check the descriptor of the **target Universe**, because an option can exist in one and not in another.

**Values picked from the form also take the user-facing form.** A log level chosen from a dropdown is `"DEBUG"` / `"INFO"` in double quotes (R27); backticks are for the literal value of a variable set outside CCT.

The tabs of the _Stratio Discovery_ descriptor, and their groups, are fixed and worth knowing by heart. Verified against `packages/discovery/discovery.json` on `Stratio/kubernetes-universe` `branch-15.1`:

- **'General'** — carries five fields directly (`_Discovery service name_`, `_Discovery instance name_`, `_Namespace_`, `_Deployment environment_`, `_Tenant_`) plus the accordions 'Networking', 'PostgreSQL', 'User Authentication', 'Resources', 'Probes', 'Metrics' and 'Log levels'. **'User Authentication' is the only one that nests further**: it holds `_Authentication method_` and then the sub-accordion 'Gosec SSO Authenticator Settings' with `_Admin group_`, `_Create and sync user groups in Metabase_`, `_Use group whitelist_`, `_Allowed groups_` and `_SERVICE URL _`. Those five need the three-level path.
- **'Settings'** → 'Import/Export service', 'Nginx', 'Metabase'. No fields sit directly under the tab, so every citation here is exactly two levels.
- **'Environment'** → **flat: no accordions at all**, so `('Environment')` is the full path for every field in it. Mostly prefilled from the cluster's global variables, but **not** read-only: only the Vault host, port and enable flag, the two `oauth2proxy` fields and the service account are `readOnly: true`, all of them `level: 2`. Three fields are `level: 1`, so they are visible in the basic view: `_Secret Role_` (prefilled with `${Application.domain}-${Application.name}`, required, `updatable: false` after the deploy) and the two that 15.1 adds, `_KEOS CA secret name_` (default `keos-ca`) and `_KEOS CA name (key inside secret)_` (default `ca.crt`), both required and updatable — these are the ones the 15.1 upgrade asks the operator to confirm.

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

Internal references carry a space after the comma: `<<summary, resumen>>`. It is the form the documentation team asked for in the review of PR #409, and the one `intelligence` and `genai` already used; all 26 per tree carry it. rocket-core still mixes the two, so do not copy an internal reference across repos.

## R31. Spanish grammar the review corrects most often

- **Subjunctive** after prospective `hasta que` (`hasta que un reintento **tenga** éxito`), after prescriptive `asegúrate de que` / `verifica que` (`asegúrate de que la versión de _Stratio Virtualizer_ **sea** 0.11.0 o superior`), and in relative clauses with a hypothetical antecedent (`una actualización que **introduzca** variables nuevas`).
- **Future tense for hypothetical consequences**, not the timeless present: `el servicio no **estará** disponible hasta que…`, `las conexiones JDBC **quedarán** bloqueadas si…`.
- **`nuevo` before the noun**: `la nueva versión`, `un nuevo campo`, `la nueva imagen`.
- **Trademark + noun takes `de`**: `el _driver_ **de** Metabase`, `las colecciones **de** Metabase` (in EN, `the Metabase driver` is correct). Write it that way in new content. **Do not go looking for the defect in the tree**: `driver Metabase` and `servidores Stratio Virtualizer` have 0 occurrences at the baseline and 0 in the whole history of `stratio-docs/`, so an earlier version of this rule that called the pages "full of" them was describing a repo that is not this one.
- **Government**: `aplicar**se** a`, `informar **sobre**`, `compartir **con**`, `establecer … **en**`.
- **Preferences**: `Desde la versión X` over `A partir de la versión X` (one occurrence left at the baseline, `troubleshooting.adoc:156`); `por lo que` over `así que` in formal prose; `automáticamente` over `de forma automática`; `indefinidamente` over `de forma indefinida`.
- **Punctuation**: comma after an opening connector (`Así,`, `En ambos casos,`, `No obstante,`); **no** comma before `y` when it coordinates predicates of the same subject; no comma before a purpose clause with `para`. In EN, **do** use a comma before `and` when it joins two independent clauses.
- **Never `Por favor` in ES.** In EN, `Please + imperative` is acceptable to soften an operational recommendation. `key-concepts.adoc` used to open with `TIP: Por favor, lee esta breve guía`; `ea8434b` cut it to `TIP: Lee esta breve guía…`, which is the pattern to imitate. **Two occurrences survive at the baseline**, both worth removing the next time you touch their page: `connect-with-other-modules/create-a-new-stratio-virtualizer-connection.adoc:68` (`Por favor, revisa cuidadosamente…` → `Revisa cuidadosamente…`) and `quick-start-guide.adoc:20` (`Por favor, selecciona "Custom"…` → `Selecciona "Custom"…`). Pair each removal with its EN line so the line counts stay equal.
- **No first person plural and no jokes.** See principle 3: the inherited pages carried a narrative voice that the docs team removes on sight, and the sweeps have already caught it — `¡abróchate el cinturón de seguridad!` (`key-concepts.adoc`, removed in `fe149fe`) is gone, and `Lamentamos las molestias` never existed here at all, so do not go hunting for it. And what remains is not always what an old rule said it was: `key-concepts.adoc:13` reads `No hace falta reinventar la rueda`, which is impersonal and correct — the first-person `no vamos a reinventar la rueda` is a phrasing to avoid writing, not a defect present in the tree. **Two genuine occurrences are still live at the baseline.** `key-concepts.adoc:64` carries it in both trees — `si tenemos en Metabase "30-01-2021 1:00 PM"` / `if we have "30-01-2021 1:00 PM" in Metabase` — so fix the pair together (`si Metabase tiene…` / `if Metabase has…`). `troubleshooting.adoc:22` carries it **in EN only**: `because we depend on Metabase's open source`, where the ES line already reads `porque _Stratio Discovery_ depende del código abierto de Metabase`. That one is an R29 mirror defect, not a Spanish one — bring the English back to what the Spanish says.

## R32. Lexical choices confirmed in editorial review

**Spanish**

- `requerir` over `exigir` and over `necesitar`: `una conexión que **requiere** autenticación`, `no **requiere** configuración adicional`.
- `hasta que` + subjunctive over `mientras no`: `falla **hasta que configures** la autenticación`.
- `no … sino que` for contrast: `Metabase no ejecuta la consulta, **sino que** espera los resultados de la base de datos`.
- `vincular` over `enlazar` for logical relations; `en el disco`, not `en disco`; `el _driver_ de Metabase`, not `driver Metabase` (see R31).
- **`disponibilizar` does not exist.** Rewrite: `para que el _driver_ esté disponible`, `que puedes usar como alternativa`. This one is preventive: 0 occurrences at the baseline and none in the history of `stratio-docs/`.
- **`soportado` / `soporte` → `compatible` / `compatibilidad`.** The canonical form is `compatibilidad con` and `es compatible con`. **Still live at the baseline, four occurrences in the ES tree**, and all three examples this rule has always cited are among them: `release-notes.adoc:73` (`Soporte para _placements_ y _priority classes_`), `architecture.adoc:9` (`siempre que sean soportados por Metabase`), `release-notes.adoc:48` (`Soporte para usar preguntas como valores en filtros`) and `key-concepts.adoc:60` (`no está soportada en la base de datos`). Two of them are inside historical release-note entries, so fix them only as part of a change that already touches that page in both trees. Note that `Universo con soporte` (`upgrade.adoc:73`, `special-upgrades.adoc:3`) is a different sense — a supported platform release — and is correct as it stands.

**English**

- `on the platform`, not `in the platform`.
- `username`, not `user name` nor a bare `user`, for a login identifier.
- Xref link text prefers the gerund: `[connecting to PostgreSQL® with TLS]`, not `[the connection to PostgreSQL® with TLS]`, even when the Spanish nominalizes.
- `authenticate with`, not `authenticate against`.
- `collection`, `question` and `dashboard` are the Metabase object names — use them, do not invent `panel` or `report` where the product says `dashboard`.

## R33. Do not generalize a rule from a single correction

Editorial review corrects sentence by sentence, and two passes can legitimately reach opposite conclusions on the same kind of element. A path in prose was moved from italics to backticks in one review and from backticks to italics in the next.

Before turning a correction into a rule: `grep` the term or the marker across the rest of the tree (decalogue principle 9) and follow the majority usage. If the correction contradicts what the tree already does, it is a candidate for a question, not for a new rule.

## R34. What the documentation review of PR #409 settled

Four conventions the documentation team applied over this repository, on top of the rules above:

- **`_Stratio PostgreSQL Operator_`**, not `_Stratio PostgreSQL_`, is the name of the service. `intelligence` uses it 88 times and `genai` 8; the short form survives in older pages and `virtualizer` still says `_Stratio PostgreSQL Framework_`, which does not exist.
- **`startup`, not `start-up`**, in the English tree: rocket-core 87/2, `intelligence` 86/3, `genai` 52/1.
- **HTTP codes are named, not quoted**: `responde con código 503` / `responds with code 503`, not `responde "503"`. Double quotes stay for the strings the system emits, such as `"Downgrade detected"`.
- **`Mandatory`, not `Required`**, in English table headers, and the legend of a column has to describe what the column actually holds: the `Obligatorio` one reads Sí/No, so its legend says `Sí o No`, not `_true_ o _false_`.

⚠️ **Never fix the spelling of a quoted string in the pages; fix it at the source.** Three strings were misspelled where they are defined, and the documentation quoted them as they were, because a reader who searches _Stratio Command Center_ or the logs for a corrected version does not find it: `Extra JARs to add to metabase plugins folder` (lowercase `metabase`), `oauth2proxy cooke name` (the `title` of `JWT_COOKIE_NAME`) and `Cannot use Hive JDBC in Stratio Metabase since it has been remove due to vulnerabilities.`. The documentation review of PR #409 was right that carrying the error forward perpetuates it, so PR #411 corrected all three at the source — the descriptor and `fixed_hive_connection.clj` — and the pages now quote the corrected form. The order is what matters: **fix the source first, then the pages**, and while the two disagree, the pages follow what the operator actually reads.

One consequence to keep in mind: the corrected labels ship with 0.56.4-0.2.1, and an operator still on 0.2.0 sees the old ones until the descriptor is synced into `Stratio/kubernetes-universe`. The same typo also lives in the descriptors of Universes 14.8, 14.9 and 14.10, so the fix has to be ported to `branch-0.50.21-0.2`.
