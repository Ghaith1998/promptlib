# 08 — Testing Strategy

**Depends on:** requirement #6, interview decision: core set + ArchUnit, and the explicit rule:

> **Every endpoint gets three security tests: (1) no auth, (2) wrong auth, (3) correct auth.**

Plus the cross-cutting cases added by the requirements round: admin metadata-only (ADR-23/24),
export data isolation (ADR-25), and the registration endpoint (ADR-22/27).

---

## 1. Pyramid for this project

```
        ┌──────────────────────────┐
        │  Manual compose smoke     │  ~10 checks, §8
        ├──────────────────────────┤
        │  @QuarkusTest REST (edge) │  contract + security matrix
        ├──────────────────────────┤
        │  Domain unit tests (core) │  pure JUnit, no Quarkus, no DB
        ├──────────────────────────┤
        │  ArchUnit (core)          │  module boundaries
        └──────────────────────────┘
```

No end-to-end Flutter tests here (client repo). No performance tests in v1. No contract-test broker:
the proto lives in the same reactor as both consumers, so `buf breaking` (when CI exists) is the
contract gate.

## 2. Dependencies

```xml
<!-- edge & core -->
<dependency>
  <groupId>io.quarkus</groupId><artifactId>quarkus-junit5</artifactId><scope>test</scope>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId><artifactId>quarkus-test-security</artifactId><scope>test</scope>
</dependency>
<dependency>
  <groupId>io.rest-assured</groupId><artifactId>rest-assured</artifactId><scope>test</scope>
</dependency>
<dependency>
  <groupId>com.tngtech.archunit</groupId><artifactId>archunit-junit5</artifactId>
  <version>1.3.0</version><scope>test</scope>
</dependency>
```

Testcontainers MySQL comes for free through Quarkus Dev Services; no explicit dependency unless you
want a custom container.

## 3. Test configuration

```properties
# src/test/resources/application.properties  (edge)
%test.quarkus.oidc.enabled=false          # @TestSecurity bypasses the mechanism entirely
%test.quarkus.log.file.enable=false
%test.quarkus.grpc.clients.core.host=localhost
%test.quarkus.grpc.clients.core.port=9001  # see §5 for the in-process option
```

```properties
# core
%test.quarkus.datasource.db-kind=mysql
%test.quarkus.datasource.jdbc.url=          # empty => Dev Services starts a MySQL container
%test.quarkus.flyway.clean-at-start=true
%test.quarkus.hibernate-orm.schema-management.strategy=validate
%test.quarkus.log.file.enable=false
```

`%test.quarkus.datasource.jdbc.url=` (empty) is the switch that lets Dev Services own the container.
Docker must be running locally; there is no H2 fallback on purpose — MySQL-vs-H2 SQL differences
would make the Flyway migrations a lie.

## 4. The security matrix (mandatory)

For **each** endpoint in `03-communication.md §2.2`, three tests. Tabular form for one resource:

| # | Endpoint | Case | Setup | Expected |
|---|---|---|---|---|
| 1 | `GET /api/v1/prompts` | no token | no `@TestSecurity` | 401 |
| 2 | `GET /api/v1/prompts` | wrong auth | `@TestSecurity(user="bob")`, prompt owned by alice | 200 + empty list (bob sees his own, not hers) |
| 3 | `GET /api/v1/prompts` | correct auth | `@TestSecurity(user="alice", roles="user")`, prompt owned by alice | 200 + 1 item |
| 4 | `GET /api/v1/prompts/{id}` | no token | — | 401 |
| 5 | `GET /api/v1/prompts/{id}` | wrong auth | `@TestSecurity(user="bob")`, alice's prompt | 404 (not 403 — no existence leak) |
| 6 | `GET /api/v1/prompts/{id}` | correct auth | owner | 200 |
| 7 | `POST /api/v1/admin/categories` | no token | — | 401 |
| 8 | `POST /api/v1/admin/categories` | wrong role | `@TestSecurity(user="alice", roles="user")` | 403 |
| 9 | `POST /api/v1/admin/categories` | correct role | `@TestSecurity(user="admin", roles="admin")` | 201 |
| 10 | `POST /api/v1/admin/prompts/{id}/hide` | wrong role | `@TestSecurity(user="alice", roles="user")` | 403 |
| 11 | `POST /api/v1/admin/prompts/{id}/hide` | correct role | admin, published prompt | 200 + `admin_audit` row |
| 12 | `GET /api/v1/admin/prompts` | correct role | admin, alice's **private** prompt exists | 200, metadata present, **no `messages`/`variables` fields** |
| 13 | `GET /api/v1/export` | another user's data | two users with prompts | 200; export contains only the caller's titles |
| 14 | `POST /api/v1/auth/register` | **no token is correct** | anonymous payload | 201; the endpoint must not require auth |
| 15 | `POST /api/v1/auth/register` | duplicate username | existing "alice" | 409 `USERNAME_TAKEN`, no second account |
| 16 | `POST /api/v1/auth/register` | weak password | policy-violating value | 422 `WEAK_PASSWORD`, no account |
| 17 | `DELETE /api/v1/prompts/{id}` | published prompt | owner, `visibility=PUBLIC` | 409 `PROMPT_PUBLISHED`, row still visible |
| 18 | `DELETE /api/v1/prompts/{id}` | unpublished | owner | 204; hidden everywhere; tag and collection freed |
| 19 | `DELETE /api/v1/tags/{id}` | attached tag | prompt carries the tag | 409 `TAG_IN_USE` |
| 20 | `DELETE /api/v1/collections/{id}` | non-empty / default | prompt inside; or `is_default` | 409 `COLLECTION_NOT_EMPTY` / `DEFAULT_COLLECTION` |
| 21 | `POST /gallery/{id}/fork` | lineage hidden | source unpublished after fork | fork readable, `forkOf` absent, no error |

