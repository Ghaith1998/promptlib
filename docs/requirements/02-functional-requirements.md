# 02 — Functional Requirements

Priority: **M** = must ship in v1, **S** = should, **C** = recorded but not v1.
Every requirement has an acceptance check. Security acceptance is implied everywhere: **each endpoint
also needs the three cases from `08-testing.md §4`** (no auth → 401, wrong auth → 403/404, correct
auth → success); those are not repeated per row.

---

## AUTH — Authentication & session

> IDs 03, 04 and 11 were withdrawn in revision 1.1 (web BFF). They are kept below for traceability.

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| AUTH-01 | Every API endpoint requires a valid Keycloak access token, **except** `POST /api/v1/auth/register` and health checks | M | `curl` without `Authorization` on any other `/api/v1/**` path returns 401 |
| AUTH-02 | Mobile and desktop clients log in with a **Direct Access Grant (ROPC)** against `promptlib-app`, posting credentials directly to Keycloak | M | A token obtained by the client is accepted by the edge; a token from a different client (wrong `aud`) is rejected 401 |
| AUTH-05 | Access tokens live 5 minutes; refresh uses rotation; a reused refresh token invalidates the session | M | Replaying a used refresh token returns `invalid_grant` |
| AUTH-06 | Client refreshes are single-flight (client obligation, verified in contract test) | M | Two parallel 401s trigger one refresh call, not two |
| AUTH-07 | Logout revokes the Keycloak session and clears local tokens | S | After logout, the old refresh token fails |
| AUTH-08 | Registration is a custom form that calls `POST /api/v1/auth/register`; the backend creates the Keycloak user and assigns role `user` | M | A brand-new account can log in through ROPC and use the library immediately |
| AUTH-09 | Accounts can also be created by an admin **in the Keycloak console** (outside the app) | M | A user created in the console can log in and use the library |
| AUTH-10 | Authorization failures do not reveal existence: reading someone else's private prompt returns 404 | M | `GET /prompts/{id}` as a non-owner returns 404, not 403 |
| AUTH-12 | Removing the `admin` role takes effect within one access-token lifetime (≤ 5 min) | S | After removal, the next token has no admin role and admin endpoints return 403 |
| AUTH-13 | The registration endpoint is unauthenticated and **unthrottled in v1** (accepted risk R-3) | M | Documented; no rate limit exists, and that is a recorded decision, not an oversight |

### Withdrawn in revision 1.1

| ID | Original requirement | Why withdrawn |
|---|---|---|
| AUTH-03 | Web BFF (`/auth/web/*`) | Flutter web is out of v1 (ADR-21) |
| AUTH-04 | Bearer **or** cookie resolves to one identity | No cookie transport exists |
| AUTH-11 | CSRF protection | Only needed for cookie-authenticated browser sessions |
| AUTH-09 (old) | Admin creates accounts **in the app** | User administration moved to the Keycloak console (ADR-22) |

## ACC — Account & profile

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| ACC-01 | First authenticated request mirrors the Keycloak user into `app_user` (idempotent) | M | Two concurrent first requests create exactly one row |
| ACC-02 | `GET /me` returns id, username, email, display name, roles | M | Response contains `roles: ["user"]` for a normal account |
| ACC-03 | `PATCH /me` updates display name only | S | Display name persists across logins; username/email remain Keycloak-owned |
| ACC-04 | Username/email changes in Keycloak propagate on the next request | S | Change email in Keycloak, call `/me`, see the new value |
| ACC-05 | The app never stores or accepts passwords | M | No password field exists in any request/response/table |

