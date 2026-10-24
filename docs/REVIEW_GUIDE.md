# Review guide

A suggested route through the code, a checklist of what to verify, and an honest list of what is most likely to
be wrong. Path shorthand: `J/` = `src/main/java/com/shk/generic/`, `T/` = `src/test/java/com/shk/generic/`,
`R/` = `src/main/resources/`. Background is in [ARCHITECTURE.md](ARCHITECTURE.md) and
[USERS_AND_SECURITY.md](USERS_AND_SECURITY.md).

---

## 1. Reading order (about 15 files)

Read top to bottom. Each step builds on the previous one.

| # | File | What to look for |
|---|---|---|
| 1 | `R/application.properties` | Every setting and its default; which values come from the environment; `server.port`, the `DEBUG` logging, `max-tokens=500`. |
| 2 | `R/db/migration/V1…V5`, `R/db/migration-paradedb/R__…` | The schema: unique `content_hash`, the CHECK constraints, `status` lifecycle, `ingested_by` nullable, V5's `IF NOT EXISTS` and the generated `content_tsv`. |
| 3 | `J/security/SecurityConfiguration.java` | The URL rule order (first match wins), `anyRequest().authenticated()` as the catch-all, `RoleHierarchy`, `@EnableMethodSecurity`. |
| 4 | `J/security/KeycloakJwtAuthenticationConverter.java` | How roles and the username are read from the token; what happens with malformed claims. |
| 5 | `docker/keycloak/generic-realm.json` | Roles and composites, the `generic-cli` audience mapper, dev users, password grant on. |
| 6 | `J/ingestion/intake/IntakeGateway.java` + `J/ingestion/intake/text/JsonTextIntakeAdapter.java` | The one ingestion entry path; where the submitter is stamped. |
| 7 | `J/ingestion/pipeline/IngestionPipeline.java` | Stage order; the claim and release logic in `ingest` and `releaseQuietly`. |
| 8 | `J/ingestion/registry/JdbcDocumentRegistry.java` | `claim` (`ON CONFLICT DO NOTHING` + re-read + retry), `markIngested` guarded by `PENDING`, `release`, `findStalePending`. |
| 9 | `J/ingestion/store/DefaultStorageStage.java` + `StalePendingSweeper.java` | Compensation order; why the sweeper releases *before* deleting chunks. |
| 10 | `J/ingestion/category/classifier/ClassificationGuard.java` | How untrusted model output is constrained to registry names. |
| 11 | `J/retrieval/search/DefaultSearchService.java` + `J/retrieval/options/RetrievalPlanner.java` | Validation; every limit; the up-front LLM budget. |
| 12 | `J/retrieval/pipeline/RetrievalPipeline.java` | The whole read path in one method: the same filter for every leg, the single-list no-fusion rule, degradation, `applied`. |
| 13 | `J/retrieval/keyword/PostgresFullTextEngine.java` + `MetadataFilterSql.java` | The SQL built with `formatted(...)`: confirm only constants and converter output are interpolated, and user text is always bound. |
| 14 | `J/retrieval/answer/RagAnswerService.java` + `NumberedSourcesPromptBuilder.java` | The "no hits → no model call" guard; citation filtering; prompt rules. |
| 15 | `J/retrieval/mcp/RetrievalMcpTools.java` + `J/ingestion/intake/mcp/IngestionMcpTools.java` | `@PreAuthorize` on every tool; parameter descriptions (what the agent reads). |

Then the tests that pin the behaviour down:
- `T/security/KeycloakIntegrationTests.java` (real tokens, real MCP);
- `T/retrieval/RetrievalModesTests.java` (every mode);
- `T/ingestion/dedup/DuplicateDetectionTests.java` (concurrency);
- `T/ingestion/store/StorageTests.java` (compensation and sweeper).

---

## 2. Checklist

### Security

- [ ] **Every endpoint has an intended role.** List all mappings (`@GetMapping`, `@PostMapping`, `@PatchMapping`,
  `@DeleteMapping`) and match each against `SecurityConfiguration`. Expected outliers: `GET /api/chat` and
  `/actuator/info` fall to "any authenticated user". `/api/chat` is a confirmed gap (finding 1).
- [ ] **Review the new docs-site feature.** `J/docs/` and its `/api/docs/**` rule appeared during this review and
  are not covered here. Check what it serves: it can expose these documents, which include dev credentials and the
  security design.
- [ ] **Every MCP tool has a role.** `@PreAuthorize` on `search_knowledge_base`, `ask_knowledge_base`,
  `list_retrieval_options` (reader) and `ingest_document` (editor). `list_categories` has none, by design: it is
  public over REST, and the connection already needs reader.
- [ ] **The method-security proxy is really in place for MCP tools.** Run `KeycloakIntegrationTests` (a reader's
  `ingest_document` must fail) after any Spring AI upgrade.
