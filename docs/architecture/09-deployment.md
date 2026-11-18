# 09 — Deployment (Docker Compose, single host)

**Depends on:** all ADRs. No CI, no backups (accepted risk), local file logs.

---

## 1. Topology

```
host (home lab)
└── docker compose project "promptlib"
    ├── mysql        mysql:8.4                     volume: mysql-data
    ├── keycloak     quay.io/keycloak/keycloak:26  volume: keycloak-data + realm import
    ├── core         promptlib/core:<tag>          volume: logs-core   (gRPC 9000, mgmt 9001)
    └── edge         promptlib/edge:<tag>          volume: logs-edge   (8080 → NPM)
        network: promptlib-net (default bridge)
```

Only `edge` is published to the host (and only on localhost/NPM). `core` and `mysql` must have no
`ports:` entry at all — a published 9000 silently voids ADR-15.

## 2. `docker-compose.yml`

```yaml
name: promptlib

services:
  mysql:
    image: mysql:8.4
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: promptlib
      MYSQL_USER: ${DB_USER}
      MYSQL_PASSWORD: ${DB_PASSWORD}
      TZ: UTC
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
      - --default-time-zone=+00:00
      - --innodb-lock-wait-timeout=5
      - --innodb-ft-min-token-size=2      # three-letter words matter for prompt search
    volumes:
      - mysql-data:/var/lib/mysql
      - ./deploy/mysql/init:/docker-entrypoint-initdb.d:ro   # creates the keycloak schema + users
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 5s
      timeout: 3s
      retries: 20
      start_period: 40s
    networks: [promptlib-net]

  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    restart: unless-stopped
    environment:
      KC_DB: mysql
      KC_DB_URL: jdbc:mysql://mysql:3306/keycloak
      KC_DB_USERNAME: ${KC_DB_USER}
      KC_DB_PASSWORD: ${KC_DB_PASSWORD}
      KC_HOSTNAME: https://auth.example.com     # public URL; tokens carry this issuer
      KC_PROXY_HEADERS: xforwarded
      KC_HTTP_ENABLED: "true"
      KC_HEALTH_ENABLED: "true"
      KC_BOOTSTRAP_ADMIN_USERNAME: ${KC_ADMIN_USER}
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KC_ADMIN_PASSWORD}
    volumes:
      - keycloak-data:/opt/keycloak/data
      - ./deploy/keycloak:/opt/keycloak/data/import:ro
    command: ["start", "--import-realm"]
    ports:
      - "127.0.0.1:8180:8080"      # NPM proxies auth.example.com here; the client calls /token directly
    healthcheck:
      test: ["CMD-SHELL", "exec 3<>/dev/tcp/127.0.0.1/9000 && echo -e 'GET /health/ready HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '200' <&3"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 60s
    depends_on:
      mysql: { condition: service_healthy }
    networks: [promptlib-net]

  core:
    image: promptlib/core:${TAG:-latest}
    restart: unless-stopped
    environment:
      QUARKUS_PROFILE: prod
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:mysql://mysql:3306/promptlib?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC
      KC_REGISTER_CLIENT_SECRET: ${KC_REGISTER_CLIENT_SECRET}
      KC_URL: http://keycloak:8080
    volumes:
      - logs-core:/var/log/promptlib
    depends_on:
      mysql: { condition: service_healthy }
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1:9001/q/health/ready || exit 1"]
      interval: 10s
      timeout: 3s
      retries: 10
      start_period: 30s
    networks: [promptlib-net]

  edge:
    image: promptlib/edge:${TAG:-latest}
    restart: unless-stopped
    environment:
      QUARKUS_PROFILE: prod
      QUARKUS_OIDC_AUTH_SERVER_URL: https://auth.example.com/realms/promptlib
      QUARKUS_OIDC_CLIENT_ID: promptlib-edge
      QUARKUS_GRPC_CLIENTS_CORE_HOST: core
      QUARKUS_GRPC_CLIENTS_CORE_PORT: "9000"
    volumes:
      - logs-edge:/var/log/promptlib
    ports:
      - "127.0.0.1:8080:8080"
    depends_on:
      core: { condition: service_healthy }
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1:8080/q/health/ready || exit 1"]
      interval: 10s
      timeout: 3s
      retries: 10
      start_period: 30s
    networks: [promptlib-net]

volumes:
  mysql-data:
  keycloak-data:
  logs-core:
  logs-edge:

networks:
  promptlib-net:
    driver: bridge
```

