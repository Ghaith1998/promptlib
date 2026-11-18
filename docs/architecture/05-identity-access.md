# 05 — Identity, Authentication, Authorization

**Depends on:** ADR-10 (all authenticated except registration), ADR-11 (realm role `admin`),
ADR-12 (ROPC), ADR-15 (edge validates, core trusts metadata), ADR-22 (registration service account),
ADR-27 (self-registration through the app).

---

## 1. Model

Keycloak owns **credentials and sessions**. The application owns **profile and domain data**.
The only shared identifier is the immutable `sub` claim.

```
Keycloak realm "promptlib"
├── clients
│   ├── promptlib-app       public: Direct Access Grant (ROPC) for Flutter mobile/desktop
│   ├── promptlib-edge      confidential: bearer audience for this API + Swagger UI
│   ├── promptlib-swagger   public: Swagger UI code flow only (PKCE, dev)
│   └── promptlib-register  confidential, service account: create users (registration only)
├── realm roles
│   ├── user   (default for every account)
│   └── admin  (granted in the Keycloak console)
└── users ── sub ──▶ app_user.keycloak_sub
```

- The Flutter client logs in **directly against Keycloak** (ROPC) and sends the bearer token to the
  app. The app never sees a password at login.
- Registration goes through `POST /api/v1/auth/register`, which creates the Keycloak user via the
  Admin API. This is the only place a password transits the backend, and the backend never stores it.
- Users, roles and account disabling are administered **in the Keycloak console**. The app has no
  user-administration API.
- `app_user` rows are created on first authenticated request, not at registration.

## 2. Realm configuration

Committed as `deploy/keycloak/realm-promptlib.json`, imported with `--import-realm`.
Key fragments (Keycloak 26 syntax):

```json
{
  "realm": "promptlib",
  "enabled": true,
  "sslRequired": "external",
  "registrationAllowed": false,
  "registrationEmailAsUsername": false,
  "loginWithEmailAllowed": true,
  "duplicateEmailsAllowed": false,
  "accessTokenLifespan": 300,
  "ssoSessionIdleTimeout": 1800,
  "ssoSessionMaxLifespan": 36000,
  "revokeRefreshToken": true,
  "refreshTokenMaxReuse": 0,
  "roles": {
    "realm": [
      { "name": "user",  "description": "Can use the prompt library" },
      { "name": "admin", "description": "Manages categories and gallery moderation" }
    ]
  },
  "defaultRoles": ["user"],
  "defaultDefaultClientScopes": ["promptlib-api"],
  "clients": [
    {
      "clientId": "promptlib-app",
      "publicClient": true,
      "standardFlowEnabled": true,
      "directAccessGrantsEnabled": true,
      "implicitFlowEnabled": false,
      "attributes": { "pkce.code.challenge.method": "S256" },
      "redirectUris": [
        "com.promptlib.app://oauth2redirect",
        "http://127.0.0.1:*"
      ]
    },
    {
      "clientId": "promptlib-edge",
      "publicClient": false,
      "standardFlowEnabled": true,
      "directAccessGrantsEnabled": false,
      "secret": "${KC_EDGE_SECRET}",
      "redirectUris": ["http://localhost:8080/q/swagger-ui/oauth2-redirect.html"],
      "attributes": { "pkce.code.challenge.method": "S256" }
    },
    {
      "clientId": "promptlib-swagger",
      "publicClient": true,
      "standardFlowEnabled": true,
      "directAccessGrantsEnabled": false,
      "attributes": { "pkce.code.challenge.method": "S256" },
      "redirectUris": ["http://localhost:8080/q/swagger-ui/oauth2-redirect.html"]
    },
    {
      "clientId": "promptlib-register",
      "publicClient": false,
      "standardFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": true,
      "secret": "${KC_REGISTER_CLIENT_SECRET}"
    }
  ],
  "clientScopes": [
    {
      "name": "promptlib-api",
      "protocol": "openid-connect",
      "attributes": { "include.in.token.scope": "false" },
      "protocolMappers": [
        {
          "name": "api-audience",
          "protocol": "openid-connect",
          "protocolMapper": "oidc-audience-mapper",
          "config": {
            "included.custom.audience": "promptlib-edge",
            "access.token.claim": "true",
            "id.token.claim": "false"
          }
        }
      ]
    }
  ]
}
```

