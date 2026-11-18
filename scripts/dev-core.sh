#!/usr/bin/env bash
# Run core in dev mode with secrets loaded from the repo .env.
# Quarkus has no built-in .env loader, so export the file explicitly.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
# quarkus:dev resolves module dependencies from the local repo, so install the proto once.
"$ROOT/mvnw" -q -DskipTests install -pl promptlib-proto -am
exec "$ROOT/mvnw" -pl promptlib-core quarkus:dev "$@"
