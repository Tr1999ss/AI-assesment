# Users and security

How authentication and authorization work, how to create users, how to get and use tokens, and what must change
before real use. Path shorthand as in [ARCHITECTURE.md](ARCHITECTURE.md): `J/` = `src/main/java/com/shk/generic/`,
`T/` = `src/test/java/com/shk/generic/`, `R/` = `src/main/resources/`.

> **Dev-only credentials in this document.** Keycloak admin `admin`/`admin` (`compose.yaml`), the realm users
> `alice`, `bob`, `carol` and `dave` whose password equals their username (`docker/keycloak/generic-realm.json`),
> and the database password `secret` (`compose.yaml`, `R/application.properties`). They exist only for local
> development. Never reuse them anywhere else.

---

## 1. The model

- **Keycloak** is the identity provider. It stores users, passwords and roles, and issues signed JWT access
  tokens. In development it runs from `compose.yaml` (`quay.io/keycloak/keycloak:26.4`, port 8180) and imports
  the `generic` realm from `docker/keycloak/generic-realm.json`.
- **The app is an OAuth2 resource server.** It never sees passwords. Every request must carry
  `Authorization: Bearer <token>`. Spring Security then checks:
  - **signature:** against Keycloak's public keys (JWKS), discovered from the issuer URI;
  - **issuer (`iss`):** must equal `spring.security.oauth2.resourceserver.jwt.issuer-uri`
    (default `http://localhost:8180/realms/generic`, env `KEYCLOAK_ISSUER_URI`);
  - **audience (`aud`):** must contain `generic-api` (`spring.security.oauth2.resourceserver.jwt.audiences`,
    env `KEYCLOAK_AUDIENCE`);
  - **expiry:** the realm's `accessTokenLifespan` is 300 seconds, so tokens last 5 minutes.
- **Roles come from the token.** `J/security/KeycloakJwtAuthenticationConverter.java` reads
  `realm_access.roles` (where Keycloak puts realm roles) and turns each into a Spring authority `ROLE_<role>`.
  The principal name is `preferred_username`; this is what ends up in `document_registry.ingested_by`.
- **Sessions:** none. `SessionCreationPolicy.STATELESS`. CSRF protection is disabled because browsers don't attach
  bearer tokens automatically (`J/security/SecurityConfiguration.java`).

```mermaid
sequenceDiagram
    autonumber
    participant U as User / client
    participant K as Keycloak (realm generic)
    participant A as App (resource server)

    U->>K: POST /realms/generic/protocol/openid-connect/token (client generic-cli, credentials)
    K-->>U: access_token (JWT: iss, aud=generic-api, realm_access.roles, preferred_username, exp)
    U->>A: GET /api/search, Authorization: Bearer access_token
    A->>K: (first request only, then cached) fetch OIDC discovery and JWKS public keys
    A->>A: verify signature, iss, aud, exp; map realm_access.roles to ROLE_*
    A->>A: URL rule (e.g. /api/search needs ROLE_kb-reader), then @PreAuthorize for MCP tools
    A-->>U: 200, or 401 (token missing or invalid), or 403 (role missing)
```

The app fetches Keycloak's keys lazily, so it starts even while Keycloak is still booting (comment in
`R/application.properties`; Spring Boot behaviour when `issuer-uri` is set).

### Clients in the realm (`docker/keycloak/generic-realm.json`)

| Client | Type | Purpose |
|---|---|---|
| `generic-api` | confidential, every flow disabled | Represents this API. It never requests tokens; it exists so tokens can name it as their audience. |
| `generic-cli` | public | Developer tools, the frontend and MCP clients. Password grant (`directAccessGrantsEnabled`) **on for dev only**; authorization code with PKCE (S256); redirect URIs `http://localhost:*`, `http://127.0.0.1:*`, `https://oauth.pstmn.io/v1/callback`. Has an `oidc-audience-mapper` adding `generic-api` to `aud`. |

