# 04 — Data & Persistence

**Depends on:** ADR-02 (one schema), ADR-16 (UUID v7), ADR-17 (soft delete), ADR-03 (modules),
ADR-24 (audit rows are transactional), ADR-26 (collection parent reserved).

---

## 1. Physical layout

```
mysql:8.4 (container `mysql`, volume `mysql-data`)
├── promptlib        ← the application schema (all tables)
└── keycloak         ← Keycloak's own schema, never touched by app code
```

One schema, one Flyway history table (`flyway_schema_history`), one owner: **`core`**.
The edge has **no datasource** at all — if it ever needs one, that is a design smell.

Why MySQL 8.4: it is an LTS line, supports `CHECK` constraints (8.0.16+), functional indexes,
`JSON`, and `FULLTEXT` on InnoDB. Everything in `01-domain-model.md §5` relies on those.

## 2. Connection configuration (`core`)

```properties
quarkus.datasource.db-kind=mysql
quarkus.datasource.username=${DB_USER}
quarkus.datasource.password=${DB_PASSWORD}
quarkus.datasource.jdbc.url=jdbc:mysql://mysql:3306/promptlib?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&characterEncoding=utf8
quarkus.datasource.jdbc.min-size=2
quarkus.datasource.jdbc.max-size=15
quarkus.datasource.jdbc.acquisition-timeout=5S
quarkus.datasource.jdbc.idle-removal-interval=5M
quarkus.datasource.jdbc.max-lifetime=30M
quarkus.hibernate-orm.jdbc.timezone=UTC
quarkus.hibernate-orm.log.sql=false          # true only while debugging a query
```

Notes:

- `connectionTimeZone=UTC` + `quarkus.hibernate-orm.jdbc.timezone=UTC` prevents the classic
  "timestamps shift by 3 hours" bug when the host or container TZ is not UTC. All timestamps are
  `Instant` in Java and `DATETIME(6)` in MySQL.
- `allowPublicKeyRetrieval=true` is required for `caching_sha2_password` without TLS. It is safe
  here only because the connection stays inside the compose network. If TLS is ever enabled,
  remove it.
- Pool sizing: 15 is generous for ~10 users; the edge has no pool, Keycloak has its own.

## 3. Flyway

```properties
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=db/migration
quarkus.flyway.baseline-on-migrate=false       # schema starts empty; never baseline silently
quarkus.flyway.validate-on-migrate=true
quarkus.flyway.clean-at-start=false            # true ONLY in %test
%test.quarkus.flyway.clean-at-start=true
quarkus.hibernate-orm.schema-management.strategy=validate   # alias: database.generation=validate
```

Rules:

1. **Migrations are forward-only.** No `undo` migrations; a mistake is fixed by a new `V__` file.
2. **Never edit a migration that has run** on your host. `validate-on-migrate` will catch you.
3. Naming: `V<n>__<snake_case_description>.sql`, e.g. `V3__add_prompt_visibility_team.sql`.
4. Seed taxonomy in `V2__seed_categories.sql` with stable, human-chosen UUIDs (so re-seeding in
   test containers yields identical ids).
5. `schema-management.strategy=validate` in **all** profiles: Hibernate must agree with Flyway's
   DDL at startup. This turns the `BINARY(16)` vs `CHAR(36)` mismatch into a startup failure
   instead of a runtime `DataException` at 2 a.m.
6. Flyway runs before Hibernate ORM initializes, so validation sees the migrated schema.

### 3.1 Migration plan

| Version | Contents |
|---|---|
| `V1__baseline.sql` | **All 12 tables**: `app_user`, `category`, `prompt`, `prompt_version`, `prompt_message`, `collection`, `collection_prompt`, `tag`, `prompt_tag`, `favorite`, `prompt_search_document`, `admin_audit` — with moderation, colours, `parent_id`, `is_default`, FKs and indexes |
| `V2__seed_categories.sql` | Coding, Writing, Marketing, Education, Research, Productivity, Other (stable UUIDs) |

