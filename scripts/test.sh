#!/usr/bin/env bash
# Runs the test suite. core tests need a MySQL schema because Dev Services need Docker.
# .env provides TEST_DB_URL; Flyway cleans and rebuilds that schema on every run.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
exec "$ROOT/mvnw" test "$@"