Row 14 is the exception that proves the rule: registration is the only endpoint where the
"no auth" case expects success. Keep it in the matrix so that a future blanket `@RolesAllowed` cannot
silently lock out signup.

"Wrong auth" is not one case — distinguish:

- **wrong role** → 403 (endpoint exists, role missing);
- **wrong owner** → 404 (resource is invisible to this caller);
- **expired/invalid token** → covered once at the mechanism level, not per endpoint.

Example:

```java
@QuarkusTest
class PromptResourceSecurityTest {

    @Test
    void noToken_returns401() {
        given().when().get("/api/v1/prompts").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "bob")
    void otherUsersPrompt_isInvisible() {
        String alicePrompt = Fixtures.promptOwnedBy("alice");
        given().when().get("/api/v1/prompts/" + alicePrompt)
               .then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "alice", roles = "user")
    void owner_canRead() {
        String id = Fixtures.promptOwnedBy("alice");
        given().when().get("/api/v1/prompts/" + id)
               .then().statusCode(200)
               .body("title", notNullValue());
    }
}
```

`@TestSecurity` must be applied at method or class level; forgetting it on a "no auth" test is the
most common way this matrix silently stops testing anything. Assert the status code explicitly —
`then().statusCode(401)` is the test.

**Enforcement:** a checklist table in the PR template (or a test naming convention
`<Resource>_<endpoint>_<case>`) keeps the matrix complete as endpoints are added. If an endpoint is
added without three tests, that is a review rejection, not a nice-to-have.

## 5. Testing gRPC without flakiness

Two options; use both, for different purposes:

| Purpose | Approach |
|---|---|
| Core's gRPC services | `@QuarkusTest` on `core` + inject the service bean directly (`@Inject PromptGrpcService`), bypassing the network. Fast, deterministic |
| Edge ↔ core contract | `@QuarkusTest` on `core` with `quarkus.grpc.server.test-port`, edge test profile pointing at it. One test per RPC asserting the happy path + one error path |
| Failure paths in edge | `@InjectMock` the gRPC client bean; simulate `StatusRuntimeException` and assert 503/504 mapping + circuit breaker behaviour |

Do **not** spin the whole compose stack in tests. That is what §8 is for.

## 6. Domain unit tests (core, no Quarkus)

Pure JUnit on aggregate methods — no `@QuarkusTest`, no DB, milliseconds:

```java
class PromptPublishTest {

    @Test
    void publish_requiresCategory() {
        Prompt p = PromptFixture.privatePromptWithVersion(1);
        assertThrows(ValidationFailed.class,
            () -> p.publish(version(1), null));
    }

    @Test
    void publish_pinsVersion_andDoesNotTouchContent() {
        Prompt p = PromptFixture.privatePromptWithVersion(2);
        PromptVersion v2 = p.version(2);
        String before = v2.messages.get(0).content;

        p.publish(v2, activeCategory());

        assertEquals(Visibility.PUBLIC, p.visibility);
        assertEquals(v2.id, p.publishedVersionId);
        assertEquals(before, p.version(2).messages.get(0).content);
    }

    @Test
    void createNewVersion_neverMutatesPrevious() {
        // property-style: snapshot v1, create v2 from v1, mutate v2 draft, assert v1 unchanged
    }
}
```

