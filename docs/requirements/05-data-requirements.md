# 05 — Data Requirements

Schema DDL lives in `../architecture/01-domain-model.md §5` and
`04-data-persistence.md`. This document states **what data must exist, what it must guarantee, and
what limits apply** — plus the additions this requirements round introduced.

---

## 1. Entities and ownership

| Entity | Owner | Contains user content? | Deletable |
|---|---|---|---|
| `app_user` | system | email/username | No (v1) |
| `category` | system (admin) | no | Deactivate only |
| `prompt` | user | metadata only | Soft delete; refused while published |
| `prompt_version` | user | **yes** (messages, variables) | Soft delete, restricted |
| `prompt_message` | user | **yes** | With its version |
| `collection` | user | names only | Hard delete, empty only; default never |
| `collection_prompt` | user | no | Membership only |
| `tag` | user | name only | Hard delete, only when unattached |
| `prompt_tag` | user | no | Membership only |
| `favorite` | user | no | Hard delete |
| `prompt_search_document` | system | derived copy of content | Rebuilt/deleted with prompt |
| `admin_audit` | system | no prompt content | Append-only, never deleted |

**Rule:** every user-content table carries an owner path `owner_id` (directly or via `prompt`), and
every query filters by it. There is no table where "who owns this" is ambiguous.

## 2. Content limits (VER-10, LIB-01)

| Limit | Value | Enforced at | Error |
|---|---|---|---|
| Message content, one message | 256 KB | API validation | 422 `CONTENT_TOO_LARGE` |
| Version total (all messages) | 1 MB | API validation | 422 `CONTENT_TOO_LARGE` |
| Messages per version | 100 | API validation | 422 `TOO_MANY_MESSAGES` |
| Variables per version | 100 | API validation | 422 `TOO_MANY_VARIABLES` |
| Prompt title | 200 chars | API + DB | 422 |
| Prompt description | 2,000 chars | API + DB | 422 |
| Version change note | 500 chars | API + DB | 422 |
| Collection name | 120 chars | API + DB | 422 |
| Tag name | 50 chars | API + DB | 422 |
| Colour (tag, collection, category) | `#RRGGBB` or null | API + DB `CHECK` | 422 |
| Visibility reason (hide) | 500 chars, min 3 | API + DB | 422 |
| Versions per prompt | **unlimited** | — | — |
| Prompts per user | **unlimited** | — | — |
| Collections per user | **unlimited** | — | — |

Why these numbers: 1 MB comfortably holds a long system prompt plus few-shot examples; 100 messages
is far beyond any realistic single prompt; unlimited versions is safe because each is bounded
individually and storage is cheap. They exist to bound worst-case memory and DB row size, not to
police usage.

## 3. Required additions from this requirements round

### 3.1 Moderation state (PUB-06..09)

```sql
ALTER TABLE prompt
  ADD COLUMN moderation_state ENUM('VISIBLE','HIDDEN') NOT NULL DEFAULT 'VISIBLE',
  ADD COLUMN hidden_reason   VARCHAR(500) NULL,
  ADD COLUMN hidden_at       DATETIME(6)  NULL,
  ADD COLUMN hidden_by       BINARY(16)   NULL,
  ADD CONSTRAINT ck_prompt_hidden CHECK (
      moderation_state = 'VISIBLE'
      OR (hidden_reason IS NOT NULL AND hidden_at IS NOT NULL AND hidden_by IS NOT NULL));
```

Gallery, search and `popular` all filter `moderation_state = 'VISIBLE'` **in addition to**
`visibility = 'PUBLIC' AND deleted_at IS NULL`. Three predicates, one place (a single query fragment),
or one of them will be forgotten.

### 3.2 Collection nesting reservation (COL-05)

```sql
ALTER TABLE collection ADD COLUMN parent_id BINARY(16) NULL,
  ADD CONSTRAINT fk_collection_parent FOREIGN KEY (parent_id) REFERENCES collection(id);
```

