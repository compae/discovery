# Translation glossary: Spanish → English

Source: "Tesauro documentación español-inglés", Stratio, November 2021.

---

## Words that are NOT translated

These words stay in English in the Spanish documentation.
**They must appear in italics** unless they are initialisms or acronyms (API, CNAME, KPI…).
Applies to both singular and plural.

> **This rule is about the SPANISH page only.** In the English page most of these words are ordinary English and take no italics — see R13 and the per-language split in the appendix. The rule that survives in both languages is the one for product names and object types.

> **Scope (do not filter).** Like `term-glossary.md`, this is the documentation team's ecosystem-wide doctrine, so it lists terms that no _Stratio Discovery_ page uses today (`_executor_`, `_keytab_`, `_pushdown_`, `Grafo de conocimiento`, `Reglas de calidad`…). Keep them: they may legitimately appear in a cross-reference or in a new page. The Discovery-specific additions are in the appendix.

**Acronym plurals differ by language.** In Spanish, acronyms are *invariable* in the plural — never add a final `s`; the plural is carried by the article or context: `las CA`, `los LLM`, `las API`, `los ID`, `las GPU` (not `las CAs` / `los LLMs`). In English they *do* pluralise with `s`: `CAs`, `LLMs`, `APIs`, `IDs`, `GPUs`. This is one of the documentation team's most frequent corrections (e.g. `CAs` → `CA` in ES).

| Term | Note |
|------|------|
| _array_ | |
| _asset_ | generic context |
| _backend_ | |
| _backup_ | |
| _batch_ | |
| _bundle_ | |
| _chart_ | |
| _cluster_ | |
| common name / CNAME | initialism: no italics |
| _cookie_ | |
| _dashboard_ | |
| _DataFrame_ | |
| Data Market | |
| _driver_ / _drivers_ | Metabase and JDBC context; the most frequent term in these docs |
| _edge_ | |
| _endpoint_ | |
| _entrypoint_ | |
| _executor_ / _executors_ | |
| _exporter_ | |
| _foreign key_ | |
| _framework_ | |
| _frontend_ | |
| _hardening_ | |
| _hash_ / _hashing_ | |
| _health check_ | |
| _host_ | |
| _job_ | |
| _cookie_ / _cookies_ | authentication context |
| _keytab_ | |
| KPI | initialism: no italics |
| _log_ / _logs_ / _logging_ | |
| _machine learning_ | |
| _mock_ | |
| _multitenancy_ | |
| _multi-tenant_ | |
| _namespace_ | |
| _whitelist_ | group authorization context |
| _owner_ | |
| _pipeline_ | |
| _placeholder_ | |
| _placement_ / _placements_ | _Stratio Command Center_ deployment context |
| _playbook_ | |
| _plugin_ | |
| _pod_ | |
| _pool_ | |
| _primary key_ | |
| _proxy_ | |
| _pushdown_ | native queries |
| _trigger_ / _triggers_ | database context |
| _restore_ | |
| _schema_ | |
| _scrape_ | |
| _snapshot_ | |
| _snippet_ / _snippets_ | SQL snippets of Metabase |
| _stateless_ | |
| _sticky session_ | |
| _stream_ | |
| _tenant_ | |
| _timestamp_ | |
| _token_ | |
| _timeout_ / _timeouts_ | |
| _troubleshooting_ | |
| _virtual host_ | |
| _widget_ | |
| _workflow_ | |
| _workspace_ | |

---

## Words that ARE translated

When the term appears in Spanish, use the indicated English equivalent.

