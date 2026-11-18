# 06 — Traceability and Architecture Amendments

This is the document that keeps the requirements folder and the architecture folder honest. Every
requirement points at an endpoint, a table, and a test. Every requirement that the current
architecture cannot satisfy is listed as an **amendment** with the exact edit required.

---

## 1. Amendment register

| # | Requirement(s) | What changes | Architecture files to edit |
|---|---|---|---|
| ~~A1~~ | ~~AUTH-03/04/11~~ | **Withdrawn in revision 1.1.** BFF cookie auth for web is not built; replaced by A11 (no web) and A12 (ROPC) | — |
| ~~A2~~ | ~~web redirect URIs / CORS~~ | **Withdrawn.** No browser client, so no web origins, no CORS, no SPA fallback | — |
| **A3** | AUTH-08, AUTH-13 | **Registration service account.** `promptlib-register` (service account, `manage-users` only) + `POST /api/v1/auth/register`. Narrower than the original A3: no realm management, no user listing, no role changes | `05-identity-access.md` (§1, §3.1, §7.1), `03-communication.md` (endpoint), `09-deployment.md` (`KC_REGISTER_CLIENT_SECRET`) |
| **A4** | PUB-06..09, ADM-02, ADM-09 | **Moderation state.** `moderation_state`, `hidden_reason`, `hidden_at`, `hidden_by`; gallery/search filter; owner visibility; `HIDDEN_BY_ADMIN` error code | `01-domain-model.md` (DDL + state machine), `03-communication.md` (endpoints + error code), `04-data-persistence.md` (migration `V5`), `08-testing.md` (matrix) |
| **A5** | COL-05 | **`collection.parent_id`** added now, unused by the v1 API | `01-domain-model.md` (DDL), `04-data-persistence.md` (`V3`) |
| **A6** | ADM-08 | **`admin_audit` table** (append-only), written in the same transaction as the admin action | `01-domain-model.md`, `04-data-persistence.md` (`V6`), `03-communication.md` (no public endpoint in v1) |
| **A7** | AUTH-08 | **Registration through the app's form**, proxying the Keycloak Admin API; the realm's own registration page stays **disabled** | `05-identity-access.md` (§2 realm JSON, §3.1) |
| **A8** | EXP-01, EXP-02 | **Export endpoints** + a versioned export schema; streaming response | `03-communication.md` (endpoints), `04-data-persistence.md` (read-only tx), `10-flutter-contract.md` (download handling) |
| **A9** | VER-10, LIB-01 | **Limits** become explicit validation with stable codes (`CONTENT_TOO_LARGE`, `TOO_MANY_MESSAGES`, `TOO_MANY_VARIABLES`) | `03-communication.md` (error codes), `04-data-persistence.md` (column sizes already adequate) |
| **A10** | AUTH-10, ADM-09 | **Admin metadata-only access** becomes an explicit authorization rule; admin prompt list is a separate projection endpoint, not a widened owner endpoint | `05-identity-access.md` (§5 authorization matrix), `03-communication.md` |
| **A11** | AUTH-01, NFR-C3 | **No web client in v1.** No BFF, no cookies, no CSRF, no CORS, no SPA fallback. The backend is a pure bearer-token resource server | `00` (ADR-21, non-goals), `02`, `03` (transports, CORS off), `09` (NPM), `10` (client contract) |
| **A12** | AUTH-02 | **Direct Access Grant (ROPC).** Client posts credentials straight to Keycloak; `directAccessGrantsEnabled: true`; PKCE documented as a client-side upgrade path | `00` (ADR-12), `05` (§2, §3, §3.2), `10` |
| **A13** | AUTH-13 | **Registration is unauthenticated and unthrottled.** Accepted risk R-3, no mitigation in v1 | `00` (§9 risk 8), `05` (§3.1), `07` |
| **A14** | TAG-06, COL-08, ADM-12 | **Colours** on tags, collections and categories: `CHAR(7)` `#RRGGBB`, nullable, validated | `00` (ADR-29), `01` (DDL), `04` (V1), `05` (export) |
| **A15** | LIB-05, LIB-06, PUB-11, TAG-04, COL-04 | **Deletion model reversed from ADR-17's original**: prompts soft-delete with no restore; published prompts cannot be deleted; tags/collections hard-delete only when unused; memberships cleaned on prompt delete | `00` (ADR-17, risk 6), `01` (§3.4), `03` (409 codes, no restore endpoint), `04` (§10.4), `08` (tests), `11` (risk) |
| **A16** | COL-06, COL-07, GAL-06 | **Default collection** per user: auto-created, undeletable, fork target; `is_default` + `default_marker` | `00` (ADR-28), `01` (§3.6, DDL), `03` (fork response), `04` (V1), `10` |
| **A17** | GAL-06, GAL-11, GAL-12 | **Fork authorship and publishability**: `createdBy` = forker, credit via lineage, fork publishable unchanged, `forkOf` only while the source is visible | `00` (ADR-30), `01` (§3.3), `03` (fork response), `10` |

