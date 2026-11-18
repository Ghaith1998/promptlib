# 07 — Resilience and Fault Tolerance

**Depends on:** requirement #5, `quarkus-smallrye-fault-tolerance`. No metrics/tracing in v1, so
degradation must be **visible in logs**.

---

## 1. The failure graph

```
Flutter ──▶ edge ──▶ core ──▶ MySQL
              │        │
              └────────┴──▶ Keycloak (JWKS only, at the edge)
```

Four things can fail: MySQL, core, Keycloak, the network between containers. There is no broker, no
cache tier, and no replica — so every mitigation is **timeout + bounded retry + honest error**, not
magic failover.

## 2. Policy per hop

| Hop | Timeout | Retry | Circuit breaker | Fallback |
|---|---|---|---|---|
| Flutter → edge | 10 s client timeout | No (user retries) | — | Error UI with `code` + `requestId` |
| edge → core (read) | 2 s deadline | 1 retry, only on `UNAVAILABLE`/`DEADLINE_EXCEEDED` | Yes, per RPC method | 503 `UPSTREAM_UNAVAILABLE` |
| edge → core (write) | 5 s deadline | **No retry** | Yes (fail fast) | 503; **never** a fake success |
| core → MySQL | 5 s acquisition, 10 s socket | No (pool handles it) | Via pool exhaustion + FT on use case | 503 `DATABASE_UNAVAILABLE` |
| core → Keycloak Admin API (registration only) | 5 s | No | Yes, on the registration endpoint | 503 `IDENTITY_PROVIDER_UNAVAILABLE` |
| edge → Keycloak (JWKS) | 2 s | No retry on request path; cached keys | Yes | Cached keys until expiry |
| edge → Keycloak (health) | 2 s | — | — | Reports DOWN, does not block traffic |

**Retry only reads.** A retried write is a duplicate fork, a double publish, or a second version.
MicroProfile Fault Tolerance has no notion of HTTP semantics — the decision lives in the code
(`@Retry` on read methods only, never on `@Transactional` write methods).

## 3. Edge implementation

```java
@ApplicationScoped
public class GalleryClient {

    @Inject @GrpcClient("core") GalleryServiceGrpc.GalleryServiceBlockingStub stub;

    @Timeout(2000)
    @Retry(maxRetries = 1, delay = 100, retryOn = {StatusRuntimeException.class},
           abortOn = {IllegalArgumentException.class})
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 10_000,
                    successThreshold = 2)
    @Fallback(fallbackMethod = "browseUnavailable")
    public BrowseResponse browse(BrowseRequest req) {
        return stub.withDeadlineAfter(2, TimeUnit.SECONDS).browse(req);
    }

    BrowseResponse browseUnavailable(BrowseRequest req) {
        throw new UpstreamUnavailableException("GalleryService.Browse");
        // mapped to 503 problem+json, never to an empty list
    }
}
```

Important details:

- **`@Retry` `retryOn` must be narrowed.** Retrying every `RuntimeException` turns a validation bug
  into three validation bugs and triples the tail latency.
- **Fallback must not silently degrade semantics.** Returning an empty gallery on failure makes the
  product look empty, which is indistinguishable from "no public prompts yet". Return 503.
- The method is `public` and on a CDI bean — FT interceptors are no-ops on private/final/self-called
  methods.
- The `withDeadlineAfter` is redundant with `@Timeout` but harmless and protects the raw stub if the
  annotation is ever removed. Keep both; a leaked call with no deadline is the classic
  "thread pool slowly drained by a hung upstream".

### 3.1 What must never be wrapped

| Operation | Why |
|---|---|
| `EnsureUser` (identity upsert) | Retrying is harmless but a failure must surface; do not fabricate an id |
| `PublishVersion` / `CreateVersion` | Non-idempotent |
| `Fork` | Non-idempotent (each call creates a prompt) |
| `Hide` / `Unhide` | Writes with an audit row; a retry after a timeout could double-audit or flip state twice |
| `POST /auth/register` | Creates an account in Keycloak; a retry after a timeout could create a duplicate or return 409 confusingly. The client must not auto-retry a registration |
| `Export` | Long-lived streaming read; retrying restarts a potentially large transfer. The client retries, not the server |
| Token validation | Handled by OIDC, not FT; wrapping it breaks the security contract |

## 4. Core implementation

Core has no `@Retry`. Its job is to fail fast and precisely:

```java
@ApplicationScoped
public class PublishService {

    @Transactional
    public Prompt publish(UUID promptId, int versionNumber, UUID categoryId, CallerContext caller) {
        Prompt p = repo.findOwnedForUpdate(promptId, caller.userId())
                .orElseThrow(NotFound::new);
        if (p.deletedAt != null) throw new Conflict(ProblemCode.PROMPT_DELETED);
        Category c = categories.findActive(categoryId)
                .orElseThrow(() -> new ValidationFailed(ProblemCode.CATEGORY_REQUIRED));
        PromptVersion v = versions.find(promptId, versionNumber).orElseThrow(NotFound::new);
        p.publish(v, c);                // domain method enforces invariants
        searchProjector.rebuild(p);     // same transaction: index never lags a commit
        return p;
    }
}
```