Tokens from any other client (for example Keycloak's built-in `admin-cli`) have a valid signature and issuer but
no `generic-api` audience, so the app rejects them with 401 `invalid_token`. Proven by
`T/security/KeycloakIntegrationTests#invalidTokensAreRejected`.

---

## 2. Roles

Three realm roles, each a **composite** containing the one below it: a composite role automatically grants the
roles it includes.

| Role | Includes | Can use |
|---|---|---|
| `kb-reader` | — | `GET /api/search`, `GET /api/search/modes`, `POST /api/ask`, MCP connection (`/sse`, `/mcp/**`), MCP tools `search_knowledge_base`, `ask_knowledge_base`, `list_retrieval_options`, `list_categories` |
| `kb-editor` | `kb-reader` | everything above, plus `POST /api/ingest/file`, `POST /api/ingest/text`, MCP `ingest_document` |
| `kb-admin` | `kb-editor` | everything above, plus `/api/admin/categories/**`, `POST /api/admin/evaluation` |

**Public, no token:** `GET /api/categories`, `/actuator/health` (only `{"status":...}` for anonymous callers;
`management.endpoint.health.show-details=when-authorized`), `/error`, and `/.well-known/oauth-protected-resource`
(section 7).

**Not reviewed:** a `GET /api/docs/**` rule (`kb-reader`, or public when `docs.public=true`) was added to
`SecurityConfiguration` by a concurrent change while this document was being written; see ARCHITECTURE.md §K.

**Any authenticated user, regardless of role:** everything not matched above falls to
`anyRequest().authenticated()`. That includes **`GET /api/chat`** (`J/demo/ChatController.java`), which sends
arbitrary text to the paid chat model. Verified: a user with no `kb-*` role got 403 on `/api/search` but 200 and a
model reply from `/api/chat`. This is a gap; see REVIEW_GUIDE.md, finding 1. `/actuator/info` also falls here.

**Where the rules live**

| Rule | Code |
|---|---|
| URL rules | `SecurityConfiguration#apiSecurity` |
| MCP tools | `@PreAuthorize(Roles.HAS_READER)` / `@PreAuthorize(Roles.HAS_EDITOR)` on each `@McpTool` method in `J/retrieval/mcp/RetrievalMcpTools.java` and `J/ingestion/intake/mcp/IngestionMcpTools.java`. `list_categories` has no annotation (it is public over REST too), but reaching it still requires `kb-reader` because the MCP connection itself does. |

**Two hierarchies.**
- **Keycloak** expands composites into the token: a `kb-editor` user's token lists `kb-editor` *and* `kb-reader`
  (observed in a real token, section 3).
- **The app** also declares `RoleHierarchy` (`SecurityConfiguration#roleHierarchy`: admin ⇒ editor ⇒ reader).
  So even a token listing only `kb-admin`, for example if someone edits the composites in Keycloak, still
  passes reader and editor checks. The mock-JWT tests rely on this: `T/TestTokens.java` puts only one role in
  each token.

---

## 3. Creating users

All three methods were run against the dev Keycloak with throwaway users, which were deleted afterwards.
Methods 3b and 3c are copied from those runs. The console labels in 3a were **not** clicked through and are
marked unverified.

### 3a. Pre-created dev users (realm file)

`docker/keycloak/generic-realm.json`:

| User | Password (dev only) | Realm roles | Effective rights |
|---|---|---|---|
| `alice` | `alice` | `kb-reader` | read |
| `bob` | `bob` | `kb-editor` | read, ingest |
| `carol` | `carol` | `kb-admin` | read, ingest, admin |
| `dave` | `dave` | none | authenticated, but no knowledge-base access (used to test 403s) |

**The realm file is only imported when the realm doesn't exist** (`--import-realm` in `compose.yaml`). `dave` was
added to the file after the dev Keycloak first started, and a token request for `dave` on that running instance
returned **401**. To apply realm-file changes, recreate the container. This **deletes users created by hand**:

```bash
docker compose up -d --force-recreate keycloak
```

To add a permanent dev user, add an entry to `users` in the realm file (same shape as `alice`) and recreate.
Keycloak's user profile expects `email`, `firstName` and `lastName`, which is why every entry has them.

### 3b. Keycloak admin console (UI)

1. Open http://localhost:8180 and log in as `admin` / `admin` (dev only).
2. Switch the realm (top-left drop-down) from `master` to **generic**.
3. **Users → Create new user.** Fill in Username, Email, First name and Last name; set Email verified on;
   **Create**.
4. **Credentials** tab → **Set password**. Enter it, switch **Temporary** off, and save.
5. **Role mapping** tab → **Assign role**. Filter by realm roles, tick `kb-reader`, `kb-editor` or `kb-admin`,
   and **Assign**.
6. Verify: the Role mapping tab lists the role. With "Hide inherited roles" unticked, a composite also shows the
   roles it includes.

*The button and tab names above follow the Keycloak 26 admin console as commonly documented; they were not
clicked through for this document (**unverified**).*

### 3c. Keycloak admin REST API (curl), verified

```bash
KC=http://localhost:8180
# 1. admin token from the master realm (dev-only admin/admin)
ADM=$(curl -s -d grant_type=password -d client_id=admin-cli -d username=admin -d password=admin \
  $KC/realms/master/protocol/openid-connect/token | jq -r .access_token)

# 2. create the user (HTTP 201)
curl -s -X POST $KC/admin/realms/generic/users -H "Authorization: Bearer $ADM" -H 'Content-Type: application/json' \
  -d '{"username":"erin","email":"erin@example.com","firstName":"Erin","lastName":"Example","enabled":true,"emailVerified":true}'

# 3. look up its id
USER_ID=$(curl -s "$KC/admin/realms/generic/users?username=erin&exact=true" -H "Authorization: Bearer $ADM" | jq -r '.[0].id')

# 4. set a password (HTTP 204)
curl -s -X PUT $KC/admin/realms/generic/users/$USER_ID/reset-password -H "Authorization: Bearer $ADM" \
  -H 'Content-Type: application/json' -d '{"type":"password","value":"Change-me-1","temporary":false}'

# 5. assign a realm role (HTTP 204): fetch the role representation, then post it as a list
ROLE=$(curl -s $KC/admin/realms/generic/roles/kb-editor -H "Authorization: Bearer $ADM")
curl -s -X POST $KC/admin/realms/generic/users/$USER_ID/role-mappings/realm -H "Authorization: Bearer $ADM" \
  -H 'Content-Type: application/json' -d "[$ROLE]"

# verify: effective roles, composites included
curl -s $KC/admin/realms/generic/users/$USER_ID/role-mappings/realm/composite -H "Authorization: Bearer $ADM" | jq -r '.[].name'

# remove the user when done (HTTP 204)
curl -s -X DELETE $KC/admin/realms/generic/users/$USER_ID -H "Authorization: Bearer $ADM"
```

Observed in the verification run:
- the effective roles were `kb-editor` and `kb-reader`;
- the user's token carried `"realm_access":{"roles":["kb-editor","kb-reader","default-roles-generic","offline_access","uma_authorization"]}`.

### 3d. `kcadm.sh` (Keycloak admin CLI inside the container), verified

```bash
# Git Bash on Windows rewrites /opt/... into a Windows path; disable that first (not needed on Linux or macOS):
export MSYS_NO_PATHCONV=1
K="docker exec generic-keycloak /opt/keycloak/bin/kcadm.sh"

$K config credentials --server http://localhost:8080 --realm master --user admin --password admin
$K create users -r generic -s username=frank -s enabled=true -s email=frank@example.com \
   -s firstName=Frank -s lastName=Example -s emailVerified=true
$K set-password -r generic --username frank --new-password Change-me-2
$K add-roles   -r generic --uusername frank --rolename kb-reader

# verify
$K get-roles -r generic --uusername frank --effective

# remove
ID=$($K get users -r generic -q username=frank -q exact=true --fields id | jq -r '.[0].id')
$K delete users/$ID -r generic
```

`--server http://localhost:8080` is Keycloak's port *inside* the container; from the host it is 8180. Without
`MSYS_NO_PATHCONV=1`, Git Bash fails with `exec: "C:/Program Files/Git/opt/keycloak/bin/kcadm.sh": … no such file`
(observed).

### Checking a user's roles from their token

Decode the token payload (it is base64url JSON; this is not a security check, just inspection):

```bash
python -c "import sys,json,base64; p=sys.argv[1].split('.')[1]; p+='='*(-len(p)%4); print(json.dumps(json.loads(base64.urlsafe_b64decode(p)),indent=2))" "$TOKEN"
```

Look for `aud` containing `generic-api`, `realm_access.roles`, and `preferred_username`. Observed for alice:
`"aud":"generic-api"`, `"realm_access":{"roles":["kb-reader"]}`, `"preferred_username":"alice"`.

---

## 4. Getting a token and using it

Password grant with the dev client. This is fine for local development; in production, use the browser
authorization-code flow (section 8).

```bash
TOKEN=$(curl -s -d grant_type=password -d client_id=generic-cli -d username=alice -d password=alice \
  http://localhost:8180/realms/generic/protocol/openid-connect/token | jq -r .access_token)
```

Tokens expire after 300 seconds; request a new one when you get 401 `invalid_token`.

The outputs below were observed against the app on port 8091.

### 401: no token, or an invalid token

```bash
curl -s "http://localhost:8091/api/search?q=leave"
# {"type":"about:blank","title":"Unauthorized","status":401,
#  "detail":"A valid bearer token from the identity provider is required","instance":"/api/search"}
# response header: WWW-Authenticate: Bearer resource_metadata="http://localhost:8091/.well-known/oauth-protected-resource"

curl -s -o /dev/null -w '%{http_code}\n' -H 'Authorization: Bearer not-a-jwt' "http://localhost:8091/api/search?q=leave"
# 401
```

### 403: valid token, missing role

```bash
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"x","text":"y","categories":["general"]}' http://localhost:8091/api/ingest/text
# alice is only kb-reader:
# {"type":"about:blank","title":"Forbidden","status":403,
#  "detail":"Your account does not have the role this operation requires","instance":"/api/ingest/text"}
```

Also observed: `bob` (`kb-editor`) gets 403 on `GET /api/admin/categories`. A user with no `kb-*` role gets 403 on
`/api/search`.

### 200: success

```bash
curl -sG -H "Authorization: Bearer $TOKEN" http://localhost:8091/api/search --data-urlencode "q=annual leave"   # 200

BOB=$(curl -s -d grant_type=password -d client_id=generic-cli -d username=bob -d password=bob \
  http://localhost:8180/realms/generic/protocol/openid-connect/token | jq -r .access_token)
curl -s -H "Authorization: Bearer $BOB" -H 'Content-Type: application/json' \
  -d '{"title":"note","text":"Hello knowledge base","categories":["general"]}' http://localhost:8091/api/ingest/text
# 200 {"status":"INGESTED", ...}; document_registry.ingested_by = 'bob'
```

### Through the frontend dev server

The Vite dev server at `localhost:5173` proxies `/api`, `/actuator`, `/sse`, `/mcp` and `/realms` (Keycloak)
(`C:\Project\assesmentFE\vite.config.ts`, outside this repository). Requesting a token at
`http://localhost:5173/realms/generic/protocol/openid-connect/token` and calling `/api/search` through 5173 returned
200 (observed).

---

## 5. Connecting an MCP client

The server speaks MCP over **HTTP with SSE**:
- `GET /sse` opens the event stream; the first event is `event:endpoint` with
  `data:/mcp/message?sessionId=…`;
- JSON-RPC messages are POSTed to that URL;
- `POST /mcp` (streamable HTTP) returns 404 (observed).

Both `/sse` and `/mcp/**` require `kb-reader` and the **same Bearer header on every request**.

```bash
curl -s -m 3 -o /dev/null -w '%{http_code}\n' http://localhost:8091/sse                                  # 401
curl -s -m 3 -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" http://localhost:8091/sse  # 200 (stream)
```

**Java (MCP SDK), as used in `T/security/KeycloakIntegrationTests#mcp`:**

```java
var transport = HttpClientSseClientTransport.builder("http://localhost:8091")
        .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
        .build();
McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
client.initialize();
client.callTool(new CallToolRequest("search_knowledge_base", Map.of("query", "ERR-4012", "mode", "KEYWORD")));
```

**Desktop MCP clients** (Claude Desktop, IDE agents) need a way to send a static header to an SSE server, for
example a local proxy or bridge that adds `Authorization`. The exact configuration depends on the client and was
not tested (**unverified**). Because tokens expire after 5 minutes, a static token will stop working quickly; for
longer sessions, raise the lifespan for a dedicated client or use a refreshing bridge.

**What a denied tool call looks like.** A `kb-reader` calling `ingest_document`, or an invalid option such as
`mode: "FUZZY"`, comes back as an MCP error result, and the session stays usable. A user without `kb-reader`
can't open the connection at all. All proven in `KeycloakIntegrationTests#mcpOverSseEnforcesPerToolRoles` and
`#mcpRetrievalToolsExposeEnumeratedOptionsAndEnforceRoles`.

### OAuth discovery (RFC 9728): what exists and what doesn't

MCP clients can discover *where to log in* from "OAuth 2.0 Protected Resource Metadata" (RFC 9728). Here is what
the running app does (observed, not configured by our code):

- 401 responses carry `WWW-Authenticate: Bearer resource_metadata="http://localhost:8091/.well-known/oauth-protected-resource"`.
- That URL is public and returns
  `{"resource":"http://localhost:8091","bearer_methods_supported":["header"],"tls_client_certificate_bound_access_tokens":true}`.

This comes from Spring Security 7's resource-server support. The source was not inspected (**unverified** which
class emits it).

