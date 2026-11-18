# 00 — Architecture Overview: Prompt Library

**Status:** v1, decisions locked · **Date:** 2026-11-18 · **Owner:** Ghaith
**Scope:** backend only. Flutter consumes this API later.
**Companion:** `../requirements/` — the application requirements specification.
Requirements drive architecture; the amendments they caused are recorded in
`../requirements/06-traceability.md` and applied throughout this folder (ADR-21…27).
**Revision 1.1:** the web client and the BFF were withdrawn. Authentication is Keycloak-only, with a
bearer token from a Direct Access Grant. See ADR-12, ADR-21, ADR-22 and §9 risk 4.

---

## 1. What we are building

A **prompt library**. A user keeps prompts in their own private workspace, organizes them with
collections and tags, and builds **immutable versions** of each prompt. A version can be published
to a **public gallery** under a fixed, admin-managed **category** taxonomy. Other users can browse
the gallery and **fork** a published prompt into their own workspace.

There is **no LLM execution in v1**: no provider keys, no streaming, no token accounting. The
domain is shaped so an execution service can be added later without re-cutting boundaries
(see `11-roadmap.md`).

## 2. Given constraints

| Constraint | Value | Architectural consequence |
|---|---|---|
| Hosting | One Docker Compose host (home lab), behind existing NPM + Cloudflare | No HA, no autoscaling, no service mesh. Containers, healthchecks, named volumes |
| Users | A dev team of 3–10, trusted | No rate limiting, no quotas, no abuse pipeline |
| Clients | Flutter **mobile and desktop** (v1). Web is deferred | ROPC for native; no browser token storage, no cookies, no server session |
| Database | One MySQL server, **one schema `promptlib`** | Shared schema ownership; single Flyway history; no cross-DB consistency problems |
| Identity | **Keycloak** container, own database | No custom credential handling; ROPC for Flutter; app mirrors users |
| Internal protocol | **gRPC** | Contract-first `.proto`, generated stubs, typed internal API |
| Edge protocol | **REST/JSON** | Flutter binds here; OpenAPI + Swagger UI generated at the edge |
| Async | **No broker in v1**, design for it | Side effects synchronous; outbox table reserved for later |
| Observability | **File logs + rotation only**, no metrics/tracing | Correlation IDs are the only cross-process debugging tool — mandatory |
| CI/CD | Deferred | Manual `docker compose up`, documented procedure |
| Backups | **None (accepted risk)** | Flagged in §9; one-line mitigation documented in `09-deployment.md` |

## 3. Decision log (ADR index)

Every decision below was made explicitly in the architecture interview. "Rejected" is not filler —
it records what we deliberately did **not** build, so future-you does not relitigate it by accident.

