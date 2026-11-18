# 03 — Communication: REST Contract, gRPC Internals, Errors

**Depends on:** ADR-05, ADR-10 (all authenticated except registration), ADR-12 (ROPC),
ADR-13 (`/api/v1`), ADR-14 (cursor), ADR-15 (metadata trust), ADR-22 (registration endpoint),
ADR-25 (export).

---

## 1. Two protocols, one rule each

| Hop | Protocol | Rule |
|---|---|---|
| Flutter → edge | REST/JSON over HTTPS | The **only** public contract. Never breaks without a `/v2` |
| edge → core | gRPC/protobuf over the compose network | Private. Free to change in lockstep with the edge deploy |

Because both artifacts ship together, a proto change does not need a deprecation cycle — but it does
need `buf breaking` review discipline once CI exists (`08-testing.md`).

## 2. REST conventions

Base: `/api/v1`. JSON only (`application/json`, errors `application/problem+json`).
All endpoints require `Authorization: Bearer <access_token>` **except** `POST /api/v1/auth/register`
(ADR-10, ADR-27). There is exactly one unauthenticated endpoint; adding a second one is an
architecture decision, not a convenience.

### 2.1 Status code policy

| Code | When |
|---|---|
| 200 | Read, update, publish/unpublish, fork |
| 201 | Create (with `Location`) |
| 204 | Delete (prompt, tag, collection), remove favorite |
| 400 | Malformed JSON, bad enum value, unparseable cursor base64 |
| 401 | Missing/expired/invalid token |
| 403 | Valid token, insufficient role (`admin` endpoints) |
| 404 | Unknown id **or** id the caller may not see (no existence leak) |
| 409 | State conflict: `VERSION_IN_USE`, `ALREADY_PUBLISHED`, `PROMPT_DELETED`, `PROMPT_PUBLISHED`, `HIDDEN_BY_ADMIN`, `TAG_IN_USE`, `COLLECTION_NOT_EMPTY`, `DEFAULT_COLLECTION`, `USERNAME_TAKEN`, optimistic-lock clash |
| 422 | Business validation: `VARIABLE_MISMATCH`, `CATEGORY_REQUIRED`, `EMPTY_PROMPT`, `CONTENT_TOO_LARGE`, `TOO_MANY_MESSAGES`, `TOO_MANY_VARIABLES` |
| 429 | Reserved, unused in v1 |
| 503 | Core unreachable / circuit open (`UPSTREAM_UNAVAILABLE`) |

404 vs 403 rule: if the caller is not allowed to know the resource exists → **404**.

### 2.2 Resource surface

| Method | Path | Purpose | Min role |
|---|---|---|---|
| GET | `/api/v1/me` | Current profile (creates mirror on first call) | user |
| PATCH | `/api/v1/me` | Update `displayName` only | user |
| GET | `/api/v1/prompts` | Private list; `?cursor&limit&q&collectionId&tag&visibility` | user |
| POST | `/api/v1/prompts` | Create prompt + version 1 in one transaction | user |
| GET | `/api/v1/prompts/{id}` | Prompt + latest version summary | owner |
| PATCH | `/api/v1/prompts/{id}` | Title, description, visibility(PRIVATE only here) | owner |
| DELETE | `/api/v1/prompts/{id}` | Soft delete; **409 `PROMPT_PUBLISHED`** while public | owner |
| GET | `/api/v1/prompts/{id}/versions` | Version history (desc) | owner or public-reader |
| POST | `/api/v1/prompts/{id}/versions` | New version; body `{fromVersionId?, changeNote?}` | owner |
| GET | `/api/v1/prompts/{id}/versions/{n}` | Full version content | owner or public-reader |
| POST | `/api/v1/prompts/{id}/versions/{n}/publish` | `{categoryId}` | owner |
| POST | `/api/v1/prompts/{id}/unpublish` | Back to private | owner |
| GET | `/api/v1/gallery` | Browse public: `?cursor&limit&q&categoryId&sort=newest\|popular` | user |
| GET | `/api/v1/gallery/{promptId}` | Public prompt + published version | user |
| POST | `/api/v1/gallery/{promptId}/fork` | Copy into caller's workspace | user |
| PUT | `/api/v1/prompts/{id}/favorite` | Idempotent add | user |
| DELETE | `/api/v1/prompts/{id}/favorite` | Idempotent remove | user |
| GET | `/api/v1/favorites` | Caller's favorites | user |
| GET | `/api/v1/categories` | Active taxonomy | user |
| POST/PATCH/DELETE | `/api/v1/admin/categories` | Taxonomy CRUD (never hard-delete; deactivate) | admin |
| GET | `/api/v1/collections` | Caller's collections + counts | user |
| POST/PATCH/DELETE | `/api/v1/collections/{id}` | CRUD + reorder; delete is **409** when non-empty, has sub-collections, or is the default | owner |
| GET | `/api/v1/tags` | Caller's tags with usage counts | user |
| DELETE | `/api/v1/tags/{id}` | Hard delete; **409 `TAG_IN_USE`** if attached | owner |
| GET | `/api/v1/export` | Stream the caller's whole library (JSON, `schemaVersion`) | user |
| GET | `/api/v1/prompts/{id}/export.md` | Single prompt as Markdown | owner or public-reader |
| GET | `/api/v1/admin/prompts` | All prompts, **metadata only** (owner, title, visibility, moderation, counts) | admin |
| POST | `/api/v1/admin/prompts/{id}/hide` | `{reason}` → hide from gallery | admin |
| POST | `/api/v1/admin/prompts/{id}/unhide` | Restore to gallery | admin |
| POST | `/api/v1/auth/register` | **Unauthenticated.** `{username, email, password, displayName?}` → Keycloak user | — |

