# 04 — Non-Functional Requirements

Every target here is **measurable with tools already in the stack** (curl timing, logs, tests). Numbers
are sized for: ≤ 10 users, ≤ 10,000 prompts, single Docker host, JVM Quarkus, MySQL on the same box.

---

## 1. Performance (`NFR-P`)

| ID | Target | How verified |
|---|---|---|
| NFR-P1 | `GET /prompts` and `GET /gallery` with 20 items: **p95 ≤ 400 ms**, p99 ≤ 800 ms | 20 sequential curls against a seeded 5,000-prompt DB; timings in access log |
| NFR-P2 | Prompt detail with full published version: p95 ≤ 250 ms | Same method |
| NFR-P3 | Writes (create prompt, create version, publish, fork): p95 ≤ 600 ms | Same |
| NFR-P4 | Search: p95 ≤ 500 ms at 10,000 indexed prompts | Same, with a stopwatch on a realistic corpus |
| NFR-P5 | Edge→core gRPC overhead ≤ 20 ms at p95 | Compare edge total vs core handler duration in logs (same `requestId`) |
| NFR-P6 | Cold start to healthy ≤ 25 s per service; ≤ 45 s for the whole stack | `docker compose up` and poll `/q/health/ready` |
| NFR-P7 | Export of a 1,000-prompt library ≤ 30 s, streaming (no full buffer in memory) | Timed request; heap stays below container limit |
| NFR-P8 | No endpoint may run longer than 10 s under any condition | Timeout configuration; forced-failure test |

No performance work beyond indexing and the above is planned. If a target is missed by < 50 %, measure
before optimising; the likely cause is a missing index or an N+1 fetch, not JVM tuning.

## 2. Scalability (`NFR-SC`)

| ID | Target |
|---|---|
| NFR-SC1 | Support 10 concurrent users and 10,000 prompts without configuration change |
| NFR-SC2 | All list endpoints are keyset-paginated; no endpoint performs `COUNT(*)` over a growing table |
| NFR-SC3 | Horizontal scaling is explicitly unsupported: no sticky sessions, no shared cache, no second instance. Scaling means bigger containers |
| NFR-SC4 | Content limits (1 MB/version, 100 messages, 100 variables) bound worst-case memory per request |

## 3. Availability and recovery (`NFR-A`)

| ID | Target |
|---|---|
| NFR-A1 | Target: 99 % monthly for the stack **during waking hours**; single host, no HA — downtime for host maintenance is accepted |
| NFR-A2 | Planned maintenance window: any time, announced in the team channel; ≤ 15 min for an app-only deploy |
| NFR-A3 | **RPO is undefined** in v1 because there are no database backups. This is an accepted, recorded risk (`architecture/09 §7`). If any prompt data becomes irreplaceable, backups become a Must, not a Should |
| NFR-A4 | RTO: ≤ 30 min for a host reboot (containers `restart: unless-stopped`, healthchecks) |
| NFR-A5 | Graceful degradation: dependency failure produces 503 within 5 s with a stable error `code`; no request hangs past its timeout |
| NFR-A6 | Data integrity beats availability: a write that cannot be fully completed (prompt + version + index) must fail and roll back, never half-succeed |

## 4. Security (`NFR-SEC`)

| ID | Target |
|---|---|
| NFR-SEC1 | All identity through Keycloak; the application stores no credentials. Login is client→Keycloak. The single exception is registration, where the backend forwards a password to the Admin API in memory, never logs it, never persists it |
| NFR-SEC2 | TLS 1.2+ everywhere public (NPM terminates); internal compose traffic is plaintext and relies on the private bridge network |
| NFR-SEC3 | Access tokens: 5 min lifetime, signed RS256, validated for signature, `exp`, `iss` and `aud` at the edge |
| NFR-SEC4 | Refresh tokens: rotation on use, reuse invalidates the session; stored in `flutter_secure_storage` (Keychain / Keystore) |
| NFR-SEC5 | The only unauthenticated endpoint is registration; it is unthrottled by decision (risk R-3) and must never be added to silently |
| NFR-SEC6 | Authorization is enforced server-side on every request: role at the edge, ownership in the core. The client is never trusted |
| NFR-SEC7 | Non-owner access to a private resource returns 404, never 403 (no existence oracle) |
| NFR-SEC8 | Input validation on every endpoint (size, type, enum, format); SQL only through parameterised queries or Panache |
| NFR-SEC9 | No secret, token, or prompt content in logs at INFO or below; no stack traces in HTTP responses |
| NFR-SEC10 | Database users are least-privilege: `promptlib` limited to `promptlib.*`, `keycloak` to `keycloak.*`; no application use of `root` |
| NFR-SEC11 | The Keycloak admin service account is used only by the admin endpoints; its secret is never sent to clients |
| NFR-SEC12 | Admin actions are auditable (actor, action, target, reason, time) in `admin_audit` |
| NFR-SEC13 | Dependency vulnerabilities are reviewed at upgrade time; automated scanning is out of v1 |
| NFR-SEC14 | Rate limiting is absent by design (trusted users). Exposing the API beyond the team is a scope change requiring NFR-SEC15 |
| NFR-SEC15 | *(reserved)* Per-user rate limits and quotas, if the user base stops being trusted |

**Threat model in one line:** the adversary is a curious colleague or a stolen phone, not an
unauthenticated internet attacker. That justifies no rate limiting and no WAF, but not lax
authorization.

## 5. Privacy (`NFR-PRIV`)

