# 11 — Roadmap, Future Work, Open Decisions

---

## 1. Build order (each phase is shippable)

| Phase | Scope | Definition of done |
|---|---|---|
| **P0 — Skeleton** | Reactor (`promptlib-proto`, `promptlib-core`, `promptlib-edge`), Quarkus configs, MySQL + Flyway `V1`, empty gRPC + REST round-trip, compose up | `GET /api/v1/me` returns a profile through the full edge→core→MySQL path with a real Keycloak token |
| **P1 — Identity complete** | ROPC login (client-side, Keycloak direct), `POST /api/v1/auth/register` via `promptlib-register`, `app_user` mirror on first request | A registered user can log in through Keycloak and reach `/me`; duplicate registration is rejected; the register endpoint works without a token |
| **P2 — Library CRUD** | Prompt create/read/update/delete, immutable versions, collections (flat), tags, variables validation, search document | Security matrix passes for every library endpoint; `VARIABLE_MISMATCH` works |
| **P3 — Publishing** | Categories (seed + admin CRUD), publish/unpublish, `CHECK` invariant, gallery browse with cursor, fork with lineage and copy counter | Publish → visible in gallery; edit does not change gallery; fork increments source count |
| **P4 — Moderation** | Hide/unhide with reason, `admin_audit`, admin prompt list (metadata only) | Admin hides a prompt → gone from gallery, owner sees reason, audit row exists; metadata-only assertions pass |
| **P5 — Search & export** | FULLTEXT query, ranking, filters, private-data leak test; whole-library JSON + Markdown export (streaming) | Searching a word unique to a private prompt returns nothing; export contains only the caller's data |
| **P6 — Hardening** | Logging + rotation + request ids, FT on reads, problem mapper, ArchUnit, smoke checklist | `grep <requestId>` works across both services; `stop mysql` yields 503 in <5 s |
| **P7 — Flutter** | Client against the frozen `/v1` contract | — |

Do not start P3 before P2's immutability tests exist. Retrofitting immutability onto a mutable model
is a rewrite, not a patch. Do not defer registration either: it is the only unauthenticated endpoint
and the only place a password touches the backend, so it earns its tests early.

## 2. Future work, with explicit triggers

Nothing below is in v1. Each item has a **trigger** so it is not built out of boredom.

| Item | Trigger | Shape of the change |
|---|---|---|
| **Semantic search (Qdrant)** | Keyword search visibly misses relevant prompts; library > ~500 prompts | `search-service` module: embed `prompt_search_document.body_text`, hybrid score = BM25 + cosine. `GET /gallery?q=…&mode=semantic`. Qdrant already runs at `10.0.0.22` |
| **LLM execution** | Users want to run prompts, not just store them | New `execution` module + provider adapters (`openai`, `anthropic`, `ollama`), secrets in env, streaming via SSE at the edge, usage/cost table. Domain is already execution-ready (typed variables, ordered messages) |
| **Async processing** | Index rebuild or counter updates make writes slow, or a second consumer appears | Transactional outbox table (`outbox_event`) + a poller; then RabbitMQ/Kafka. Never publish to a broker inside the business transaction without an outbox |
| **Metrics + tracing** | You cannot answer "is it slow?" from logs | `quarkus-micrometer` + Prometheus; `quarkus-opentelemetry` with trace context over gRPC metadata (extends `x-request-id`) |
| **Loki log shipping** | Two services are not enough to `grep` | Add Alloy/Promtail; parse `rid` as a label; switch console to JSON |
| **CI/CD** | Manual builds become error-prone, or more than one person deploys | Jenkins (already running) or GitHub Actions: build + tests + `buf breaking` + image push, then `compose pull && up -d` |
| **Backups** | **Any real data appears** | Nightly `mysqldump` + off-host copy + one tested restore. See `09 §7` |
| **Rate limiting** | The API is exposed beyond trusted users | Edge filter with a per-user token bucket; 429 + `Retry-After` |
| **Idempotency keys** | Flutter gains offline/retry behaviour | `Idempotency-Key` header on POSTs, stored with the response for 24 h |
| **Gallery extraction** | Read traffic dominates, or gallery needs Qdrant/OpenSearch | Extract per `02 §4`; REST contract unchanged |
| **Team visibility tier** | Someone needs to share with 3 people but not everyone | `visibility = TEAM` + a membership table + a third authorization branch. Schema and authz change, not config |
| **Flutter web client** | Someone needs browser access | First decide the token strategy: SPA with in-memory tokens (backend unchanged) or a BFF (backend becomes an OAuth client, ADR-21 reversed). Then enable CORS, register redirect URIs, add SPA fallback |
| **Import (JSON / other tools)** | Export exists and someone asks to migrate in | Importer keyed on `schemaVersion`; dry-run validation first |
| **Account deletion / erasure** | A user leaves and asks for removal | Anonymize `app_user`, purge soft-deleted content, decide what happens to published copies |
| **User deletion / GDPR** | A user asks | Anonymize `app_user`, soft-delete prompts, decide on published copies |
| **Prompt execution history** | Execution exists | New tables; never inside the prompt aggregate |

## 3. Open decisions (deliberately deferred)