`schema` and `data` are separated on purpose: `V1` creates the shape, `V2` inserts the taxonomy. If
they were one file, re-seeding a test database and reasoning about a failed migration would be harder
for no benefit.

The schema starts empty, so there are no incremental `V3…` files. Everything we discussed — moderation,
audit, search, collections, colours — is in the baseline because there is no production data to
preserve. Resist adding `V3__misc`; the first post-release change opens that series.

**Two artifacts, one authority:**

| File | Role |
|---|---|
| `Database/V1__baseline.sql` | Authoritative DDL, reviewed and copied into `core/src/main/resources/db/migration/` |
| `Database/V1__baseline.dbml` | Diagram view for humans; generated semantics, never edited first |

When either changes, regenerate and diff the other. The DBML parser catches missing tables and broken
refs; it cannot catch a missing `CHECK`, so those need an eye on the SQL.

## 4. UUID v7 identifiers

```java
// shared/Ids.java
public final class Ids {
    public static UUID newId() { return UuidCreator.getTimeOrderedEpoch(); }  // RFC 9562 v7
}
```

- Library: `com.github.f4b6a3:uuid-creator` (small, no transitive baggage). Java 21 has no built-in v7.
- Stored as `BINARY(16)`. Hibernate 6 maps `UUID` to `binary(16)` on MySQL; the DDL above matches.
- The edge receives/emits **strings**; conversion happens once, at the edge mapper boundary.
- Time-ordered ids give index locality similar to auto-increment without leaking row counts
  (`/prompts/1` tells the internet how many prompts exist; `/prompts/0193a1f2…` does not).
- Never generate ids in the database (`UUID_TO_BIN(UUID())` produces v1/v4 and breaks ordering).

Panache entity:

```java
@Entity
@Table(name = "prompt")
public class Prompt extends PanacheEntityBase {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    public AppUser owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    public Visibility visibility = Visibility.PRIVATE;

    // ... timestamps, counters, lineage

    @PrePersist
    void onCreate() {
        if (id == null) id = Ids.newId();
        createdAt = updatedAt = Instant.now();
    }
}
```

## 5. Panache patterns

**Use `PanacheEntityBase` with explicit `@Id`, not `PanacheEntity`.** `PanacheEntity` gives a
`Long id`; we need UUIDs. This is the single most common mistake when copying Panache examples.

| Pattern | Use |
|---|---|
| Active Record (`Prompt.listAll()`) | Only inside `persistence` classes and short read paths |
| Repository (`PromptRepository`) | Default for anything with a rule attached (publish, fork, delete) |
| `PanacheQuery` + `page(Page.ofSize())` | Avoid; we use keyset cursors, not offset pages |
| Projections (`select new …`) | Gallery browse and search results — never load full aggregates for lists |

Rules:

1. **No `@Transactional` on gRPC service methods.** Transaction boundaries live in a
   `@Transactional` application-service method inside `domain.X` (one per use case). A gRPC method
   calls exactly one such method.
2. Entities never leave `core` — no entity reaches the proto mapper with lazy collections
   uninitialised. Mappers receive explicitly fetched snapshots (or `@Transactional`-scoped DTOs).
3. `LazyInitializationException` is a design failure, not something to fix with `JOIN FETCH`
   sprinkled everywhere. List endpoints use projections; detail endpoints fetch what they map.
4. Soft delete: every repository method that lists/finds includes `deleted_at IS NULL`
   (`@Query` with a named parameter, or a Hibernate `@Filter` enabled by an interceptor).

## 6. Concurrency: version numbering and publish

`CreateVersion` must produce `max(version_number) + 1` without duplicates under concurrent calls.

```java
@Transactional
public PromptVersion createVersion(UUID promptId, UUID fromVersionId, CallerContext caller) {
    Prompt prompt = promptRepository.findOwnedForUpdate(promptId, caller.userId()); // SELECT ... FOR UPDATE
    int next = promptVersionRepository.nextNumber(promptId);                        // max + 1
    PromptVersion v = copyOf(fromVersionId, prompt, next, caller);
    promptVersionRepository.persist(v);
    prompt.latestVersionId = v.id;
    return v;
}
```