- [ ] **The audience is enforced.** `spring.security.oauth2.resourceserver.jwt.audiences` is set;
  `KeycloakIntegrationTests#invalidTokensAreRejected` covers the wrong-audience case.
- [ ] **No secrets in the repo.** `git grep -nIE "sk-or-v1-|sk-[A-Za-z0-9_-]{30,}|BEGIN .*PRIVATE"` returns
  nothing, and only the documented dev credentials exist (`compose.yaml`, realm file, `application.properties`).
- [ ] **No secrets in responses.** 503 handlers return generic text; the reranker error tests pass.
- [ ] **No secrets in logs.** Check `logging.level.org.springframework.ai=DEBUG`. Prompts are logged; decide
  whether that is acceptable, and confirm keys aren't (**not verified**).
- [ ] **401 and 403 are problem-details JSON with the `WWW-Authenticate` challenge** (`SecurityTests`).
- [ ] **The `.well-known/oauth-protected-resource` content is acceptable.** It lacks `authorization_servers` and
  claims mTLS-bound tokens.

### Retrieval

- [ ] **The metadata filter reaches every leg.** In `RetrievalPipeline#run`, the same `filter` is passed to
  `semantic.search` and `keywordEngine.search`, and both engines use `MetadataFilterSql.andClause`. Test:
  `RetrievalModesTests#everyLegAppliesTheSameCategoryFilter`, and `ParadeDbBm25Tests` for BM25.
- [ ] **The relevance cutoff is mode-aware.** Thresholds live only inside legs, and nothing compares RRF or
  rerank scores to `minScore`. Then decide whether `retrieval.keyword.min-score=0.0` is acceptable (finding 2).
- [ ] **Single-list results keep their native scores.** `RetrievalModesTests#defaultIsUnchangedPlainSemanticSearch`
  asserts COSINE, `candidates == topK` and no `rrfK`.
- [ ] **Every limit is enforced before work starts:** topK, candidates, LLM calls (`RetrievalPlannerTest`).
- [ ] **Degraded stages are visible:** `applied` shows NONE and `warnings` gives the reason
  (`RetrievalModesTests#failingOptionalStagesDegradeWithWarningsInsteadOfFailing`).
- [ ] **Keyword input can't inject syntax** (`RetrievalModesTests#keywordInputIsNeverParsedAsSyntax`,
  `ParadeDbBm25Tests#userInputIsNeverParsedAsQuerySyntax`).

### Data

- [ ] **Migrations run on a fresh database:** any Testcontainers test, for example `GenericApplicationTests`.
- [ ] **Migrations run on an existing database.** The dev database went from v4 to v5 (startup log line
  "Successfully applied 1 migration … v5").
- [ ] **The baseline is safe.** On a database whose tables were created before Flyway, `baseline-version=0` means
  V1 runs and creates `document_registry`. That fails if the table already exists. It was fine here because only
  `vector_store` pre-existed.
- [ ] **The `INGESTED` invariant:** the CHECK `ck_document_registry_ingested_complete` plus `markIngested`'s
  `WHERE status='PENDING'`.
- [ ] **The duplicate key is the normalized text,** not bytes or filename (`Sha256ContentHasher`).
- [ ] **The embedding dimension is consistent** across `spring.ai.vectorstore.pgvector.dimensions`, the V5
  placeholder and the embedding model (1536 for text-embedding-3-small).

### Operations

- [ ] **Ports:** `server.port=9000` collides with Docker on the dev machine; the frontend expects 8091.
- [ ] **Docker Compose:** Spring Boot skips `docker compose up` if any service runs; Keycloak re-imports the realm
  only on a fresh container.
- [ ] **Memory:** the full test suite plus IDE plus Docker exhausted commit memory once (JVM crash). Use
  `-DargLine=-Xmx1g` on small machines.
- [ ] **Thread pool:** `retrievalExecutor` has 8–32 threads and a queue of 200; overload returns 503.
- [ ] **The sweeper's `stale-after` (15m)** must exceed the slowest real ingestion, or slow ingestions are cancelled.
- [ ] **`max-tokens=500`** may truncate answers.
- [ ] **No rate limiting.**

---

## 3. Things most likely to be wrong or surprising

Ordered by my assessment of risk.

**1. `/api/chat` is an open LLM proxy for every authenticated user.**
- *Code:* `J/demo/ChatController.java` with `SecurityConfiguration` `anyRequest().authenticated()`.
- *Verified:* a user with no `kb-*` role received a model reply.
- *Impact:* cost and abuse, plus every prompt is logged at DEBUG.
- *Fix:* remove the demo controller, or require a role. **Highest priority.**

