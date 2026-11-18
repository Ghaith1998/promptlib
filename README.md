# Prompt Library — backend

Quarkus backend for a prompt library: private workspaces, immutable prompt versions, a moderated
public gallery, and export. Flutter is the client; this repository is the server only.

- **`promptlib-edge`** — public REST API (`/api/v1`), Keycloak OIDC, OpenAPI/Swagger UI, gRPC clients.
- **`promptlib-core`** — domain rules, MySQL persistence, gRPC server. Owns the schema.
- **`promptlib-proto`** — gRPC contracts.

## Documentation

| Folder | Contents |
|---|---|
| [`docs/architecture/`](docs/architecture/README.md) | 13 documents: context, domain, boundaries, data, identity, logging, resilience, testing, deployment, roadmap, layout |
| [`docs/requirements/`](docs/requirements/README.md) | Application requirements: vision, 109 functional requirements, journeys, NFRs, data, traceability |
| [`database/`](database/) | `V1__baseline.sql` (authoritative), DBML diagram view |

Read `docs/architecture/00-architecture-overview.md` first, then
`docs/architecture/12-project-layout.md` before adding a package.

## Requirements

- JDK 21
- Docker (for MySQL, Keycloak, and Dev Services in tests)

The Maven wrapper is included; no global Maven install is needed.

## Build and test

```bash
./scripts/test.sh verify       # compile + all tests (loads .env)
./scripts/test.sh -pl promptlib-core -am -Dtest=PromptLifecycleTest
```

`core` tests run against a dedicated MySQL schema (`promptlib_test`), because this host has no
Docker for Dev Services. `TEST_DB_URL` comes from `.env`; without it the tests fall back to
Dev Services and fail on a Docker-less machine. `./mvnw verify` alone will fail for the same reason.

## Run locally

### With a remote (home-lab) MySQL

`core` expects these variables, normally provided by the repo `.env` (gitignored):

```bash
DB_URL='jdbc:mysql://core.home:7/promptlib?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC'
DB_USER=promptlib
DB_PASSWORD=…
```

Then:

```bash
./scripts/dev-core.sh      # sources .env, installs proto, runs quarkus:dev  (HTTP 9001, gRPC 9000)
./scripts/dev-edge.sh      # in another terminal; needs Keycloak at OIDC_URL
```

Quarkus has no built-in `.env` loader, which is why the script exports the file. Flyway applies
`V1__baseline.sql` on first start and Hibernate validates against it.

### With the compose stack

Not scaffolded yet — see `docs/architecture/09-deployment.md` and the build order in `12 §9`.


## Conventions

- ids are UUID v7 (`BINARY(16)`); timestamps are UTC `DATETIME(6)`
- every endpoint is authenticated except `POST /api/v1/auth/register`
- `@Transactional` only in `domain/*/service`
- ArchUnit fails the build on a layering violation
- schema changes are Flyway migrations in `database/`; never edit a migration that has run

## Licence

Private project.