`findOwnedForUpdate` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)`. Alternative (`UPDATE ... SET
counter = counter + 1`) would need a counter column; the row lock is simpler and the prompt row is
already the aggregate root. At ~10 users this is free.

`PublishVersion` is the other locked path: lock the prompt row, validate category is active, set
`publishedVersionId` + `visibility`, bump `updatedAt`/`search document`.

Forking increments `copy_count` with a direct `UPDATE prompt SET copy_count = copy_count + 1`
(atomic in InnoDB, no read-modify-write race).

## 7. JSON columns

`prompt_version.variables_json` is `JSON` (not `TEXT`):

- MySQL validates JSON syntax on write → malformed schema cannot be persisted.
- Panache mapping: a JPA `AttributeConverter<List<VariableDef>, String>` with Jackson, plus
  `@JdbcTypeCode(SqlTypes.JSON)` so Hibernate 6 handles the type properly.
- Never query *inside* JSON in v1. If a query needs a variable, it is a projection bug; promote the
  field to a column then.

## 8. Full-text search (v1)

`prompt_search_document` is authoritative for gallery search. `prompt_message.ft_message_content`
is kept for future per-message search and is not queried in v1 — do not be surprised by its
existence.

Rebuild points:

| Trigger | Action |
|---|---|
| Version created | Rebuild document for the prompt (latest content) |
| Version published | Rebuild with the published version's content |
| Prompt title/description/category/visibility changed | Rebuild metadata columns |
| Prompt soft-deleted | Set `visibility = 'DELETED'` (or delete the row) so search cannot return it |

Query (MySQL boolean mode):

```sql
SELECT p.* FROM prompt p
JOIN prompt_search_document d ON d.prompt_id = p.id
WHERE p.visibility = 'PUBLIC' AND p.deleted_at IS NULL
  AND MATCH(d.title, d.description, d.body_text) AGAINST (:q IN BOOLEAN MODE)
ORDER BY p.created_at DESC, p.id DESC
LIMIT :limit
```

Operational notes:

- Default `innodb_ft_min_token_size = 3`: two-letter queries silently return nothing. Document it,
  or set it to 2 in `my.cnf` **before** the first index build (changing it later requires a rebuild).
- No stemming in MySQL FULLTEXT: "running" does not match "run". Acceptable for v1; this is exactly
  why Qdrant arrives later.
- Stopwords are filtered; do not promise exact-match search on "the", "and", "for".
- Escape boolean operators (`+`, `-`, `*`, `"`) from user input or the query becomes a syntax error.

## 9. User mirror (Keycloak → `app_user`)

```
first authenticated request
  → edge knows sub/email/username from token
  → call AccountService.EnsureUser(sub, email, username)
  → core: INSERT ... ON DUPLICATE KEY UPDATE last_seen_at = ?   (unique on keycloak_sub)
  → returns app_user.id, used as x-user-id for the rest of the request
```

- The upsert is idempotent; concurrent first requests cannot duplicate rows (`uk_user_sub`).
- `email`/`username` are refreshed on every login (Keycloak is the source of truth).
- `display_name` is app-owned: never overwritten from the token after creation.
- Deleting a Keycloak user does not delete `app_user` in v1; prompts would become orphaned.

## 10. Limits, export, and audit writes

### 10.1 Content limits are validated before persistence

| Check | Where | Code |
|---|---|---|
| Version total ≤ 1 MB | Edge validation **and** core use case | `CONTENT_TOO_LARGE` |
| Message content ≤ 256 KB | Same | `CONTENT_TOO_LARGE` |
| Messages ≤ 100 | Same | `TOO_MANY_MESSAGES` |
| Variables ≤ 100 | Same | `TOO_MANY_VARIABLES` |
| Title ≤ 200, description ≤ 2,000, change note ≤ 500 | Edge (Hibernate Validator) + DB column size | 422 |