Everything else in `ADM` that concerns users and roles — creating accounts outside registration,
disabling accounts, granting/revoking `admin` — happens in the **Keycloak admin console**, not in this
API (ADR-22). There is no `/admin/users` surface, no service account with realm-management rights, and
no `LAST_ADMIN` rule to enforce in code.

Admin endpoints never return message content or variables for private prompts (requirements ADM-09).
The admin prompt list is a dedicated projection, not a widened owner endpoint.

#### Fork response

```json
{
  "id": "0193…",
  "title": "Review checklist (fork)",
  "visibility": "PRIVATE",
  "ownerId": "0193…",
  "forkOf": {
    "promptId": "0193…",
    "title": "Review checklist",
    "authorDisplayName": "alice",
    "versionNumber": 3
  }
}
```

`forkOf` is present **only** when the source is still readable by the caller (`PUBLIC`, `VISIBLE`,
not deleted). A missing `forkOf` is normal and must not be treated as an error by the client. The
forked version's `createdBy` is the caller — credit lives in `forkOf`, not in authorship fields.

`POST /prompts` body:

```json
{
  "title": "Blog outline generator",
  "description": "Turns a topic into a 5-section outline",
  "messages": [
    {"role": "SYSTEM", "content": "You are a technical editor."},
    {"role": "USER",   "content": "Outline an article about {{topic}} for {{audience}}."}
  ],
  "variables": [
    {"name": "topic", "type": "string", "required": true},
    {"name": "audience", "type": "enum", "required": false,
     "default": "developers", "enumValues": ["developers", "executives"]}
  ]
}
```

### 2.3 Cursor pagination

Response envelope (all list endpoints):

```json
{ "items": [ ... ], "nextCursor": "eyJ0IjoxNzYzNTQwMDAwMDAwLCJpIjoiMDE5My4uLiJ9", "hasMore": true }
```

- Cursor = base64url of `{"t": <createdAt epoch micros>, "i": "<uuid>"}` for `sort=newest`;
  `{"c": <copyCount>, "i": "<uuid>"}` for `sort=popular`.
- Ordering is always `(<sortKey> DESC, id DESC)` — the tiebreaker is what makes the cursor stable.
- `limit` default 20, max 100. No `total` field: no `COUNT(*)` on the gallery (ADR-14).
- An unparseable cursor → 400 `INVALID_CURSOR`. A cursor whose row was deleted is fine: keyset
  comparison simply continues.

### 2.4 Optimistic concurrency

