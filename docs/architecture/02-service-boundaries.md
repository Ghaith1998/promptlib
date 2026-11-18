# 02 — Service Boundaries, Modules, and the Edge

**Depends on:** ADR-01 (two deployables), ADR-03 (modular-first), ADR-04 (edge as ACL),
ADR-08 (gallery in-domain), ADR-20 (ArchUnit).

---

## 1. Why two deployables and not five

The interview settled on "modular first, extract later", with one schema. That combination makes
extra processes **expensive and dishonest**: with a shared schema, every "service" would still be
coupled at the table level, so the split would buy process isolation only, while costing a proto
file, a container, a healthcheck, and a deploy slot per service.

Two deployables are the point where the split still pays for itself:

| Boundary | What it buys | What it costs |
|---|---|---|
| `edge` vs `core` | One public contract. OIDC validated once. OpenAPI/Swagger in one place. Internal topology can change without touching Flutter. Separate failure domain for request validation/rate limiting (later) | One extra hop (~1–3 ms on localhost), one DTO⇄proto mapping layer |
| Everything inside `core` | Local transactions, one Flyway history, no distributed consistency problems, no network failure modes between prompt and gallery | Must be kept modular by tooling, not willpower |

The edge is **not** a proxy. It is an **anti-corruption layer** (ADR-04):

```
Flutter  ──▶  edge REST DTOs  ──▶  [mapping]  ──▶  core proto messages  ──▶  domain
                    ▲                                     │
                    └────────── stable contract ───────────┘
```

REST DTOs are owned by the edge and are allowed to differ from proto messages: they carry
Jackson annotations, JSON naming, and validation groups. Proto messages stay transport-shaped.
When the mapping starts to hurt, that is the signal that the public contract and the domain have
diverged for a real product reason — not an invitation to leak proto types into REST.

## 2. Deployable: `edge`

**Responsibility:** be the only public surface.

| Concern | Implementation |
|---|---|
| Authentication | `quarkus-oidc`, `application-type=service`, bearer token only. JWKS from Keycloak; no callback, no cookie, no server session |
| Registration | `POST /api/v1/auth/register` → `core` → Keycloak Admin API (`promptlib-register`). The only unauthenticated endpoint |
| Authorization | coarse: `@RolesAllowed("user")` on the API, `admin` on `/api/v1/admin/**` and category writes |
| Contract | `quarkus-rest` + `quarkus-rest-jackson`, resources under `/api/v1` |
| Validation | `quarkus-hibernate-validator` on request DTOs → 422 problem+json; content limits enforced before gRPC |
| Documentation | `quarkus-smallrye-openapi`, `security-scheme=oidc`, Swagger UI in dev only |
| Client to core | `quarkus-grpc` `@GrpcClient("core")`, deadline on every call |
| Resilience | `quarkus-smallrye-fault-tolerance` around read calls only (`07-resilience.md`) |
| Errors | one `ExceptionMapper` chain → RFC 9457 problem+json |
| Identity propagation | filter writes `x-user-id`, `x-user-roles`, `x-request-id` metadata |
| Export | streams `ExportService` response to the client; no buffering of the whole library |
| Logging | file + rotation, request-scoped `requestId` in MDC (`06-logging.md`) |

**Must not** contain: business rules, SQL, Panache entities, or a second source of truth for
prompt content. If a rule can be stated without HTTP, it belongs in `core`.

## 3. Deployable: `core`

**Responsibility:** everything domain. It is a gRPC server and nothing else — no REST, no Swagger.

```
core
├── grpc/            PromptService, VersionService, GalleryService, CategoryService,
│                    CollectionService, TagService, AccountService, AdminService,
│                    ExportService + interceptors
├── domain/prompt/   Prompt, PromptVersion, PromptMessage, VariableSchema, VariableParser
├── domain/library/  Collection, Tag, membership ordering
├── domain/gallery/  visibility, moderation filter, browse query, favorite, fork, copy_count
├── domain/category/ Category, taxonomy validation
├── domain/account/  app_user mirror, profile update
├── domain/admin/    hide/unhide, user & role management (Keycloak Admin API port), audit writer
├── domain/export/   read-only snapshot projection, JSON + Markdown serializers
├── persistence/     Panache repositories (one per aggregate), search-document projector
└── shared/          CallerContext, Ids (UUID v7), Clock, Cursor, ProblemCode, Tx
```