| # | Question | Why it can wait | Who decides |
|---|---|---|---|
| O1 | Do published prompts get a human-readable slug? | UUID links work; slugs add collision/rename rules | Product |
| O2 | Can a user have more than one workspace? | v1: workspace == user. Multi-workspace changes ownership, sharing, quotas | Product |
| O3 | Should forks show lineage publicly ("forked from X")? | Data is stored; only the UI/API exposure is missing | Product |
| O4 | Who can unpublish someone else's prompt? | v1: owner only + admin read-only. Needs a policy + audit | Product |
| O5 | Is there a draft state separate from "unpublished version"? | Current model: any version not pointed to by `published_version_id` is effectively a draft | Domain |
| O6 | Search result ranking weights | v1 uses MySQL relevance; tune only with real data | Backend |
| O7 | What happens to `copy_count` when a fork is deleted? | Currently never decremented. Monotonic is simpler and arguably more honest | Product |
| O8 | Do categories need i18n? | Seeded English names; adding `category_translation` is additive | Product |
| O9 | Retention for soft-deleted rows | Nothing purges them in v1; a job later | Ops |

## 4. Risk register (updated)

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **No backups** | — | **Total data loss** | One cron line (`09 §7`). Highest priority non-feature work |
| Destructive migration with no rollback | Medium | Data loss | Ban destructive migrations in v1; expand/contract pattern |
| Shared schema drift if gallery is extracted | Low (deferred) | Broken deploy | Single Flyway owner + `02 §4` procedure |
| Keycloak outage = full outage | Low | Full downtime | JWKS caching, short timeouts, `ADR-10` accepted |
| FULLTEXT min token size surprises | Medium | Bad search UX | `innodb_ft_min_token_size=2` set **before** first index build |
| Prompt content in logs | Medium | Privacy | `06 §4` rules + review; never log body |
| `BINARY(16)` vs `CHAR(36)` mismatch | Medium | Startup failure (good) or runtime error (bad) | `schema-management.strategy=validate` in every profile |
| Version immutability eroded by a "quick fix" | Medium | Broken trust in shared links | ArchUnit + repository exposes no mutators + `08 §6` test |
| Single host dies | Low | Downtime + data loss | Out of scope for v1; documented |
| Flutter refresh race logs users out | Medium | Bad UX | Single-flight refresh (`10 §2`) |
| Registration service-account secret leaks | Low | **Arbitrary account creation** | `.env` only, core-only usage, `manage-users` only, no logging, rotate on suspicion |
| Open registration endpoint abused | Medium | Junk accounts, Keycloak noise | Accepted for now (`00 §9` risk 8); add a per-IP limit or invite code when it becomes discoverable |
| ROPC blocked by a future Keycloak release | Low | Login breaks | Standard Flow is already enabled and redirect URIs registered; PKCE is a client-side migration (`05 §3.2`) |
| Export treated as a backup | High | False sense of safety | Export is per-user and manual; the risk register entry for backups stays |
| Irreversible delete with no backups | Medium | **Permanent data loss on a mis-tap** | No restore by design; confirmation dialog names the consequence; tags/collections can only be deleted when unused |
| Moderation predicate forgotten in a new query | Medium | Hidden prompt resurfaces | One shared query fragment + a test that asserts zero results for hidden prompts in all three paths |
| Requirement drift between the two folders | Medium | Building the wrong thing | `requirements/06-traceability.md` is updated with every change |

## 5. First 10 commits (suggested, in order)

1. `chore: reactor pom, java 21, quarkus 3.33 LTS`
2. `feat(proto): common.proto + account.proto, codegen wired`
3. `feat(core): app_user mirror + EnsureUser over gRPC`
4. `feat(edge): /api/v1/me, OIDC bearer, problem+json mapper`
5. `feat(register): POST /auth/register via promptlib-register, USERNAME_TAKEN/WEAK_PASSWORD`
6. `feat(db): V1 baseline (+moderation cols) + V2 categories, flyway + validate`
7. `feat(prompt): create/read with immutable v1, variables parser, limits`
8. `test(prompt): invariant tests + 3-case security matrix`
9. `feat(version): create/publish/unpublish, category invariant`
10. `feat(gallery): browse cursor, fork, favorites, search document`
11. `feat(admin): hide/unhide + admin_audit`
12. `feat(export): streaming JSON + Markdown`
13. `chore(ops): compose, log rotation, request-id propagation, smoke script`

Each commit leaves the stack runnable. No commit mixes a migration with a behaviour change.

## 6. Definition of "v1 done"

- Every M requirement in `../requirements/02-functional-requirements.md` is
  implemented with its acceptance check.
- Every endpoint in `03 §2.2` implemented with its three security tests, plus the cross-cutting cases
  in `08 §4` (registration, admin metadata-only, export leak).
- A prompt can be created, versioned, published, browsed, searched, forked, moderated, deleted,
  and exported; a published prompt must be unpublished before deletion.
- Editing a published prompt never changes what a reader sees.
- A private prompt never appears in gallery, search, another user's list, or any admin payload.
- Every request produces one `requestId` visible in both services' log files.
- `docker compose stop core` degrades to 503 with a stable `code`, not a hang.
- Both auth flows work: ROPC login against Keycloak, and registration through the API.
- In-app administration is limited to categories and moderation; users/roles are managed in Keycloak.
- The Flutter app can be built against `/v1` without a single backend change.
