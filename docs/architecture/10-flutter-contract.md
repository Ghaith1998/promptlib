# 10 — Flutter Integration Contract

**Depends on:** ADR-10 (all authenticated except registration), ADR-12 (ROPC), ADR-13 (`/api/v1`),
ADR-14 (cursor), ADR-15 (identity from token), ADR-27 (registration).

This document is the boundary between the backend and the Flutter app. The backend team owns it;
the client implements it. Any deviation is a backend bug or a conscious `/v2`.

---

## 1. Client responsibilities

| Concern | Client behaviour |
|---|---|
| Auth | ROPC: credentials posted directly to Keycloak; token refresh in the client |
| Token storage | access token in memory; refresh token in `flutter_secure_storage` |
| Refresh | single-flight on 401; on failure, clear tokens and re-login |
| `X-Request-Id` | optional to send, but **always display the `requestId` from an error body** |
| Base URL | one constant, e.g. `https://promptlib.example.com/api/v1` |
| Timeouts | 10 s for reads, 20 s for writes; never infinite |
| Retry | none on 4xx; one retry on 5xx/network for **GET only** |
| Version pin | no version negotiation; the app is built against `/v1` |

## 2. Authentication flow

```
launch
 ├─ refresh token present? ──yes──▶ POST /token (grant_type=refresh_token)
 │                                   ├─ 200 → authenticated
 │                                   └─ 400 invalid_grant → wipe, go to login
 └─ no ──▶ login screen ──▶ ROPC against Keycloak ──▶ tokens
```

The app talks to Keycloak **directly** for login and registration goes through the backend:

```
POST {issuer}/protocol/openid-connect/token     # grant_type=password, client_id=promptlib-app
POST {issuer}/protocol/openid-connect/logout    # client_id + refresh_token
POST /api/v1/auth/register                      # our API, unauthenticated
```

There is no `GET /auth` redirect, no callback, no cookie. The backend never participates in login
(ADR-12). Registration is the only call that carries a password to our API.

Details that break in practice:

- **Do not** store the access token in shared preferences. It is short-lived for a reason.
- **Do not** cache an access token across app restarts; refresh on launch instead.
- `refresh_token` rotation is on (`revokeRefreshToken=true`): two concurrent refreshes with the same
  token will invalidate the session. Single-flight is mandatory.
- Logout must call Keycloak's token endpoint with the refresh token; deleting local tokens leaves the
  Keycloak session alive.
- On `401` with `error="invalid_token"` → refresh. On `401` because the user was disabled → refresh
  also fails; show the login screen, not a retry loop.
- Never put a `client_secret` in the app: `promptlib-app` is public.

## 3. Screen → endpoint map

| Screen | Calls |
|---|---|
| Login | Keycloak only (ROPC) |
| Register | `POST /api/v1/auth/register` (no token) |
| My library | `GET /prompts` (cursor, filters) |
| Prompt detail | `GET /prompts/{id}` + `GET /prompts/{id}/versions` |
| Version detail | `GET /prompts/{id}/versions/{n}` |
| Editor (new prompt) | `POST /prompts` |
| Editor (new version) | `POST /prompts/{id}/versions` → then edit locally → `POST …/publish` |
| Publish sheet | `GET /categories` → `POST /prompts/{id}/versions/{n}/publish` |
| Gallery | `GET /gallery` (cursor, `categoryId`, `q`, `sort`) |
| Public prompt | `GET /gallery/{id}` → Fork / Favorite actions |
| After fork | Navigate to the new prompt in the default collection; show "Forked from …" only if `forkOf` is present |
| Collections | `GET/POST/PATCH/DELETE /collections` |
| Tags | `GET /tags` (inline creation happens through prompt update) |
| Favorites | `GET /favorites`, `PUT/DELETE /prompts/{id}/favorite` |
| Profile | `GET /me`, `PATCH /me` |
| Settings → Export | `GET /export`, `GET /prompts/{id}/export.md` |
| Admin: categories | `GET /categories`, `/admin/categories` |
| Admin: moderation | `GET /admin/prompts`, `POST /admin/prompts/{id}/hide|unhide` |

The editor's most important rule: **there is no "edit version" call.** The client forks a version
server-side (`POST /versions`) and edits the *draft in memory* until it saves content — and since
versions are immutable, "save" means creating the version with its content in one call. Practical
sequence:

```json
POST /api/v1/prompts/{id}/versions
{ "fromVersionId": "…", "changeNote": "Shorter intro" }
→ 201 { "versionNumber": 4, "messages": [...copy of source...], "variables": [...] }

# the client then edits locally and creates a NEW version with the final content
POST /api/v1/prompts/{id}/versions
{ "changeNote": "Shorter intro", "messages": [...], "variables": [...] }
```

If that feels wasteful, the alternative is `POST /versions` accepting full content on creation
(supported — `fromVersionId` is optional). Use that: **one call** to create a version with content,
no copy-then-edit. The copy form exists only for "duplicate and tweak" in the UI when the user has
no unsaved changes. Pick one per screen and document it in the client repo.

## 4. Cursor pagination in the client