Nullable, foreign-keyed, **not exposed by the v1 API**. Adding it now costs one column and removes a
painful migration later.

### 3.3 Admin audit (ADM-08)

```sql
CREATE TABLE admin_audit (
  id          BINARY(16)   NOT NULL PRIMARY KEY,
  actor_id    BINARY(16)   NOT NULL,
  action      VARCHAR(64)  NOT NULL,     -- PROMPT_HIDDEN, PROMPT_UNHIDDEN, ROLE_GRANTED,
                                         -- ROLE_REVOKED, USER_DISABLED, USER_ENABLED,
                                         -- CATEGORY_CREATED, CATEGORY_UPDATED, USER_CREATED
  target_type VARCHAR(32)  NOT NULL,     -- PROMPT, USER, CATEGORY
  target_id   BINARY(16)   NOT NULL,
  reason      VARCHAR(500) NULL,
  request_id  VARCHAR(64)  NULL,
  created_at  DATETIME(6)  NOT NULL,
  KEY idx_audit_created (created_at),
  KEY idx_audit_target (target_type, target_id),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Append-only: no update, no delete, no API to modify. Written in the **same transaction** as the admin
action — an action without an audit row is a bug, and a rolled-back action must not leave one.

### 3.4 Search document must carry moderation state

`prompt_search_document` gains `moderation_state` (or the search query joins `prompt` and filters
there — one join is acceptable at this scale; keep the join, avoid duplicated state).

### 3.5 Default collection

```sql
ALTER TABLE collection
  ADD COLUMN is_default     BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN default_marker CHAR(1) NULL,          -- 'Y' for the default, NULL otherwise
  ADD CONSTRAINT ck_collection_default CHECK (
      (is_default = FALSE AND default_marker IS NULL) OR
      (is_default = TRUE  AND default_marker = 'Y')),
  ADD UNIQUE KEY uk_collection_default (owner_id, default_marker);
```

`default_marker` exists because MySQL has no partial unique index: `UNIQUE(owner_id, is_default)`
would allow only one `false` row per user. With a nullable marker, the unique index constrains only
the default row, and any number of normal collections can share `NULL`.

### 3.6 Colours

```sql
-- tag, collection, category
color CHAR(7) NULL,   -- '#RRGGBB'
CHECK (color IS NULL OR color REGEXP '^#[0-9A-Fa-f]{6}$')
```

Nullable on purpose: colour is decoration, not identity. Validation happens in the API for a clean
422 and in the database as a backstop.

## 4. Export format (EXP-01)

One JSON document, UTF-8, pretty-printed, `Content-Disposition: attachment`.

```json
{
  "schemaVersion": "1.0",
  "exportedAt": "2026-11-18T10:15:30Z",
  "exportedBy": { "id": "0193…", "username": "ghaith" },
  "categories": [ { "slug": "coding", "name": "Coding" } ],
  "collections": [
    { "id": "0193…", "name": "Code review", "description": null, "sortOrder": 0,
      "color": null, "isDefault": false, "promptIds": ["0193…"] }
  ],
  "tags": [ { "id": "0193…", "name": "review", "color": "#4F46E5" } ],
  "favorites": [ { "promptId": "0193…", "createdAt": "…" } ],
  "prompts": [
    {
      "id": "0193…",
      "title": "Review checklist",
      "description": "…",
      "visibility": "PUBLIC",
      "categorySlug": "coding",
      "forkOfPromptId": null,
      "copyCount": 4,
      "createdAt": "…",
      "updatedAt": "…",
      "latestVersion": 3,
      "publishedVersion": 2,
      "tags": ["review"],
      "collectionIds": ["0193…"],
      "versions": [
        {
          "versionNumber": 2,
          "changeNote": "Added accessibility section",
          "createdAt": "…",
          "variables": [
            { "name": "language", "type": "enum", "required": true,
              "default": "typescript", "enumValues": ["typescript", "java"] }
          ],
          "messages": [
            { "role": "SYSTEM", "position": 0, "content": "You are a senior reviewer." },
            { "role": "USER", "position": 1, "content": "Review this {{language}} code:\n\n{{code}}" }
          ]
        }
      ]
    }
  ]
}
```

Rules:

- `schemaVersion` is mandatory; a future importer switches on it.
- Only the caller's prompts; only versions of those prompts; no other user's data.
- Soft-deleted prompts are **excluded** by default; a `?includeDeleted=true` flag may include them
  with `deletedAt` set (S priority).
- Secrets, tokens, ids of other users, and internal fields (`hidden_by`) are never exported.
- Markdown export (EXP-02) is a rendered view of one prompt: title, metadata, then each message under
  a `### SYSTEM` / `### USER` heading, variables listed at the end.

