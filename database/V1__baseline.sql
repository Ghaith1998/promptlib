-- ============================================================================
--  Prompt Library — V1 baseline schema
--  Target: MySQL 8.4 (InnoDB, utf8mb4)
--  Revision: 1.2 (2026-11-18)
--
--  This file is the authoritative schema. The ERD export in this folder is a
--  generated view of it (see requirements/06-traceability.md).
--
--  Conventions
--    * ids          UUID v7 stored as BINARY(16), generated in Java
--    * timestamps   DATETIME(6), always UTC
--    * soft delete  prompt (user-facing delete) and prompt_version (audit);
--                   app_user.deleted_at is reserved and unused in v1
--    * hard delete  tags/collections only when unused; prompt families cascade
--    * no AUTO_INCREMENT anywhere
--
--  Apply order matters: `prompt` is created before `prompt_version` and the
--  version pointers are added by ALTER at the end, because the two tables
--  reference each other.
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- Users (mirror of Keycloak; no credentials are ever stored here)
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
  id            BINARY(16)   NOT NULL,
  keycloak_sub  VARCHAR(36)  NOT NULL,
  username      VARCHAR(64)  NOT NULL,
  email         VARCHAR(255) NOT NULL,
  display_name  VARCHAR(128) NULL,
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  last_seen_at  DATETIME(6)  NULL,
  deleted_at    DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_sub (keycloak_sub),
  KEY idx_user_username (username),
  KEY idx_user_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Categories (global taxonomy, admin-managed, seeded by V2)
