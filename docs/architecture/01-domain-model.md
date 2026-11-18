# 01 — Domain Model

**Depends on:** ADR-06 (immutable versions), ADR-07 (pinned publish), ADR-08 (gallery in-domain),
ADR-09 (category required), ADR-16 (UUID v7), ADR-17 (soft delete), ADR-18 (typed variables),
ADR-23 (moderation axis), ADR-24 (audit), ADR-26 (collection parent reserved).

---

## 1. Aggregates

Three aggregates, one per consistency boundary:

| Aggregate root | Contains | Invariant it protects |
|---|---|---|
| **Prompt** | PromptVersion (1..n), PromptMessage (inside a version) | A public prompt always points at one of its *own*, *published* versions |
| **Collection** | CollectionPrompt (ordered membership) | A prompt appears at most once in a collection |
| **Account** | app_user row | Keycloak `sub` is unique and stable |

`Category` and `Tag` are **reference data**, not aggregates: categories are global and admin-managed;
tags are per-user labels. `Favorite` is a lightweight relation owned by the gallery module.

## 2. Entity–relationship view

```
app_user 1───n prompt 1───n prompt_version 1───n prompt_message
   │              │  │
   │              │  └──n prompt_tag n───1 tag (owner-scoped)
   │              │
   │              └──n collection_prompt n───1 collection
   │              │
   │              └──n favorite n───1 app_user
   │
   └──n collection, tag, prompt_version (created_by)

category 1───n prompt            (set on publish; required when PUBLIC)
prompt.fork_of_prompt_id ──▶ prompt        (self-reference, lineage)
prompt_search_document 1───1 prompt        (derived index, rebuilt on version save)
```

## 3. Lifecycle rules (the part that must not drift)

### 3.1 Version immutability

A `prompt_version` row is **write-once**. There is no `updateVersion` operation anywhere:
not in the REST API, not in gRPC, not in the repository. Editing means:

```
open published prompt
  └─▶ "Create new version"  (server copies content of the chosen version into version N+1)
        └─▶ edit version N+1 (it is still write-once, but it starts as a copy)
              └─▶ publish N+1     (pointer moves; N stays exactly as it was)
```

**Why this rule is absolute:** a shared link, a fork, or an exported copy must never change because
the author had a second thought. Immutability also makes the gallery read path cacheable and makes
"what did I copy?" answerable forever.

> Implementation guard: the Panache repository for `PromptVersion` exposes `persist` and read methods
> only. No `setX` on content fields after construction; fields are `final`-by-convention and there is
> no public setter. A test asserts that no `UPDATE prompt_version` statement is emitted during an edit
> flow (see `08-testing.md §6`).

### 3.2 Visibility and publication

```
                 create prompt
                       │
                       ▼
              ┌─────────────────┐
              │ PRIVATE         │  published_version_id = NULL
              │ latest = v1     │  category_id = NULL allowed
              └────────┬────────┘
        publish(vN, categoryId)
                       │  requires: vN ∈ prompt.versions, category active
                       ▼
              ┌─────────────────┐
              │ PUBLIC          │  published_version_id = vN
              │                 │  category_id = required
              └────────┬────────┘
   unpublish / delete  │  publish(vM) moves the pointer to vM
        ┌──────────────┼───────────────┐
        ▼              ▼               ▼
┌───────────────┐  ┌───────────────┐  admin hide(reason)
│ PRIVATE       │  │ PUBLIC (vM)   │  ─────────────────▶ ┌─────────────────┐
│ (again)       │  └───────────────┘                      │ HIDDEN          │
└───────────────┘                                         │ visibility stays│
                                                          │ PUBLIC, but out │
                                                          │ of the gallery  │
                                                          └────────┬────────┘
                                                                   │ admin unhide
                                                                   ▼
                                                          ┌─────────────────┐
                                                          │ PUBLIC (same v) │
                                                          └─────────────────┘
```

Rules:

1. `visibility = PUBLIC` **implies** `category_id IS NOT NULL AND published_version_id IS NOT NULL`.
2. `published_version_id` must belong to the same prompt. Enforced in code + a consistency test.
3. Unpublishing clears `published_version_id` but never deletes the version.
4. Deleting a public prompt removes it from the gallery immediately (soft delete).
5. Publishing is idempotent: publishing the already-published version returns 200 and changes nothing.
6. `moderation_state = HIDDEN` is **orthogonal** to visibility: the prompt keeps `PUBLIC` and its
   pinned version, but is filtered out of gallery, search and `popular`. The owner cannot publish or
   unpublish while hidden (409 `HIDDEN_BY_ADMIN`); only an admin can unhide.
7. Hiding requires a reason (≥ 3 chars) and records `hidden_by` + `hidden_at`; the owner sees
   `moderationState` and `hiddenReason` on their own prompt detail.

### 3.3 Forking

`POST /api/v1/gallery/{promptId}/fork` requires the source to be `PUBLIC`, `VISIBLE`, and not deleted.

```
new Prompt(
  owner        = caller,              // the forker owns the copy outright
  title        = source.title + " (fork)",
  visibility   = PRIVATE,
  fork_of_prompt_id  = source.id,
  fork_of_version_id = source.published_version_id
)
new PromptVersion( copy of source.published_version, version_number = 1, created_by = caller )
source.copy_count += 1        // monotonic counter, no decrement on delete
```

Decisions:

- **Ownership**: the fork belongs to the forker. The original author has no control over it — cannot
  edit, unpublish, hide or delete it, and does not see it in their library.
- **Content**: messages and variables are copied rows, not references. No propagation in either
  direction.
- **Authorship** (ADR-30): the copied version's `created_by` is the **forker**. Credit is preserved by
  `fork_of_version_id`, which still points at the version the original author wrote. If explicit
  attribution is ever needed, add a nullable `attributed_to` column — do not overload `created_by`.
- **Publishability**: a fork may be published **unchanged**. The gallery can therefore contain the
  same content twice, once per author, with a lineage link. That is expected, not a defect.
- **Lineage display**: "forked from *title* by *author*" is resolved only while the source is visible.
  If the source is unpublished, hidden or deleted, the line disappears — never an error.
- `copy_count` is incremented on the *source* row, never decremented, and is **not inherited** by the
  fork (a new fork starts at 0).
- Forking your own prompt is allowed (useful for branching) and still increments `copy_count`.
- A fork always lands in the caller's **default collection** (ADR-28); filing elsewhere remains optional.

### 3.4 Deletion

There is **no trash and no restore endpoint** (ADR-17). Deleting a prompt is final from the user's
point of view; the row survives only for integrity and future purging.

| Action | Behaviour | Guard |
|---|---|---|
| Delete a **published** prompt | Refused | `409 PROMPT_PUBLISHED` — unpublish first, then delete |
| Delete an unpublished prompt | Sets `prompt.deleted_at` **and** removes its memberships/favorites/search row in the same transaction | — |
| Delete a version | Refused if latest or published | `409 VERSION_IN_USE`; otherwise `deleted_at` |
| Delete a tag | Refused while attached to any prompt | `409 TAG_IN_USE` with a count |
| Delete a collection | Refused when non-empty or when it has sub-collections | `409 COLLECTION_NOT_EMPTY` |
| Delete the default collection | Refused | `409 DEFAULT_COLLECTION` |
| Delete a user | Out of scope in v1 (no endpoint) | — |

The cleanup step is not optional:

```sql
UPDATE prompt SET deleted_at = ?, updated_at = ? WHERE id = ?;
DELETE FROM prompt_tag              WHERE prompt_id = ?;
DELETE FROM collection_prompt       WHERE prompt_id = ?;
DELETE FROM favorite                WHERE prompt_id = ?;
DELETE FROM prompt_search_document  WHERE prompt_id = ?;
-- versions and messages stay, invisible, for integrity and audit
```

Without it, a soft-deleted prompt keeps its tags "in use" and its collections non-empty forever, and
the guards above become impossible to satisfy.

Query rule: every read path filters `prompt.deleted_at IS NULL`. Enforce with a named Panache query or
a `@Where`-style Hibernate filter; **never** rely on callers remembering.

### 3.5 Moderation (admin)

Hiding is **not** a visibility change. It is a separate state so the owner cannot undo it and so the
reason survives:

```
POST /api/v1/admin/prompts/{id}/hide   { reason }   → moderation_state HIDDEN, hidden_by, hidden_at
POST /api/v1/admin/prompts/{id}/unhide              → moderation_state VISIBLE, reason retained in audit
```

Rules:

- Only `admin` may hide/unhide. The owner never sees a hide control.
- Hiding requires `reason` (3–500 chars); it is stored on the row **and** in `admin_audit`.
- A hidden prompt keeps `visibility = PUBLIC` and `published_version_id`; it is filtered out of the
  gallery, keyword search and `popular` by `moderation_state = 'VISIBLE'`.
- The owner's `GET /prompts/{id}` includes `moderationState`, `hiddenReason`, `hiddenAt`.
- While hidden, owner publish/unpublish attempts return 409 `HIDDEN_BY_ADMIN`.
- Unhiding restores the prompt to the gallery with the same pinned version.
- Deleting a hidden prompt is allowed (owner) and is a soft delete; the audit trail stays.
- Hidden + deleted is a valid combination; both predicates are applied independently.

### 3.6 Default collection

Every user has exactly one default collection (ADR-28), created lazily and idempotently the first time
their `app_user` row is mirrored:

```
ensureDefaultCollection(user):
  INSERT INTO collection (id, owner_id, name, is_default, default_marker, ...)
  VALUES (uuid7(), :userId, 'My prompts', TRUE, 'Y', ...)
  ON DUPLICATE KEY UPDATE id = id          -- unique (owner_id, default_marker)
```

Rules:

- `UNIQUE(owner_id, default_marker)` enforces one default per user; `default_marker` is `NULL` for
  every other collection, and MySQL allows many `NULL`s in a unique index.
- The default collection **cannot be deleted** (`409 DEFAULT_COLLECTION`) or converted into a normal
  one through the v1 API; it can be renamed and reordered.
- Forks are added to the default collection. No other operation forces membership: a prompt may sit
  in zero collections.
- Deleting the last prompt from a collection does **not** delete the collection; that is explicit.

## 4. Content model

### 4.1 Messages

A version owns an ordered list of messages. Ordering is explicit (`position`), never implicit by id.

| Field | Type | Notes |
|---|---|---|
| `role` | `SYSTEM` \| `USER` \| `ASSISTANT` | No `TOOL` in v1 |
| `position` | int, 0-based, unique per version | Reordering rewrites positions inside the new version only |
| `content` | `MEDIUMTEXT` | Holds `{{variable}}` placeholders |

Why a table and not a JSON blob: keyword search must index `content` with a MySQL `FULLTEXT` index,
and that requires a text column. A JSON array would force application-side scanning.

### 4.2 Typed variables

Stored as `variables_json` on the version (JSON column). Shape:

```json
[
  {
    "name": "topic",
    "type": "string",
    "required": true,
    "default": null,
    "description": "Subject of the article",
    "enumValues": []
  },
  {
    "name": "tone",
    "type": "enum",
    "required": false,
    "default": "neutral",
    "description": null,
    "enumValues": ["neutral", "formal", "playful"]
  }
]
```

Rules validated on version creation:

1. `name` matches `^[a-zA-Z_][a-zA-Z0-9_]*$`, unique within the version.
2. `type ∈ {string, number, boolean, enum}`; `enum` requires a non-empty `enumValues`.
3. Every `{{name}}` found in any message content has exactly one definition, and every definition is
   referenced at least once. Mismatch → 422 with `code = VARIABLE_MISMATCH` and the offending names.
4. `default` must satisfy the declared type and, for `enum`, be one of `enumValues`.
5. Placeholder parsing is `\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\}\}` and is applied to all roles.

This schema is deliberately execution-ready: a future service can render a fill-in form and build a
provider request without touching the domain model.

## 5. Schema (Flyway `V1__baseline.sql`)

All ids are `BINARY(16)` (UUID v7, time-ordered → good index locality). Timestamps are `DATETIME(6)`
in UTC. Charset `utf8mb4`, collation `utf8mb4_0900_ai_ci`, engine InnoDB.