```dart
Future<Page<PromptSummary>> fetch({String? cursor}) async {
  final res = await api.get('/gallery', query: {
    'limit': 20,
    if (cursor != null) 'cursor': cursor,
    if (query.isNotEmpty) 'q': query,
  });
  return Page(items: res.items, next: res.nextCursor);
}
```

Rules:

- Never send `page`/`offset`; the server ignores unknown params but the behaviour would surprise you.
- Treat `nextCursor == null` or `hasMore == false` as end of list.
- Reset the cursor whenever filters change. Reusing a cursor across a different filter yields a
  valid but nonsensical slice (keyset comparison against a different order).
- Deduplicate by `id` when appending: a concurrent insert can shift nothing, but a retried request
  can duplicate a page.

## 5. Error handling contract

```json
{
  "type": "https://promptlib.local/problems/variable-mismatch",
  "title": "…", "status": 422, "detail": "…", "instance": "/api/v1/prompts",
  "code": "VARIABLE_MISMATCH",
  "requestId": "0193a1f2-…",
  "errors": [{"field": "variables[1].name", "code": "UNUSED_VARIABLE", "message": "…"}]
}
```

Client mapping (switch on `code`, never on `detail`):

| `code` | UI behaviour |
|---|---|
| `VARIABLE_MISMATCH` | Inline editor error, focus the offending variable |
| `CATEGORY_REQUIRED` | Open the category picker |
| `CONTENT_TOO_LARGE` / `TOO_MANY_MESSAGES` / `TOO_MANY_VARIABLES` | Block the save, show the actual limit |
| `VERSION_IN_USE` | Explain why the delete is refused |
| `HIDDEN_BY_ADMIN` | Show the admin's reason; disable publish/unpublish |
| `PROMPT_PUBLISHED` | Offer "unpublish, then delete" as two explicit steps |
| `TAG_IN_USE` | Show how many prompts use the tag; offer to detach |
| `COLLECTION_NOT_EMPTY` / `DEFAULT_COLLECTION` | Explain that contents must move first; the default is permanent |
| `USERNAME_TAKEN` | Inline field error on the register form |
| `WEAK_PASSWORD` | Show the Keycloak policy hint on the register form |
| `STALE_WRITE` | Reload the prompt and re-apply the user's change |
| `PROMPT_DELETED` | Remove from local cache, pop the screen |
| `UPSTREAM_UNAVAILABLE` / `DATABASE_UNAVAILABLE` / `IDENTITY_PROVIDER_UNAVAILABLE` | Banner "service unavailable", retry button, show `requestId` |
| `INVALID_CURSOR` | Bug: reset the list and log `requestId` |
| unknown `code` | Generic error + `requestId` (never show `detail` raw) |

`requestId` goes in the error dialog's "details" section or the clipboard. It is the only thing that
makes log-based support possible.

## 6. Local cache (client-side, no backend implications)

| Data | Cache policy |
|---|---|
| Prompt list | In-memory for the session; no disk cache in v1 |
| Prompt detail | In-memory; invalidate after create/publish/delete |
| Gallery | In-memory; pagination cursor kept per filter |
| Categories | Disk cache with a 24 h TTL — taxonomy changes rarely, and this removes a round-trip from the publish sheet |
| Drafts in the editor | **Local disk**, per prompt, so a crash does not lose work. Never upload until the user saves |
| Tokens | Refresh token only, secure storage (§2) |

No offline mutations in v1. No sync queue: an offline "publish" would need idempotency keys
(`11-roadmap.md`).

## 7. Deep links (client-side)

The backend exposes UUIDs, not slugs (ADR-16). A share link is:

```
https://promptlib.example.com/p/{uuid}
```

The client resolves it with `GET /gallery/{uuid}`. Because a published prompt may later be
unpublished, hidden or deleted, the client must handle 404 with a "no longer public" screen rather
than a crash. Do not assume a shared link is permanent — that is a product decision, not a technical
one.

Mobile and desktop register a deep-link handler for the app scheme. There is no web build in v1, so
there is no browser route to preserve.

## 8. What the client must **not** do

- Call `core` gRPC directly, or any URL other than `/api/v1`.
- Log, persist, or send the password anywhere except Keycloak's token endpoint (login) or
  `/api/v1/auth/register` (signup).
- Trust that a prompt's public content equals its latest version — always read `publishedVersion`.
- Send `userId`/`ownerId` in a body. Identity comes from the token; server-side fields are ignored.
- Build pagination from array length or `total`. There is no `total`.
- Render `detail` strings to end users without a `code` mapping; they are for developers.
- Retry `POST /fork` automatically.

## 9. Future client-facing changes (planned, not built)

| Change | Breaking? | Note |
|---|---|---|
| Semantic search (`GET /gallery?q=…&mode=semantic`) | No | Query param + new `sort` value |
| Prompt execution / streaming | No | New endpoint family, SSE or chunked JSON |
| Idempotency keys on POST | No | Optional header |
| `/api/v2` for breaking REST changes | Yes | Only with a new path prefix, never in place |
| Offline sync | No | Additive endpoints for batch operations |
