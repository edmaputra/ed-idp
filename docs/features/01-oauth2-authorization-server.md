# Feature 1 — OAuth 2.0 Authorization Server

EnhAuthServ is a full OAuth 2.0 Authorization Server (RFC 6749) built on Spring Authorization Server. It issues JWT access tokens, refresh tokens, and OIDC ID tokens.

## Supported grant types

| Grant type | Use case | Notes |
|---|---|---|
| `authorization_code` | Interactive user login (web/mobile apps) | Requires user authentication + consent |
| `authorization_code` + PKCE | Public clients (SPAs, native apps) | `code_challenge` required; no client secret |
| `client_credentials` | Machine-to-machine | No user context; scopes restricted (see [Token Policy](04-token-policy.md)) |
| `refresh_token` | Renew access tokens | Rotation configurable |

## Client authentication methods

- `client_secret_basic` — HTTP Basic (`Authorization: Basic base64(id:secret)`)
- `client_secret_post` — credentials in the form body
- `none` — public clients, with PKCE required

## Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `/oauth2/authorize` | GET | Authorization Code / PKCE entry point |
| `/oauth2/token` | POST | Token issuance (all grant types) |
| `/oauth2/jwks` · `/t/{tenant}/oauth2/jwks` | GET | Public JWK set for signature verification |

## Security filter chains

`SecurityConfig` registers four ordered chains:

1. **`@Order(1)` Tenant machine endpoints** — `/t/{tenant}/oauth2/introspect|revoke`, `permitAll` (auth handled inside the use case).
2. **`@Order(2)` Authorization Server** — OAuth2/OIDC endpoints + consent page; authenticated; OIDC enabled; token customizers attached.
3. **`@Order(3)` Introspection/Revocation** — non-tenant `/oauth2/introspect|revoke`, `permitAll`.
4. **`@Order(4)` Default** — form login; everything authenticated except `/logged-out` and the per-tenant discovery/JWKS endpoints.

## Module ownership

| Package | Responsibility |
|---|---|
| `authorization/` | Authorization-code policy, PKCE validation, and scope rules for interactive and machine flows |
| `clients/` | Client registration bootstrap, client authentication, and allowed client scopes |
| `consent/` | Consent decision flow and persistence used during first-time or expanded authorization |
| `tokens/` | Token issuance rules, refresh handling, introspection, and revocation behavior |
| `oauth/` | Spring Security wiring, metadata, issuer, JWKs, and authorization-server configuration |
| `tenancy/` | Tenant resolution that scopes authorization-server requests per tenant |
| `shared/` | Shared logout and cross-feature helpers used by the auth-server flow |

## Authorization Code flow (summary)

```text
Client → GET /oauth2/authorize         (user authenticates via form login)
       → consent page (if scopes not yet granted)
       → redirect back with ?code=...
Client → POST /oauth2/token            (exchange code → access/refresh/id token)
```

## API contracts

> These endpoints are provided by Spring Authorization Server. Tenant-scoped requests use the `/t/{tenant}/...` path prefix (see [Multi-Tenancy](03-multi-tenancy.md)).

### `GET /oauth2/authorize`

Initiates the Authorization Code (optionally PKCE) flow.

| Query param | Required | Description |
|---|---|---|
| `response_type` | yes | `code` |
| `client_id` | yes | Registered client id |
| `redirect_uri` | yes | Must match a registered redirect URI |
| `scope` | yes | Space-delimited scopes, e.g. `openid profile email` |
| `state` | recommended | Opaque CSRF value echoed back |
| `code_challenge` | PKCE only | Base64URL challenge |
| `code_challenge_method` | PKCE only | `S256` |

```http
GET /oauth2/authorize?response_type=code&client_id=demo-client
    &redirect_uri=http://127.0.0.1:9000/login/oauth2/code/demo-client
    &scope=openid%20profile&state=xyz HTTP/1.1
```

**Success** — `302 Found` to the redirect URI:

```http
Location: {redirect_uri}?code=SplxlOBeZQQ...&state=xyz
```

**Error** — `302 Found` with `?error=invalid_request&error_description=...&state=xyz` (or the login page if the user is unauthenticated).

### `POST /oauth2/token`

`Content-Type: application/x-www-form-urlencoded`. Client authenticates via `client_secret_basic` (HTTP Basic), `client_secret_post` (body), or `none` (public + PKCE).

**Authorization code:**

```http
POST /oauth2/token HTTP/1.1
Authorization: Basic ZGVtby1jbGllbnQ6ZGVtby1zZWNyZXQ=
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=SplxlOBeZQQ...
&redirect_uri=http://127.0.0.1:9000/login/oauth2/code/demo-client
```

PKCE adds `code_verifier=...` and omits the `Authorization` header.

**Client credentials:** `grant_type=client_credentials&scope=read write`
**Refresh:** `grant_type=refresh_token&refresh_token=...`