**What's missing.** The document has **no `authorization_servers` entry**, so a client can't learn that Keycloak
issues the tokens, and automatic login does not work: clients must be given a token. It also claims
`tls_client_certificate_bound_access_tokens: true`, but this app does not use certificate-bound tokens.

**To complete it:** add the Keycloak issuer as an authorization server in that metadata, and correct the mTLS flag.
How to customise it in Spring Security 7 has not been worked out here (**unverified**).

---

## 6. What changed, and the evidence

| Change | Evidence |
|---|---|
| **The admin API key was removed.** The earlier `X-Admin-Api-Key` filter (`AdminApiKeyFilter`, `AdminSecurityProperties`, `admin.api-key`) no longer exists; admin endpoints need the `kb-admin` role. | No such classes in `J/security/` (file listing); `T/category/web/CategoryAdminTests#adminEndpointsRequireTheAdminRole` (anonymous 401, editor 403, admin 200) |
| **Per-tool `@PreAuthorize` on MCP tools** | `T/security/SecurityTests#mcpIngestToolRejectsReaders`, `#mcpIngestToolAllowsEditors`, `#mcpToolsRefuseCallsWithNoAuthenticationAtAll`; over the real SSE transport with real tokens: `T/security/KeycloakIntegrationTests#mcpOverSseEnforcesPerToolRoles` |
| **Audience validation** | `KeycloakIntegrationTests#invalidTokensAreRejected`: a token from `admin-cli` (no `generic-api` audience) gets 401 with `invalid_token` |
| **Signature and format validation** | same test: `not-a-jwt` gets 401 |
| **Role matrix on every REST area** | `SecurityTests#readerCanSearchButNotIngestOrAdminister`, `#editorCanIngestAndSearchButNotAdminister`, `#adminCanDoEverything`, `#tokenWithoutKnowledgeBaseRolesIsForbidden`; real tokens: `KeycloakIntegrationTests#realTokensAreValidatedAndRolesEnforced` |
| **`ingested_by` audit** | `SecurityTests#ingestionRecordsWhoSubmittedTheDocument`, `KeycloakIntegrationTests#ingestionIsAuditedWithTheKeycloakUsername` |
| **The options endpoint needs `kb-reader`** | `T/retrieval/RetrievalModesTests#modesEndpointListsEveryOptionAndRequiresReader`; real token: `KeycloakIntegrationTests#mcpRetrievalToolsExposeEnumeratedOptionsAndEnforceRoles` (no-role user gets 403) |
| **401/403 bodies are problem details** | `SecurityTests#anonymousCallersGet401AsProblemDetailWithBearerChallenge`, `#readerCanSearchButNotIngestOrAdminister` |