| Spanish | English |
|---------|---------|
| Activos de negocio | Business assets |
| Actualizar versión | Upgrade |
| Afinar | Fine-tune |
| Almacén de datos | Data store |
| Alta disponibilidad | High availability |
| Anotaciones | Annotations |
| Arquitectura | Bitness (in a specific context) |
| Asignación dinámica | Dynamic allocation |
| Atributo | Attribute |
| Autenticación multifactor | Multifactor authentication / MFA |
| Bajar de versión | Downgrade |
| Balanceador de carga | Load Balancer |
| Barra lateral | Sidebar |
| Base de datos | DB/database |
| Cadena de caracteres | String |
| Casilla | Checkbox (depending on context) |
| Código abierto | Open source |
| Cola | Queue |
| Conjunto de datos | Data set |
| Consulta | Query |
| Cumplimiento | Compliance |
| Depuración | Debug |
| Eliminar | Delete |
| Encriptación | Encryption |
| Enmascaramiento de datos | Data masking |
| Enrutamiento de puerto | Port forwarding |
| Entrada | Input |
| Envoltorio | Wrapper |
| Etiquetas | Labels |
| (de) extremo a extremo | End-to-end |
| Fuente de datos | Data source |
| Gestor de secretos | Secrets engine |
| Glosario de negocio | Business glossary |
| Gobierno del dato / Gobierno de datos | Data governance |
| Grafo de conocimiento | Knowledge graph |
| Grano fino | Fine-grained |
| Guía de inicio rápido | Quick start guide |
| Instanciar | Instantiate |
| Interfaz de la línea de comandos | CLI |
| Interfaz de usuario | UI |
| Interfaz gráfica de usuario | GUI |
| Lanzamiento | Release |
| Lista de acceso | ACL |
| Lista de admitidos | Whitelist |
| Máquina virtual | VM/virtual machine |
| Máquina virtual Java | JVM/Java virtual machine |
| Mecanismo de asignación de recursos | Leasing/lease |
| Mensajes de alerta | Warnings |
| Metadatado adicional | Addition of metadata |
| Objeto hijo | Child object |
| Personalizado/a | Custom |
| Petición | Request |
| Políticas de atributos | Tag policies |
| Problemas conocidos | Known issues |
| Proveedor de identidad | Identity provider |
| Proveedor de servicios (en la nube) | Cloud provider |
| Quitar | Remove |
| Recurso compartido | Share |
| Reenvío / redirección | Forwarding |
| Registrado/autenticado (context-dependent) | Logged |
| Reglas de calidad | Quality rules |
| Reglas de negocio | Business rules |
| Rendimiento | Performance |
| Ruta | Path |
| Sistema autónomo | Autonomous System (AS) |
| Sistema de archivos | File system |
| Sumidero de datos | Data sink |
| Suplantación | Impersonation |
| Textualmente | Verbatim |
| Tiempo de caída | Downtime |
| Tiempo de ejecución | Runtime |
| Tiempo de espera | Timeout |
| Tipos de activos de negocio | Business asset types |
| TLS mutuo | Mutual TLS |
| Tolerancia a fallos | Fault tolerance |
| Tolerancia a fallos automática | Automatic failover |
| Tras bambalinas | Under the hood |
| Unidades | Units |
| Ventana emergente | Pop-up |
| Visualización | Dashboarding |
| Volumen persistente | PV |

---

# Appendix: Stratio Discovery additions and nuances

Verified in the `stratio-docs/` tree of `discovery-cicd`. Extends the tables above; does not replace them.

## Additional words that are NOT translated

These appear in italics in ES. Per R13, whether they stay italic in EN depends on their nature.

### Generic infrastructure / Metabase jargon — italic in ES, **NOT** italic in EN

