# Prompt Library — Architecture Documentation

Backend architecture for a **Prompt Library**: Quarkus services, MySQL, Keycloak, gRPC internally,
REST at the edge. Flutter consumes the REST contract later; this repository documents the backend
only.

**Status:** v1 decisions locked (2026-11-18). Nothing implemented yet.
**Basis:** an interactive architecture interview; every decision below was made explicitly, and the
rejected alternative is recorded so it does not get relitigated.
**Companion:** [`../requirements/`](../requirements/) — the application
requirements specification. Ten requirement-driven amendments (A1–A10) are applied throughout this
folder as ADR-21…ADR-27.

---

## Documents

| Doc | Contents | Read it when |
|---|---|---|
| [`00-architecture-overview.md`](00-architecture-overview.md) | Context, constraints, **ADR index**, topology, request lifecycle, accepted risks, glossary | First |
| [`01-domain-model.md`](01-domain-model.md) | Aggregates, invariants, publish state machine, fork rules, DDL, variables schema | Designing tables or rules |
| [`02-service-boundaries.md`](02-service-boundaries.md) | Why two deployables, edge as anti-corruption layer, module rules, extraction path | Touching package structure |
| [`03-communication.md`](03-communication.md) | REST surface, status codes, cursor pagination, error model, proto design, metadata, status mapping | Writing an endpoint or RPC |
| [`04-data-persistence.md`](04-data-persistence.md) | MySQL/Flyway/Panache conventions, UUID v7, transactions, locking, FULLTEXT, user mirror | Writing a query or migration |
| [`05-identity-access.md`](05-identity-access.md) | Realm/client/role config, ROPC login, registration proxy, edge validation, ownership rules | Anything auth |
| [`06-logging.md`](06-logging.md) | File logging + rotation, correlation IDs, what to log, PII, verification steps | Debugging or adding log lines |
| [`07-resilience.md`](07-resilience.md) | Timeouts, retries, circuit breakers, degradation matrix, banned patterns | Calling across a hop |
| [`08-testing.md`](08-testing.md) | Test pyramid, **3-case security matrix**, Testcontainers, ArchUnit, fixtures | Every endpoint |
| [`09-deployment.md`](09-deployment.md) | Compose file, Dockerfiles, env, NPM, healthchecks, upgrade/rollback, backups gap | Deploying |
| [`10-flutter-contract.md`](10-flutter-contract.md) | ROPC flow, screen→endpoint map, cursor usage, error codes, caching | Building the client |
| [`11-roadmap.md`](11-roadmap.md) | Build order, future work with triggers, open decisions, risk register | Planning the next month |
| [`12-project-layout.md`](12-project-layout.md) | Repository/module/package tree, ports, naming, where each change goes, ArchUnit rules | Before adding a package or class |

## The architecture in one screen

```
Flutter ──REST /api/v1, Bearer JWT (ROPC)──▶ edge
                                             │  OIDC validation, OpenAPI/Swagger,
                                             │  problem+json, FT on reads
                                             │ gRPC + x-user-id / x-request-id
                                             ▼
                                            core
                                             │  prompt · version · collection ·
                                             │  tag · gallery · category · account
                                             │  single Flyway history, ArchUnit rules
                                             ▼
                                           MySQL `promptlib`
                          Keycloak `promptlib` realm ◀── JWKS ── edge
```

## Decisions that shape everything else

1. **Two deployables, one schema.** Edge is the only public surface and doubles as an
   anti-corruption layer; `core` is modular inside and can be split later without touching Flutter.
2. **Versions are immutable.** Editing a published prompt creates a new version; the gallery stays
   pinned to the old one until you publish again.
3. **Everything is authenticated**, with exactly one exception: registration. No anonymous browsing,
   which makes Keycloak a hard dependency for every read. Login is a Direct Access Grant obtained by
   the client; the backend only validates bearer tokens.
4. **Publishing requires an existing category**, and categories are a seeded, admin-managed
   taxonomy — not free text.
5. **Gallery lives in the prompt service**, since it is a visibility rule plus a pinned version.
6. **Moderation is separate from visibility**, audited, and admin-only — the owner cannot undo it.
7. **Deletion is final.** Prompts soft-delete with no restore or trash; a published prompt must be
   unpublished first; tags and collections are hard-deleted only when unused. Every user gets an
   undeletable default collection that receives forks.
8. **No broker, no metrics, no backups in v1** — all three are recorded as accepted risks with
   explicit triggers and one-line mitigations.

## Highest-priority gaps

| Gap | Consequence | Fix |
|---|---|---|
| No database backups | Total loss of every prompt on host/volume failure | `09 §7` — one cron line |
| No destructive-migration policy enforcement | Unrecoverable schema mistakes | `09 §6` — expand/contract only |
| Keycloak on the read path | IdP outage = full outage | `05 §8`, `07 §6` |

## Conventions

- All timestamps UTC, `Instant` in Java, `DATETIME(6)` in MySQL.
- All ids UUID v7, `BINARY(16)` in MySQL, strings on the wire.
- Money-free domain; no floating point anywhere.
- Public API changes require a `/v2`; proto changes are internal and ship in lockstep.
- Every code path that returns an error must set a stable `code` from `ProblemCode`.