| ID | Target |
|---|---|
| NFR-PRIV1 | Private prompt content is visible to its owner only; admins see metadata, never content |
| NFR-PRIV2 | Favorites, collections, tags are per-user and never exposed to others |
| NFR-PRIV3 | Exports contain only the caller's data |
| NFR-PRIV4 | Public content is readable by every authenticated user — users are told this at publish time (confirmation in the publish sheet) |
| NFR-PRIV5 | No analytics, tracking pixels, third-party scripts, or telemetry leaves the host |
| NFR-PRIV6 | Soft-deleted content remains in the database (not purged). This is a known privacy limitation and must be stated in the internal docs |

## 6. Usability and accessibility (`NFR-UX`)

| ID | Target |
|---|---|
| NFR-UX1 | "Reach a copied prompt" ≤ 3 taps from app launch for a prompt in favorites or the last-used list |
| NFR-UX2 | Every error surfaced to a user carries a stable `code` and a copyable `requestId`; no raw exception text |
| NFR-UX3 | All destructive actions (delete, unpublish, hide) require confirmation that states the consequence |
| NFR-UX4 | Empty, loading, and error states are visually distinct everywhere (no empty list on failure) |
| NFR-UX5 | Responsive from 360 px to desktop widths; keyboard navigation on desktop for the editor |
| NFR-UX6 | UI language: English in v1. Content may contain any characters, but no RTL layout work is in scope |
| NFR-UX7 | Touch targets ≥ 44 px; contrast ≥ 4.5:1 for text (WCAG AA intent, not a formal audit) |
| NFR-UX8 | The editor never loses unsaved draft text on navigation or session expiry (local persistence) |

## 7. Maintainability (`NFR-M`)

| ID | Target |
|---|---|
| NFR-M1 | ArchUnit passes: no cross-domain imports, no transport types in the domain, no Panache in gRPC services |
| NFR-M2 | Domain modules ≥ 80 % line coverage; every endpoint has the 3-case security matrix; overall ≥ 65 % |
| NFR-M3 | Every architectural decision is recorded as an ADR with the rejected alternative |
| NFR-M4 | A new endpoint can be added without touching another domain module (proved by the port pattern) |
| NFR-M5 | Public API changes require `/v2`; proto changes ship in lockstep and are reviewed for breaking changes |
| NFR-M6 | No migration is edited after it has run; all migrations are forward-only and additive in v1 |
| NFR-M7 | Build from a clean checkout in ≤ 5 min (no tests) on the target host |

## 8. Compatibility (`NFR-C`)

| ID | Target |
|---|---|
| NFR-C1 | REST/JSON only at the public edge; UTF-8 everywhere; ISO-8601 UTC timestamps |
| NFR-C2 | Flutter clients: iOS, Android, Windows/macOS/Linux desktop. No platform-specific API behaviour; authentication is identical on every platform (bearer token) |
| NFR-C3 | No web build in v1: no CORS, no browser cookie session, no SPA routing requirements |
| NFR-C4 | Minimum client capability: HTTP/1.1 or h2, JSON, bearer auth — no gRPC, no WebSockets in v1 |
| NFR-C5 | Mobile OS: last 2 majors of iOS and Android; desktop: the current OS versions supported by the Flutter toolchain |

## 9. Portability and operability (`NFR-O`)

| ID | Target |
|---|---|
| NFR-O1 | 12-factor: all environment-specific values via env vars or `%prod` config; no host paths baked into images |
| NFR-O2 | Four containers + one network + four volumes; `docker compose up -d` is the entire install |
| NFR-O3 | Configuration is documented in `.env.example`; no secret is committed |
| NFR-O4 | Health endpoints allow `depends_on: service_healthy` ordering |
| NFR-O5 | Logs are structured enough to `grep` by `requestId` across services |
| NFR-O6 | Upgrade = pull image + restart; rollback = previous image tag, possible only for non-destructive migrations |
| NFR-O7 | No component requires manual state outside its volume |

## 10. Data integrity (`NFR-D`)

| ID | Target |
|---|---|
| NFR-D1 | Version immutability is enforced at the API level and proven by a test |
| NFR-D2 | `PUBLIC ⇒ category AND published_version` enforced by DB `CHECK` and application validation |
| NFR-D3 | Version numbers are unique per prompt (`UNIQUE` key as backstop) and strictly increasing |
| NFR-D4 | All writes that must be atomic are in one transaction (prompt + v1; publish + search document) |
| NFR-D5 | All timestamps UTC; all ids UUID v7; no floating point for any numeric content |
| NFR-D6 | Soft-deleted rows are excluded by every read path (tested per endpoint) |
| NFR-D7 | Schema/entity drift fails startup (`schema-management.strategy=validate`) |

## 11. Compliance (`NFR-CMP`)

| ID | Target |
|---|---|
| NFR-CMP1 | No regulated personal data beyond email/username. No formal GDPR programme in v1 |
| NFR-CMP2 | Export provides data portability for the user's own content (EXP-01) |
| NFR-CMP3 | Erasure is not implemented: soft delete only. Documented limitation; account deletion is a v2 item |
| NFR-CMP4 | Data stays on the team's own host; no third-party processors — except the LLM providers the user copies text into manually, which is outside the system |

## 12. Verification summary

| Group | Verified by |
|---|---|
| Performance | curl timing against a seeded DB; log timings |
| Security | 3-case matrix per endpoint, cross-user tests, tampered-metadata test, registration abuse review |
| Data integrity | unit tests on invariants + DB constraint tests + `validate` startup |
| Usability | manual review against `NFR-UX1..8` before v1 |
| Operability | the 10-step smoke checklist (`architecture/08 §8`) + forced failure tests |
| Maintainability | ArchUnit + coverage report |

There is no automated NFR gate in v1 (no CI). These are checked manually at release and recorded in
the release notes.