```sql
CREATE TABLE app_user (
  id            BINARY(16)   NOT NULL PRIMARY KEY,
  keycloak_sub  VARCHAR(64)  NOT NULL,
  username      VARCHAR(64)  NOT NULL,
  email         VARCHAR(255) NOT NULL,
  display_name  VARCHAR(128) NULL,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  last_seen_at  DATETIME(6)  NULL,
  deleted_at    DATETIME(6)  NULL,
  UNIQUE KEY uk_user_sub (keycloak_sub)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE category (
  id          BINARY(16)   NOT NULL PRIMARY KEY,
  slug        VARCHAR(64)  NOT NULL,
  name        VARCHAR(128) NOT NULL,
  description VARCHAR(500) NULL,
  color       CHAR(7)      NULL,
  sort_order  INT          NOT NULL DEFAULT 0,
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  UNIQUE KEY uk_category_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prompt (
  id                    BINARY(16)   NOT NULL PRIMARY KEY,
  owner_id              BINARY(16)   NOT NULL,
  title                 VARCHAR(200) NOT NULL,
  description           VARCHAR(2000) NULL,
  visibility            ENUM('PRIVATE','PUBLIC') NOT NULL DEFAULT 'PRIVATE',
  category_id           BINARY(16)   NULL,
  latest_version_id     BINARY(16)   NULL,
  published_version_id  BINARY(16)   NULL,
  fork_of_prompt_id     BINARY(16)   NULL,
  fork_of_version_id    BINARY(16)   NULL,
  copy_count            INT          NOT NULL DEFAULT 0,
  moderation_state      ENUM('VISIBLE','HIDDEN') NOT NULL DEFAULT 'VISIBLE',
  hidden_reason         VARCHAR(500) NULL,
  hidden_at             DATETIME(6)  NULL,
  hidden_by             BINARY(16)   NULL,
  created_at            DATETIME(6)  NOT NULL,
  updated_at            DATETIME(6)  NOT NULL,
  deleted_at            DATETIME(6)  NULL,
  KEY idx_prompt_owner (owner_id, deleted_at, created_at),
  KEY idx_prompt_gallery (visibility, moderation_state, category_id, deleted_at, created_at),
  KEY idx_prompt_fork (fork_of_prompt_id),
  CONSTRAINT fk_prompt_owner FOREIGN KEY (owner_id) REFERENCES app_user(id),
  CONSTRAINT fk_prompt_category FOREIGN KEY (category_id) REFERENCES category(id),
  CONSTRAINT fk_prompt_hidden_by FOREIGN KEY (hidden_by) REFERENCES app_user(id),
  CONSTRAINT ck_prompt_public CHECK (visibility = 'PRIVATE' OR
      (category_id IS NOT NULL AND published_version_id IS NOT NULL)),
  CONSTRAINT ck_prompt_hidden CHECK (moderation_state = 'VISIBLE' OR
      (hidden_reason IS NOT NULL AND hidden_at IS NOT NULL AND hidden_by IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prompt_version (
  id             BINARY(16)   NOT NULL PRIMARY KEY,
  prompt_id      BINARY(16)   NOT NULL,
  version_number INT          NOT NULL,
  change_note    VARCHAR(500) NULL,
  variables_json JSON         NOT NULL,
  created_by     BINARY(16)   NOT NULL,
  created_at     DATETIME(6)  NOT NULL,
  deleted_at     DATETIME(6)  NULL,
  UNIQUE KEY uk_version_number (prompt_id, version_number),
  CONSTRAINT fk_version_prompt FOREIGN KEY (prompt_id) REFERENCES prompt(id),
  CONSTRAINT fk_version_author FOREIGN KEY (created_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prompt_message (
  id         BINARY(16)  NOT NULL PRIMARY KEY,
  version_id BINARY(16)  NOT NULL,
  role       ENUM('SYSTEM','USER','ASSISTANT') NOT NULL,
  position   INT         NOT NULL,
  content    MEDIUMTEXT  NOT NULL,
  UNIQUE KEY uk_message_position (version_id, position),
  CONSTRAINT fk_message_version FOREIGN KEY (version_id) REFERENCES prompt_version(id),
  FULLTEXT KEY ft_message_content (content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Remaining tables follow the same conventions:

```sql
collection(id, owner_id, parent_id NULL, name, description, color NULL,
           sort_order, is_default BOOLEAN, default_marker CHAR(1) NULL,
           created_at, updated_at)   -- UNIQUE(owner_id, default_marker)
