# 03 — User Journeys

Each journey lists the happy path, the empty state, and the failure paths that must not be forgotten.
Endpoint names refer to `../architecture/03-communication.md §2.2`.

---

## J1 — First login (new team member)

**Precondition:** the user has an account, either self-registered through the app or created by an
admin in the Keycloak console.

```
1. App opens → no refresh token → login screen (username + password)
2. App POSTs grant_type=password directly to Keycloak (ROPC, client promptlib-app)
3. Keycloak returns access + refresh tokens
4. App calls GET /me with the bearer token
   → edge sees an unknown sub → AccountService.EnsureUser → app_user row created
   → 200 { id, username, email, displayName: null, roles: ["user"] }
5. Landing screen: empty library ("No prompts yet — create your first one" + gallery link)
```

Signup variant:

```
Login screen → "Create account"
  → POST /api/v1/auth/register  (no token; username, email, password)
  → 201; the account exists in Keycloak with role "user"
  → back to login → ROPC → GET /me creates app_user
```

Edge cases:

| Case | Behaviour |
|---|---|
| Registration disabled/closed | Keycloak rejects new accounts; the user asks an admin to create one in the console |
| Username or email already exists | 409 `USERNAME_TAKEN`; no partial account; no `app_user` row |
| Weak password | 422 `WEAK_PASSWORD`; Keycloak's policy is the source of truth |
| Two people register the same username simultaneously | One 201, one 409; Keycloak enforces uniqueness |
| First API request races (two devices) | Upsert is idempotent; one `app_user` row |
| Keycloak down during registration | 503 `IDENTITY_PROVIDER_UNAVAILABLE`; nothing created, retry later |
| DB down during step 4 | 503 `DATABASE_UNAVAILABLE`; the app shows an error with `requestId` |

## J2 — Create a prompt

```
1. Tap "New prompt"
2. Title (required, ≤ 200), description (optional, ≤ 2000)
3. Editor: add messages, choose role (system/user/assistant), reorder
4. Type content with {{placeholders}} → editor lists detected placeholders
5. Define each variable: type, required, default, description
   → client blocks save while a placeholder is undefined (server would reject with VARIABLE_MISMATCH)
6. Save → POST /prompts (prompt + v1 in one transaction)
7. Navigate to prompt detail, version 1 selected
```

Empty state: the editor starts with a single empty `USER` message and no variables.

Failure paths:

| Case | Behaviour |
|---|---|
| Empty content | 422 `EMPTY_PROMPT`; the save button is disabled client-side anyway |
| Placeholder mismatch (client bypassed) | 422 with `errors[]` naming each offending variable |
| Over 1 MB / 100 messages / 100 variables | 422 `CONTENT_TOO_LARGE` with the limit stated |
| Network lost after submit | Client must not auto-retry POST (no idempotency key); show "check your library" rather than a duplicate |

## J3 — Iterate: new version, then publish

```
1. Open a prompt → "New version" (cannot edit an existing version)
2. Choose "start from v2" (copy) or blank
3. Edit messages/variables in the new version
4. Save → POST /prompts/{id}/versions → versionNumber 3 (latest, still private)
5. "Publish" → category picker (GET /categories)
6. POST /prompts/{id}/versions/3/publish { categoryId }
   → visibility PUBLIC, publishedVersion = 3
7. The gallery now shows v3's content; v2 stays readable in the timeline
```

Rules the UI must communicate:

- The word "Save" on an existing version does not exist. There is "Save as new version".
- Publishing is an explicit act; saving a version never publishes.
- Publishing an older version is allowed (roll back the public view without deleting history).
- Unpublish is one tap and affects only the gallery, never the library.

Failure paths:

| Case | Behaviour |
|---|---|
| Category deactivated between picker load and publish | 422 `CATEGORY_REQUIRED`; refresh the category list |
| Prompt hidden by an admin | 409 `HIDDEN_BY_ADMIN` with the reason; publish button disabled |
| Already published | 200 idempotent, no duplicate state |
| Concurrent publish of two versions | The last transaction wins, both are consistent; no partial pointer |

## J4 — Browse the gallery and fork

```
1. Gallery tab → GET /gallery?sort=newest&limit=20
2. Filter by category chips (GET /categories)
3. Search box → GET /gallery?q=review+checklist
4. Open a card → GET /gallery/{id}
   → full published content, variables, category, owner display name, copy_count
5. "Fork to my library" → POST /gallery/{id}/fork
   → 201, private prompt with v1, lineage recorded, source copy_count +1
6. App navigates to the forked prompt inside "My prompts" (the default collection)
   and shows "Forked from <title> by <author>" when the source is still visible
```

Empty states: "No public prompts yet" (with a CTA to publish one), and "No results for <query>"
(with a link to clear filters). The two must be visually distinct — an empty gallery is not a failure.