| ID | Decision | Rejected alternative | Detail doc |
|---|---|---|---|
| ADR-01 | Two deployables: `edge` (REST) + `core` (gRPC) | 3–7 separate services; single monolith | `02-service-boundaries.md` |
| ADR-02 | One MySQL server, **one schema**, shared by all domain modules | DB-per-service, schema-per-service | `04-data-persistence.md` |
| ADR-03 | Modular-first: one `core` deployable, domain modules enforced by ArchUnit | Maven multi-module, convention-only | `02`, `08` |
| ADR-04 | Edge is an anti-corruption layer; internal topology may change behind it | Flutter calling services directly | `02`, `03` |
| ADR-05 | gRPC internally, REST at the edge | REST everywhere; gRPC-Web to Flutter | `03-communication.md` |
| ADR-06 | **Versions are immutable**; "editing" = create a new version | In-place edit + audit log | `01-domain-model.md` |
| ADR-07 | Publishing pins `published_version_id`; edits never leak into the gallery | Auto-publish on edit | `01` |
| ADR-08 | Gallery lives in the same service/schema as prompts; public = visibility + pinned version | Separate gallery service with content snapshots | `01`, `02` |
| ADR-09 | Publishing requires an **existing** category (seeded, admin CRUD) | Free-text categories, user proposals | `01` |
| ADR-10 | **Everything is authenticated** (one exception: `POST /auth/register`); no anonymous reads, including the gallery | Anonymous gallery browsing; anonymous registration removed too | `05`, `03` |
| ADR-11 | Keycloak **realm role `admin`** imported with the realm | Local `is_admin` flag, service account | `05-identity-access.md` |
| ADR-12 | **Direct Access Grant (ROPC)** for mobile/desktop, obtained by the client directly from Keycloak; PKCE is the documented client-side upgrade path | Implicit flow, browser session, BFF | `05`, `10` |
| ADR-13 | Path versioning `/api/v1` | Media-type versioning, custom header | `03` |
| ADR-14 | **Cursor** pagination on lists | Offset/page, `COUNT(*)` totals | `03` |
| ADR-15 | Edge validates JWT, forwards `sub` + roles in gRPC metadata; core trusts it | Re-validation in core, mTLS | `03`, `05` |
| ADR-16 | **UUID v7** identifiers everywhere (BINARY(16)) | BIGINT identity, UUID v4 | `04` |
| ADR-17 | **Prompt delete is a soft delete with no user-facing restore.** Versions are retained for integrity; tags and collections are hard-deleted only when unused | Trash + restore; hard-deleting prompts | `01` |
| ADR-18 | Typed variable schema per version | Free-form `{{placeholder}}` only | `01` |
| ADR-19 | Favorites + copy count only | Likes, view counts, comments | `01` |
| ADR-20 | ArchUnit rules fail the build on illegal cross-module imports | Package conventions in a README | `08` |
| ADR-21 | **No web client in v1.** Therefore no BFF, no cookies, no CSRF, no CORS. The backend stays a pure resource server (revises the earlier BFF decision) | Web BFF with server-side sessions; SPA with tokens in localStorage | `05`, `10` |
| ADR-22 | The only service account is **`promptlib-register`** (`manage-users`, registration endpoint only). No in-app user administration | Broad admin client (view/manage users, roles, realm); Keycloak-console-only onboarding | `05`, `09` |
| ADR-23 | **Moderation is a separate axis** (`moderation_state`) from visibility; only an admin can hide/unhide, with a mandatory reason | Admin flips prompt to PRIVATE; admin deletes the prompt | `01`, `03`, `04` |
| ADR-24 | Admin actions are recorded in an **append-only `admin_audit` table**, in the same transaction as the action | Application logs only | `01`, `04` |
| ADR-25 | Users can **export** their whole library (versioned JSON) and single prompts (Markdown); import is deferred | No export; import in v1 | `03`, `05` |
| ADR-26 | `collection.parent_id` is **reserved now** but one level is the only supported depth in v1 | Add hierarchy later with a migration | `01`, `04` |
| ADR-27 | **Self-registration through the app's form**, which proxies to the Keycloak Admin API; the realm's own registration page stays disabled | Keycloak-hosted registration page; admin-only account creation | `05` |
| ADR-28 | Every user gets an auto-created **default collection**; forks land there, but filing remains optional | Forks create unfiled prompts; every prompt must belong to a collection | `01`, `03` |
| ADR-29 | **Colours are part of the product**: `tag.color`, `collection.color`, `category.color`, `CHAR(7)` `#RRGGBB`, nullable, validated at the API | No colours; a fixed palette instead of free choice | `01`, `04` |
| ADR-30 | **Fork authorship**: `owner_id` and the copied version's `created_by` are the forker; credit travels only through `fork_of_*` lineage, and a fork may be published unchanged | `created_by` keeps the original author; forks cannot be published until edited | `01`, `03`, `10` |

## 4. System context

```
                          ┌──────────────┐
   Flutter mobile/desktop │ Cloudflare / │
   (ROPC → Bearer JWT)    │ NPM proxy    │
                          └──────┬───────┘
                                 │ http://edge:8080
                                 ▼
                    ┌──────────────────────────┐
                    │ edge                     │
                    │ REST, OIDC resource srv   │
                    │ OpenAPI + Swagger UI      │
                    └────────────┬─────────────┘
                                 │ gRPC (protobuf)
                                 │ metadata: x-user-id, x-user-roles, x-request-id
                                 ▼
                    ┌──────────────────────────┐        ┌─────────────┐
                    │ core                     │──JWKS─▶│  Keycloak   │
                    │ prompt · version ·       │        │ realm:      │
                    │ collection · tag ·       │        │  promptlib  │
                    │ gallery · category ·     │        └──────┬──────┘
                    │ account                  │               │
                    └────────────┬─────────────┘        keycloakdb
                                 │ JDBC (Agroal)
                                 ▼
                    ┌──────────────────────────┐
                    │ MySQL 8  ·  promptlib    │
                    │ single Flyway history    │
                    └──────────────────────────┘
```

Edge also validates tokens (it is the public boundary); core validates only the *metadata contract*,
not cryptography, because it is never published outside the compose network.

## 5. Runtime topology

| Container | Image | Published port | Internal port | Volume |
|---|---|---|---|---|
| `mysql` | `mysql:8.4` | none | 3306 | `mysql-data` |
| `keycloak` | `quay.io/keycloak/keycloak:26.x` | 127.0.0.1:8180 (NPM → `auth.example.com`) | 8080 | `keycloak-data` + realm import |
| `core` | `promptlib/core:dev` | **none** | 9000 (gRPC), 9001 (health) | `logs-core` |
| `edge` | `promptlib/edge:dev` | 8080 (via NPM) | 8080 | `logs-edge` |

`core` must never publish a host port: its security model is "only the edge can reach me".

## 6. Module map inside `core`

```
com.promptlib
├── shared/                 kernel: ids, CallerContext, Clock, Page/Cursor, errors, tx helpers
├── domain/
│   ├── prompt/             Prompt, PromptVersion, Message, VariableSchema  (aggregate)
│   ├── library/            Collection, Tag, PromptTag, CollectionPrompt
│   ├── gallery/            visibility rules, browse queries, favorites, copy counter
│   ├── category/           Category taxonomy (seeded + admin CRUD)
│   ├── account/            app_user mirror, profile
│   └── admin/              moderation, user/role management (Keycloak Admin API), audit
├── persistence/            Panache repositories, Flyway-facing read models, search document
└── grpc/                   service implementations + interceptors (identity, request-id, errors)
```

