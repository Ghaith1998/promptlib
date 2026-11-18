# 06 — Logging and Log Rotation

**Depends on:** requirement #7 (logs + rotation), interview decision: **local file logs only**, no
Loki/metrics/tracing in v1.

Consequence: **logs are the only observability**. That makes correlation IDs and consistent
structure non-negotiable, not nice-to-haves.

---

## 1. Configuration (both `edge` and `core`)

```properties
# ---- console (docker logs) ----
quarkus.log.console.enable=true
quarkus.log.console.format=%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p [%c{2.}] (%t) rid=%X{requestId} %s%e%n

# ---- file with rotation ----
quarkus.log.file.enable=true
quarkus.log.file.path=/var/log/promptlib/${quarkus.application.name}.log
quarkus.log.file.format=%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p [%c{2.}] (%t) rid=%X{requestId} uid=%X{userId} %s%e%n
quarkus.log.file.rotation.enabled=true
quarkus.log.file.rotation.max-file-size=10M
quarkus.log.file.rotation.max-backup-index=10
quarkus.log.file.rotation.rotate-on-boot=true
quarkus.log.file.rotation.file-suffix=.yyyy-MM-dd
quarkus.log.file.async.enabled=true
quarkus.log.file.async.queue-length=1024
quarkus.log.file.async.overflow=block          # never silently drop audit-relevant lines

# ---- levels ----
quarkus.log.level=INFO
quarkus.log.category."com.promptlib".level=DEBUG
quarkus.log.category."io.quarkus.oidc".level=INFO
quarkus.log.category."org.hibernate.SQL".level=WARN
%dev.quarkus.log.category."com.promptlib".level=DEBUG
%prod.quarkus.log.category."com.promptlib".level=INFO
%test.quarkus.log.file.enable=false            # tests must not write into the volume
```

Two sinks on purpose:

- **stdout** so `docker compose logs -f edge` works and Docker's json-file driver can cap it;
- **file** because the requirement asks for a file with rotation, and it survives `docker logs`
  truncation.

### 1.1 Rotation semantics

| Property | Value | Meaning |
|---|---|---|
| `max-file-size` | `10M` | Rotate **after** the record that crosses 10 MB (not a hard cap) |
| `max-backup-index` | `10` | Keep 10 rotated files; older ones deleted |
| `file-suffix` | `.yyyy-MM-dd` | Suffix pattern for the rotated file name |
| `rotate-on-boot` | `true` | Fresh file per container start → easy "what happened in this run" |

Resulting files: `core.log`, `core.log.2026-11-18.1`, … Keep the total bounded: 10 MB × 11 ≈ 110 MB
per service. On a home-lab host with two services that is acceptable; revisit if the volume grows.

`async.enabled=true` + `overflow=block` is the right trade: blocking under pressure is vastly
preferable to losing the line that explains an outage. If log volume ever makes blocking visible,
fix the log volume — do not switch to `discard`.

> If you would rather let Docker own rotation, remove the file handler and set
> `logging: driver: json-file, options: {max-size: 10m, max-file: "10"}`. Do not run both
> unbounded — that is how a 40 GB `-json.log` happens.

## 2. Correlation IDs

One id per client request, threaded through everything:

```
Flutter            X-Request-Id: <optional uuid>
   │
   ▼
edge filter        requestId = client value (validated UUID) or new UUID v7
                   MDC.put("requestId", requestId)
   │
   ▼ gRPC metadata  x-request-id: <requestId>
core interceptor   MDC.put("requestId", metadata value)
                   MDC.put("userId", x-user-id)
   │
   ▼ error body     problem+json includes "requestId"
```

Rules:

- Accept a client-supplied `X-Request-Id` only if it parses as a UUID; otherwise generate one.
- Never accept more than 64 chars; never log an unvalidated header verbatim.
- The edge always returns `X-Request-Id` in the response, even on success. Flutter should show it in
  its error dialog — that single field turns "it broke" into a `grep`.
- Clear MDC in a `finally` block. Thread pools reuse threads; leaked MDC is worse than no MDC.

Implementation sketch (edge):

```java
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {
    @Context HttpServerRequest request;

    @Override public void filter(ContainerRequestContext ctx) {
        String incoming = ctx.getHeaderString("X-Request-Id");
        String id = Uuids.isValid(incoming) ? incoming : Ids.newId().toString();
        MDC.put("requestId", id);
        ctx.setProperty("requestId", id);
    }

    @Override public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        res.getHeaders().putSingle("X-Request-Id", req.getProperty("requestId"));
        MDC.remove("requestId");
    }
}
```