**Success** — `200 OK`:

```json
{
  "access_token": "eyJraWQ...",
  "refresh_token": "8xLOxBtZp8...",
  "scope": "openid profile",
  "id_token": "eyJraWQ...",
  "token_type": "Bearer",
  "expires_in": 300
}
```

> `refresh_token` is present for `authorization_code` / `refresh_token` grants; `id_token` only when `openid` scope is requested. `expires_in` reflects [token policy](04-token-policy.md).

**Error** — `400 Bad Request` (`invalid_grant`, `invalid_scope`, …) or `401 Unauthorized` (`invalid_client`):

```json
{ "error": "invalid_grant" }
```

### `GET /oauth2/jwks` · `GET /t/{tenant}/oauth2/jwks`

No auth. See [OpenID Connect](02-openid-connect.md) for the JWKS response shape.

## Token format

Access and ID tokens are **signed JWTs** (RSA). Signing keys are exposed per tenant at the JWKS endpoint. Tokens carry standard claims plus any [dynamic claims](05-dynamic-claims.md) configured for the target.

## Implementation

The protocol endpoints themselves are provided by **Spring Authorization Server** — this project does not hand-write `/oauth2/authorize` or `/oauth2/token`. The project's code configures and customizes that machinery in [`oauth/SecurityConfig`](../../src/main/java/io/github/edmaputra/enhauthserv/oauth/SecurityConfig.java).

| Concern | Class / bean | Notes |
|---|---|---|
| Filter chains (1–4) | `oauth/SecurityConfig.tenantMachineEndpointsFilterChain` … `defaultSecurityFilterChain` | Ordered `@Bean SecurityFilterChain`s |
| AS endpoints enabled | `OAuth2AuthorizationServerConfigurer.authorizationServer()` | Wired in the `@Order(2)` chain |
| Issuer | `oauth/SecurityConfig.authorizationServerSettings(...)` | `AuthorizationServerSettings.builder().issuer(app.issuer-uri)` |
| Signing key | `oauth/SecurityConfig.jwkSource()` | In-memory RSA keypair (`ImmutableJWKSet`) |
| Client store | `registeredClientRepository` → `oauth/TenantAwareRegisteredClientRepository` | Tenant-scoped JDBC repo |
| Authorization store | `authorizationService` → `oauth/TenantAwareOAuth2AuthorizationService` | Holds codes/tokens |
| Token TTLs | `oauth/SecurityConfig.tokenSettings(...)` | From [`tokens/TokenPolicyProperties`](04-token-policy.md) |
| Token claims | `oauth/SecurityConfig.jwtTokenCustomizer(...)` | Adds [dynamic claims](05-dynamic-claims.md) + client-credentials scope guard |
| Login & consent | form login (`@Order(4)`), [`consent/OAuth2AuthorizationConsentController`](08-consent.md) | |

### Code-path map

- `GET /oauth2/authorize` → SAS `OAuth2AuthorizationEndpointFilter` → (login if needed) → ([consent](08-consent.md) if scopes ungranted) → redirect with `code`.
- `POST /oauth2/token` → SAS `OAuth2TokenEndpointFilter` → grant authentication provider → `jwtTokenCustomizer` runs → tokens persisted via `TenantAwareOAuth2AuthorizationService`.

## Authorization Code flow — sequence

```mermaid
sequenceDiagram
    participant UA as User-Agent
    participant AZ as SAS /oauth2/authorize
    participant Login as Form login (@Order 4)
    participant Consent as OAuth2AuthorizationConsentController
    participant TK as SAS /oauth2/token
    participant Cust as jwtTokenCustomizer
    participant Store as TenantAwareOAuth2AuthorizationService

    UA->>AZ: GET /oauth2/authorize (response_type=code, scope, PKCE?)
    alt not authenticated
        AZ-->>UA: redirect /login
        UA->>Login: credentials
        Login-->>UA: session established
        UA->>AZ: retry authorize
    end
    alt scopes not yet consented
        AZ-->>UA: redirect to consent
        UA->>Consent: approve scopes
        Consent-->>UA: redirect back to authorize (consent_approved)
        UA->>AZ: authorize with consent
    end
    AZ->>Store: save authorization (code)
    AZ-->>UA: 302 redirect_uri?code=...
    UA->>TK: POST /oauth2/token (code, client auth / code_verifier)
    TK->>Store: load authorization by code
    TK->>Cust: customize JWT (claims, scope guard)
    Cust-->>TK: claims applied
    TK->>Store: save tokens
    TK-->>UA: 200 {access_token, refresh_token, id_token?}
```

## Related tests

- `authorization/AuthServerAuthorizationFlowTests`, `authorization/AuthServerPkceFlowTests`
- `tokens/AuthServerTokenEndpointTests`
- `integration/AuthServerIntegrationTests`
