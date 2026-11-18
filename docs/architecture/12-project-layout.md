# 12 — Project Layout, Naming and Guidelines

**Depends on:** ADR-01 (two deployables), ADR-03 (modular-first), ADR-04 (edge as ACL), ADR-20
(ArchUnit).
**Companion:** `../../database/V1__baseline.sql` (authoritative schema) and
`../requirements/02-functional-requirements.md`.

---

## 1. Repository root

```
promptlib/
├── pom.xml                     parent, packaging=pom, modules: proto, core, edge
├── mvnw / mvnw.cmd / .mvn/     Maven wrapper — no global Maven required
├── README.md
├── .editorconfig
├── .gitignore
├── .env.example
│
├── promptlib-proto/            contracts only, no hand-written Java
├── promptlib-core/             domain + persistence + gRPC server
├── promptlib-edge/             REST + OIDC + OpenAPI + gRPC clients
│
├── database/                   V1__baseline.sql (authoritative), V1__baseline.dbml, seed
├── deploy/                     compose, Keycloak realm, MySQL init
├── docs/
│   ├── architecture/           00…12
│   └── requirements/           README…06
└── scripts/                    smoke.sh, dbml-check.sh
```

Three modules, not four. There is **no shared module** between edge and core: sharing DTOs or
exceptions would collapse the anti-corruption layer described in `02 §1`. The only shared artifact is
`promptlib-proto` (generated types).

`database/` is the single source of truth for DDL. The core module copies `V*.sql` onto the classpath
at build time (Maven `<resources>`), so Flyway never reads a second, drifting copy.

## 2. `promptlib-core`

```
com.promptlib
├── domain/
│   ├── prompt/   { model, port, service }
│   ├── library/  { model, port, service }
│   ├── gallery/  { model, port, service }
│   ├── category/ { model, port, service }
│   ├── account/  { model, port, service }
│   ├── admin/    { model, port, service }
│   └── export/   { model, service }
├── persistence/
│   ├── Jpa*Repository.java
│   ├── SearchDocumentProjector.java
│   └── projection/          GalleryRow, PromptSummaryRow, AdminPromptRow
├── grpc/
│   ├── *GrpcService.java
│   ├── mapper/              *ProtoMapper
│   └── interceptor/         CallerContextInterceptor, GrpcErrorInterceptor
├── adapter/
│   ├── keycloak/            KeycloakRegistrationAdapter
│   └── time/                SystemClock
├── shared/
│   ├── CallerContext, Ids, Clock, Cursor, Page, ProblemCode
│   ├── error/               DomainException, NotFound, Forbidden, Conflict,
│   │                        ValidationFailed, UpstreamUnavailable
│   ├── port/                PromptLookup, UserLookup, CategoryLookup
│   └── validation/          Colour, VariableSchemaRules
└── config/
```

### What a `port` is

An interface the domain owns and infrastructure implements:

```java
// domain/prompt/port/PromptRepository.java      (owned by the domain)
public interface PromptRepository {
    Optional<Prompt> findOwned(UUID id, UUID ownerId);
    void persist(Prompt prompt);
}

// persistence/JpaPromptRepository.java          (owned by infrastructure)
@ApplicationScoped
public class JpaPromptRepository implements PromptRepository { … }
```

The dependency arrow points inward. Five reasons it earns its keep here:

1. ArchUnit has something concrete to enforce; otherwise "domain must not depend on persistence" is
   violated on day one.
2. Use-case tests run without a database (fake repository, milliseconds).
3. Cross-domain access is explicit: `domain.gallery` uses `shared.port.PromptLookup`, which becomes a
   gRPC call the day gallery is extracted (`02 §4`).
4. `KeycloakRegistrationPort` + adapter is the only way to test registration without a container.
5. Repositories return `Optional<Prompt>`, never a `PanacheQuery`, `EntityManager` or an open session — no lazy-loading
   surprises leaking into the domain.

Every `port` package carries a `package-info.java` stating: *implementations live in `persistence` or
`adapter`; nothing here may import Panache, Hibernate, gRPC or JAX-RS.*

### Coarse query ports

List screens do not go through aggregate repositories. Each read concern gets one interface returning
projections:

| Port | Returns | Used by |
|---|---|---|
| `prompt/port/LibraryQuery` | `PromptSummaryRow` | `GET /prompts` |
| `gallery/port/GalleryQuery` | `GalleryRow` | `GET /gallery`, search |
| `admin/port/AdminQuery` | `AdminPromptRow` | `GET /admin/prompts` |

One interface per concern, not per table, and never an entity in a list payload.

## 3. `promptlib-edge`

```
com.promptlib.edge
├── api/
│   ├── *Resource.java
│   └── dto/{prompt,version,gallery,library,admin,common}
├── mapping/      *DtoMapper
├── client/       *Client  (gRPC stubs, deadlines, fault tolerance)
├── security/     RequestIdFilter, IdentityPropagationFilter, Roles
├── error/        ProblemExceptionMapper, GrpcStatusMapper, ProblemResponse
└── config/       OpenApiConfig, JacksonConfig
```

No `service/`, no `repository/`, no persistence. A JAX-RS method is: validate → map → call client →
map. If it needs an `if` that is not validation, the rule belongs in core.

## 4. Naming

| Artifact | Convention | Example |
|---|---|---|
| Maven module | `promptlib-<role>` | `promptlib-core` |
| Root package | `com.promptlib` (core), `com.promptlib.edge` (edge) | — |
| Aggregate root | singular noun | `Prompt`, `PromptVersion` |
| Value object | immutable noun | `Colour`, `Visibility`, `VariableDefinition` |
| Use-case service | `<Aggregate>Service` / `<Verb>Service` | `PromptService`, `PublishService` |
| Repository port | `<Aggregate>Repository` | `PromptRepository` |
| Query port | `<Concern>Query` | `GalleryQuery` |
| External port | `<System>Port` | `KeycloakRegistrationPort` |
| Persistence adapter | `Jpa<Aggregate>Repository` | `JpaPromptRepository` |
| External adapter | `<System>Adapter` | `KeycloakRegistrationAdapter` |
| gRPC implementation | `<Domain>GrpcService` | `GalleryGrpcService` |
| Edge client | `<Domain>Client` | `GalleryClient` |
| REST resource | `<Domain>Resource` | `PromptResource` |
| Request DTO | `<Verb><Thing>Request` | `CreatePromptRequest` |
| Response DTO | `<Thing><Shape>Response` | `PromptSummaryResponse` |
| Mapper | `<Thing>ProtoMapper` / `<Thing>DtoMapper` | `PromptDtoMapper` |
| Exception | `<Situation>Exception` + `ProblemCode` | `PromptPublishedException` |
| Test | `<Class><Concern>Test` | `PromptPublishTest` |
| Flyway | `V<n>__<snake_case>.sql` | `V3__add_team_visibility.sql` |
| Table / column | singular table, snake_case column | `prompt_version`, `published_version_id` |
| Config | `promptlib.<area>.<key>` | `promptlib.export.batch-size` |

**Banned:** `*Manager`, `*Helper`, `*Util`, `*Impl`, `*DTO` as a suffix, static mutable state,
business logic in a resource or gRPC class.

## 5. Where a change goes

| You need to… | Put it in |
|---|---|
| Add a business rule | a method on the aggregate in `domain/<x>/model` |
| Add a use case | `domain/<x>/service`, `@Transactional` |
| Add a list screen query | `persistence/projection` + a `<Concern>Query` port |
| Add an endpoint | `edge/api/<Domain>Resource` + DTO in `api/dto` |
| Change the Flutter-facing shape | `edge/api/dto` only |
| Change the internal contract | `promptlib-proto` + both mappers |
| Add a column | new `V<n>__` in `database/` + entity field + `04` |
| Add an error the client handles | `ProblemCode` + `ProblemExceptionMapper` |
| Add a config value | `application.properties` under `promptlib.*` |
| Talk to Keycloak | `adapter/keycloak` implementing a port |
| Read another domain | `shared/port` interface, implemented in `persistence` |
| Add a log line | the layer that owns the event, with an `event=` key |

## 6. Dependency rules (ArchUnit)

