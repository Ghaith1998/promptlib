#!/usr/bin/env python3
"""Idempotently finish a freshly imported Keycloak realm.

Realm import cannot express everything we need:
  * realm-management roles for the `promptlib-register` service account
  * the `kc-admin-api` client scope (Keycloak 26.8 requires it on admin calls)
  * `user` as part of the default-roles composite, so self-registered users get it
  * password/name fixes when the import ignored credentials

Usage: scripts/keycloak-bootstrap.py
Environment: KC_URL, KC_ADMIN_USER, KC_ADMIN_PASSWORD, KC_REGISTER_CLIENT_SECRET
"""
import base64
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("KC_URL", "http://10.0.0.3:8082").rstrip("/")
ADMIN = os.environ.get("KC_ADMIN_USER", "admin")
PASSWORD = os.environ.get("KC_ADMIN_PASSWORD", "admin")
REALM = "promptlib"
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REALM_FILE = os.path.join(HERE, "deploy", "keycloak", "realm-promptlib.json")


def request(method, path, token=None, body=None, form=None):
    url = BASE + path
    data = None
    headers = {}
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw) if raw else None
        except json.JSONDecodeError:
            return e.code, raw.decode(errors="replace")


def admin_token():
    status, payload = request("POST", "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": ADMIN, "password": PASSWORD,
    })
    if status != 200:
        sys.exit(f"admin login failed: {status} {payload}")
    return payload["access_token"]


def ensure_realm(token):
    status, _ = request("GET", f"/admin/realms/{REALM}", token)
    if status == 200:
        print(f"realm {REALM} exists")
        return
    with open(REALM_FILE) as f:
        realm = json.load(f)
    status, payload = request("POST", "/admin/realms", token, body=realm)
    if status not in (201, 409):
        sys.exit(f"realm creation failed: {status} {payload}")
    print(f"realm {REALM} created")


def ensure_scope(token):
    status, scopes = request("GET", f"/admin/realms/{REALM}/client-scopes", token)
    by_name = {s["name"]: s for s in scopes}
    if "promptlib-api" not in by_name:
        body = {
            "name": "promptlib-api", "protocol": "openid-connect",
            "attributes": {"include.in.token.scope": "false"},
            "protocolMappers": [{
                "name": "api-audience", "protocol": "openid-connect",
                "protocolMapper": "oidc-audience-mapper",
                "config": {"included.custom.audience": "promptlib-edge",
                           "access.token.claim": "true", "id.token.claim": "false"},
            }],
        }
        status, payload = request("POST", f"/admin/realms/{REALM}/client-scopes", token, body=body)
        if status not in (201, 409):
            sys.exit(f"scope creation failed: {status} {payload}")
        status, scopes = request("GET", f"/admin/realms/{REALM}/client-scopes", token)
        by_name = {s["name"]: s for s in scopes}
    api = by_name["promptlib-api"]
    admin_scope = by_name.get("kc-admin-api")

    status, clients = request("GET", f"/admin/realms/{REALM}/clients", token)
    for client in clients:
        cid = client["id"]
        request("PUT", f"/admin/realms/{REALM}/clients/{cid}/default-client-scopes/{api['id']}", token)
        if client["clientId"] == "promptlib-register" and admin_scope:
            request("PUT", f"/admin/realms/{REALM}/clients/{cid}/default-client-scopes/{admin_scope['id']}", token)
    print("client scopes attached")


def service_account_roles(token):
    status, clients = request("GET", f"/admin/realms/{REALM}/clients?clientId=promptlib-register", token)
    cid = clients[0]["id"]
    _, sa = request("GET", f"/admin/realms/{REALM}/clients/{cid}/service-account-user", token)
    _, rm_clients = request("GET", f"/admin/realms/{REALM}/clients?clientId=realm-management", token)
    rm = rm_clients[0]["id"]
    roles = []
    for name in ("manage-users", "view-users"):
        _, role = request("GET", f"/admin/realms/{REALM}/clients/{rm}/roles/{name}", token)
        roles.append(role)
    status, _ = request("POST", f"/admin/realms/{REALM}/users/{sa['id']}/role-mappings/clients/{rm}",
                        token, body=roles)
    print(f"service account roles: {status}")


def default_user_role(token):
    _, role = request("GET", f"/admin/realms/{REALM}/roles/user", token)
    status, default_role = request("GET", f"/admin/realms/{REALM}/roles/default-roles-{REALM}", token)
    if status != 200:
        print("default-roles composite not found, skipping")
        return
    status, _ = request("POST", f"/admin/realms/{REALM}/roles-by-id/{default_role['id']}/composites",
                        token, body=[role])
    print(f"user added to default roles: {status}")


def seeded_users(token):
    for username in ("alice", "admin"):
        status, users = request("GET", f"/admin/realms/{REALM}/users?username={username}", token)
        if status != 200 or not users:
            continue
        uid = users[0]["id"]
        request("PUT", f"/admin/realms/{REALM}/users/{uid}", token, body={
            "firstName": username.capitalize(), "lastName": "Tester",
            "email": f"{username}@local", "emailVerified": True, "enabled": True,
        })
        request("PUT", f"/admin/realms/{REALM}/users/{uid}/reset-password", token,
                body={"type": "password", "value": username, "temporary": False})
    print("seeded users refreshed")


if __name__ == "__main__":
    token = admin_token()
    ensure_realm(token)
    ensure_scope(token)
    service_account_roles(token)
    default_user_role(token)
    seeded_users(token)
    print("done")