## 5. Retention and deletion

| Data | Retention | Mechanism |
|---|---|---|
| Active prompts/versions | Indefinite | — |
| Deleted prompts | **Indefinite in v1** (no purge job) | `deleted_at`; memberships/favorites/search rows removed immediately |
| Tags / collections | **Hard deleted** when unused | Row removed |
| Audit rows | Indefinite (append-only) | — |
| Log files | 10 backups × 10 MB per service, then pruned | Quarkus file rotation |
| Keycloak sessions | Per realm config (idle 30 min, max 10 h) | Keycloak |
| Disabled user's content | Retained forever | No cascade |

**Known consequences:**

- "Delete" is final for the user (no restore), yet the bytes remain until a future purge job — anyone
  with database access can read deleted prompts. Recorded in `NFR-PRIV6`.
- Tags and collections are genuinely erased, so a mistaken tag delete is unrecoverable with no
  backups. The `TAG_IN_USE` / `COLLECTION_NOT_EMPTY` guards exist to make that mistake hard, not
  impossible.

## 6. Account and identity data

| Field | Source | Mutable by user | Notes |
|---|---|---|---|
| `app_user.id` | app (UUID v7) | no | FK target everywhere; created on first authenticated request, not at registration |
| `keycloak_sub` | Keycloak | no | Stable identity link |
| `username` | Keycloak | no (in app) | Refreshed each login |
| `email` | Keycloak | no (in app) | Refreshed each login |
| `display_name` | app | yes | Never overwritten from token |
| roles | Keycloak token | no | Not stored locally; no drift |
| `disabled` | Keycloak | admin only | Effective at next token |

No account deletion in v1 (ADM-06 disables instead). Prompts of a disabled user remain owned and their
public entries stay published — that is a deliberate, documented choice; hiding them is an admin
action requiring a reason.

## 7. Backup reality (restated because it is a data requirement)

There is **no database backup**. Consequences, stated without softening:

- A dropped table, a bad `DELETE`, or a lost volume is unrecoverable.
- `EXP-01` is the only user-controlled safety net.
- No destructive migration may ship (`architecture/09 §6`).

If any prompt becomes something the team depends on, this section must change from "accepted" to
"required" — it is one cron line, and it is the cheapest risk reduction available.

## 8. Data quality rules

| Rule | Enforcement |
|---|---|
| All ids are UUID v7, generated application-side | `Ids.newId()`, no DB `UUID()` |
| Timestamps stored UTC, `DATETIME(6)` | JDBC + Hibernate timezone config |
| `versionNumber` unique per prompt, gapless in practice | Unique key + row lock |
| Category `slug` unique, lowercase, `^[a-z0-9-]+$` | DB unique + validation |
| Tag `(owner_id, name)` unique, case-insensitive | Collation `utf8mb4_0900_ai_ci` |
| No orphan versions | FK `prompt_version.prompt_id` |
| No orphan favourites | FK; plus query-time visibility filter |
| `copy_count ≥ 0` | `INT UNSIGNED` or application guard |