## LIB — Prompts (private library)

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| LIB-01 | Create a prompt with title, description, messages and variables; version 1 is created in the same transaction | M | 201 returns the prompt with `latestVersion = 1`; a failure in version creation leaves no prompt row |
| LIB-02 | List the caller's prompts with cursor pagination and filters (`q`, `visibility`, `collectionId`, `tag`) | M | 25 prompts → two pages, no duplicates, stable across inserts |
| LIB-03 | Get one prompt with metadata and latest version summary | M | Owner sees it; non-owner gets 404 (unless public, via the gallery endpoint) |
| LIB-04 | Update title and description only; content changes always create a version | M | There is no request field that can change message content on an existing prompt |
| LIB-05 | Delete an **unpublished** prompt: soft delete plus removal of its tag, collection, favorite and search rows in one transaction | M | After delete it is absent from list, gallery, search and fork targets; the tag becomes deletable |
| LIB-06 | Deleting a **published** prompt is refused until it is unpublished | M | 409 `PROMPT_PUBLISHED`; the prompt stays visible |
| LIB-07 | Duplicate an own prompt into the same workspace, recording lineage | S | New prompt `fork_of_prompt_id` points at the source; both remain independent |
| LIB-08 | Metadata updates are protected against lost updates | S | PATCH with a stale `expectedUpdatedAt` returns 409 `STALE_WRITE` |
| LIB-09 | Prompt titles need not be unique; duplicates are allowed | M | Creating two prompts with the same title succeeds |
| LIB-10 | An empty or whitespace-only prompt cannot be created or published | M | 422 `EMPTY_PROMPT` |

### Withdrawn in revision 1.2

| ID | Original requirement | Why withdrawn |
|---|---|---|
| LIB-06 (old) | Restore a soft-deleted prompt | Deletion is final for the user (ADR-17); no trash screen |

## COL — Collections (one level)

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| COL-01 | Create, rename, reorder, delete collections (flat, no nesting in v1) | M | Full CRUD round-trip; deleted collection disappears but prompts survive |
| COL-02 | A prompt can belong to zero or many collections | M | Adding the same prompt to two collections succeeds |
| COL-03 | Collection membership is ordered and reorderable | S | Moving a prompt changes its position without touching others' content |
| COL-04 | Deleting a collection is allowed only when it is empty and has no sub-collections | M | 409 `COLLECTION_NOT_EMPTY` otherwise; prompts are never deleted by this action |
| COL-05 | The data model reserves one optional parent level for a future tree | S | `collection.parent_id` exists, is nullable, and is unused by the v1 API |
| COL-06 | Each user has exactly one **default** collection, created automatically on first login | M | Concurrent first requests produce one default; `UNIQUE(owner_id, default_marker)` holds |
| COL-07 | The default collection cannot be deleted; it can be renamed | M | 409 `DEFAULT_COLLECTION`; rename succeeds |
| COL-08 | Collections accept an optional colour (`#RRGGBB`), validated server-side | S | Invalid value → 422; absent → null |

## TAG — Tags

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| TAG-01 | Tags are per-user; two users may have a tag with the same name | M | Both users create "review"; both see only their own |
| TAG-02 | Tags are created implicitly when attached to a prompt | M | Sending `tags: ["review"]` creates the tag if missing |
| TAG-03 | Rename a tag; the rename applies to all prompts carrying it | S | Rename propagates to every tagged prompt |
| TAG-04 | Delete a tag only when it is not attached to any prompt | M | 409 `TAG_IN_USE` with the count; no prompt is ever lost |
| TAG-05 | Tag names are trimmed, case-insensitive unique per user, max 50 chars | M | "Review" and "review" resolve to one tag |
| TAG-06 | Tags accept an optional colour (`#RRGGBB`), validated server-side | S | Invalid value → 422; absent → null |

