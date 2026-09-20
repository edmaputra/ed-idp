# Feature 6 — Token Introspection (RFC 7662)

Resource servers can validate an opaque or JWT token by calling the introspection endpoint, which returns the token's active status and metadata.

## Endpoints

| Endpoint | Method | Auth |
|---|---|---|
| `/oauth2/introspect` | POST (form) | HTTP Basic (client) |
| `/t/{tenant}/oauth2/introspect` | POST (form) | HTTP Basic (client) |

Request parameter: `token` (required). Response: JSON with RFC 7662 fields (`active`, `scope`, `client_id`, `exp`, …).

## API contract

`Content-Type: application/x-www-form-urlencoded`. The calling client authenticates with HTTP Basic and must hold the `introspection` scope.

```http
POST /oauth2/introspect HTTP/1.1
Authorization: Basic ZGVtby1jbGllbnQ6ZGVtby1zZWNyZXQ=
Content-Type: application/x-www-form-urlencoded

token=eyJraWQ...
```

| Form param | Required | Description |
|---|---|---|
| `token` | yes | The token to introspect |

**`200 OK` — active token:**

```json
{
  "active": true,
  "token_type": "Bearer",
  "scope": "openid profile",
  "client_id": "demo-client",
  "sub": "demo-user",
  "username": "demo-user",
  "exp": 1718370000,
  "iat": 1718369700,
  "jti": "a1b2c3d4...",
  "iss": "http://localhost:9000",
  "azp": "demo-client"
}
```

**`200 OK` — inactive / invalid / expired token** (per RFC 7662, not an error):

```json
{ "active": false }
```

**Error responses** (body `{ "error": ..., "error_description": ... }`):

| Status | `error` | Cause |
|---|---|---|
| `400 Bad Request` | `invalid_request` | Missing `token` parameter |
| `401 Unauthorized` | `invalid_client` | Client authentication failed |
| `403 Forbidden` | `unauthorized_client` | Client lacks the `introspection` scope |

## Flow (`IntrospectTokenService`)

1. Validate that `token` is present → otherwise `400 invalid_request`.
2. Authenticate the calling client via HTTP Basic (`clients/ClientAuthenticationService`) → otherwise `401`.
3. Confirm the client holds the `introspection` scope (`authorization/AuthorizationPolicyService` + `clients/ClientScopeService`) → otherwise `403 unauthorized_client`.
4. Delegate to `tokens/introspection/TokenIntrospectionValidator` for the actual RFC 7662 evaluation.

## Why a custom endpoint

Custom introspection/revocation services run as `permitAll` chains; client authentication and scope checks are handled inside the service rather than by Spring's default chain. This keeps tenant path rewriting and the `introspection`-scope gate consistent across tenant and non-tenant paths.

## Implementation

| Concern | Class / file |
|---|---|
| Controller | [`tokens/introspection/OAuth2TokenIntrospectionController`](../../src/main/java/io/github/edmaputra/enhauthserv/tokens/introspection/OAuth2TokenIntrospectionController.java) |
| Service | [`tokens/introspection/IntrospectTokenService`](../../src/main/java/io/github/edmaputra/enhauthserv/tokens/introspection/IntrospectTokenService.java) (+ `IntrospectTokenCommand`, `IntrospectTokenResult`) |
| Client auth | `clients/ClientAuthenticationService` |
| Scope policy | `authorization/AuthorizationPolicyService` + `clients/ClientScopeService` |
| Token check | `tokens/introspection/TokenIntrospectionValidator` |
| Filter chains | `oauth/SecurityConfig` `@Order(1)` (tenant path, permitAll) and `@Order(3)` (base path, permitAll) |

Notes from the code:

- The endpoint is `permitAll` at the chain level; **client authentication and the `introspection`-scope gate happen inside the service**, not in Spring Security.
- `TokenIntrospectionValidator` first checks the token is active in `OAuth2AuthorizationService`, then decodes the JWT; any `JwtException` yields `{"active": false}`.

## Introspection — sequence

```mermaid
sequenceDiagram
    participant RS as Resource server
    participant Ctrl as OAuth2TokenIntrospectionController
    participant UC as IntrospectTokenService
    participant Auth as ClientAuthenticationService
    participant Pol as AuthorizationPolicyService
    participant V as TokenIntrospectionValidator

    RS->>Ctrl: POST /oauth2/introspect (Basic auth, token)
    Ctrl->>UC: introspect(IntrospectTokenCommand)
    alt token missing
        UC-->>Ctrl: 400 invalid_request
    end
    UC->>Auth: authenticateBasic(header)
    alt not authenticated
        Auth-->>UC: authenticated=false
        UC-->>Ctrl: 401 invalid_client
    end
    UC->>Pol: validateScope(clientId, "introspection")
    alt scope missing
        Pol-->>UC: unauthorized
        UC-->>Ctrl: 403 unauthorized_client
    end
    UC->>V: introspect(token)
    V-->>UC: RFC 7662 map (active true/false)
    UC-->>Ctrl: 200 result
    Ctrl-->>RS: JSON
```

## Related tests

- `TokenIntrospectionEndpointTests`, `IntrospectTokenServiceTests`, `AuthorizationPolicyUseCaseTests`