**No amendment needed** (requirements confirm existing decisions): immutable versions (ADR-06),
pinned publishing (ADR-07), gallery in-domain (ADR-08), category required (ADR-09), UUID v7 (ADR-16),
soft delete (ADR-17), typed variables (ADR-18), forks-only copy count (ADR-19), `/api/v1` (ADR-13),
cursor pagination (ADR-14), metadata trust (ADR-15), registration-only service account (ADR-22).

## 2. Conflicts found and how they were resolved

| # | Conflict | Resolution |
|---|---|---|
| C1 | "Shared community library for a dev team" implies team-private sharing; the architecture has only PRIVATE/PUBLIC | Resolved by the interview: keep per-user private + global public. Recorded as a non-goal with a trigger. **A future TEAM tier is a schema/authorization change, not a config toggle** |
| C2 | Flutter Web was added as a target, conflicting with "security stays in Keycloak" | **Web is dropped from v1.** No browser client means no token storage problem, no BFF, no session. ADR-21 records the withdrawal and the trigger to revisit |
| C3 | "Manage users + roles" vs "no service-to-service credentials" | Split: user/role administration moves to the **Keycloak console**; the only app credential is registration-only (`manage-users`), because a custom signup form was explicitly chosen and needs it |
| C4 | "Hide any public prompt" vs `visibility` being an owner decision | A4 — a separate moderation axis, so the owner cannot undo an admin action |
| C5 | "View all prompts" vs private content privacy | Resolved: metadata only (ADM-09). Content access is not granted to admins at all, which is stricter and simpler |
| C6 | Success metric "curated quality gallery" vs "no featured flag" | Curated by *category + moderation*, not by editorial featuring. `popular` = forks, which is the honest signal |
| C7 | Export as safety net vs "no backups" | Export covers user-level recovery only; the risk register entry stays. It does not replace `09 §7` |
| C8 | "Restore a deleted prompt" (original ADR-17) vs "delete is final" | Resolved for delete-is-final: prompts soft-delete for integrity, no trash and no restore endpoint. `LIB-06` withdrawn; ADR-17 rewritten |
| C9 | "Every prompt has a collection" (original SQL `collection_id NOT NULL`) vs "a prompt can be unfiled" | Resolved: filing is optional; a default collection exists as a fork target, not as an owner |

## 3. Traceability matrix

Legend: **E** = endpoint(s) in `03-communication.md`, **T** = table(s), **A** = architecture doc that
governs it, **Test** = primary verification.