## VER — Versions and variables

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| VER-01 | A version is immutable. No API can change its messages or variables after creation | M | No endpoint exists; attempting a content PATCH returns 404/405; a DB-level test proves no UPDATE occurs |
| VER-02 | Create a version either from scratch (with content) or as a copy of an existing version | M | Both request shapes produce a new `versionNumber = max + 1` |
| VER-03 | Version numbers are strictly increasing per prompt under concurrent creation | M | 10 parallel creates yield versions 1..10 with no duplicates |
| VER-04 | List versions in descending order with number, change note, author, timestamp | M | Timeline shows the current version first |
| VER-05 | Read any version's content: messages (role, position, content) and variables | M | Response preserves message order exactly |
| VER-06 | Variables are typed (`string`, `number`, `boolean`, `enum`) with required/default/description | M | An `enum` without values is rejected 422 |
| VER-07 | Every `{{placeholder}}` has exactly one definition and every definition is used | M | Mismatch returns 422 `VARIABLE_MISMATCH` naming both sides |
| VER-08 | Message roles are limited to `SYSTEM`, `USER`, `ASSISTANT` | M | Any other role is rejected 400/422 |
| VER-09 | Change note is optional, max 500 chars | S | Saving without it succeeds; 501 chars returns 422 |
| VER-10 | Limits: 1 MB per version (all messages combined), 100 messages, 100 variables | M | Over-limit returns 422 `CONTENT_TOO_LARGE` with the limit in the message |
| VER-11 | The latest version cannot be deleted; a published version can never be deleted | M | Both cases return 409 `VERSION_IN_USE` |
| VER-12 | A version belonging to another prompt cannot be referenced | M | Cross-prompt version id returns 404 |
| VER-13 | Variable substitution happens in the client; the server never renders prompts | M | No render endpoint exists in v1; the API returns the raw template and the variable schema |

## PUB — Publishing and moderation

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| PUB-01 | Publishing requires an **active existing** category | M | Publish without/with unknown/inactive category → 422 `CATEGORY_REQUIRED` |
| PUB-02 | Publishing pins one version; the gallery serves exactly that version | M | Publishing v2 while v3 exists shows v2's content in the gallery |
| PUB-03 | Publishing is idempotent (publishing the already-published version is a no-op 200) | S | Two identical publish calls produce one state and no error |
| PUB-04 | Unpublish removes the prompt from the gallery without deleting anything | M | Gallery 404 after unpublish; owner still sees all versions |
| PUB-05 | Editing after publish never changes public content (enforced by VER-01 + PUB-02) | M | Publish v2, create v3, gallery still returns v2 |
| PUB-06 | An admin can hide any public prompt with a mandatory reason | M | Hidden prompt disappears from gallery and from `popular`/search |
| PUB-07 | A hidden prompt cannot be republished by its owner; only an admin can unhide | M | Owner publish attempt returns 409 `HIDDEN_BY_ADMIN` |
| PUB-08 | The owner can see that their prompt is hidden and why | M | `GET /prompts/{id}` includes `moderationState` + `hiddenReason` for the owner |
| PUB-09 | Unhiding restores the previous published state (the prompt returns to the gallery) | M | After unhide, the same version is visible again |
| PUB-10 | Deleting a prompt that others forked leaves the forks untouched | M | Fork remains readable after the source is deleted |
| PUB-11 | A published prompt cannot be deleted; the owner must unpublish first | M | 409 `PROMPT_PUBLISHED`; after unpublish the delete succeeds |

## GAL — Public gallery

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| GAL-01 | Browse published prompts with cursor pagination | M | 30 published prompts → 2 pages of 20/10 with no duplicates |
| GAL-02 | Filter by category; sort by `newest` or `popular` (fork count) | M | `sort=popular` orders by `copy_count DESC, id DESC` |
| GAL-03 | Keyword search inside the gallery (`q` over title, description, message bodies) | M | A word unique to one public prompt returns exactly that prompt |
| GAL-04 | A public prompt's **full published content** is readable before forking | M | Detail response includes all messages and variables of the published version |
| GAL-05 | Draft/unpublished versions are never exposed to non-owners | M | A public prompt with an unpublished v3 exposes only v2 via any endpoint |
| GAL-06 | Fork copies the published version into the caller's workspace as a private prompt, filed in the caller's **default collection** | M | Fork returns 201; the copy is private, `versionNumber = 1`, `createdBy` = forker, member of the default collection, lineage recorded |
| GAL-07 | Fork increments the source `copy_count` exactly once per call | M | Two forks → `copy_count` +2; deleting the fork does not decrement |
| GAL-08 | Soft-deleted and admin-hidden prompts never appear in gallery, search, or `popular` | M | Zero occurrences across all three paths |
| GAL-09 | List active categories for the publish picker and gallery filters | M | Inactive categories are absent from the list but existing published prompts keep working |
| GAL-10 | The gallery requires authentication (no anonymous access) | M | No-token request returns 401, not a public page |
| GAL-11 | A fork may be published unchanged; the gallery then shows two entries with a lineage link | M | Publish the fork immediately → 200; both entries visible; source `copyCount` unchanged by publishing |
| GAL-12 | `forkOf` is returned only while the source is visible | M | Source unpublished → fork still readable, `forkOf` absent, no error |

