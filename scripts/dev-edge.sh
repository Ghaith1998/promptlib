#!/usr/bin/env bash
# Run the edge in dev mode. Needs core on :9000 and a reachable Keycloak realm.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
"$ROOT/mvnw" -q -DskipTests install -pl promptlib-proto -am
exec "$ROOT/mvnw" -pl promptlib-edge quarkus:dev "$@"