| Requirements | E | T | A | Test |
|---|---|---|---|---|
| AUTH-01..02, 05..13 | all `/api/v1/**`, `POST /auth/register` | `app_user` | 05 | 3-case matrix; registration cases (no token = 201, duplicate = 409, weak = 422) |
| AUTH-05..07 | Keycloak | — | 05 | manual + client contract test |
| AUTH-08..09, 12 | `POST /auth/register`, Keycloak console | `app_user` | 05 | registration integration (optional Keycloak container) |
| ACC-01..05 | `/me` | `app_user` | 03, 05 | idempotent upsert test, concurrent-first-request test |
| LIB-01..10 | `/prompts*` | `prompt`, `prompt_version` | 01, 03, 04 | 3-case matrix + invariant tests; delete cleanup |
| COL-01..08 | `/collections*` | `collection`, `collection_prompt` | 01 | CRUD + membership + empty/default guards + colour validation |
| TAG-01..06 | `/tags` | `tag`, `prompt_tag` | 01 | per-user isolation; rename propagation; TAG_IN_USE |
| VER-01..13 | `/prompts/{id}/versions*` | `prompt_version`, `prompt_message` | 01, 04 | immutability test; variable mismatch; concurrency |
| PUB-01..10 | `/publish`, `/unpublish`, `/admin/prompts/*/hide` | `prompt`, `admin_audit` | 01 | pinning test; hidden-state test |
| GAL-01..12 | `/gallery*` | `prompt`, `prompt_search_document` | 01, 03 | cursor stability; draft-leak test; fork counter, authorship, lineage visibility |
| SRCH-01..08 | `/prompts?q`, `/gallery?q` | `prompt_search_document` | 04 §8 | private-leak test; operator injection test |
| FAV-01..04 | `/favorites`, `/prompts/{id}/favorite` | `favorite` | 01 | idempotency; visibility change behaviour |
| EXP-01..06 | `/export`, `/prompts/{id}/export.md` | all owned rows | 03, 05 | schema snapshot test; cross-user leak grep |
| ADM-01..10 | `/admin/*` | `category`, `prompt`, `admin_audit`, `app_user` | 05 | role gate; metadata-only assertions; audit rows |
| OPS-01..08 | `/q/health/*` | — | 06, 07, 09 | smoke checklist; forced-failure tests; log grep |

## 4. Requirements with no current implementation home

| Requirement | Status | Action |
|---|---|---|
| AUTH-08/13 (registration + open endpoint) | New endpoint and a new credential | Phase 1. It is the only unauthenticated route and the only password path through the backend |
| AUTH-09/ADM-11 (user administration) | Deliberately *not* implemented in the app | Procedure in `05 §7`; documented in the runbook, no code |
| EXP-01/02 (export) | New endpoints | Phase 5 (cheap, high value, only user safety net) |
| ADM-08 (audit) | New table | Phase 4 with the moderation work; transactional with the action |
| COL-05 (`parent_id`) | New column | Add in `V3` while the table is small |

## 5. Deferred requirements → architecture roadmap

| Deferred | Architecture entry | Trigger |
|---|---|---|
| JSON/third-party import (EXP-06) | `11-roadmap.md` future work | Export exists + someone asks |
| Semantic search | `11-roadmap.md` (Qdrant) | Keyword search misses relevant prompts |
| Execution / chains / variants | `11-roadmap.md` (execution service) | Users want to run prompts |
| Team visibility | `11-roadmap.md` future work | Someone needs to share with a subset |
| Flutter web client | `11-roadmap.md` future work | Someone needs browser access (decide token strategy first) |
| Featured prompts | `11-roadmap.md` (curation) | Gallery quality drops |
| Notifications | `11-roadmap.md` | Admin actions need to reach users |
| Rate limiting | `11-roadmap.md` | Users stop being trusted |
| Backups | `09-deployment.md §7` | Any irreplaceable data |
| Metrics/tracing | `11-roadmap.md` | Logs stop being enough |

## 6. Release gate (v1 is done when)

- [ ] Every M requirement in `02` has a passing acceptance check.
- [ ] Every authenticated endpoint has the three security cases; registration has its own three
      (no token succeeds, duplicate 409, weak password 422).
- [ ] Cross-user leak tests pass for library, gallery, search, favorites, export and admin.
- [ ] Immutability, publish pinning, moderation, fork authorship/lineage and fork-counter tests pass.
- [ ] Delete guards pass: `PROMPT_PUBLISHED`, `TAG_IN_USE`, `COLLECTION_NOT_EMPTY`, `DEFAULT_COLLECTION`.
- [ ] ArchUnit is green; schema `validate` passes on a clean volume.
- [ ] The 10-step smoke test in `architecture/08 §8` passes on the target host.
- [ ] Forced failures (`stop core`, `stop mysql`) produce 503 + stable code within 5 s.
- [ ] One `requestId` is greppable across both services.
- [ ] Export produces a valid document that contains every owned prompt.
- [ ] Registration works end to end: form → 201 → ROPC login → `/me`.
- [ ] The architecture folder reflects amendments A3–A13 (A1/A2 withdrawn).