Core interceptor: read `x-request-id`, put MDC, call `listener.onComplete()` → remove. Do not rely on
`@ApplicationScoped` state to carry it.

## 3. What to log, per layer

| Layer | INFO | DEBUG | Never |
|---|---|---|---|
| edge | method, path template, status, duration ms, requestId, userId | resolved app_user id, gRPC deadline, payload size | Authorization header, raw JWT, query string values that may contain prompt text |
| core gRPC | RPC name, status code, duration, requestId, userId | entity ids, version numbers, category slug | prompt content, variable values, emails |
| domain rules | publish/unpublish decisions, fork, soft delete, category change | invariant checks | full entity dumps |
| admin | hide/unhide, category changes — **plus** the `admin_audit` row | target ids, reason | private prompt content (never) |
| registration | `user.registered` with username (never the password) | Keycloak user id | password, request body |
| persistence | migration start/finish, pool exhaustion warnings | slow query (>200 ms) with **parameter-free** SQL | bound parameter values |

Structured event lines (one line, key=value, stable keys) for the things you will actually grep:

```
INFO  [c.p.a.PublishService] rid=… uid=… event=prompt.published promptId=… version=3 category=coding
INFO  [c.p.a.ForkService]    rid=… uid=… event=prompt.forked sourceId=… newId=… copyCount=42
WARN  [c.p.e.ProblemMapper]  rid=… uid=… event=request.failed code=UPSTREAM_UNAVAILABLE status=503 rpc=GalleryService.Browse
```

This is the cheap substitute for metrics: a fixed vocabulary of `event=` keys. Keep it small
(`prompt.created`, `prompt.published`, `prompt.unpublished`, `prompt.forked`, `prompt.deleted`,
`category.changed`, `admin.prompt.hidden`, `admin.prompt.unhidden`, `user.registered`,
`export.generated`, `request.failed`).

Admin events appear **twice by design**: a log line for operators and an `admin_audit` row for
accountability. The log line carries the `requestId`; the audit row carries actor, action, target and
reason. Do not attempt to reconstruct the audit trail from logs — they rotate.

## 4. PII and secrets

- Prompt **content is user data**: never log message bodies, variable values, or search queries at
  INFO. At DEBUG, log only lengths/hashes.
- Emails: allowed in the `app_user` domain events at DEBUG; never in the request log line.
- Tokens, `Authorization`, DB passwords, `KC_EDGE_SECRET`, `KC_REGISTER_CLIENT_SECRET`: never logged,
  ever. If a framework logs headers at TRACE, keep TRACE off in prod.
- **Registration passwords must never be logged, traced, or included in an exception message.** The
  request body of `POST /auth/register` is the one place in the system where a plaintext password
  exists server-side; treat any logging of that DTO as a security defect.
- Exception messages coming from the DB can contain SQL fragments; the problem mapper logs the
  exception class + scrubbed message, not the full stack at INFO (stack at ERROR is fine).

## 5. Log level policy

| Profile | Root | com.promptlib | Hibernate SQL | OIDC |
|---|---|---|---|---|
| dev | INFO | DEBUG | false | INFO |
| test | WARN | INFO | false | WARN |
| prod | INFO | INFO | false | INFO |

Never ship `DEBUG` for `com.promptlib` in prod "temporarily": with async file logging it fills the
disk quietly, and rotation will happily delete the evidence you needed.

## 6. Verification checklist (do this once, at scaffold time)

1. `docker compose exec edge sh -c 'tail -n 5 /var/log/promptlib/edge.log'` shows the request you
   just made, with `rid=`.
2. `grep <rid> /var/log/promptlib/*.log` across both services returns the edge line and the core
   line — proving propagation.
3. A forced 500 returns `requestId` in the body and that same id appears in the log.
4. Rotate: `docker compose exec core sh -c 'dd if=/dev/zero of=/var/log/promptlib/core.log bs=1M count=11'`
   then trigger a log line → a `.1` file appears and old backups are pruned at 10.
5. `docker compose restart core` creates a fresh `core.log` (rotate-on-boot) while `.1` keeps the
   previous run.

## 7. Future (documented, not built)

- **Loki shipping**: add Promtail/Alloy sidecar, parse `rid` as a label. No code changes needed —
  the console format is already line-structured.
- **JSON logs**: `quarkus-logging-json` for machine parsing. Do it when Loki arrives, not before;
  JSON logs in a terminal are unreadable and hide problems during development.
- **Metrics/traces**: `11-roadmap.md`. Once OTel is in, `traceId` replaces `requestId` as the join
  key; keep `requestId` anyway for client-facing support.