## SRCH — Search

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| SRCH-01 | Keyword search over title, description and all message content | M | Ranking returns the exact match first |
| SRCH-02 | Two scopes: caller's own library (`/prompts?q=`) and the public gallery (`/gallery?q=`) | M | The same query returns disjoint result sets as expected |
| SRCH-03 | Search never returns another user's private prompt under any scope | M | Test with a unique token in a private prompt → zero results in gallery scope |
| SRCH-04 | Empty `q` behaves as browse (no empty-result page) | M | `?q=` and no `q` return the same list |
| SRCH-05 | Search is case-insensitive; substrings shorter than the configured minimum token size may not match | S | Documented behaviour; test asserts a 2-char query returns a defined result (not an error) |
| SRCH-06 | Search input cannot break the query (no boolean operator injection) | M | `+ - * " ( )` in `q` returns 200 with results, never 500 |
| SRCH-07 | The search index is updated in the same transaction as the content change | M | After publishing, the new text is findable immediately |
| SRCH-08 | English-only relevance is accepted; no stemming promise | M | Documented; no acceptance beyond "exact words match" |

## FAV — Favorites

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| FAV-01 | Add/remove a favorite on any visible prompt (own or public) | M | Idempotent both ways: repeat calls do not fail |
| FAV-02 | List favorites with cursor pagination | M | Only the caller's favorites, never another user's |
| FAV-03 | Favoriting a prompt that becomes private/hidden/deleted makes it disappear from the list without an error | S | List omits inaccessible rows rather than failing |
| FAV-04 | The prompt detail indicates whether the caller favorited it | S | `favorited: true/false` present |

## EXP — Export

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| EXP-01 | Export the caller's entire library as one JSON document | M | Envelope contains `schemaVersion`, prompts, versions, collections, tags, favorites |
| EXP-02 | Export a single prompt as Markdown (readable, includes variables and message roles) | S | File opens correctly and contains every message in order |
| EXP-03 | Export never includes another user's data or any secret | M | Grep the export for another user's prompt title → zero hits |
| EXP-04 | Export is a point-in-time consistent snapshot | S | Generated inside one read transaction |
| EXP-05 | Export is available for a 1,000-prompt library within 30 s and streams rather than buffering | S | Measured; no OOM at 10 MB |
| EXP-06 | Import from JSON or third-party tools is **not** in v1 | C | No import endpoint exists |

## ADM — Administration

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| ADM-01 | Admin can create, rename, reorder, deactivate categories; never hard-delete a category in use | M | Deactivating keeps published prompts readable; deletion of in-use category is refused |
| ADM-02 | Admin can hide/unhide any public prompt, with a mandatory reason (PUB-06..09) | M | Reason < 3 chars → 422 |
| ADM-12 | Admin can set an optional category colour (`#RRGGBB`), validated server-side | S | Invalid value → 422 |
| ADM-04 | Admin can list all prompts with metadata only (owner, title, visibility, moderation state, counts) | M | Response contains no message content for private prompts |
| ADM-08 | Every admin action **in the app** is recorded in an `admin_audit` table (actor, action, target, reason, requestId, timestamp) | M | Hide + unhide produce two rows with the correct actor |
| ADM-09 | Admin endpoints never expose private prompt content, only metadata | M | Test asserts no `messages`/`variables` fields in admin prompt responses |
| ADM-10 | The `admin` role is required at the edge; the core re-checks it from metadata | M | Tampered `x-user-roles` (missing admin) on a direct core call → `PERMISSION_DENIED` |
| ADM-11 | User administration (list, create, disable, role changes) happens in the **Keycloak console**, not the app | M | No `/admin/users` route exists; documented procedure verified once |

