# How-To: Resource Server Token Validation & Revocation

This guide walks through validating access tokens and managing token lifecycles from downstream API services, resource servers, and API gateways.

---

## Overview

Downstream resource servers can validate tokens using two standard strategies:
1. **Local Cryptographic Verification (Fast / Stateless)**: Download and cache public keys from the JWKS endpoint to verify token signature and expiry offline.
2. **Token Introspection (RFC 7662 - Authoritative / Real-Time)**: Call the Authorization Server's `/oauth2/introspect` endpoint to verify active status and real-time revocation.

Additionally, clients and resource servers can proactively invalidate compromised tokens using **Token Revocation (RFC 7009)**.

---

## Method 1: Local JWT Signature Verification via JWKS

Resource servers verify JWTs by fetching the public RSA key set from the Authorization Server.

### 1. Fetch Signing Keys
```bash
# Global / Default Tenant
curl -s http://localhost:9000/oauth2/jwks | jq

# Specific Tenant (e.g. acme-corp)
curl -s http://localhost:9000/t/acme-corp/oauth2/jwks | jq
```

**Expected JWKS Response:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "e": "AQAB",
      "use": "sig",
      "kid": "4a72d3f4-...",
      "n": "vX8rQ..."
    }
  ]
}
```

### 2. Spring Security Resource Server Configuration (Java Example)
In downstream Spring Boot microservices, add `spring-boot-starter-oauth2-resource-server` and configure `application.properties`:

```properties
# Validates signature against JWKS and verifies issuer
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9000
```
Or for a specific tenant:
```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9000/t/acme-corp
```

---

## Method 2: Remote Token Introspection (RFC 7662)

When an API gateway needs real-time validation (checking whether a token was revoked before its TTL expires):

### Requirements:
- Calling client must authenticate via HTTP Basic or Form Body.
- Calling client must hold the `introspection` scope.

### Request Introspection:
```bash
curl -s -X POST http://localhost:9000/oauth2/introspect \
  -u demo-client:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=ACCESS_TOKEN_HERE" | jq
```

> For a specific tenant: `POST http://localhost:9000/t/acme-corp/oauth2/introspect` (or add `X-Tenant-ID: acme-corp`).

### Active Token Response (`200 OK`):
```json
{
  "active": true,
  "token_type": "Bearer",
  "scope": "openid profile read",
  "client_id": "demo-client",
  "sub": "demo-user",
  "username": "demo-user",
  "exp": 1718370300,
  "iat": 1718370000,
  "jti": "8f03b2-...",
  "iss": "http://localhost:9000"
}
```

### Inactive or Expired Token Response (`200 OK`):
Per RFC 7662, expired or invalid tokens do not return HTTP error codes:
```json
{
  "active": false
}
```

---

## Method 3: Token Revocation (RFC 7009)

When a user logs out or an access token is compromised, clients or resource servers can proactively revoke the token.

### Requirements:
- Calling client must authenticate with its credentials.
- Calling client must hold the `revocation` scope.
- Revocation is strictly client-isolated: a client may only revoke tokens it was issued.

### Revoke an Access or Refresh Token:
```bash
curl -s -X POST http://localhost:9000/oauth2/revoke \
  -u demo-client:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=TOKEN_TO_REVOKE" \
  -d "token_type_hint=access_token"
```

**Response:**
`200 OK` with an empty response body (per RFC 7009).

### Verify Revocation:
Subsequent introspection of the revoked token immediately returns:
```json
{
  "active": false
}
```

---

## Method 4: OIDC RP-Initiated Logout

To end the user's interactive browser session and trigger single logout:

Redirect the browser to `/connect/logout`:
```text
http://localhost:9000/connect/logout?
  id_token_hint=ID_TOKEN_VALUE
  &post_logout_redirect_uri=http://localhost:3000/logged-out
  &state=xyzState123
```

- Terminates the Spring Security HTTP session.
- Redirects the user to the registered `post_logout_redirect_uri` (or defaults to the `/logged-out` confirmation page).

---

## Troubleshooting

| Problem | Cause | Resolution |
|---|---|---|
| `403 Forbidden` (`unauthorized_client`) on `/introspect` | Calling client lacks the `introspection` scope. | Add `introspection` to client's registered scopes. |
| `403 Forbidden` (`invalid_scope`) on `/revoke` | Calling client lacks the `revocation` scope. | Add `revocation` to client's registered scopes. |
| Token still active after revocation | Token was issued to a different `client_id`. | Revocation is scoped per client; ensure authenticating client owns the token. |

---

## Related Guides
- [Machine-to-Machine Integration](02-m2m-client-credentials.md)
- [SPA & PKCE Login](03-spa-pkce-login.md)
- [Token Introspection Specification](../features/06-token-introspection.md)
- [Token Revocation Specification](../features/07-token-revocation.md)
