#!/usr/bin/env bash
# Finish the realm after import (roles, scopes, seeded users).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
exec python3 "$ROOT/scripts/keycloak-bootstrap.py"