### Withdrawn in revision 1.1

| ID | Original requirement | Why withdrawn |
|---|---|---|
| ADM-03 | Admin lists all users in-app | Moved to the Keycloak console (ADR-22) |
| ADM-05 | Admin assigns/removes roles in-app | Moved to the Keycloak console; avoids a realm-management credential |
| ADM-06 | Admin disables/enables users in-app | Moved to the Keycloak console |
| ADM-07 | Last-admin guard | Only relevant to in-app role changes; Keycloak blocks removing the last admin itself |

## OPS — Operability

| ID | Requirement | Pri | Acceptance |
|---|---|---|---|
| OPS-01 | `/q/health/live` and `/q/health/ready` on both services | M | Ready reports DOWN when MySQL is unreachable |
| OPS-02 | File logs with rotation: 10 MB, 10 backups, rotate on boot | M | A `.1` file appears after crossing the size; old files pruned |
| OPS-03 | Every request has one `requestId`, propagated edge → core and returned in error bodies and the `X-Request-Id` header | M | `grep <requestId>` finds lines in both services |
| OPS-04 | Dependency failures return 503 with a stable `code` within 5 s; no hanging requests | M | `stop mysql` → 503 `DATABASE_UNAVAILABLE` within 5 s |
| OPS-05 | The service refuses to start if the schema and entities disagree | M | Renaming a column in DDL without the entity aborts startup |
| OPS-06 | No secrets, tokens, or prompt content appear in logs at INFO or below | M | Log review checklist in `06-logging.md §4` passes |
| OPS-07 | All public traffic is TLS-terminated at the reverse proxy | M | Plain HTTP on the public hostname redirects or is refused |
| OPS-08 | Startup succeeds with an empty database (Flyway creates everything) | M | Fresh volume → healthy stack with seeded categories |

---

## Detailed acceptance: the four requirements that carry the most risk

### A. Variables (VER-06/07/10)

```gherkin
Given a version with messages containing {{topic}} and {{audience}}
When I save variables [topic: string required] and [tone: enum unused]
Then the API returns 422 VARIABLE_MISMATCH
  And errors[] names "audience" (undefined) and "tone" (unused)
  And no version row is created
```

### B. Publish pinning (PUB-02/05)

```gherkin
Given a prompt with v1 and v2, v2 published
When I create v3 with different content
Then GET /gallery/{id} still returns v2's messages
  And GET /prompts/{id} shows latestVersion = 3 and publishedVersion = 2
  And search still indexes v2's text, not v3's
```

### C. Moderation (PUB-06..09)

```gherkin
Given a published prompt
When an admin hides it with reason "off-topic"
Then it is absent from /gallery, /gallery?q= and sort=popular
  And the owner sees moderationState = HIDDEN and the reason
  And the owner's publish attempt returns 409 HIDDEN_BY_ADMIN
  And one admin_audit row records action=PROMPT_HIDDEN
When the admin unhides it
Then it reappears in the gallery with the same published version
```

### D. Admin privacy (ADM-04/09)

```gherkin
Given a private prompt owned by alice
When an admin calls GET /admin/prompts
Then the row appears with owner, title, visibility, counts
  And the payload contains no message content and no variables
When the admin calls GET /prompts/{alicePromptId}
Then the response is 404 (the admin has no ownership)
```