collection_prompt(collection_id, prompt_id, position, added_at)  -- PK(collection_id, prompt_id)
tag(id, owner_id, name, color NULL, created_at)                   -- UNIQUE(owner_id, name)
prompt_tag(prompt_id, tag_id)                                    -- PK(prompt_id, tag_id)
favorite(user_id, prompt_id, created_at)                         -- PK(user_id, prompt_id)
prompt_search_document(prompt_id PK, owner_id, visibility, category_id,
                       title, description, body_text, updated_at,
                       FULLTEXT KEY ft_search (title, description, body_text))

admin_audit(id PK, actor_id, action VARCHAR(64), target_type VARCHAR(32), target_id,
            reason VARCHAR(500) NULL, request_id VARCHAR(64) NULL, created_at,
            KEY idx_audit_created (created_at), KEY idx_audit_target (target_type, target_id))
```

`collection.parent_id` is nullable and foreign-keyed, but **the v1 API does not expose nesting**
(ADR-26); one level is the only supported depth. Adding the column now avoids a rewrite later.

`admin_audit` is **append-only**: no update path, no delete path, no public endpoint in v1. Rows are
written in the same transaction as the admin action (ADR-24).

**Why a `prompt_search_document` table** even though MySQL could search the sources: the gallery
search must combine title + description + all message bodies in one ranked query. A denormalized
document gives one `FULLTEXT` index, one `MATCH ... AGAINST` clause, and — critically — becomes the
exact payload for a future Qdrant collection (`11-roadmap.md`). It is rebuilt on every version
create/publish and prompt metadata update.

## 6. Indexing rationale

| Index | Serves |
|---|---|
| `idx_prompt_owner(owner_id, deleted_at, created_at)` | Private list, cursor-ordered |
| `idx_prompt_gallery(visibility, category_id, deleted_at, created_at)` | Gallery browse by category |
| `uk_version_number(prompt_id, version_number)` | Version lookup + monotonic numbering |
| `ft_search(title, description, body_text)` | Keyword search |
| `uk_user_sub` | Login-time upsert of the mirrored user |

Do **not** add an index on `title` alone: search goes through `prompt_search_document`, not `LIKE`.

## 7. Invariant checklist (what the tests must prove)

1. No API path can mutate an existing version's content or variables.
2. `PUBLIC ⇒ category_id ≠ NULL ∧ published_version_id ≠ NULL` (DB CHECK + app guard).
3. `published_version_id.prompt_id = prompt.id`.
4. Publishing a version belonging to another prompt fails (404, not 400 — do not leak existence).
5. `version_number` is gapless-per-prompt in practice and strictly increasing (`max + 1` under a
   row lock on the prompt; concurrent version creation must not duplicate numbers).
6. Fork requires source `PUBLIC`; forking a private prompt returns 404.
7. Forking increments `copy_count` exactly once per call.
8. Soft-deleted prompts appear in no list, no gallery, no fork target, and cannot be published.
9. Every `{{var}}` matches exactly one declared variable, and vice versa.
10. Deleting the published or latest version is refused (409 `code = VERSION_IN_USE`).
11. `HIDDEN ⇒ hidden_reason, hidden_at, hidden_by` are non-null (DB CHECK + app guard).
12. A hidden prompt never appears in gallery, search or `popular`; the owner cannot publish/unpublish
    it (409 `HIDDEN_BY_ADMIN`).
13. Every admin action writes exactly one `admin_audit` row in the same transaction; a rolled-back
    action writes none.
14. Every request enforces the content limits of `05-data-requirements.md §2` (1 MB/version,
    100 messages, 100 variables) before touching persistence.
15. Exactly one default collection exists per user, enforced by `UNIQUE(owner_id, default_marker)`;
    it is never deleted.
16. A fork's version has `created_by = forker`; `fork_of_*` is the only authorship link to the source.
17. Colours, when present, match `^#[0-9A-Fa-f]{6}$` (DB `CHECK` + API validation).