Validation happens at the edge for fast feedback and again in the use case, because the edge is a
convenience boundary, not a security boundary (ADR-15). A 1 MB payload is small enough that reading
it fully before rejecting is acceptable; no streaming request bodies in v1.

### 10.2 Export runs in one read transaction, streams the response

```java
@Transactional(readOnly = true)   // Repeatable read: one consistent snapshot
public void writeExport(OutputStream out, CallerContext caller) {
    json.writeStartObject();
    json.write("schemaVersion", EXPORT_SCHEMA_VERSION);
    // prompts + versions + collections + tags + favorites, streamed
}
```

Rules:

- Read-only transaction, default isolation (MySQL `REPEATABLE READ`) → point-in-time consistency
  without locking writers.
- Paginate internally in chunks of 100 prompts and write directly to the response stream; never build
  a `List<Prompt>` of everything.
- Exclude soft-deleted prompts unless `includeDeleted=true`; exclude every other user's row.
- The export must not include `hidden_by`, audit rows, or token material.
- If the connection drops mid-export, that is acceptable: the client retries. No partial file is
  stored server-side.

### 10.3 Audit rows are transactional with the action

```java
@Transactional
public void hide(UUID promptId, String reason, CallerContext admin) {
    Prompt p = prompts.findPublicForUpdate(promptId).orElseThrow(NotFound::new);
    p.hide(reason, admin.userId());              // domain guard: reason length, state
    audit.record(admin, "PROMPT_HIDDEN", "PROMPT", promptId, reason);
}
```

If `audit.record` fails, the hide rolls back. That is intentional: an unaudited admin action must not
exist. There is no "best effort" audit in this design.

### 10.4 Prompt delete is a multi-table operation

Soft-deleting a prompt is not a single `UPDATE`. In one transaction:

```java
@Transactional
public void delete(UUID promptId, CallerContext caller) {
    Prompt p = repo.findOwnedForUpdate(promptId, caller.userId()).orElseThrow(NotFound::new);
    if (p.visibility == PUBLIC) throw new Conflict(ProblemCode.PROMPT_PUBLISHED);
    if (p.deletedAt != null) return;                       // idempotent
    p.deletedAt = Instant.now();
    promptTagRepository.deleteByPrompt(promptId);
    collectionPromptRepository.deleteByPrompt(promptId);
    favoriteRepository.deleteByPrompt(promptId);
    searchProjector.delete(promptId);
}
```

Rationale: tags and collections are reusable resources. Leaving memberships behind makes a
soft-deleted prompt keep a tag "in use" and a collection non-empty forever, so the user can never
delete either — and the guards in `01 §3.4` become unsatisfiable. Versions and messages stay.

Same pattern for hard-deleting a tag or collection: check references inside the transaction, then
delete, letting the FK `RESTRICT` be the backstop rather than the primary check.

## 11. What will bite us (and the guard for each)

| Risk | Guard |
|---|---|
| Flyway/Hibernate type mismatch (`BINARY(16)` vs `CHAR(36)`) | `schema-management.strategy=validate` at startup + in `%test` |
| Migration edited after running | `validate-on-migrate=true`; CI check once CI exists |
| `LazyInitializationException` in list endpoints | Projections for lists; `@Transactional` on use-case methods |
| Soft-deleted rows leaking into lists | Named queries with `deleted_at IS NULL`; test asserts 0 rows after delete |
| Duplicate version numbers | `PESSIMISTIC_WRITE` on prompt row + unique key as backstop |
| Timestamp drift | UTC everywhere: JDBC url, Hibernate, `Instant`, `DATETIME(6)` |
| `COUNT(*)` creeping into gallery | Cursor envelope has no `total` field; review any new `count()` call |
| Search returning private prompts | Search joins `prompt.visibility`; test with a private row present |