- One transaction per use case. If search projection fails, the publish rolls back — correct:
  a published prompt that cannot be found is worse than a failed publish.
- No retry inside core. If MySQL is down, return `UNAVAILABLE` quickly; the edge breaker opens and
  stops hammering.
- `PESSIMISTIC_WRITE` lock acquisition has a timeout; configure a lock wait timeout (MySQL
  `innodb_lock_wait_timeout`, default 50 s → set 5 s for this workload) so a stuck transaction
  cannot hold a request thread for a minute.

## 5. Database resilience

| Concern | Setting |
|---|---|
| Pool exhaustion | `max-size=15`, `acquisition-timeout=5S` → fail fast instead of queueing forever |
| Stale connections | `max-lifetime=30M`, `idle-removal-interval=5M` (below MySQL's `wait_timeout`) |
| Lock storms | `innodb_lock_wait_timeout=5` for the app user |
| Long queries | no query should exceed 200 ms at this scale; log a WARN above that |
| Startup ordering | compose `depends_on: mysql: condition: service_healthy` — the app still retries the pool, but Flyway failing on a cold DB is a bad first impression |

Compose healthcheck for MySQL:

```yaml
healthcheck:
  test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "-p$$MYSQL_ROOT_PASSWORD"]
  interval: 5s
  timeout: 3s
  retries: 20
  start_period: 30s
```

## 6. Keycloak-down behaviour

Quarkus caches the JWKS. With `accessTokenLifespan=300`:

| Elapsed since Keycloak died | Effect |
|---|---|
| < cache TTL | Tokens issued before the outage still validate; reads and writes work |
| > cache TTL, cache still in memory | New tokens cannot be verified; **all** requests 401/503 |
| Keycloak restarted with same keys | Cache refills, traffic recovers without intervention |

Mitigations:

1. Keep `quarkus.oidc.connection-timeout` low (2 s) so a dead IdP does not hold request threads.
2. Do **not** disable issuer/audience validation to "make it work during the outage". That converts
   an availability incident into a security incident.
3. Health endpoint must report OIDC DOWN (`quarkus.oidc.health.enabled=true`) so NPM/compose
   healthchecks can react.
4. Accept the product consequence: with ADR-10 (everything authenticated), an IdP outage is a full
   outage. That is the price of "no anonymous reads"; it is recorded in `00 §9`.

## 7. Degradation matrix (what the user sees)

| Failure | Endpoint affected | Response | Log |
|---|---|---|---|
| core down | all | 503 `UPSTREAM_UNAVAILABLE` | `event=request.failed rpc=…` |
| MySQL down | all | 503 `DATABASE_UNAVAILABLE` | `event=db.unavailable` at ERROR |
| Keycloak down, cache cold | all | 401 or 503 | `event=auth.unavailable` at ERROR |
| Slow core (>2 s) | reads | 504 `UPSTREAM_TIMEOUT` (`@Timeout` → 503/504 mapping) | WARN with duration |
| Circuit open | reads | 503 immediately, `Retry-After: 10` | `event=circuit.open` once per transition |
| Search index rebuild fails | publish | transaction rolls back, 500/503 | ERROR with promptId |
| Duplicate publish | publish | 200 idempotent | DEBUG |
| Stale `expectedUpdatedAt` | PATCH | 409 `STALE_WRITE` | INFO |
| Keycloak Admin API down | registration only | 503 `IDENTITY_PROVIDER_UNAVAILABLE`; normal traffic unaffected | `event=request.failed rpc=AccountService` |
| Admin hides a prompt twice | hide | 200 idempotent, one audit row per state change | INFO |
| Export interrupted | export | client retries; no partial server state | WARN with prompt count |
| Duplicate registration | register | 409 `USERNAME_TAKEN`, no second account | INFO |

## 8. Anti-patterns explicitly banned

1. **Retry on POST** — duplicates data; there is no idempotency key in v1.
2. **Fallback that returns empty/stale data as if fresh** — hides outages, erodes trust in search.
3. **Catch `Exception` around a transaction and continue** — leaves the persistence context dirty.
4. **Timeout of 0 / infinite** on any outbound call.
5. **`@Retry` on a method that is also `@Transactional`** — the retry runs outside the transaction
   boundary and reapplies a partially failed unit of work.
6. **Circuit breaker on writes** without an explicit "open" path — an open breaker on publish means
   the UI must show "try again in 10 s", not spin.
7. **Retry storms between services** — edge retries 1×, core never retries. Amplification is
   `N_hops`, keep it at 1.

## 9. Verification (manual, since there are no metrics)

```bash
# core down
docker compose stop core
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $T" localhost:8080/api/v1/gallery
# expect 503, body code=UPSTREAM_UNAVAILABLE, and exactly one retry + one circuit transition in logs

# db down (core stays up)
docker compose stop mysql
# expect 503 DATABASE_UNAVAILABLE within ~5s, not a 60s hang

# recovery
docker compose start mysql core
# expect normal 200 within the breaker delay (10s)
```

Add these three commands to `09-deployment.md`'s smoke-test checklist; they are the only resilience
tests you can run without a metrics stack.
