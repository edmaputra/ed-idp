# ed-idp Documentation

## Contents
- [Overview](#overview)
- [Documentation Portal](docs/README.md)
- [Features & Roadmap](docs/features-and-roadmap.md)
- [Architecture & System Design](docs/architecture/00-architecture.md)
- [Built-in Clients and User](#built-in-clients-and-user)
- [Standards and Endpoints](#standards-and-endpoints)
- [Token Lifetime and Policy Controls](#token-lifetime-and-policy-controls)
- [Custom Claims and Profile Rules](#custom-claims-and-profile-rules)
- [Security and Persistence](#security-and-persistence)
- [Error Patterns](#error-patterns)
- [How-To (Operational Runbooks)](docs/how-to/README.md)

## Overview
ed-idp is a Spring Authorization Server based OAuth 2.1 and OpenID Connect provider with custom extensions for:
- Token introspection (RFC 7662)
- Token revocation (RFC 7009)
- Dynamic profile-based claims in ID Token, Access Token, and UserInfo
- Token lifetime and issuance policy controls
- PKCE support for public clients

Default base URL:
- `http://localhost:9000`

## Built-in Clients and User

### Confidential client
- Client ID: `demo-client`
- Client Secret: `demo-secret`
- Auth methods: `client_secret_basic`, `client_secret_post`
- Grants: `client_credentials`, `authorization_code`, `refresh_token`
- Redirect URI: `http://127.0.0.1:9000/login/oauth2/code/demo-client`
- Post-logout redirect URI: `http://127.0.0.1:9000/logged-out`
- Scopes: `openid profile email read write introspection revocation`

### Public PKCE client
- Client ID: `pkce-public-client`
- Auth method: `none`
- Grants: `authorization_code` (PKCE required)
- Redirect URI: `http://127.0.0.1:9000/login/oauth2/code/pkce-public-client`
- Post-logout redirect URI: `http://127.0.0.1:9000/logged-out`
- Scopes: `openid profile email read`

### Demo end user
- Username: `demo-user`
- Password: `demo-password`

## Standards and Endpoints

### Discovery and key material
- OIDC discovery: `GET /.well-known/openid-configuration`
- JWK Set: `GET /oauth2/jwks`

### Authorization and token
- Authorization endpoint: `GET /oauth2/authorize`
- Token endpoint: `POST /oauth2/token`

### OIDC user endpoints
- UserInfo: `GET /userinfo`
- RP-initiated logout: `POST /connect/logout`
- Logged out page: `GET /logged-out`

### Machine endpoints
- Token introspection: `POST /oauth2/introspect`
- Token revocation: `POST /oauth2/revoke`

## Token Lifetime and Policy Controls

Token behavior is configurable via `app.token.*` properties.

### Properties
- `app.token.access-token-time-to-live`: access token TTL (default `5m`)
- `app.token.refresh-token-time-to-live`: refresh token TTL (default `7d`)
- `app.token.reuse-refresh-tokens`: refresh token rotation toggle (default `false`)
- `app.token.client-credentials-allowed-scopes`: allowed scopes for `client_credentials` issuance (default `read,write,introspection,revocation`)

### Policy behavior
- For `client_credentials`, access-token issuance is denied with `invalid_scope` if any requested scope is outside `client-credentials-allowed-scopes`.
- For authorization code flow, refresh token rotation follows `reuse-refresh-tokens`.

## Custom Claims and Profile Rules

Claims come from a user profile and profile-attribute store.

### Standard profile claims in UserInfo
- `sub`, `preferred_username`, `name`, `email`, `email_verified`, `locale`, `zoneinfo`, `updated_at`, `department`, `tenant`

### Attribute-based custom claim routing
Each custom attribute key can be configured to appear in one or more targets:
- `USERINFO`
- `ID_TOKEN`
- `ACCESS_TOKEN`

Reserved JWT/OIDC claims are protected and not overwritten by custom attributes.

## Security and Persistence
- Passwords are BCrypt encoded.
- OAuth2 authorization, consent, and registered clients are stored using JDBC.
- Flyway manages schema migrations.
- Default runtime database is in-memory H2.

## Tenant Resolution

Tenant identity is resolved in this order:
1. Header (default: `X-Tenant-ID`)
2. Legacy path prefix (`/t/{tenant}/...`)
3. Optional strict rejection (`invalid_request`) when unresolved

### Tenant resolution properties
- `tenant.resolution.header-enabled` (default `true`)
- `tenant.resolution.path-enabled` (default `true`)
- `tenant.resolution.header-name` (default `X-Tenant-ID`)
- `tenant.resolution.require-explicit-tenant` (default `false`)
- `tenant.resolution.enforce-trusted-proxy-for-header` (default `false`)
- `tenant.resolution.header-trusted-sources` (default loopback addresses)

### Header trust boundary guidance
- Enable `tenant.resolution.enforce-trusted-proxy-for-header=true` in production.
- Configure your edge proxy/API gateway to strip external tenant headers and inject a trusted value.
- Keep `tenant.resolution.header-trusted-sources` aligned with proxy addresses.

## Error Patterns
Common OAuth errors returned by endpoints:
- `invalid_client`: bad or missing client credentials
- `invalid_request`: missing required request parameters
- `invalid_scope`: unregistered scope or denied by token policy
- `invalid_grant`: invalid/expired/reused refresh token
- `unsupported_grant_type`: unsupported `grant_type`
- `unauthorized_client`: client lacks required scope for introspection/revocation

## How-To

### 1. Start the application
Requirements:
- Java 25
- Maven Wrapper (already included)

Run:

```bash
./mvnw spring-boot:run
```

The app starts on:
- `http://localhost:9000`

### 2. Verify OIDC metadata and JWKs
Get provider metadata:

```bash
curl -s http://localhost:9000/.well-known/openid-configuration | jq
```

Get signing keys:

```bash
curl -s http://localhost:9000/oauth2/jwks | jq
```

### 3. Get an access token with client_credentials

```bash
curl -s -u demo-client:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9000/oauth2/token | jq
```

Expected fields:
- `access_token`
- `token_type` (Bearer)
- `expires_in`
- `scope`

### 4. Use Token Introspection (RFC 7662)
First get a token from step 3, then:

```bash
ACCESS_TOKEN="<paste_access_token_here>"

curl -s -u demo-client:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=${ACCESS_TOKEN}" \
  http://localhost:9000/oauth2/introspect | jq
```

Header-based tenant form:

```bash
curl -s -u demo-client:demo-secret \
  -H "X-Tenant-ID: demo" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=${ACCESS_TOKEN}" \
  http://localhost:9000/oauth2/introspect | jq
```

Expected response for valid token includes:
- `active: true`
- `client_id`
- `scope`
- `exp`, `iat`, `jti`

Notes:
- Missing or invalid Basic Auth returns `invalid_client`.
- Client must include the `introspection` scope.

### 5. Use Token Revocation (RFC 7009)

```bash
ACCESS_TOKEN="<paste_access_token_here>"

curl -i -u demo-client:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=${ACCESS_TOKEN}&token_type_hint=access_token" \
  http://localhost:9000/oauth2/revoke
```

Expected:
- HTTP 200 even for unknown tokens (idempotent behavior).

Verify revocation:

```bash
curl -s -u demo-client:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=${ACCESS_TOKEN}" \
  http://localhost:9000/oauth2/introspect | jq
```

Expected:
- `active: false`

### 6. Authorization Code flow (confidential client)
1. Open this URL in browser and log in with `demo-user` / `demo-password`:

```text
http://localhost:9000/oauth2/authorize?response_type=code&client_id=demo-client&redirect_uri=http%3A%2F%2F127.0.0.1%3A9000%2Flogin%2Foauth2%2Fcode%2Fdemo-client&scope=openid%20profile%20email%20read&state=demo-state
```

2. Copy `code` from the redirect URL.

3. Exchange code for tokens:

```bash
AUTH_CODE="<paste_code_here>"

curl -s -u demo-client:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=${AUTH_CODE}&redirect_uri=http://127.0.0.1:9000/login/oauth2/code/demo-client" \
  http://localhost:9000/oauth2/token | jq
```

Expected fields:
- `access_token`
- `refresh_token`
- `id_token`

### 7. Refresh token flow

```bash
REFRESH_TOKEN="<paste_refresh_token_here>"

curl -s -u demo-client:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token&refresh_token=${REFRESH_TOKEN}&scope=read" \
  http://localhost:9000/oauth2/token | jq
```

With default policy (`reuse-refresh-tokens=false`):
- A new refresh token is issued
- Reusing the old refresh token returns `invalid_grant`

### 8. Call UserInfo endpoint
Requires an access token minted with OIDC scopes.

```bash
ACCESS_TOKEN="<paste_oidc_access_token_here>"

curl -s \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  http://localhost:9000/userinfo | jq
```

Expected claims include:
- `sub`, `preferred_username`, `name`, `email`, `email_verified`
- `department`, `tenant`
- plus configured custom attributes

### 9. PKCE flow for public client
1. Generate PKCE values (`code_verifier`, `code_challenge`, method `S256`).
2. Open authorize URL with `client_id=pkce-public-client` and PKCE params.
3. Log in and get authorization code from redirect.
4. Exchange code without client secret, but include `client_id` and `code_verifier`.

Token request example:

```bash
AUTH_CODE="<paste_code_here>"
CODE_VERIFIER="<original_code_verifier_here>"

curl -s \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&client_id=pkce-public-client&code=${AUTH_CODE}&redirect_uri=http://127.0.0.1:9000/login/oauth2/code/pkce-public-client&code_verifier=${CODE_VERIFIER}" \
  http://localhost:9000/oauth2/token | jq
```

If `code_challenge` is missing in authorize request, server returns `invalid_request`.

### 10. RP-initiated logout
You need an `id_token` from authorization code flow.

```bash
ID_TOKEN="<paste_id_token_here>"

curl -i \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "id_token_hint=${ID_TOKEN}&post_logout_redirect_uri=http://127.0.0.1:9000/logged-out&state=logout-state" \
  http://localhost:9000/connect/logout
```

Expected:
- Redirect to `/logged-out?state=logout-state`
- Session invalidated (next authorize request redirects to `/login`)

### 11. Configure token lifetime and policy controls
Edit `src/main/resources/application.properties`:

```properties
app.token.access-token-time-to-live=5m
app.token.refresh-token-time-to-live=7d
app.token.reuse-refresh-tokens=false
app.token.client-credentials-allowed-scopes=read,write,introspection,revocation
```

Examples:
- Restrict machine scopes to read-only:

```properties
app.token.client-credentials-allowed-scopes=read
```

- Increase access token TTL:

```properties
app.token.access-token-time-to-live=15m
```

Duration format accepts Spring Boot duration styles like `90s`, `5m`, `7d`.

### 12. Troubleshooting quick checks
`invalid_client`
- Verify Basic Auth client credentials.
- Check client exists and secret is correct.

`invalid_scope`
- Scope not registered on client, or blocked by `app.token.client-credentials-allowed-scopes`.

`invalid_grant`
- Refresh token is invalid, expired, or already rotated.

`unsupported_grant_type`
- Requested grant type is not enabled for the client.
