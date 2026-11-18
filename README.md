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
./mvnw clean verify            # compile + all tests
./mvnw -pl promptlib-core test # one module
```

Tests for `core` start a throwaway MySQL through Dev Services; Docker must be running.

## Run locally

The full stack (MySQL, Keycloak, core, edge) lives in `deploy/` and is not scaffolded yet — see
`docs/architecture/09-deployment.md` and the build order in `12 §9`.

Once it exists:

```bash
cp .env.example .env           # fill in secrets
docker compose -f deploy/docker-compose.yml up -d
curl -s localhost:8080/q/health/ready
```

## Conventions

- ids are UUID v7 (`BINARY(16)`); timestamps are UTC `DATETIME(6)`
- every endpoint is authenticated except `POST /api/v1/auth/register`
- `@Transactional` only in `domain/*/service`
- ArchUnit fails the build on a layering violation
- schema changes are Flyway migrations in `database/`; never edit a migration that has run

## Licence

Private project.