| Term | ES italic → EN roman | Note |
|------|----------------------|------|
| _driver_ / _drivers_ | driver / drivers | the most frequent term in these docs: Metabase drivers, the JDBC driver, the _Stratio {modulename}_ driver |
| _dashboard_ / _dashboards_ | dashboard / dashboards | Metabase object; one of the three that are NOT translated in ES |
| _cookie_ / _cookies_ | cookie / cookies | `stratio-cookie`, `metabase.SESSION` |
| _token_ / _tokens_ | token / tokens | JWT context |
| _whitelist_ | whitelist | group authorization |
| _timeout_ / _timeouts_ | timeout / timeouts | |
| _plugin_ / _plugins_ | plugin / plugins | the Metabase plugins folder |
| _snippet_ / _snippets_ | snippet / snippets | SQL snippets |
| _trigger_ / _triggers_ | trigger / triggers | the two Stratio database triggers |
| _placement_ / _placements_ | placement / placements | _Stratio Command Center_ deployment |
| _troubleshooting_ | troubleshooting | also the title of a page |
| _streaming_ | streaming | Metabase streaming responses |
| _login_ | login | |
| _backend_ | backend | |
| _pool_ | pool | JDBC connection pool |
| _x-ray_ | x-ray | Metabase automatic exploration |

### Metabase object names — half translated in ES, half not

| EN | ES | Note |
|----|----|------|
| collection | colección | translated; no italics in ES |
| question | pregunta | translated; no italics in ES |
| subscription | suscripción | translated; no italics in ES |
| pulse | pulso | translated; no italics in ES |
| model | modelo | translated; no italics in ES |
| dashboard | _dashboard_ | kept in English, italics in ES, roman in EN |
| snippet | _snippet_ | kept in English, italics in ES, roman in EN |
| x-ray | _x-ray_ | kept in English, italics in ES, roman in EN |

### Italic in **BOTH** ES and EN

| Term | Note |
|------|------|
| every `_Stratio X_` | product names |
| file names and paths | _log4j2.xml_, _/etc/pki/discovery.pem_ |

### Neither italics nor translation

`discovery-cicd` is a component id → backticks. `gosec-sso`, `headers`, `jwt`, `metabase-native` are values of `AUTHENTICATOR` → backticks. `'Gosec SSO Authenticator Settings'`, `_Use virtualizer jdbc from Extra JARs_` and every other _Stratio Command Center_ label stay in English in both trees, marked per R1.

## Additional ES→EN translations

| Spanish | English |
|---------|---------|
| Actualización de versión (de _Stratio Discovery_) | Version upgrade |
| Actualización (de un _driver_, del JDBC, de la imagen) | Update |
| Cambios incompatibles | Breaking changes |
| Colección (de preguntas y _dashboards_) | Collection |
| Pulso | Pulse |
| Modelo | Model |
| Comprobar la instalación | Verify installation |
| Conceptos importantes | Key concepts |
| Conectar con otros módulos | Connect with other modules |
| Exportación e importación de colecciones | Export and import of collections |
| Grupo de administración | Admin group |
| Grupos permitidos | Allowed groups |
| Notas de _release_ | Release notes |
| Novedades | What's new |
| Almacén de metadatos | Metadata store |
| Pregunta (objeto de Metabase) | Question |
| Problemas conocidos | Known issues |
| Suscripción (a un _dashboard_) | Subscription |
| Universo | Universe |

## Canonical lexical preferences (word choice, not translation)

The docs team unifies vocabulary; it even corrects terms that were already in English. The ones this repo breaks most often:

| Avoid | Use | Language |
|-------|-----|----------|
| `soporte de X` / `X está soportado` | `compatibilidad con X` / `X es compatible` | ES |
| `support` (as a capability, noun) | `compatibility` | EN |
| `obsolete` | `deprecated` | EN |
| `deprecado` / `deprecar` | `obsoleto` / `en desuso` | ES |
| `A partir de la versión X` | `Desde la versión X` | ES |
| `Starting from` | `As of` | EN |
| `panel` (for a Metabase dashboard) | `_dashboard_` / `dashboard` | ES + EN |
| `consulta guardada` (for a Metabase question) | `pregunta` / `question` | ES + EN |
| `múltiples` | `varios` / `diversos` | ES only |
| `librería` (software) | `biblioteca` | ES only |
| `arrancar` (a service or a project, as a verb) | `iniciar` / `iniciarse` (the noun `arranque` and the infinitive `al arrancar` stay; the EN equivalent does not change) | ES only |
| `tiene que` (obligation) | `debe` | ES only |
| `en local` / `de forma local` | `localmente` | ES only |
| `a la vez` | `simultáneamente` | ES only |
| `de forma opcional` | `opcionalmente` | ES only |