`PATCH /prompts/{id}` and `PATCH /me` accept `If-Unmodified-Since`-free optimistic locking via an
`etag` field returned on GET and echoed in the PATCH body (`"expectedUpdatedAt": "..."`).
Mismatch → 409 `STALE_WRITE`. Cheap, and it stops two Flutter windows from clobbering metadata.

Version content does not need this: it is immutable.

### 2.5 Idempotency

| Endpoint | Behaviour |
|---|---|
| `PUT /favorite`, `DELETE /favorite` | Naturally idempotent; repeated calls return 200/204 |
| `POST /prompts/{id}/versions/{n}/publish` | Idempotent: already-published → 200, no state change |
| `POST /gallery/{id}/fork` | **Not** idempotent: each call creates a new prompt. Client must guard double-tap |
| `POST /prompts` | Not idempotent. `Idempotency-Key` is a v2 item (`11-roadmap.md`) |

## 3. Error model (RFC 9457)

```json
{
  "type": "https://promptlib.local/problems/variable-mismatch",
  "title": "Prompt variables do not match placeholders",
  "status": 422,
  "detail": "Placeholders [audience] have no definition; variable [tone] is never used.",
  "instance": "/api/v1/prompts",
  "code": "VARIABLE_MISMATCH",
  "requestId": "0193a1f2-7c4e-7c3a-9c2f-0f1e2d3c4b5a",
  "errors": [
    {"field": "variables[1].name", "code": "UNUSED_VARIABLE", "message": "tone is never referenced"}
  ]
}
```

- `code` is the stable machine-readable contract; Flutter switches on it, never on `detail`.
- `requestId` always present and equals the edge log MDC value — this is the support handshake.
- Validation errors from Hibernate Validator are converted into `errors[]`, never leaked as
  `ConstraintViolationException` text.
- Never include stack traces, SQL, or Keycloak error bodies.

Full `ProblemCode` enum lives in `shared` and is mirrored in the OpenAPI description.

## 4. gRPC design

### 4.1 Files and generation

```
promptlib-proto/src/main/proto/promptlib/v1/
├── common.proto      Page, Cursor, Problem, Visibility, Role, Variable
├── prompt.proto      PromptService
├── version.proto     VersionService
├── gallery.proto     GalleryService
├── category.proto    CategoryService
├── collection.proto  CollectionService
├── tag.proto         TagService
└── account.proto     AccountService
```

`option java_package = "com.promptlib.proto.v1"; option java_multiple_files = true;`
Versioned package `promptlib.v1` — a breaking change creates `promptlib.v2`, not an edit.

### 4.2 Service sketch

```proto
syntax = "proto3";
package promptlib.v1;
import "google/protobuf/timestamp.proto";
import "google/protobuf/empty.proto";

message Prompt {
  string id = 1;
  string title = 2;
  string description = 3;
  Visibility visibility = 4;
  string category_id = 5;
  int32 latest_version_number = 6;
  int32 published_version_number = 7;
  int32 copy_count = 8;
  string fork_of_prompt_id = 9;
  google.protobuf.Timestamp created_at = 10;
  google.protobuf.Timestamp updated_at = 11;
  bool favorited = 12;
}

service PromptService {
  rpc ListPrompts(ListPromptsRequest) returns (ListPromptsResponse);
  rpc GetPrompt(GetPromptRequest) returns (GetPromptResponse);
  rpc CreatePrompt(CreatePromptRequest) returns (Prompt);
  rpc UpdatePromptMetadata(UpdatePromptMetadataRequest) returns (Prompt);
  rpc DeletePrompt(DeletePromptRequest) returns (google.protobuf.Empty);
  rpc RestorePrompt(RestorePromptRequest) returns (Prompt);
}

service VersionService {
  rpc ListVersions(ListVersionsRequest) returns (ListVersionsResponse);
  rpc GetVersion(GetVersionRequest) returns (PromptVersion);
  rpc CreateVersion(CreateVersionRequest) returns (PromptVersion);
  rpc PublishVersion(PublishVersionRequest) returns (Prompt);
  rpc UnpublishPrompt(UnpublishPromptRequest) returns (Prompt);
}

service GalleryService {
  rpc Browse(BrowseRequest) returns (BrowseResponse);
  rpc GetPublicPrompt(GetPublicPromptRequest) returns (GetPublicPromptResponse);
  rpc Fork(ForkRequest) returns (Prompt);
  rpc AddFavorite(FavoriteRequest) returns (google.protobuf.Empty);
  rpc RemoveFavorite(FavoriteRequest) returns (google.protobuf.Empty);
}
```

