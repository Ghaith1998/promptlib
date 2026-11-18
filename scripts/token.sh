#!/usr/bin/env bash
# Print an access token for manual API testing.
#   ./scripts/token.sh            -> alice / alice
#   ./scripts/token.sh admin admin
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
USERNAME="${1:-alice}"
PASSWORD="${2:-alice}"
BASE="${KC_URL:-http://localhost:8180}"
REALM="${KC_REALM:-promptlib}"
CLIENT="${KC_PUBLIC_CLIENT:-promptlib-app}"

RESPONSE=$(curl -sS -X POST "$BASE/realms/$REALM/protocol/openid-connect/token" \
  -d "client_id=$CLIENT" -d "grant_type=password" \
  -d "username=$USERNAME" -d "password=$PASSWORD" -d "scope=openid")

printf '%s' "$RESPONSE" | python3 -c '
import json, sys
d = json.load(sys.stdin)
if "access_token" not in d:
    sys.exit("login failed: " + d.get("error", "?") + " - " + d.get("error_description", ""))
print(d["access_token"])
'
