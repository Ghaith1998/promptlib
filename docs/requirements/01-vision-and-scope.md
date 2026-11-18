# 01 — Vision and Scope

---

## 1. Problem statement

A small engineering team accumulates prompts — system messages, review checklists, extraction
templates, translation instructions — in chat histories, Notion pages, Slack threads and personal
notes. Three things go wrong:

1. **They are unfindable.** Months later nobody remembers whether the good one was in a DM or a doc.
2. **They are unversioned.** Someone "improves" a prompt and the original wording is gone; nobody can
   tell which version produced the result they liked.
3. **They are unshareable.** A genuinely good prompt stays with its author because there is no
   low-friction way to publish and copy it.

Existing tools either store text without structure (notes apps) or cost money and require accounts
for everyone (commercial prompt managers). A self-hosted library on the team's own infrastructure
removes both objections.

## 2. Positioning

The product is deliberately **two things at once**, as decided in the requirements interview:

| Role | What that means for the spec |
|---|---|
| **Shared community library** for a 3–10 person dev team | The gallery, categories, fork and copy-count matter. Publishing must be low-friction, and quality must be visible |
| **Learning vehicle** for distributed-systems practice | The scope stays small enough to finish, but the architecture is real: gRPC boundaries, OIDC, Flyway, ArchUnit, resilience. This justifies *not* taking shortcuts in the backend that would make the project trivial |

The tension is acknowledged: a learning vehicle tempts scope creep, and a community library tempts
polish. The resolution is the **MoSCoW split in `02`** — everything marked M is small enough to
build; anything that expands the domain (chains, variants, execution) is deferred with a trigger.

## 3. Personas

### P1 — The practitioner (primary)

A backend/frontend engineer who uses LLMs daily. Has 30–200 prompts in notes. Wants: store, find,
version, copy. Cares about **speed to copy** more than about browsing. Uses mobile and desktop.

Success for P1: "I stopped keeping a second copy in my notes app."

### P2 — The curator (secondary)

The team lead or the person who writes the good prompts. Publishes 5–20 prompts, wants them found and
reused. Cares about categories being meaningful and about the gallery not filling with junk. Also
holds the `admin` role.

Success for P2: "Three people forked my review checklist this month."

### P3 — The occasional consumer (tertiary)

A teammate who never creates prompts but occasionally browses the gallery to copy something.
Logs in rarely; must not be blocked by onboarding friction.

Success for P3: "I found what I needed in under a minute without asking anyone."

### Non-persona — the public internet

There is **no anonymous user** (ADR-10). Every visitor is authenticated. If that changes, the
security model, the rate-limiting requirement, and the gallery's abuse surface all change with it.

## 4. Goals

| # | Goal | Measure (v1) |
|---|---|---|
| G1 | A user can store a prompt and retrieve it weeks later | A prompt created in month 1 is found by keyword in month 2 without remembering its title |
| G2 | Published prompts are reused by colleagues | ≥ 3 distinct users have forked at least one prompt (`copy_count` > 0 on ≥ 3 prompts) |
| G3 | Version history makes prompt changes safe | Zero cases of "I lost the old wording" — every edit leaves the previous version readable |
| G4 | The gallery is worth browsing | ≥ 15 published prompts across ≥ 4 categories within the first month |
| G5 | The system is operable by one person | Restart, upgrade and diagnose happen from `docker compose` + log files, no external tooling |

These are deliberately measurable with data the app already has. "Users are happy" is not a goal
because it cannot be checked.

## 5. Non-goals (v1)

Each of these is a real product decision, not an oversight:

| Non-goal | Why not now | Trigger to revisit |
|---|---|---|
| **LLM execution / playground** | Requires provider keys, cost tracking, streaming, per-provider quirks. It would dominate the build | Users ask to *run* prompts, not just store them |
| **Chains / multi-step workflows** | Useless without execution; needs an execution engine and a graph model | Execution exists |
| **Prompt variants (A/B)** | Versions already provide alternatives over time; concurrent variants need UI and a publish policy | Users maintain parallel variants manually |
| **Team/shared workspace visibility** | The public gallery already covers sharing; a third visibility tier adds tables and authz rules | Someone needs to share with 3 people but not everyone |
| **Import from ChatGPT/Claude/other tools** | Parsing third-party export formats is a project of its own | Export exists and someone asks to migrate in |
| **Semantic search** | Keyword search is adequate under ~500 prompts; Qdrant adds an index to keep in sync | Keyword search visibly misses relevant prompts |
| **Comments / collaboration / activity feed** | Social features on a 10-person tool are noise | Someone asks "who changed this and why" beyond change notes |
| **Notifications (email/push)** | No events worth interrupting anyone for yet | Admin actions need to reach a user (e.g. a hidden prompt) |
| **Anonymous public gallery** | Would need abuse protection, rate limiting, SEO considerations, and a second authz model | The product is offered outside the team |
| **Flutter web client** | Browser token storage is a separate security design (SPA memory tokens or a BFF). Adds CORS, redirect URIs, SPA routing | Someone needs browser access |
| **In-app user administration** | Users, roles and disabling are managed in the Keycloak console. Building it in-app means a high-value service account | Admins find the console too inconvenient |
| **Password reset / email flows** | Keycloak's own reset page covers it; no SMTP in the stack | Users need reset without admin help |
| **Offline writes** | Requires idempotency keys and conflict resolution | Mobile usage in low-connectivity environments |
| **Trash / restore** | Deletion is final by decision; a recovery bin implies a retention policy and a purge job | A user loses something important |
| **Backups, CI/CD, metrics, tracing** | Explicitly deferred in the architecture interview; recorded as accepted risks | First real data loss, or a second deployer |

## 6. Assumptions

1. The team is ≤ 10 users; total prompts ≤ 10,000 in the first year.
2. All users are trusted colleagues. There is no adversarial user inside the system; the threat model
   is "mistake and curiosity", not "attacker with an account".
3. Prompt content is **English**. Search relevance is not required to handle Arabic or other
   tokenisation-heavy languages (see `NFR-SR2`).
4. The host is a single machine with Docker Compose; there is no orchestration, no replica, no
   autoscaling.
5. Users access the product through the Flutter clients (mobile and desktop), not through curl —
   though the OpenAPI document is kept accurate for Swagger and debugging.
6. Keycloak is the only identity source. There are no local passwords, ever.
7. Prompt content is text. No images, no file attachments, no binary assets in v1.
8. One prompt belongs to exactly one owner. Ownership never transfers.

## 7. Scope boundary (what "done" includes)

```
IN                                    │ OUT
──────────────────────────────────────┼──────────────────────────────────────
Account + profile (mirrored)          │ Password management, MFA policy
Private prompts, immutable versions   │ Running prompts
Collections (one level) + tags        │ Nested folder trees
Favorites                             │ Likes, views, comments; trash/restore
Publish/unpublish with categories     │ Scheduled publishing
Public gallery, keyword search, fork  │ Semantic search, recommendations
Whole-library export (JSON + MD)      │ Import, third-party migration
Registration + ROPC login             │ Flutter web client, browser sessions
Admin: categories, moderation, audit  │ In-app user/role administration
File logs + rotation, health          │ Metrics, tracing, log aggregation
Docker Compose deployment             │ Kubernetes, HA, CI/CD
──────────────────────────────────────┴──────────────────────────────────────
```

## 8. Stakeholders

| Role | Interest | Decision authority |
|---|---|---|
| Product owner (you) | Scope, priorities, UX trade-offs | Final on all "what" questions |
| Backend implementer (you) | Domain rules, API shape, operability | Final on "how" within the architecture docs |
| Flutter implementer | Contract stability, error codes, pagination | Consumes `10-flutter-contract.md`; changes go through `/v2` |
| Team users (P1–P3) | Speed, findability, trust in version history | Feedback only in v1 |
| Admin (P2) | Gallery quality, user management | Feedback only in v1 |

## 9. Glossary (product-facing)

| Term | Meaning for users |
|---|---|
| **Prompt** | A named idea in your library. Holds metadata; the words live in a version |
| **Version** | A frozen snapshot of a prompt's messages and variables. Cannot be edited, only superseded |
| **Publish** | Make one version visible to everyone in the gallery, under a category |
| **Unpublish** | Take it out of the gallery. The owner keeps everything |
| **Hide** | An administrator's moderation action. The prompt leaves the gallery and cannot be republished until an admin unhides it |
| **Fork** | Copy a public prompt into your own workspace as a new private prompt |
| **Duplicate** | Copy your own prompt inside your workspace |
| **Collection** | A named list you put prompts in (one level) |
| **Tag** | A free label you attach to prompts, yours only |
| **Gallery** | The public catalog, visible to every logged-in user |
| **Category** | A fixed, admin-managed classification required for publishing |