Rules:

- Proto messages are **flat and explicit**. No `google.protobuf.Any`, no `Struct`.
- Ids travel as `string` (UUID text) to keep the wire readable; core converts to `UUID`.
- Timestamps are `google.protobuf.Timestamp` UTC; the edge formats ISO-8601.
- Enums are never reused/renumbered; unknown enum values must be handled defensively.
- Every RPC returns a domain-shaped error via `google.rpc.Status` details, not a raw exception.

### 4.3 Identity metadata (ADR-15)

| Metadata key | Value | Source |
|---|---|---|
| `x-user-id` | `app_user.id` (UUID) | resolved from `sub` at the edge |
| `x-user-sub` | Keycloak `sub` | token claim (audit only) |
| `x-user-roles` | comma-separated, e.g. `user,admin` | realm roles |
| `x-request-id` | UUID v7 | generated at edge, reused if client supplied |

A `ServerInterceptor` in `core`:

1. rejects calls without `x-user-id` → `UNAUTHENTICATED` (fails closed);
2. builds `CallerContext` and puts it in a request-scoped holder;
3. sets MDC `requestId` and `userId`;
4. clears MDC in `finally`.

Health-check RPCs are exempt from identity checks.

**Security assumption:** `core` listens on 9000 inside the compose network and publishes no host
port. If that ever changes, add mTLS before exposing it (alternative recorded in ADR-15).

### 4.4 Deadlines and status mapping

Every edge call sets a deadline (2 s reads, 5 s writes). Core maps exceptions:

| Domain exception | gRPC status | Edge HTTP |
|---|---|---|
| `NotFound` | `NOT_FOUND` | 404 |
| `Forbidden` | `PERMISSION_DENIED` | 403 |
| `Conflict` | `ABORTED` / `ALREADY_EXISTS` (by code) | 409 |
| `ValidationFailed` | `INVALID_ARGUMENT` + `BadRequest` details | 422 |
| `UpstreamUnavailable` | `UNAVAILABLE` | 503 |
| anything else | `INTERNAL` (message scrubbed) | 500 |

The edge has a single mapper from `StatusRuntimeException` → `ProblemResponse`, so error shape is
identical whether the failure happened in the edge or in core.

## 5. OpenAPI and Swagger UI

```properties
quarkus.smallrye-openapi.info-title=Prompt Library API
quarkus.smallrye-openapi.info-version=1.0.0
quarkus.smallrye-openapi.path=/q/openapi
quarkus.smallrye-openapi.security-scheme=oidc
quarkus.smallrye-openapi.security-scheme-name=keycloak
quarkus.swagger-ui.path=/q/swagger-ui
quarkus.swagger-ui.always-include=false     # dev only; flip deliberately for prod
quarkus.http.cors.enabled=false             # no browser client in v1 (ADR-21)
```

CORS is off because there is no Flutter web build. If a web client is added later, that is an
architecture change: enable CORS with an explicit origin list, and decide the token strategy first
(`11-roadmap.md`).

Because every endpoint except registration requires a token, the OpenAPI document must declare the
security scheme globally; otherwise the Swagger UI "Try it out" button will 401 and look broken.
Swagger UI logs in through a dedicated client:

- `promptlib-app` — public client used by Flutter (ROPC; no secret).
- `promptlib-swagger` — public client (PKCE) with a redirect URI of
  `http://localhost:8080/q/swagger-ui/oauth2-redirect.html`, used by Swagger UI only.

## 6. Contract-change checklist

Before changing anything under `promptlib-proto` or `/api/v1`:

1. Is this additive? (new field with a new tag, new endpoint) → fine.
2. Does it rename/remove a REST field? → needs `/api/v2` or a deprecation window with both fields.
3. Does it change an enum's meaning? → forbidden; add a new value.
4. Does it change an error `code`? → treat as breaking for Flutter.
5. Did `buf breaking` pass? (once CI exists)