-- ---------------------------------------------------------------------------
CREATE TABLE category (
  id          BINARY(16)   NOT NULL,
  slug        VARCHAR(64)  NOT NULL,
  name        VARCHAR(128) NOT NULL,
  description VARCHAR(500) NULL,
  color       CHAR(7)      NULL,
  sort_order  INT          NOT NULL DEFAULT 0,
  active      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6)  NOT NULL,
  updated_at  DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_category_slug (slug),
  KEY idx_category_active (active, sort_order),
  CONSTRAINT ck_category_slug CHECK (slug REGEXP '^[a-z0-9-]+$'),
  CONSTRAINT ck_category_color CHECK (color IS NULL OR color REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Prompts (metadata only; content lives in prompt_version)
-- ---------------------------------------------------------------------------
CREATE TABLE prompt (
  id                    BINARY(16)    NOT NULL,
  owner_id              BINARY(16)    NOT NULL,
  title                 VARCHAR(200)  NOT NULL,
  description           VARCHAR(2000) NULL,
  visibility            ENUM('PRIVATE','PUBLIC') NOT NULL DEFAULT 'PRIVATE',
  category_id           BINARY(16)    NULL,
  latest_version_id     BINARY(16)    NULL,
  published_version_id  BINARY(16)    NULL,
  fork_of_prompt_id     BINARY(16)    NULL,
  fork_of_version_id    BINARY(16)    NULL,
  copy_count            INT UNSIGNED  NOT NULL DEFAULT 0,
  moderation_state      ENUM('VISIBLE','HIDDEN') NOT NULL DEFAULT 'VISIBLE',
  hidden_reason         VARCHAR(500)  NULL,
  hidden_at             DATETIME(6)   NULL,
  hidden_by             BINARY(16)    NULL,
  created_at            DATETIME(6)   NOT NULL,
  updated_at            DATETIME(6)   NOT NULL,
  deleted_at            DATETIME(6)   NULL,
  PRIMARY KEY (id),
  KEY idx_prompt_owner   (owner_id, deleted_at, created_at, id),
  KEY idx_prompt_gallery (visibility, moderation_state, category_id, deleted_at, created_at, id),
  KEY idx_prompt_popular (visibility, moderation_state, deleted_at, copy_count, id),
  KEY idx_prompt_fork    (fork_of_prompt_id),
  CONSTRAINT fk_prompt_owner    FOREIGN KEY (owner_id)    REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_prompt_hidden_by FOREIGN KEY (hidden_by)  REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_prompt_category FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE RESTRICT,
  CONSTRAINT fk_prompt_fork     FOREIGN KEY (fork_of_prompt_id) REFERENCES prompt(id) ON DELETE SET NULL,
  CONSTRAINT ck_prompt_public CHECK (
      visibility = 'PRIVATE'
      OR (category_id IS NOT NULL AND published_version_id IS NOT NULL)),
  CONSTRAINT ck_prompt_hidden CHECK (
      moderation_state = 'VISIBLE'
      OR (hidden_reason IS NOT NULL AND hidden_at IS NOT NULL AND hidden_by IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Prompt versions (immutable; editing = creating a new row)
-- ---------------------------------------------------------------------------
CREATE TABLE prompt_version (
  id             BINARY(16)   NOT NULL,
  prompt_id      BINARY(16)   NOT NULL,
  version_number INT          NOT NULL,
  change_note    VARCHAR(500) NULL,
  variables_json JSON         NOT NULL,
  created_by     BINARY(16)   NOT NULL,
  created_at     DATETIME(6)  NOT NULL,
  deleted_at     DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_version_number (prompt_id, version_number),
  KEY idx_version_prompt (prompt_id, created_at),
  CONSTRAINT fk_version_prompt FOREIGN KEY (prompt_id)  REFERENCES prompt(id)   ON DELETE RESTRICT,
  CONSTRAINT fk_version_author FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT ck_version_number CHECK (version_number >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Messages (ordered content of a version)
-- ---------------------------------------------------------------------------
CREATE TABLE prompt_message (
  id         BINARY(16)  NOT NULL,
  version_id BINARY(16)  NOT NULL,
  role       ENUM('SYSTEM','USER','ASSISTANT') NOT NULL,
  position   INT         NOT NULL,
  content    MEDIUMTEXT  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_position (version_id, position),
  KEY idx_message_version (version_id),
  CONSTRAINT fk_message_version FOREIGN KEY (version_id) REFERENCES prompt_version(id) ON DELETE CASCADE,
  CONSTRAINT ck_message_position CHECK (position >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Collections (one level in v1; parent_id reserved for a future tree)
-- ---------------------------------------------------------------------------
CREATE TABLE collection (
  id             BINARY(16)   NOT NULL,
  owner_id       BINARY(16)   NOT NULL,
  parent_id      BINARY(16)   NULL,
  name           VARCHAR(120) NOT NULL,
  description    VARCHAR(1000) NULL,
  color          CHAR(7)      NULL,
  sort_order     INT          NOT NULL DEFAULT 0,
  is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
  default_marker CHAR(1)      NULL,          -- 'Y' on the default collection; NULL otherwise
  created_at     DATETIME(6)  NOT NULL,
  updated_at     DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_collection_default (owner_id, default_marker),
  KEY idx_collection_owner (owner_id, sort_order, id),
  KEY idx_collection_parent (parent_id),
  CONSTRAINT fk_collection_owner  FOREIGN KEY (owner_id)  REFERENCES app_user(id)   ON DELETE RESTRICT,
  CONSTRAINT fk_collection_parent FOREIGN KEY (parent_id) REFERENCES collection(id) ON DELETE RESTRICT,
  CONSTRAINT ck_collection_default CHECK (
      (is_default = FALSE AND default_marker IS NULL)
      OR (is_default = TRUE AND default_marker = 'Y')),
  CONSTRAINT ck_collection_color CHECK (color IS NULL OR color REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE collection_prompt (
  collection_id BINARY(16)  NOT NULL,
  prompt_id     BINARY(16)  NOT NULL,
  position      INT         NOT NULL DEFAULT 0,
  added_at      DATETIME(6) NOT NULL,
  PRIMARY KEY (collection_id, prompt_id),
  KEY idx_cp_prompt (prompt_id),
  KEY idx_cp_order (collection_id, position),
  CONSTRAINT fk_cp_collection FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE,
  CONSTRAINT fk_cp_prompt     FOREIGN KEY (prompt_id)     REFERENCES prompt(id)     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Tags (per user; many-to-many with prompts)
-- ---------------------------------------------------------------------------
CREATE TABLE tag (
  id         BINARY(16)  NOT NULL,
  owner_id   BINARY(16)  NOT NULL,
  name       VARCHAR(50) NOT NULL,
  color      CHAR(7)     NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tag_owner_name (owner_id, name),
  CONSTRAINT fk_tag_owner FOREIGN KEY (owner_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT ck_tag_color CHECK (color IS NULL OR color REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prompt_tag (
  prompt_id BINARY(16) NOT NULL,
  tag_id    BINARY(16) NOT NULL,
  PRIMARY KEY (prompt_id, tag_id),
  KEY idx_pt_tag (tag_id),
  -- A tag cannot be deleted while it is attached (409 TAG_IN_USE).
  CONSTRAINT fk_pt_prompt FOREIGN KEY (prompt_id) REFERENCES prompt(id) ON DELETE CASCADE,
  CONSTRAINT fk_pt_tag    FOREIGN KEY (tag_id)    REFERENCES tag(id)    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Favorites (per user, any visible prompt)
-- ---------------------------------------------------------------------------
CREATE TABLE favorite (
  user_id    BINARY(16)  NOT NULL,
  prompt_id  BINARY(16)  NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (user_id, prompt_id),
  KEY idx_favorite_prompt (prompt_id),
  CONSTRAINT fk_fav_user   FOREIGN KEY (user_id)   REFERENCES app_user(id) ON DELETE CASCADE,
  CONSTRAINT fk_fav_prompt FOREIGN KEY (prompt_id) REFERENCES prompt(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Search projection (rebuilt on version create/publish and metadata change)
-- ---------------------------------------------------------------------------
CREATE TABLE prompt_search_document (
  prompt_id       BINARY(16)    NOT NULL,
  owner_id        BINARY(16)    NOT NULL,
  visibility      ENUM('PRIVATE','PUBLIC') NOT NULL,
  moderation_state ENUM('VISIBLE','HIDDEN') NOT NULL,
  category_id     BINARY(16)    NULL,
  title           VARCHAR(200)  NOT NULL,
  description     VARCHAR(2000) NULL,
  body_text       MEDIUMTEXT    NOT NULL,
  updated_at      DATETIME(6)   NOT NULL,
  PRIMARY KEY (prompt_id),
  KEY idx_search_scope (visibility, moderation_state, category_id),
  FULLTEXT KEY ft_search (title, description, body_text),
  CONSTRAINT fk_search_prompt FOREIGN KEY (prompt_id) REFERENCES prompt(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Admin audit (append-only; no FK on target on purpose)
-- ---------------------------------------------------------------------------
CREATE TABLE admin_audit (
  id          BINARY(16)   NOT NULL,
  actor_id    BINARY(16)   NOT NULL,
  action      VARCHAR(64)  NOT NULL,
  target_type VARCHAR(32)  NOT NULL,
  target_id   BINARY(16)   NOT NULL,
  reason      VARCHAR(500) NULL,
  request_id  VARCHAR(64)  NULL,
  created_at  DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  KEY idx_audit_created (created_at),
  KEY idx_audit_target  (target_type, target_id),
  KEY idx_audit_actor   (actor_id, created_at),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Deferred foreign keys (circular prompt <-> prompt_version)
--
-- `prompt` points at its latest/published version, while `prompt_version`
-- points back at its prompt. Both tables must exist before these are added.
--
-- RESTRICT is deliberate: a version that is latest or published cannot be
-- deleted. The application refuses first (409 VERSION_IN_USE); the constraint
-- is the backstop. Purging a soft-deleted prompt therefore requires clearing
-- latest_version_id/published_version_id first — documented, not automated.
-- ---------------------------------------------------------------------------
ALTER TABLE prompt
  ADD CONSTRAINT fk_prompt_latest
    FOREIGN KEY (latest_version_id) REFERENCES prompt_version(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_prompt_published
    FOREIGN KEY (published_version_id) REFERENCES prompt_version(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_prompt_fork_version
    FOREIGN KEY (fork_of_version_id) REFERENCES prompt_version(id) ON DELETE SET NULL;

-- ============================================================================
-- Operational notes
-- ============================================================================
-- 1. Explicitly not in V1: seed categories (V2), any future chain/variant or
--    execution tables, and user administration (lives in Keycloak).
--
-- 2. Soft delete of a prompt must, in the same transaction:
--      UPDATE prompt SET deleted_at = ?, updated_at = ? WHERE id = ?;
--      DELETE FROM prompt_tag        WHERE prompt_id = ?;
--      DELETE FROM collection_prompt WHERE prompt_id = ?;
--      DELETE FROM favorite          WHERE prompt_id = ?;
--      DELETE FROM prompt_search_document WHERE prompt_id = ?;
--    Versions and messages remain. Without this cleanup, a soft-deleted
--    prompt would keep its tags "in use" and its collections non-empty.
--
-- 3. Delete rules enforced in the application, backed by the constraints:
--      published prompt      -> 409 PROMPT_PUBLISHED
--      tag attached anywhere -> 409 TAG_IN_USE
--      non-empty collection  -> 409 COLLECTION_NOT_EMPTY
--      default collection    -> 409 DEFAULT_COLLECTION
--      latest/published version -> 409 VERSION_IN_USE
--
-- 4. `collection.default_marker` is the only portable way to enforce one
--    default collection per user in MySQL (no partial unique indexes).
--    Set 'Y' for the default and NULL for every other row.
--
-- 5. Search: `innodb_ft_min_token_size=2` must be set in my.cnf BEFORE this
--    index is first populated; changing it later requires a rebuild.
--
-- 6. Fulltext does not stem. "running" will not match "run" — accepted for v1,
--    semantic search is a later Qdrant addition.
-- ============================================================================