Dependency rule: `domain.X` may depend on `shared` and on its own package. Cross-domain access goes
through an explicit **port** interface (`shared.port`), never through another domain's repository.
This is enforced by ArchUnit, not by discipline (`08-testing.md`).

## 7. Request lifecycle

```
1. Flutter → edge: GET /api/v1/gallery?limit=20   Authorization: Bearer <JWT>
2. edge: Quarkus OIDC validates signature/exp/issuer/audience against cached JWKS → SecurityIdentity
3. edge: @Timeout(3s) @CircuitBreaker wrapper calls GalleryService.BrowseGallery over gRPC
         metadata: x-user-id=<uuid>, x-user-roles=user, x-request-id=<uuid7>
4. core: ServerInterceptor validates metadata, builds CallerContext, opens MDC requestId
5. core: GalleryService → GalleryBrowseQuery → Panache → MySQL
6. core: maps entities to proto messages, returns
7. edge: maps proto → REST DTO, returns JSON; errors become application/problem+json
```

## 8. Non-goals for v1

- LLM execution, provider keys, streaming, cost tracking
- Prompt chains / multi-step workflows and concurrent variants (single prompt only)
- Import from JSON or third-party tools (export only)
- Semantic/vector search (Qdrant is planned, not built)
- Comments, real-time collaboration, activity feeds
- Notifications, email, webhooks
- Multi-tenant organizations or per-workspace teams (workspace == user); a `TEAM` visibility tier is
  a schema + authorization change, not a toggle
- Public/anonymous access of any kind, and per-user rate limiting/quotas
- **A Flutter web client** in v1 (it needs a browser token strategy; deferred)
- In-app user administration (create/disable users, assign roles) — done in the Keycloak console
- Horizontal scaling, read replicas, caching layer
- Metrics, traces, log aggregation
- Automated backups and CI/CD
- Account deletion and hard-deletion of user content (soft delete only, no restore UI)
- A trash/restore screen: deleting a prompt is final from the user's point of view
- Mobile-specific push notifications

## 9. Accepted risks (read before shipping)

1. **No backups.** A lost volume loses every prompt. Mitigation is one cron line
   (`09-deployment.md §7`). This is the single highest-impact gap in the design; the whole-library
   export (ADR-25) is the only user-level safety net.
2. **Shared schema.** A `core` migration can break a future extracted service. Mitigated by
   single-owner Flyway and table-prefix reviews, not eliminated.
3. **Keycloak is on the read path.** Every request, including gallery browsing, needs a valid
   token; IdP downtime = full outage. Mitigated by JWKS caching and short connection timeouts,
   not eliminated.
4. **Registration credential.** `core` holds a Keycloak service-account secret (`promptlib-register`)
   that can create accounts. It is used by one endpoint and nothing else. Combined with risk 8, this
   means the credential is reachable through an unauthenticated route: treat it as the highest-value
   secret in the stack.
5. **No rate limiting.** Acceptable only while the user set is trusted; do not expose publicly
   without adding a quota filter.
6. **Deletes are not reversible.** Prompts are soft-deleted (no restore UI, no endpoint); tags and
   collections are hard-deleted. With no backups, a mistaken delete is permanent.
7. **Single MySQL instance, no replica.** Corruption or host loss = downtime until restore, and
   there is nothing to restore from (risk 1).
8. **Open registration endpoint.** `POST /api/v1/auth/register` is unauthenticated and unthrottled by
   decision. Anyone who can reach it can create an account. Accepted behind Cloudflare for a home
   lab; revisit if the hostname becomes discoverable. Mitigation when it does: a per-IP limit on that
   one path, or an invite code. Recorded in the requirements folder as risk R-3.

## 10. Glossary

| Term | Meaning |
|---|---|
| **Prompt** | The stable identity of an idea: owner, title, visibility. Holds no content itself |
| **Version** | Immutable content snapshot: ordered messages + typed variables |
| **Published version** | The one version the gallery serves; pinned by `prompt.published_version_id` |
| **Fork** | A new private prompt copied from a visible prompt/version, with lineage recorded |
| **Duplicate** | A private copy of the caller's own prompt; same mechanism as fork |
| **Publish** | Make one version visible in the gallery under a category |
| **Unpublish** | Owner removes the prompt from the gallery; history is kept |
| **Hide** | Admin moderation: removes a public prompt from the gallery until an admin unhides it |
| **Category** | Global taxonomy value, required for publication, admin-managed |
| **Collection** | Per-user ordered grouping of prompts (one level in v1) |
| **Tag** | Per-user free-form label; many-to-many with prompts |
| **Workspace** | The user's private scope. There is exactly one per user in v1 |
| **Gallery** | The set of prompts with `visibility = PUBLIC`, `moderation_state = VISIBLE`, not deleted |