**2. The keyword and hybrid "nothing relevant" guard is weak.**
- *Cause:* plain-text keyword queries use OR semantics (`PostgresFullTextEngine.ANY_TERM`) and the default floor is
  `retrieval.keyword.min-score=0.0`, so one shared stemmed word makes a chunk count as relevant.
- *Effect:* in KEYWORD or HYBRID mode, `/api/ask` will usually call the model even for unanswerable questions, and
  then relies on the prompt's "say you don't know".
- *Status:* not measured. SEMANTIC mode (the default) is unaffected.

**3. MCP authorization depends on Spring AI internals.**
- *What it relies on:* `@PreAuthorize` on tool methods only works because Spring AI runs sync tools on the request
  thread (`immediateExecution(true)` on the servlet stack) and registers the *proxied* bean.
- *Why that is acceptable today:* it is tested end to end, and if it breaks it fails closed (no security context
  means an exception, not access).
- *Action:* re-run `KeycloakIntegrationTests` on every Spring AI upgrade.

**4. Prompt injection through documents.**
- *Exposure:* document text is placed into prompts for classification, LLM reranking and answering.
- *Mitigations:* "treat as data" instructions in every system prompt, and `ClassificationGuard` restricting
  categories to registry names.
- *What remains:*
  - a document can try to steer the **LLM reranker** ("score this passage 10") and promote itself;
  - it can steer the **answer** text.
- *Tests:* none for adversarial documents. One manual check during development: a classification injection
  attempt was ignored.

**5. SQL built with string formatting.**
- *Where:* `PostgresFullTextEngine` and `ParadeDbBm25Engine` interpolate (a) a constant tsquery expression and (b)
  `PgVectorFilterExpressionConverter` output into SQL.
- *Why it is currently safe:* user text is always a bound parameter (`:query`), and category values are both
  validated by `CategoryNames` and escaped by the converter.
- *Risk:* the safety relies on Spring AI's escaping (its Javadoc says values are JSON-serialized and quote-escaped)
  and on nobody adding a contributor that interpolates raw strings.
- *Review:* `MetadataFilterSql` and any new `SearchFilterContributor`.

**6. Chunks are searchable before their document is `INGESTED`.**
- *Why:* `DefaultStorageStage` writes chunks and then marks the row; search never checks `document_registry.status`.
- *Effect:* during that window, or if compensation fails (`deleteChunksQuietly` only logs), chunks of a
  non-ingested document can appear in results.
- *Mitigation:* the sweeper eventually removes them for crashed runs, but not for a failed delete in a live run
  whose claim was released (**unverified** whether any path leaves orphans permanently).

**7. The sweeper can cancel slow but healthy ingestions.**
- *Why:* anything `PENDING` for longer than `ingestion.store.stale-after` (15m) is released.
- *Effect:* a huge PDF with slow embedding calls could exceed that. The ingestion then fails its `markIngested`
  and cleans up correctly, but the upload fails.

**8. The `websearch` trigger is a heuristic.**
- *What it does:* `PostgresFullTextEngine.SEARCH_SYNTAX` switches to `websearch_to_tsquery` (AND semantics) when the
  text contains a quote, a `-word`, or " or ".
- *Effect:* a natural question containing the word "or" ("leave or vacation?") silently becomes an AND/OR
  expression with different recall.

**9. The evaluation is not yet evidence.**
- The sample golden set is saturated: hit@5 = 1.0 for every configuration measured.
- The live run is incomplete: 3 transform configurations are missing.
- All integration tests use a bag-of-words stub embedder (`T/StubEmbeddingModel.java`), so semantic *quality* is
  never tested automatically.

**10. Concurrency edges in category admin.**
- *Delete:* `DefaultCategoryAdminService#delete` is check-then-act (count usage, then delete), so a concurrent
  ingestion could tag a document with a category being deleted.
- *Cache:* invalidation is per instance; other instances see changes within `category.registry.cache-ttl` (60s).

**11. Silent truncation and caps.**
- `spring.ai.openai.chat.options.max-tokens=500` applies to answers.
- `retrieval.max-context-chars=12000` drops trailing sources.
- Multi-query variants are capped at `retrieval.transform.max-multi-query-variants`.

None of these limits is reported back to the caller. `explain` shows the searched queries but not which sources
were dropped from the answer prompt.

**12. Small inconsistencies in comments and config.**
- `VectorStoreChunkStore` Javadoc says "JSONB metadata", but the column is `json`.
- The `Roles` Javadoc says each role "includes the ones above it"; it is the reverse.
- `StoreProperties.sweepInterval` is unused.
- `server.port=9000` doesn't match how the app is actually run (8091).

**13. Dual Jackson versions.** Jackson 2.21.5 and 3.1.5 are both on the classpath. The code uses Jackson 3 types
with Jackson 2 annotation packages, which is the intended Jackson 3 setup. A wrong import of
`com.fasterxml.jackson.databind` would compile but use a different `ObjectMapper` configuration.
