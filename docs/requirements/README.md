# Prompt Library — Application Requirements Specification

**Version:** 1.0 (draft) · **Date:** 2026-11-18 · **Status:** decisions locked, not implemented
**Companion:** `../architecture/` (13 architecture documents + 30 ADRs)

---

## 1. Purpose

This folder specifies **what** the application must do, for whom, and how we will know it works.
The architecture folder specifies **how** it is built. Where the two disagree, this specification
wins (requirements drive architecture) — but every disagreement is listed in
[`06-traceability.md`](06-traceability.md) with an explicit amendment to the architecture docs.

## 2. Document map

| Doc | Answers |
|---|---|
| [`01-vision-and-scope.md`](01-vision-and-scope.md) | Why does this exist, who is it for, what is explicitly out? |
| [`02-functional-requirements.md`](02-functional-requirements.md) | 109 requirements by module, with MoSCoW priority and acceptance criteria; 9 withdrawn across revisions 1.1–1.2 |
| [`03-user-journeys.md`](03-user-journeys.md) | End-to-end flows, including failure and empty states |
| [`04-non-functional-requirements.md`](04-non-functional-requirements.md) | Performance, availability, security, usability, maintainability targets |
| [`05-data-requirements.md`](05-data-requirements.md) | Content limits, export format, audit records, retention |
| [`06-traceability.md`](06-traceability.md) | Requirement → ADR → endpoint → table → test; **architecture amendments** |

## 3. Product in one paragraph

A **prompt library for a small engineering team**. Every user has a private workspace where they
create prompts, organize them into collections and tags, and build immutable versions. A user can
publish a version to a **public gallery** under a fixed category taxonomy, where any logged-in
colleague can read the full content and fork it into their own workspace. There is no LLM execution:
the product stores, organizes, versions, and shares prompts — the closest thing to execution is
client-side variable substitution and a copy button.

## 4. Requirements conventions

| Term | Meaning |
|---|---|
| **M** (Must) | v1 cannot ship without it |
| **S** (Should) | Planned for v1, can slip one release without breaking the product |
| **C** (Could) | Recorded, explicitly not v1 |
| **FR-xxx** | Functional requirement, grouped by module prefix |
| **NFR-xxx** | Non-functional requirement |
| **Acceptance** | Observable check that a tester or an automated test can perform |

Priority distribution: **M = 88, S = 20, C = 1** across 109 active requirements, plus 9 withdrawn
(web BFF, cookie transport, CSRF, in-app user administration, restore). A requirement without an
acceptance check is a wish, so every FR below has one.

## 5. Scope summary

**In v1:** ROPC login against Keycloak, registration through a custom form, private library with
collections, tags, favorites, immutable versions, typed variables, publishing with categories, public
gallery with keyword search and fork, whole-library export, admin moderation with an audit trail,
file logging with rotation.

**Out of v1:** LLM execution, chains/workflows, prompt variants, import from other tools, semantic
search, team/workspace sharing, comments, notifications, public anonymous access, metrics/tracing,
automated backups, CI/CD, mobile offline writes.

## 6. How to use this during implementation

1. Pick the phase in `11-roadmap.md` of the architecture folder.
2. Take the FRs whose module matches that phase.
3. Write the acceptance check as a test **before** the code — especially the three security cases
   (`08-testing.md §4`).
4. When a requirement changes, update this folder **and** the traceability table. A requirement
   that only exists in a chat message does not exist.