**Not covered by any test:** `/api/chat` (open to every authenticated user, as noted above).

---

## 7. Secrets

- **No real secret is committed.** Real secrets come from environment variables: `OPENROUTER_API_KEY`,
  `COHERE_API_KEY`, `CROSS_ENCODER_API_KEY` (`R/application.properties`). A pattern scan of the tracked and
  untracked files (`sk-…`, `sk-or-v1-…`, private-key headers, AWS key ids) found nothing.
- **Reranker keys are masked** in `toString()` (`RerankProperties.Cohere#toString`, `CrossEncoder#toString`;
  tested in `RerankersTest#cohereIsUnavailableWithoutKeyAndKeyIsMaskedInToString`). HTTP reranker errors expose
  only status codes (`HttpRerankClient`; `RerankersTest#cohereErrorsExposeStatusOnlyNeverBodyOrKey`).
- **Provider errors are logged, not returned.** 503 responses use generic messages (`IngestionExceptionHandler`,
  `RetrievalExceptionHandler`). The tests that inject `key=sk-secret` into failures assert it never appears in the
  response.
- **Prompts are logged.** `logging.level.org.springframework.ai=DEBUG` writes prompts (document text and questions)
  to the application log. Whether API keys appear there was not checked (**unverified**). Treat DEBUG logs as
  sensitive.