```
edge         → proto + edge packages only
core.grpc    → domain.*, shared, proto
core.domain  → shared (+ JPA annotations on entities)
core.adapter → domain ports, shared
core.persist → domain ports, domain model, shared
core.shared  → nothing
```

Enforced by `ArchitectureTest`:

```java
@AnalyzeClasses(packages = "com.promptlib", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_never_sees_infrastructure =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..persistence..", "..grpc..", "..adapter..",
                "jakarta.ws.rs..", "io.grpc..", "com.promptlib.proto..");

    @ArchTest
    static final ArchRule grpc_never_touches_panache =
        noClasses().that().resideInAPackage("..grpc..")
            .should().dependOnClassesThat()
            .resideInAPackage("io.quarkus.hibernate.orm.panache..");

    @ArchTest
    static final ArchRule shared_is_a_leaf =
        noClasses().that().resideInAPackage("..shared..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..domain..", "..persistence..", "..grpc..");

    @ArchTest
    static final ArchRule gallery_reaches_prompt_only_through_ports =
        classes().that().resideInAPackage("..domain.gallery..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..domain.gallery..", "..shared..", "java..", "org.jboss.logging..");

    @ArchTest
    static final ArchRule edge_has_no_domain_access =
        noClasses().that().resideInAPackage("com.promptlib.edge..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.promptlib.domain..", "com.promptlib.persistence..");

    @ArchTest
    static final ArchRule entities_stay_in_core =
        noClasses().that().resideOutsideOfPackages("..domain..", "..persistence..")
            .should().beAnnotatedWith(jakarta.persistence.Entity.class);
}
```

## 7. Class-level guidelines

1. One public top-level type per file; the filename equals the type name.
2. Aggregates enforce invariants: `prompt.publish(version, category)` throws; services orchestrate.
3. `PromptVersion` has no content mutators. Immutability is a compile-time property.
4. `@Transactional` only in `domain/*/service`.
5. Repositories return entities or projections, never DTOs.
6. Ports return `Optional`, never `null`; collections are never `null`.
7. Every failure is a typed exception carrying a `ProblemCode`.
8. Ids via `Ids.newId()`; time via injected `Clock`.
9. `CallerContext` is the only source of identity.
10. gRPC and REST methods stay under ~15 lines: map → delegate → map.
11. Log through JBoss Logging with `event=` keys; never log content, passwords or tokens.
12. `package-info.java` in every package, one sentence of intent.
13. Tests mirror the package they test; rule tests are plain JUnit, not `@QuarkusTest`.
14. No `@Entity` reaches edge or proto; map to DTOs at the boundary.

## 8. Test layout

```
promptlib-core/src/test/java/com/promptlib/
├── domain/prompt/model/PromptPublishTest.java
├── domain/prompt/service/VersionServiceTest.java
├── persistence/PanachePromptRepositoryTest.java
├── grpc/GalleryGrpcServiceTest.java
└── ArchitectureTest.java

promptlib-edge/src/test/java/com/promptlib/edge/
├── api/PromptResourceSecurityTest.java
├── api/GalleryResourceTest.java
├── error/ProblemExceptionMapperTest.java
└── mapping/PromptDtoMapperTest.java
```

`src/test/resources/application.properties` holds the `%test` overrides (Dev Services MySQL, OIDC
disabled, file logging off). Every authenticated endpoint gets the three cases from `08 §4`.

## 9. Build order

| Step | Deliverable |
|---|---|
| 1 | Root POM, wrapper, `.gitignore`, `.editorconfig`, README |
| 2 | `promptlib-proto`: `common.proto`, `account.proto`; codegen verified |
| 3 | `core`: `shared`, `domain.account`, `persistence`, `AccountGrpcService` |
| 4 | `edge`: `security`, `error`, `AccountClient`, `AuthResource`, `AccountResource`; `/me` end to end |
| 5 | `deploy/` + `scripts/smoke.sh`; compose up |
| 6 | `domain.prompt` + `domain.library` + their adapters |
| 7 | `domain.gallery`, `domain.category`, `domain.admin`, `domain.export` |

## 10. When this document is wrong

Layout documents rot faster than code. If a new concern does not fit any folder in §5, the correct
move is to amend this document and `package-info.java` **in the same commit** as the new package —
not to invent `util/`.