> **Where the ES rows from `múltiples` down come from.** The documentation team applied them across its review of `genai-core` (PR #854, 86 files): it removed `arranc-` 17 times, `soportado` 11, `múltiples` 10, `librería` 7 and `en local` 3. They are Spanish word choices, not concept renames, so they hold in every module. Apply them in prose, never inside a quoted label or a literal.

> **Mirroring is not back-translating.** When the ES is revised, update the EN sentence by sentence against its own sources; do not re-translate the EN from the new ES. A back-translated EN inherits every ES slip (in `genai-core`, an ES "muy baja frecuencia" turned a correct EN "very high-frequency" into "very low-frequency"), brings false friends ("a medida" → "for measurement", "fijar" → "fix", "puntual" → "punctual") and translates UI labels that must be copied from the UI strings of the target language.

> **`deprecated` vs `obsoleto` (per language):** EN uses `deprecated` (not `obsolete`); ES uses `obsoleto` / `en desuso`, **never** `deprecado` / `deprecar`. The two languages diverge here on purpose.

> **ES noun + attributive English noun:** in Spanish, an English product or technology used attributively takes `de`: `el _driver_ **de** Metabase` (not `driver Metabase`), `los servidores **de** _Stratio {modulename}_` (not `servidores Stratio Virtualizer`), `la carpeta **de** _plugins_`. This repo breaks it repeatedly.

---

## Fixed ES→EN equivalents enforced in editorial review

The EN page mirrors the ES one (decalogue R29), so these pairs are fixed even when a more idiomatic English turn exists:

| Spanish | English | Not |
|---------|---------|-----|
| arrancar (un servicio, un contenedor) | start up | start |
| cerrar la sesión | close the session | log out |
| retirada (de una funcionalidad) | retirement | removal |
| eliminación | removal | |
| ventana de intervención | maintenance window | intervention window |
| parada, tiempo de parada | downtime | outage |
| interfaz de administración | administration interface | admin interface |
| actualización (de versión del producto) | upgrade | update |
| actualización (de una imagen, un _driver_ o una dependencia) | update | upgrade |
| sincronizar (el catálogo, los grupos) | synchronize | sync (in prose; `sync` is fine in a log excerpt) |
| a medida | custom | for measurement |
| trazas, nivel de trazas (de un servicio) | logs, log level | traces, trace level |
| confirmar (una migración o una transacción de base de datos) | commit | confirm |

**`support` as a capability**: the ES noun is `compatibilidad con`; in EN the noun is `compatibility with` but the verb `does not support` is accepted and preferred. This overrides the blanket entry in the canonical-preferences table above.

⚠️ **Do not import a lexical rule from another module's copy of this skill.** Five Stratio repos carry a `write-stratio-docs` skill, and their glossaries have diverged because each one owns different concepts. Two guards before you copy a row across:

- **A row that renames a concept belongs to the module that owns the concept.** `rocket-core` had to rename its workflow nodes across 53 English files; that row is Rocket's, and it does not apply here, because this repo documents no _Stratio Rocket_ workflow at all (a case-insensitive grep for `rocket` over both trees returns 0 at the baseline). Adding it here would only invite someone to "fix" a word this product does not use.
- **A row that renames a common word can break a literal.** `probe` is the example to keep in mind: it is the `'Probes'` accordion of the descriptor (R28 — copy a `title` verbatim) and it is also Kubernetes' own liveness/readiness/startup probe, so a blanket ES→EN substitution on it would corrupt both. If a term is simultaneously a UI label, a platform concept and a common noun, the entry needs an explicit scope note or it does not belong in the table.