### 3.1 Module dependency rules

Allowed:

```
grpc            → domain.*, shared
domain.prompt   → shared
domain.library  → shared, domain.prompt (via port only)
domain.gallery  → shared, domain.prompt (via port only)
domain.category → shared
domain.account  → shared
domain.admin    → shared, domain.prompt/gallery/account (via ports only), KeycloakAdminPort
domain.export   → shared, domain.prompt/gallery/library (via read-only ports)
persistence     → domain.*, shared
shared          → nothing (no domain imports, ever)
```

Forbidden, all enforced by ArchUnit:

1. Any `domain.X` importing `domain.Y` classes other than interfaces in `shared.port`.
2. Any `domain.*` importing `persistence.*` or `grpc.*`.
3. `shared` importing anything from `domain`, `persistence`, `grpc`.
4. `grpc` classes touching Panache (`PanacheEntity`, `PanacheQuery`, `EntityManager`).
5. `domain.*` importing `jakarta.ws.rs.*` or gRPC types (transport leakage).
6. Any class outside `persistence` referencing a `@Entity` type directly in a query.

### 3.2 Ports (the only sanctioned cross-domain seam)

```java
// shared/port/PromptLookup.java
public interface PromptLookup {
    Optional<PromptSnapshot> findVisible(UUID promptId, CallerContext caller);
    boolean existsPublic(UUID promptId);
}
```

`domain.gallery` depends on this interface; `persistence` (or `domain.prompt`'s adapter) implements
it. With one schema the implementation is a simple query today; when `gallery` is extracted, the
implementation becomes a gRPC client and **nothing in the gallery domain changes**. That is the
whole payoff of doing this now.

## 4. Extraction path (when "later" arrives)

Trigger conditions — extract `gallery` into its own deployable only when at least one is true:

- Gallery traffic exceeds private chatter by an order of magnitude (read scaling matters).
- Gallery needs a different datastore (e.g. Qdrant + OpenSearch) that would pollute the prompt schema.
- Gallery deployments need to be independent (different release cadence).

Procedure:

1. Snapshot published content into a `gallery` schema (`prompt_id`, `version_id`, `title`,
   `body`, `category`, `published_at`, `copy_count`) — this is the ADR-08 alternative we rejected.
2. Add `GalleryService` to a new deployable; edge's gRPC client target changes.
3. Replace `PromptLookup`'s implementation with a gRPC call or a read-only replica view.
4. Delete the local gallery queries. Keep the public REST contract byte-identical.

Do **not** extract `collection` or `tag`: they are pure prompt metadata and would generate chatty
joins across a network for no benefit.

## 5. Package and naming conventions

| Thing | Convention | Example |
|---|---|---|
| Root package | `com.promptlib` | |
| Domain package | `com.promptlib.domain.<aggregate>` | `domain.prompt` |
| Repository | `<Aggregate>Repository` in `persistence` | `PromptRepository` |
| gRPC impl | `<Name>GrpcService` | `GalleryGrpcService` |
| REST resource | `<Name>Resource` in `edge.api` | `GalleryResource` |
| DTO | `<Name>Request` / `<Name>Response` in `edge.api.dto` | `PublishVersionRequest` |
| Mapper | `<Name>Mapper` (edge: proto⇄DTO; core: entity⇄proto) | `PromptMapper` |
| Problem code | `SCREAMING_SNAKE` enum in `shared.ProblemCode` | `VERSION_IN_USE` |
| Proto package | `promptlib.v1` | `promptlib.v1.PromptService` |

## 6. What we explicitly do **not** do

- No service discovery (compose DNS is enough).
- No API gateway product (NPM terminates TLS; `edge` routes).
- No shared library between `edge` and `core` except `promptlib-proto` (generated types) and nothing
  else — sharing DTOs would collapse the anti-corruption layer.
- No gRPC-Web / Connect from Flutter. If a web build ever needs gRPC, add a grpc-web proxy in front
  of `edge`, never expose `core`.
- No `@ApplicationScoped` singletons holding mutable request state in `core`; everything
  request-scoped flows through `CallerContext`.