Notes and gotchas:

- `KC_DB_URL` points at the `keycloak` schema inside the **same** MySQL container. That is allowed
  (separate schema, separate Flyway history — Keycloak runs its own migrations). The app user must
  have privileges on both schemas, or use two users (recommended, see §3). If your MySQL user is
  scoped to `promptlib`, Keycloak will fail at startup with "Access denied for database".
- Keycloak health lives on the **management port 9000**, not 8080. The `/dev/tcp` check avoids
  requiring curl in the Keycloak image.
- `127.0.0.1:8080:8080` binds to the host loopback only. NPM (running on the same host) reaches it;
  the internet does not.
- Do not put `ports:` on `core`. Ever.
- `QUARKUS_PROFILE=prod` with `%prod.` prefixed properties in `application.properties`.

## 3. Environment file

`.env` is **gitignored**; `.env.example` is committed (ADR from the interview).

```bash
# .env.example
MYSQL_ROOT_PASSWORD=change-me
DB_USER=promptlib
DB_PASSWORD=change-me
KC_DB_USER=keycloak
KC_DB_PASSWORD=change-me
KC_ADMIN_USER=admin
KC_ADMIN_PASSWORD=change-me
KC_EDGE_SECRET=change-me
KC_REGISTER_CLIENT_SECRET=change-me
TAG=2026.11.18-1
```

Hardening: create two MySQL users instead of one — `promptlib` (only `promptlib.*`) and `keycloak`
(only `keycloak.*`). It costs two `GRANT` lines and removes a class of accidental damage.

`deploy/mysql/init/01-init.sql` runs **only on an empty data volume**:

```sql
CREATE DATABASE IF NOT EXISTS keycloak CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'promptlib'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON promptlib.* TO 'promptlib'@'%';

CREATE USER IF NOT EXISTS 'keycloak'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON keycloak.* TO 'keycloak'@'%';

FLUSH PRIVILEGES;
```

If the volume already exists, that init script will not run — create the schema by hand with
`docker compose exec mysql mysql -u root -p`. This is the single most common "Keycloak won't start"
cause in a fresh compose stack.

## 4. Dockerfiles (both modules)

```dockerfile
# core/Dockerfile — same shape for edge
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY promptlib-proto/pom.xml promptlib-proto/
COPY promptlib-core/pom.xml promptlib-core/
RUN mvn -B -q dependency:go-offline -pl promptlib-proto,promptlib-core -am
COPY promptlib-proto promptlib-proto
COPY promptlib-core promptlib-core
RUN mvn -B -q -DskipTests package -pl promptlib-core -am

FROM eclipse-temurin:21-jre
RUN groupadd -r app && useradd -r -g app app && mkdir -p /var/log/promptlib && chown -R app:app /var/log/promptlib
WORKDIR /app
COPY --from=build /build/promptlib-core/target/quarkus-app/ /app/
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
EXPOSE 9000 9001
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
```

Rules:

- Pin base images by tag **and** digest when you care about reproducibility; at minimum pin tags.
- Run as non-root with a writable log directory — otherwise the file handler fails at startup with
  a permission error that looks like a logging bug.
- `MaxRAMPercentage=75` inside a memory-limited container is mandatory. A JVM that sees host RAM
  and sizes its heap against 32 GB will be OOM-killed.
- Set `mem_limit` for each service (edge 384m, core 768m, keycloak 1g, mysql 1g on a small host) —
  compose v2 respects `deploy.resources.limits` or the legacy `mem_limit`.

## 5. Reverse proxy (existing NPM)

| Public | Target | Notes |
|---|---|---|
| `promptlib.example.com` | `http://127.0.0.1:8080` | WebSocket not needed; enable HTTP/2; keep `X-Request-Id` passthrough |
| `auth.example.com` | `http://127.0.0.1:8180` (Keycloak) | **Required** in v1: the Flutter client calls Keycloak's token endpoint directly (ROPC), so the realm must be reachable from devices |

NPM settings that matter:

- Forward `X-Forwarded-Proto`/`-For`; Keycloak needs `KC_PROXY_HEADERS=xforwarded`.
- Do **not** buffer responses for gRPC — irrelevant here (only REST at the edge), but relevant if
  someone later proxies `core`.
- Set a request timeout above the edge's write timeout (10 s is enough) — **except**
  `/api/v1/export`, which needs 60 s and `proxy_buffering off` so the stream reaches the client.