The `promptlib-register` service account needs exactly one `realm-management` role:
**`manage-users`** (plus `view-users` if the implementation checks for existing usernames before
creating). It must **not** get `manage-realm` or role-mapping rights: registration creates a user and
nothing else.

Decisions embedded here:

- `directAccessGrantsEnabled: true` on `promptlib-app` — this is ROPC (ADR-12). Your reference doc
  chose it for the mobile client; it is deprecated by OAuth 2.1 and documented as a compromise with
  a client-side upgrade path (§3.2).
- `registrationAllowed: false` — Keycloak's own registration page is **off**. Registration happens
  through the app's form (ADR-27), so there is one place that creates accounts.
- `accessTokenLifespan: 300` (5 min) keeps revocation latency tolerable with refresh rotation.
- `revokeRefreshToken: true` + `refreshTokenMaxReuse: 0` gives one-shot refresh tokens.
- The `promptlib-api` client scope carries the audience mapper and is a **default scope for every
  client**, so tokens minted for `promptlib-app` (ROPC) actually contain `aud = promptlib-edge`.
  Putting the mapper on `promptlib-edge` itself would only affect tokens issued *to* the edge — the
  classic reason a ROPC token gets rejected with `invalid_token` / wrong audience.
- `redirectUris` remain for the future PKCE migration; nothing uses them in v1 (no browser client).
- `promptlib-register` is the only service account, and it exists solely for account creation.

## 3. Flutter login: ROPC (v1)

The client posts credentials **directly to Keycloak**. Quarkus never sees them.

```http
POST {kc}/realms/promptlib/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
&client_id=promptlib-app
&username=alice&password=…
&scope=openid profile email
```

```
1. app shows its own login screen (username + password)
2. app POSTs the form above directly to Keycloak
3. Keycloak returns access_token (5 min) + refresh_token + id_token
4. app stores access_token in memory, refresh_token in flutter_secure_storage
5. every API call: Authorization: Bearer <access_token>
6. on 401 → refresh once; on 400 invalid_grant → wipe tokens, show login
7. logout: POST {kc}/…/logout with client_id + refresh_token, then clear storage
```

Rules:

- The backend is **not** in the login path. No callback, no session, no cookie (reference doc §6.3).
- Never distinguish "no such user" from "wrong password" in the UI.
- Refresh is single-flight: concurrent 401s must share one refresh future, otherwise rotation
  invalidates the second call and the user is logged out randomly.
- The access token never touches disk; only the refresh token is persisted.
- A `client_secret` must never be embedded in the app: `promptlib-app` is a public client.

### 3.1 Registration (ADR-27)

The only place a password transits the backend. Chosen deliberately (ref §5.2), because there is no
SMTP and no Keycloak-hosted registration page.

```http
POST /api/v1/auth/register          # unauthenticated
{ "username": "alice", "email": "a@x", "password": "…", "displayName": "Alice" }
```

```
1. edge validates the payload (username format, email, password policy mirrored from Keycloak)
2. edge → core → Keycloak Admin API (promptlib-register service account):
     POST /admin/realms/promptlib/users   { username, email, enabled: true,
                                            emailVerified: false,
                                            credentials: [{ type: "password", value, temporary: false }],
                                            realmRoles: ["user"] }
3. 201 Created. No app_user row yet.
4. The new user logs in through the normal ROPC flow; EnsureUser creates app_user then.
```

Rules and consequences:

- **The password transits `core`** once, in memory, over TLS inside the compose network. It is never
  logged, never persisted, and must not be passed to any logging or tracing interceptor.
- The endpoint is **unauthenticated and unthrottled** by decision. Anyone who can reach it can create
  an account. This is recorded as risk 8 in `00 §9` and R-3 in the requirements folder.
- Duplicate username/email → 409 `USERNAME_TAKEN`. Do not reveal whether the email exists separately.
- Keycloak is the source of truth for the password policy; the edge validates the same rules only to
  give a fast error. A policy change in Keycloak must be mirrored in code (documented debt).
- If Keycloak is down, registration returns 503 `IDENTITY_PROVIDER_UNAVAILABLE`; no partial state is
  written because `app_user` is not created here.
- Failure after Keycloak created the user but before the response reaches the client (network drop):
  the account exists. The user can log in with the credentials they submitted — that is why no
  compensating delete is attempted.

### 3.2 Upgrade path: PKCE

Not built in v1. Documented because the cost of migrating is client-side only:

- `promptlib-app` already has `standardFlowEnabled` and the redirect URIs.
- The client switches to `flutter_appauth` (system browser, code + PKCE).
- The backend does not change at all: it still only validates bearer tokens.

The reason ROPC is acceptable here is the trust model: 3–10 known colleagues, no third-party
clients, no MFA requirement today. It is not acceptable for a public product, and the docs say so.

## 4. Edge token validation

```properties
quarkus.oidc.auth-server-url=https://auth.example.com/realms/promptlib
quarkus.oidc.client-id=promptlib-edge
quarkus.oidc.application-type=service
quarkus.oidc.token.audience=promptlib-edge
quarkus.oidc.token.issuer=https://auth.example.com/realms/promptlib
quarkus.oidc.connection-timeout=2S
quarkus.oidc.health.enabled=true
```

**Issuer trap (the #1 Keycloak integration bug):** Keycloak stamps `iss` with its configured
frontend URL (`KC_HOSTNAME`), not with the URL the app used. If the container is reachable at
`http://keycloak:8080` but `KC_HOSTNAME=https://auth.example.com`, tokens say
`https://auth.example.com/...` and validation fails with `issuer mismatch`.

Two consistent options:

| Option | Config |
|---|---|
| **A (recommended)** | `KC_HOSTNAME=https://auth.example.com` and `quarkus.oidc.auth-server-url` also uses that public URL. Requires the edge container to resolve it (NPM/Cloudflare reachable from the Docker network) |
| B | Internal URL for discovery + `quarkus.oidc.token.issuer` set to the public issuer the tokens actually carry |

Never mix: discovery from one URL, issuer unset, and tokens minted from another.

## 5. Authorization

Two levels only. Anything finer is a domain rule, not a role.

| Level | Mechanism | Example |
|---|---|---|
| **Role gate** (edge) | `@RolesAllowed("user")`, `@RolesAllowed("admin")` | Category CRUD requires `admin` |
| **Ownership** (core) | Every query is scoped by `CallerContext.userId()` | `findOwned(promptId, userId)` |

Ownership rules:

| Action | Rule |
|---|---|
| Read/update/delete a private prompt | owner only; anything else → 404 |
| Read a public prompt | any authenticated user |
| Read versions of a public prompt | only up to and including, or exactly, the published version — drafts stay private |
| Create a version / publish / unpublish | owner only; **blocked while hidden** (409 `HIDDEN_BY_ADMIN`) |
| Fork / duplicate | fork: source must be PUBLIC and not hidden; duplicate: owner only |
| Favorite | any visible prompt |
| Category write | `admin` |
| Hide / unhide a prompt | `admin` only; owner cannot see or call the control |
| List any prompt (metadata) | `admin`; **never** message content or variables |
| Read the audit trail | `admin` (v1: no API, DB access only) |
| Create/disable users, grant/revoke roles | **Keycloak console** — not exposed by this API |

Admin is deliberately **not** a super-owner. An admin sees that a private prompt exists (owner,
title, counts, moderation state) but cannot open it. If support requires reading user content, that
is a new requirement and a new decision — it is not a hidden capability of the current role.

**Never** trust a `userId` from the request body or query string. It comes from metadata only.

> The "read versions of a public prompt" rule matters: a public prompt may have unpublished draft
> versions containing sensitive content. `GET /prompts/{id}/versions` for a non-owner on a public
> prompt must return only the published version, not the history.

## 6. User provisioning (mirror)

```
edge request
  → identity from token (sub, preferred_username, email, realm_access.roles)
  → AccountService.EnsureUser(sub, username, email)
  → app_user upsert, id returned
  → metadata x-user-id = that id
```

- If the upsert fails (DB down), the request fails with 503 — do not proceed with a fabricated id.
- `app_user.id` is what all foreign keys reference; the `sub` is stored only for lookup/audit.
- Username/email changes in Keycloak propagate on the next request.
- `display_name` is app-owned (user edits it in Flutter).
- Accounts arrive only through the app's registration endpoint (default role `user`). Admins do not
  create accounts in the app; if one is needed they use the Keycloak console.
- A disabled account cannot obtain new tokens; its `app_user` row and prompts remain, and its public
  prompts stay published unless an admin hides them.

Do **not** store role assignments in `app_user`. Roles are read from the token on every request;
there is no local role table to drift. Consequence: removing the `admin` role in Keycloak takes
effect the next time a token is issued (≤5 min with the configured lifespan).

## 7. Admin bootstrap

1. Keycloak container env:
   `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` create the master-realm admin.
2. Log into the admin console, open realm `promptlib`, create the app user (or let them register
   through the app), then assign realm role `admin`.
3. The role appears in the token's `realm_access.roles`; the edge maps it to a `SecurityIdentity`
   role and `@RolesAllowed("admin")` starts working.

There is no `is_admin` column anywhere. That is deliberate: one source of truth.

### 7.1 Registration credential (ADR-22)

Registration is the only operation that needs a service account. It is deliberately narrow:

```properties
quarkus.keycloak.admin-client.server-url=https://auth.example.com
quarkus.keycloak.admin-client.realm=promptlib
quarkus.keycloak.admin-client.client-id=promptlib-register
quarkus.keycloak.admin-client.client-secret=${KC_REGISTER_CLIENT_SECRET}
quarkus.keycloak.admin-client.grant-type=client_credentials
```

```java
@ApplicationScoped
public class KeycloakRegistrationAdapter implements UserRegistrationPort {

    @Inject Keycloak keycloak;   // quarkus-keycloak-admin-client

    @Override
    public String createUser(String username, String email, String displayName, String password) {
        UserRepresentation u = new UserRepresentation();
        u.setUsername(username);
        u.setEmail(email);
        u.setEnabled(true);
        u.setEmailVerified(false);
        u.setCredentials(List.of(new CredentialRepresentation() {{
            setType("password"); setValue(password); setTemporary(false);
        }}));
        Response r = keycloak.realm("promptlib").users().create(u);   // 201 + Location
        return extractId(r);                                            // == the 'sub'
    }
}
```

Rules:

- The port lives in `domain/account` (registration is account acquisition); the adapter lives in
  infrastructure. The domain never imports Keycloak types.
- The service account has `manage-users` (and `view-users` only if duplicate checks need it) — **no
  `manage-realm`, no role mapping**. It cannot grant `admin`, disable accounts, or change roles.
- The client is used by exactly one endpoint. It must never be reachable from prompt, gallery or
  export code paths.
- The returned Keycloak user id is the `sub` that will later link `app_user` on first login.
- If Keycloak returns 409, map to `USERNAME_TAKEN` (or `EMAIL_TAKEN`) without confirming which field
  already exists beyond what the user typed.
- The secret is a high-value credential: `.env` only, never logged, never returned. Rotating it is
  the recovery path if it leaks.
- Because the endpoint is open, this credential is reachable by anyone who can POST there. That is
  risk 8 in `00 §9`.

## 8. Failure modes

| Situation | Behaviour |
|---|---|
| Keycloak unreachable at startup | Edge starts; OIDC discovery is lazy. First request fails with 503 until reachable. Health endpoint reports DOWN if `quarkus.oidc.health.enabled=true` |
| Keycloak down while keys are cached | Existing tokens keep validating until the JWKS cache expires; then requests fail (see `07-resilience.md`) |
| Token expired | 401 with `WWW-Authenticate: Bearer error="invalid_token"`; Flutter refreshes |
| User deleted in Keycloak | Existing access token remains valid ≤5 min; afterward 401. `app_user` row remains |
| Clock skew between containers | Signature validation fails with `invalid_token`. Run NTP on the host; both containers use host time |
| Wrong realm | 401 `invalid_issuer`; check `auth-server-url` |
| Registration called while Keycloak is down | 503 `IDENTITY_PROVIDER_UNAVAILABLE`; no `app_user` row, nothing to clean up |
| Register service-account secret rotated | Registration returns 503/401 from Keycloak; all other traffic unaffected |
| Duplicate registration | 409 `USERNAME_TAKEN`; the account is not duplicated |
| Password policy mismatch | Keycloak rejects with 400; the edge maps it to 422 `WEAK_PASSWORD` |

## 9. Deliberately out of scope (v1)

- Fine-grained permissions (prompt-level sharing with specific users)
- MFA policy, social login, organization brokering (Keycloak features, not app work)
- Token introspection / opaque tokens (the `service` type uses JWT only)
- A user-facing session list or "sign out other devices"
- In-app user administration (create/disable/roles) — done in the Keycloak console
- Account deletion and GDPR erasure (`11-roadmap.md`); disabling is the v1 substitute
- Email invitations and password reset flows (Keycloak's own reset page covers the latter)
- A web client and therefore any browser token/session mechanism
