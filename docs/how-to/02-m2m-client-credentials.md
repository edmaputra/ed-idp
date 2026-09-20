# How-To: Machine-to-Machine (M2M) Client Credentials Integration

This guide walks through registering and authenticating a backend daemon or microservice using the OAuth 2.0 **Client Credentials Grant** (`client_credentials`).

---

## Overview

The Client Credentials flow is intended for machine-to-machine (M2M) communication where no user is present.
- The client authenticates directly with its `client_id` and `client_secret`.
- Spring Authorization Server validates credentials and returns a signed JWT access token.
- EnhAuthServ enforces a **Scope Whitelist Guard** (`app.token.client-credentials-allowed-scopes`) to prevent automated clients from requesting user-level privileges.

---

## Step 1: Generate Client Secret & BCrypt Hash

Generate a random secret and compute its BCrypt hash.

In Java or via a shell tool:
```bash
# Example secret: "my-service-secret-12345"
# BCrypt hash example:
$2a$10$w8T0iL6X8t0W2f/e5dOq1e2i3u4y5t6r7e8w9q0p1o2i3u4y5t6r7
```

---

## Step 2: Register the Client in the Database

Insert a new client record into `oauth2_registered_client`:

```sql
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_id_issued_at,
    client_secret,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    post_logout_redirect_uris,
    scopes,
    client_settings,
    token_settings,
    tenant_id
) VALUES (
    'm2m-billing-service-uuid',
    'billing-service',
    CURRENT_TIMESTAMP,
    '{bcrypt}$2a$10$w8T0iL6X8t0W2f/e5dOq1e2i3u4y5t6r7e8w9q0p1o2i3u4y5t6r7',
    'Billing Microservice',
    'client_secret_basic,client_secret_post',
    'client_credentials',
    NULL,
    NULL,
    'read,write,introspection',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.access-token-time-to-live":["java.time.Duration",300.0],"settings.token.reuse-refresh-tokens":false}',
    'demo'
);
```

> **Note**: For tenant-scoped deployments, replace `demo` with your target `tenant_id` (e.g. `acme-corp`).

---

## Step 3: Verify Scope Guard Configuration

By default, EnhAuthServ only permits M2M clients to request scopes listed in `app.token.client-credentials-allowed-scopes` (defined in `application.properties`):

```properties
app.token.client-credentials-allowed-scopes=read,write,introspection,revocation
```

If your client requests a scope outside this list (e.g., `openid`, `profile`, or `admin`), the token endpoint will immediately reject the request with `400 Bad Request` (`invalid_scope`).

---

## Step 4: Request an Access Token

### Option A: HTTP Basic Authentication (`client_secret_basic`)

```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -u billing-service:my-service-secret-12345 \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=read write" | jq
```

### Option B: Form Body Authentication (`client_secret_post`)

```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=billing-service" \
  -d "client_secret=my-service-secret-12345" \
  -d "scope=read write" | jq
```

### Option C: Tenant-Scoped Token Request

When targeting a specific tenant (e.g., `acme-corp`), use the path prefix or `X-Tenant-ID` header:

```bash
curl -s -X POST http://localhost:9000/t/acme-corp/oauth2/token \
  -u billing-service:my-service-secret-12345 \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=read" | jq
```

**Expected Response (`200 OK`):**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "read write"
}
```

---

## Step 5: Inspect and Validate the Token

Decode the JWT payload (e.g. via `jq` or `jwt.io`):
```json
{
  "iss": "http://localhost:9000",
  "sub": "billing-service",
  "aud": "billing-service",
  "exp": 1718370300,
  "nbf": 1718370000,
  "iat": 1718370000,
  "jti": "5a46e1d2-...",
  "scope": [
    "read",
    "write"
  ]
}
```

---

## Troubleshooting Common Issues

| Error | Cause | Fix |
|---|---|---|
| `401 Unauthorized` (`invalid_client`) | Missing or incorrect `client_id` / `client_secret`. | Verify credentials and BCrypt hash prefix `{bcrypt}`. |
| `400 Bad Request` (`invalid_scope`) | Requested scope is not registered for client OR not allowed in `client-credentials-allowed-scopes`. | Add the scope to `app.token.client-credentials-allowed-scopes` in `application.properties`. |
| `302 Found` to `/login` | Client omitted credentials or sent invalid Basic Auth syntax. | Ensure `Authorization: Basic <base64>` header is present and valid. |

---

## Related Guides
- [Tenant Onboarding](01-tenant-onboarding.md)
- [Resource Server Token Validation](05-resource-server-token-validation.md)
- [Token Policy Specification](../features/04-token-policy.md)