Assert the invariants from `01-domain-model.md §7` here, not through HTTP. HTTP tests should not be
the place where "a version is immutable" is proven.

Statement-level guard for immutability (optional but cheap): enable Hibernate statistics in a test,
run a "create new version" flow, assert zero `update` statements against `prompt_version`.

## 7. ArchUnit rules

```java
@AnalyzeClasses(packages = "com.promptlib", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule shared_must_not_depend_on_domains =
        noClasses().that().resideInAPackage("..shared..")
            .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..persistence..", "..grpc..");

    @ArchTest
    static final ArchRule domains_do_not_see_transport =
        noClasses().that().resideInAnyPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.ws.rs..", "io.grpc..", "com.promptlib.proto..");

    @ArchTest
    static final ArchRule domains_do_not_see_persistence =
        noClasses().that().resideInAnyPackage("..domain.prompt..", "..domain.gallery..")
            .should().dependOnClassesThat().resideInAPackage("..persistence..");

    @ArchTest
    static final ArchRule grpc_does_not_touch_panache =
        noClasses().that().resideInAPackage("..grpc..")
            .should().dependOnClassesThat().resideInAnyPackage("io.quarkus.hibernate.orm.panache..", "jakarta.persistence..");

    @ArchTest
    static final ArchRule gallery_reaches_prompt_only_through_ports =
        classes().that().resideInAPackage("..domain.gallery..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..domain.gallery..", "..shared..", "java..", "org.jboss.logging..");
}
```

The last rule is the one that matters: it is the mechanical expression of ADR-04/ADR-08. Without it,
"modular first" is a comment.

## 8. Manual compose smoke test

Run after every deploy (`09-deployment.md §6`):

1. `docker compose ps` — all healthy.
2. `GET /q/health/ready` on edge — UP.
3. Login via Keycloak, obtain token.
4. Create prompt → 201; create version → 201; publish with category → 200.
5. `GET /api/v1/gallery` → contains the published prompt.
6. Fork it → 201; source `copyCount` incremented.
7. Search for a word from the body → found; search for a word only in a private prompt → not found.
8. Delete the prompt → gone from gallery and search; the tag it used becomes deletable; the collection may become empty.
9. `docker compose stop core` → gallery returns 503 with `code=UPSTREAM_UNAVAILABLE`; `start` → recovers.
10. `grep <requestId>` finds lines in both services' log files.
11. Admin hides the prompt with a reason → gone from gallery + search; owner sees the reason;
    `admin_audit` has one row; unhide restores it.
12. Export the library → JSON contains the published prompt and its versions; Markdown export opens.
13. Registration: `POST /auth/register` with no token → 201; duplicate username → 409; the new user
    can log in through Keycloak (ROPC) and `/me` returns a profile.
14. Fork a public prompt → appears in the default collection; its version's `createdBy` is the forker;
    source `copy_count` incremented.
15. Delete a published prompt → 409; unpublish then delete → 204; the tag it used becomes deletable.

## 9. Coverage and gates

| Area | Target | Rationale |
|---|---|---|
| `domain.*` (core) | ≥ 80 % line | Rules live here; bugs here are expensive |
| gRPC mappers | happy + one error path each | Mapping bugs are silent |
| edge resources | 100 % of endpoints have the 3-case matrix | Security regression guard |
| ArchUnit | must pass | Boundary regression guard |
| Overall | ≥ 65 % | Avoid theatre coverage; don't chase 90 % on DTOs |

No mutation testing in v1. No Pact. No Selenium.

## 10. Fixtures

One `Fixtures` helper per test module, building entities through the domain API (not raw SQL), so
fixtures cannot create states the production code forbids:

```java
Fixtures.user("alice");                 // app_user with fixed UUID v7 for deterministic assertions
Fixtures.promptOwnedBy("alice");        // private, v1 with a system+user message
Fixtures.publicPrompt("bob", "coding"); // published, category resolved by slug
Fixtures.category("coding");            // from the seeded V2 set
```

Use **fixed** UUIDs in fixtures (not random) so failure output is readable and cursors are stable in
assertions. The seeded categories already have stable ids from `V2__seed_categories.sql`.