---

## 8. Production hardening checklist

| # | Item | Why |
|---|---|---|
| 1 | Don't import `docker/keycloak/generic-realm.json` as is. Create the realm, roles and clients explicitly, with no pre-created users. | Dev users have password = username. |
| 2 | Disable the password grant on `generic-cli` (`directAccessGrantsEnabled=false`); use authorization code + PKCE for people and client credentials (service accounts) for machines. | The password grant exposes user credentials to clients. |
| 3 | Restrict `generic-cli` redirect URIs to real frontend URLs (not `http://localhost:*`), and narrow `webOrigins`. | Prevents token theft through open redirects. |
| 4 | Set `KEYCLOAK_ISSUER_URI` to the real HTTPS issuer and `KEYCLOAK_AUDIENCE` if the client id differs. | `iss` must match exactly. |
| 5 | Run Keycloak in production mode (`start`, not `start-dev`) with a real database, HTTPS and hostname settings; change the bootstrap admin. | `start-dev` stores data in the container and allows HTTP. |
| 6 | Serve the app over HTTPS (TLS at the app or a reverse proxy). | Bearer tokens must not cross the network in clear text. |
| 7 | Keep secrets in a secret store or environment injection; rotate the dev DB password (`secret`). | |
| 8 | **Fix `/api/chat`:** remove it, or require a role. | Any authenticated user can spend model credits. |
| 9 | Set AI logging to INFO or WARN. | DEBUG writes document contents and questions to logs. |
| 10 | Add rate limiting (per user and per IP), at least on `/api/ask`, `/api/search` with LLM options, `/api/ingest/*` and MCP. | None exists; only per-request caps do. |
| 11 | Configure CORS only if a browser calls the API directly. Today the frontend uses a same-origin dev proxy, and no CORS configuration exists in the code. | |
| 12 | Decide on token lifespan and refresh for MCP clients. | 5-minute tokens break long agent sessions. |
| 13 | Complete or disable the RFC 9728 metadata (`authorization_servers`, mTLS flag). | It currently misdescribes the server. |
| 14 | Consider restricting `/actuator/info` and checking which actuator endpoints are exposed. | It is open to any authenticated user. |
| 15 | Review the Keycloak brute-force protection and password policy of the realm (not set in the realm file). | |
