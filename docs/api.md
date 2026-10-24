# API reference

All endpoints need `Authorization: Bearer <Keycloak access token>` unless marked public.
Errors are RFC 9457 problem details (`application/problem+json`).

| Endpoint | Role |
|---|---|
| `GET /api/categories`, `GET /actuator/health` | public |
| `GET /api/search`, `GET /api/search/modes`, `POST /api/ask`, MCP `/sse` | `kb-reader` |
| `POST /api/ingest/file`, `POST /api/ingest/text`, MCP `ingest_document` | `kb-editor` |
| `/api/admin/categories/**`, `POST /api/admin/evaluation` | `kb-admin` |

Roles include the ones below them (`kb-admin` ⊃ `kb-editor` ⊃ `kb-reader`).

---

## `GET /api/search`

| Parameter | Type | Default | Notes |
|---|---|---|---|
| `q` | string | required | ≤ `retrieval.max-query-chars` (2000) |
| `categories` | string, repeatable | all | unknown names → 400 |
| `scope` | `ANY` \| `PRIMARY` | `ANY` | `ANY`: tagged with any of them; `PRIMARY`: primary category is one of them |
| `topK` | int | 5 | 1..20 |
| `minScore` | 0..1 | 0.25 | **cosine floor for the semantic leg only** |
| `mode` | `semantic` \| `keyword` \| `hybrid` | `retrieval.default-mode` | |
| `queryTransform` | `none` \| `rewrite` \| `multi_query` \| `hyde` | `retrieval.default-query-transform` | |
| `rerank` | `none` \| `llm` \| `cohere` \| `cross_encoder` | `retrieval.default-rerank` | `cohere` / `cross_encoder` only when configured |
| `diversify` | `none` \| `mmr` | `retrieval.default-diversify` | |
| `candidates` | int | `topK`, or 30 when fusing/reranking/MMR | topK..100 |
| `mmrLambda` | 0..1 | 0.7 | 1 = pure relevance, 0 = pure diversity |
| `explain` | bool | false | adds `explain` and per-hit `trace` |

Option values are case-insensitive; `-` and `_` are interchangeable (`multi-query` = `MULTI_QUERY`).

**Response**

```json
{
  "query": "ERR-4012 gateway timed out",
  "hits": [
    {
      "chunkId": "…", "documentId": "…", "source": "payments-runbook.md", "title": "Error codes",
      "page": null, "categories": ["engineering"], "primaryCategory": "engineering", "chunkIndex": 0,
      "score": 0.0325, "scoreType": "RRF", "text": "ERR-4012 means …",
      "trace": [
        {"stage": "semantic[0]", "rank": 1, "score": 0.61, "scoreType": "COSINE"},
        {"stage": "keyword[0]",  "rank": 1, "score": 0.37, "scoreType": "FTS_RANK"},
        {"stage": "rrf",         "rank": 1, "score": 0.0328, "scoreType": "RRF"}
      ]
    }
  ],
  "applied": {
    "mode": "HYBRID", "queryTransform": "NONE", "rerank": "NONE", "diversify": "NONE",
    "topK": 5, "candidates": 30, "keywordEngine": "POSTGRES_FTS", "minScore": 0.25, "keywordMinScore": 0.0,
    "rrfK": 60, "mmrLambda": null, "llmCalls": 0, "scoreType": "RRF"
  },
  "warnings": [],
  "explain": {
    "originalQuery": "ERR-4012 gateway timed out",
    "semanticQueries": ["ERR-4012 gateway timed out"],
    "keywordQueries": ["ERR-4012 gateway timed out"],
    "stages": [
      {"name": "semantic[0]", "candidates": 12, "durationMs": 212, "detail": "query: …"},
      {"name": "keyword[0]",  "candidates": 3,  "durationMs": 6,   "detail": "query: …"},
      {"name": "rrf",         "candidates": 14, "durationMs": 0,   "detail": "2 lists, k=60"}
    ]
  }
}
```

- `applied` is what actually ran. If an optional stage (transform, rerank) fails or times out, the search still
  succeeds, the stage shows as `NONE` in `applied`, and `warnings` says why.
- `scoreType` tells you what `score` means. **Scores of different types are not comparable**:
  `COSINE` 0..1, `FTS_RANK` 0..1 (Postgres rank normalized), `BM25` unbounded, `RRF` ≈ 0.01–0.05, `RERANK` 0..1.
- `explain` / `trace` appear only with `explain=true`.

**Errors**

| Status | When |
|---|---|
| 400 `Invalid retrieval option` | unknown value (body lists `validValues`), reranker not configured (lists available ones), out-of-range `topK` / `candidates` / `mmrLambda` / `minScore`, or the combination needs more than `retrieval.max-llm-calls-per-request` LLM calls |
| 400 `Invalid categories` | unknown category name |
| 503 `Search unavailable` | a retrieval leg (database or embedding model) failed; details are logged, never returned |

## `GET /api/search/modes`

Every option with `enabled`, `unavailableReason` (e.g. `"COHERE_API_KEY is not set"`), description and cost note,
plus `defaults`, `limits` and the active `keywordEngine`.

## `POST /api/ask`

```json
{
  "question": "What does ERR-4012 mean?",
  "categories": ["engineering"], "scope": "ANY", "topK": 5,
  "mode": "hybrid", "queryTransform": "none", "rerank": "llm", "diversify": "none",
  "candidates": 20, "mmrLambda": 0.7, "explain": false
}
```

Response: `question`, `answer`, `grounded`, `citations[]` (only sources the answer cites), `retrieval`
(same as `applied` above), `warnings`, and `explain` when requested.
If retrieval finds nothing above each leg's own relevance floor, the answer is
"I couldn't find anything relevant…" and the chat model is not called.

## `POST /api/admin/evaluation` (kb-admin)

```json
{
  "seedCorpus": true,
  "k": 5,
  "configurations": ["semantic", "keyword", "hybrid", "hybrid+rerank:llm", "hybrid+rerank:llm+mmr", "semantic+hyde"],
  "details": false
}
```

All fields optional. Configuration spec: `<mode>[+<transform>][+rerank:<type>][+mmr]`; the **first one is the baseline**
and every other result carries `delta` (this − baseline) for hit rate, recall, MRR and p50 latency.
Default configurations: every technique alone plus key combinations, baseline `semantic`; Cohere / cross-encoder
configurations are added automatically when configured. Also: `goldenSet` (inline, same shape as
`src/main/resources/eval/golden-set.json`).

---

## MCP tools

Connect to `/sse` with `Authorization: Bearer <token>` (role `kb-reader`).

| Tool | Role | Parameters |
|---|---|---|
| `search_knowledge_base` | kb-reader | `query`, `categories[]`, `primaryOnly`, `topK`, `mode` (SEMANTIC\|KEYWORD\|HYBRID), `queryTransform` (NONE\|REWRITE\|MULTI_QUERY\|HYDE), `rerank` (NONE\|LLM\|COHERE\|CROSS_ENCODER), `diversify` (NONE\|MMR), `candidates`, `explain` |
| `ask_knowledge_base` | kb-reader | `question`, `categories[]`, `mode`, `queryTransform`, `rerank`, `diversify` |
| `list_retrieval_options` | kb-reader | none; same content as `GET /api/search/modes` |
| `list_categories` | kb-reader | none |
| `ingest_document` | kb-editor | `name`, `text` or `base64`, `mimeType`, `categories[]` |

Option parameters are JSON-schema enums, so the calling model sees the allowed values. Invalid or unavailable
choices return a tool error (`isError: true`) naming the valid values; the session stays usable.