Failure paths:

| Case | Behaviour |
|---|---|
| Prompt unpublished/deleted between list and detail | 404 → "This prompt is no longer public" (not a crash) |
| Hidden by admin | Absent from list; detail 404 |
| Double-tap fork | Two prompts are created (documented, non-idempotent). The client must disable the button after the first tap |
| Source unpublished/hidden after fork | The fork is unaffected; the "Forked from" line disappears |
| Source deleted after fork | The fork is independent and fully readable |

## J5 — Find an old prompt (the core value)

```
1. Library tab → search "extract json"
2. GET /prompts?q=extract+json → ranked results with a snippet
3. Optionally narrow: collection filter, tag filter, visibility
4. Open → version timeline → pick the version that worked
5. "Copy" → client substitutes variables locally and copies the final text
```

Acceptance for the product goal G1: a prompt created in month 1 is findable in month 2 by a word from
its body, without remembering the title.

## J6 — Organize

```
First login → "My prompts" exists as the default collection
Collections: create "Code review" → drag three prompts in → reorder
Tags: type "review" in the prompt editor → tag created and attached
       rename "review" → "code-review" in the tag manager → applies to all prompts
Delete a collection → allowed only when empty: prompts never disappear with it
Delete the default collection → refused (409 DEFAULT_COLLECTION)
Delete a tag still attached → refused (409 TAG_IN_USE) with the count
```

## J7 — Export (the safety net)

```
Settings → Export → GET /export
→ promptlib-export-2026-11-18.json downloaded (schemaVersion, prompts, versions,
  collections, tags, favorites)
Single prompt → "Export as Markdown" → readable file with roles and variables
```

This is the only user-facing protection against the missing database backups. It must therefore work
even when the gallery is unhealthy — it touches only the caller's rows.

## J8 — Admin moderates the gallery

```
1. Admin screen → Gallery moderation → list public prompts (metadata)
2. Hide a prompt → reason required ("duplicate of …", "contains customer data")
3. POST /admin/prompts/{id}/hide { reason }
   → moderationState HIDDEN, gallery entry gone, admin_audit row written
4. Owner opens their prompt → sees "Hidden by an administrator: <reason>"; publish disabled
5. Admin later unhides → prompt returns to the gallery under the same version
```

## J9 — Admin manages users (in Keycloak, not in the app)

```
1. Admin opens the Keycloak admin console → realm promptlib → Users
2. "Add user" → username, email, enabled → set a temporary password or send a reset
   (self-registration through the app is the usual path, J1)
3. Assign the realm role "admin" to a trusted colleague → role appears in their next token
   ⚠ Keycloak refuses to remove the last admin role in a realm, so no in-app guard is needed
4. A departing user: set Enabled = false → new tokens are refused; existing ones die within 5 min
5. Their prompts remain owned; published ones stay in the gallery unless moderated (J8)
```

Why it lives in Keycloak: doing it in-app would require a `realm-management` credential inside the
service. The only service account the app has can create accounts, nothing else (`ADR-22`).

Note: disabling is not deleting. There is no account deletion in v1 (see `05 §6`).

## J10 — Service degradation (what users actually see)

| Failure | User-visible result |
|---|---|
| `core` down | Any library/gallery action → banner "Service unavailable", `code=UPSTREAM_UNAVAILABLE`, `requestId`, retry button |
| MySQL down | Same, `code=DATABASE_UNAVAILABLE`, after ~5 s (not a spinner forever) |
| Keycloak down, cache cold | Login fails; existing sessions may continue until JWKS cache expiry |
| Session expired | Silent refresh; if that fails, return to login **without losing unsaved editor draft** (draft is stored locally) |
| Search returns nothing unexpectedly | Distinguish "no matches" from "search unavailable" — never show an empty state on a 503 |

## J11 — Delete

```
1. Prompt → Delete → confirmation ("This is permanent; the prompt leaves the gallery")
2. If published → 409 PROMPT_PUBLISHED → the client offers "Unpublish, then delete"
3. DELETE /prompts/{id} → soft delete + memberships/favorites/search rows removed
4. Prompt disappears from library, gallery, search, favorites, collections
5. Its tag becomes deletable; its collection may become empty
```

There is no trash and no restore. Soft-deleted rows exist only for integrity and a future purge job
(see `05 §5`).

## J12 — Publishing a second time (version rollback)

```
1. Prompt published at v5; v6 was a bad idea
2. Open v5 in the timeline → "Publish this version"
3. POST /prompts/{id}/versions/5/publish { categoryId }
4. Gallery serves v5 again; v6 remains in history, unpublished
```

This is the supported rollback mechanism. There is no "undo publish" beyond republishing an older
version or unpublishing entirely.