- No cookie or SPA-fallback configuration is required: there is no web client (ADR-21), no CORS and
  no session cookie. If either is ever added, it is an architecture change, not a proxy tweak.
- Cloudflare: keep "Under Attack" off for the API path; it interferes with bearer-token calls from
  mobile clients on some plan tiers. Also ensure Cloudflare does not cache API responses.
- **Keycloak is public in v1.** The Flutter client posts credentials to
  `auth.example.com/realms/promptlib/protocol/openid-connect/token` directly (ROPC), so the realm
  cannot be internal-only. Expose the realm endpoints on the public hostname, but restrict the
  **admin console** (`/admin/**`) by IP allow-list or a separate internal hostname — it is the most
  sensitive UI in the stack.

### 5.1 Dev Keycloak in Compose (no full stack)

For a local realm without the whole stack:

```bash
docker compose -f deploy/docker-compose.yml up -d
./scripts/keycloak-bootstrap.sh      # scopes, service-account roles, seeded users
```

`deploy/docker-compose.yml` runs `quay.io/keycloak/keycloak:26.8.2` with `start-dev` (embedded H2),
publishes `8082:8080`, and mounts `deploy/keycloak/` as the import directory. `start-dev` is for
development only; the production container uses `start` with MySQL and `KC_HOSTNAME`.

Set in `.env`:

```
KC_URL=http://<host>:8082
OIDC_URL=http://<host>:8082/realms/promptlib
KC_REGISTER_CLIENT_SECRET=dev-only-register-secret
```

The issuer is whatever URL the token endpoint is reached on. If Keycloak is reached by IP, the
issuer is that IP — `quarkus.oidc.auth-server-url` must match exactly or every request fails with
"issuer mismatch".

## 6. Deploy and rollback

```bash
# build (on host, or later in CI)
docker compose build --pull core edge
docker compose up -d

# smoke test — the 10 checks from 08-testing.md §8
docker compose ps
curl -sf http://127.0.0.1:8080/q/health/ready
```

Rollback with Flyway forward-only migrations:

| Change | Rollback |
|---|---|
| Image only, no migration | `TAG=<previous> docker compose up -d core edge` |
| Additive migration (new nullable column/table) | Redeploy previous image; the extra column/table is harmless |
| Destructive migration (drop/rename) | **No rollback.** With no backups this is unrecoverable — see §7 |

Therefore: **never ship a destructive migration** in v1. Add the new column, dual-write in code if
needed, drop in a later release. This is the only discipline that substitutes for backups.

## 7. Backups (accepted risk, one-line mitigation)

The interview decided "none for now". Recorded honestly: **host loss or a bad `DELETE` destroys every
prompt with zero recovery path.** If you change your mind, this is the whole fix:

```bash
# crontab -e  (host)
15 3 * * * docker compose -f /opt/promptlib/docker-compose.yml exec -T mysql \
  mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines promptlib \
  | gzip > /backup/promptlib-$(date +\%F).sql.gz && find /backup -name 'promptlib-*.sql.gz' -mtime +14 -delete
```

Restore: `gunzip -c file.sql.gz | docker compose exec -T mysql mysql -u root -p promptlib`.
Test the restore once — an untested backup is a rumour.

## 8. Upgrades

| Component | Procedure |
|---|---|
| App images | Build, `TAG` bump, `up -d core edge`, run smoke test. No migration in the same step unless additive |
| MySQL 8.4 → next LTS | Dump first. Even though backups are "none", a **pre-upgrade** dump is not a backup policy, it is basic hygiene |
| Keycloak 26.x patch | `docker compose pull keycloak && up -d keycloak`; it migrates its own schema. Read release notes for breaking provider changes |
| Realm changes | Edit `realm-promptlib.json` and re-import **only in a fresh environment**; `--import-realm` does not update existing realms. Change the live realm through the console or `kcadm` |

## 9. Host-level checklist

- Docker log driver capped: `"log-opts": {"max-size": "10m", "max-file": "5"}` in `/etc/docker/daemon.json`.
- NTP active (`timedatectl`) — clock skew breaks JWT validation.
- Timezone UTC in containers (`TZ=UTC`), display in local time in Flutter.
- The volume `mysql-data` is on a disk that is actually backed up at the hypervisor level, if any.
- Ports 3306, 9000, 8080 are **not** reachable from the LAN except through NPM on the host loopback.
