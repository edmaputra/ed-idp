# Feature 7 — Token Revocation (RFC 7009)

Clients can proactively invalidate an access or refresh token they were issued.

## Endpoints

| Endpoint | Method | Auth |
|---|---|---|
| `/oauth2/revoke` | POST (form) | HTTP Basic (client) |
| `/t/{tenant}/oauth2/revoke` | POST (form) | HTTP Basic (client) |

Parameters: `token` (required), `token_type_hint` (optional — `access_token` or `refresh_token`). On success returns `200 OK` with an empty body (per RFC 7009).

## API contract

`Content-Type: application/x-www-form-urlencoded`. Client authenticates with HTTP Basic and must hold the `revocation` scope.

```http
POST /oauth2/revoke HTTP/1.1
Authorization: Basic ZGVtby1jbGllbnQ6ZGVtby1zZWNyZXQ=
Content-Type: application/x-www-form-urlencoded

token=8xLOxBtZp8...&token_type_hint=refresh_token
```

| Form param | Required | Description |
|---|---|---|
| `token` | yes | The token to revoke |
| `token_type_hint` | no | `access_token` or `refresh_token` |

**Success** — `200 OK`, empty body (per RFC 7009; also returned when the token is unknown).

**Error responses** (body `{ "error": ..., "error_description": ... }`):

| Status | `error` | Cause |
|---|---|---|
| `400 Bad Request` | `invalid_request` | Missing `token` parameter |
| `401 Unauthorized` | `invalid_client` | Client authentication failed |
| `403 Forbidden` | `invalid_scope` | Client lacks the `revocation` scope |

## Flow (`RevokeTokenService`)

1. Validate `token` is present → otherwise `400 invalid_request`.
2. Authenticate the client via HTTP Basic (`clients/ClientAuthenticationService`) → otherwise `401`.
3. Confirm the client holds the `revocation` scope (`tokens/revocation/RevocationAuthorizationService`, checking the scopes granted by `ClientAuthenticationService`) → otherwise `403 invalid_scope`.
4. Delegate to `tokens/revocation/TokenRevoker`, which invalidates the token for the authenticated client via the tenant-aware `OAuth2AuthorizationService`.

A client may only revoke tokens it owns; revocation is scoped to the authenticated `registeredClientId`.

## Implementation

| Concern | Class / file |
|---|---|
| Controller | [`tokens/revocation/OAuth2TokenRevocationController`](../../src/main/java/io/github/edmaputra/enhauthserv/tokens/revocation/OAuth2TokenRevocationController.java) |
| Service | [`tokens/revocation/RevokeTokenService`](../../src/main/java/io/github/edmaputra/enhauthserv/tokens/revocation/RevokeTokenService.java) (+ `RevokeTokenCommand`, `RevokeTokenResult`) |
| Client auth | `clients/ClientAuthenticationService` |
| Scope policy | `tokens/revocation/RevocationAuthorizationService` |
| Revocation | `tokens/revocation/TokenRevoker` |
| Filter chains | `oauth/SecurityConfig` `@Order(1)` (tenant path) and `@Order(3)` (base path), both permitAll |

Notes from the code:

- Same `permitAll` + in-service auth pattern as introspection; the scope gate here is the `revocation` scope.
- Revocation is bound to the authenticated `registeredClientId`, so a client can only revoke tokens it owns. An unknown token still returns `200 OK`.

## Revocation — sequence

```mermaid
sequenceDiagram
    participant Cl as Client
    participant Ctrl as OAuth2TokenRevocationController
    participant UC as RevokeTokenService
    participant Auth as ClientAuthenticationService
    participant Pol as RevocationAuthorizationService
    participant Rev as TokenRevoker
    participant Store as TenantAwareOAuth2AuthorizationService

    Cl->>Ctrl: POST /oauth2/revoke (Basic auth, token, hint?)
    Ctrl->>UC: revoke(RevokeTokenCommand)
    alt token missing
        UC-->>Ctrl: 400 invalid_request
    end
    UC->>Auth: authenticateBasic(header)
    alt not authenticated
        UC-->>Ctrl: 401 invalid_client
    end
    UC->>Pol: canRevoke(grantedScopes)
    alt scope missing
        UC-->>Ctrl: 403 unauthorized_client
    end
    UC->>Rev: revokeTokenForClient(token, hint, registeredClientId)
    Rev->>Store: invalidate token for client
    Store-->>Rev: done
    Rev-->>UC: ok
    UC-->>Ctrl: 200 OK (empty)
    Ctrl-->>Cl: 200 OK
```

## Related tests

- `TokenRevocationEndpointTests`, `RevokeTokenServiceTests`
