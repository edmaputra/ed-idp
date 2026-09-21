# How-To: Tenant Onboarding & Multi-Tenant Isolation

This guide walks through onboarding and configuring a new tenant in **ed-idp**.

---

## Overview

In ed-idp, multi-tenancy is logical and schema-isolated:
- Every tenant has a unique identifier matching `^[A-Za-z0-9_-]+$` (e.g. `acme-corp`, `fintech-eu`).
- Each tenant receives its own dynamic issuer: `{baseIssuer}/t/{tenantId}` (e.g. `http://localhost:9000/t/acme-corp`).
- Database records in `oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent`, `users`, `user_profiles`, `user_profile_attributes`, and `claim_inclusion_rules` are partitioned by `tenant_id`.
- Tenant context is resolved per-request by `TenantContextFilter` and stored in thread-local `TenantContext`.

---

## Step 1: Choose a Tenant Identifier

Tenant IDs must consist only of alphanumeric characters, hyphens, and underscores:
```text
Valid:   acme-corp, tenant_123, department-a
Invalid: acme corp (spaces), acme/corp (slashes), acme@corp (special chars)
```

For this guide, we will use `acme-corp`.

---

## Step 2: Verify Per-Tenant Discovery & Key Material

Because issuers are dynamic, a tenant partition is automatically recognized as soon as it is requested. Test the tenant's OpenID Connect discovery metadata:

```bash
curl -s http://localhost:9000/t/acme-corp/.well-known/openid-configuration | jq
```

**Expected Response (`200 OK`):**
```json
{
  "issuer": "http://localhost:9000/t/acme-corp",
  "authorization_endpoint": "http://localhost:9000/t/acme-corp/oauth2/authorize",
  "token_endpoint": "http://localhost:9000/t/acme-corp/oauth2/token",
  "jwks_uri": "http://localhost:9000/t/acme-corp/oauth2/jwks",
  "userinfo_endpoint": "http://localhost:9000/t/acme-corp/userinfo",
  "introspection_endpoint": "http://localhost:9000/t/acme-corp/oauth2/introspect",
  "revocation_endpoint": "http://localhost:9000/t/acme-corp/oauth2/revoke",
  "end_session_endpoint": "http://localhost:9000/t/acme-corp/connect/logout"
}
```

Verify the tenant's public JWK Set:
```bash
curl -s http://localhost:9000/t/acme-corp/oauth2/jwks | jq
```

---

## Step 3: Choose Request Resolution Method

Clients and downstream services can identify their tenant in one of two ways:

### Option A: Path Prefix (Recommended for Public Endpoints & Discovery)
Prepend `/t/{tenant}` to any OAuth2 or OIDC URL:
```bash
# Authorization Endpoint
http://localhost:9000/t/acme-corp/oauth2/authorize

# Token Endpoint
curl -s -X POST http://localhost:9000/t/acme-corp/oauth2/token \
  -d "grant_type=client_credentials&scope=read"
```

### Option B: HTTP Header (Recommended for Internal Microservices)
Pass the `X-Tenant-ID` header to standard canonical endpoints:
```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -H "X-Tenant-ID: acme-corp" \
  -d "grant_type=client_credentials&scope=read"
```

---

## Step 4: Provision Tenant Data

State is partitioned by the `tenant_id` column. When creating clients, users, or claim rules for `acme-corp`, set `tenant_id = 'acme-corp'`.

### Example: Provisioning a Tenant Client
```sql
INSERT INTO oauth2_registered_client (
    id, client_id, client_id_issued_at, client_secret,
    client_name, client_authentication_methods, authorization_grant_types,
    redirect_uris, post_logout_redirect_uris, scopes,
    client_settings, token_settings, tenant_id
) VALUES (
    'client-acme-svc-001',
    'acme-service',
    CURRENT_TIMESTAMP,
    '{bcrypt}$2a$10$w8T0iL6X8t0W2f/e5dOq1e2i3u4y5t6r7e8w9q0p1o2i3u4y5t6r7', -- BCrypt hash of 'acme-secret'
    'Acme Internal Service',
    'client_secret_basic,client_secret_post',
    'client_credentials',
    NULL,
    NULL,
    'read,write',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.access-token-time-to-live":["java.time.Duration",300.0],"settings.token.reuse-refresh-tokens":false}',
    'acme-corp'
);
```

### Example: Provisioning a Tenant User
```sql
-- 1. Authentication Credentials
INSERT INTO users (username, password, enabled, tenant_id)
VALUES ('acme-alice', '{bcrypt}$2a$10$w8T0iL6X8t0W2f/e5dOq1e2i3u4y5t6r7e8w9q0p1o2i3u4y5t6r7', true, 'acme-corp');

INSERT INTO authorities (username, authority, tenant_id)
VALUES ('acme-alice', 'ROLE_USER', 'acme-corp');

-- 2. Extended Profile
INSERT INTO user_profiles (username, name, email, email_verified, locale, zoneinfo, department, tenant)
VALUES ('acme-alice', 'Alice Smith', 'alice@acme.com', true, 'en-US', 'America/New_York', 'finance', 'acme-corp');
```

---

## Step 5: Enforcing Strict Tenant Resolution

By default in development, requests without an explicit tenant default to `demo`. In production, you should enable strict tenant resolution to reject un-tenanted requests with `400 Bad Request`:

In `application.properties`:
```properties
tenant.resolution.require-explicit-tenant=true
```

Test rejection without tenant:
```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9000/oauth2/introspect
# Response: 400 Bad Request
```

Test acceptance with header:
```bash
curl -s -H "X-Tenant-ID: acme-corp" -o /dev/null -w "%{http_code}\n" http://localhost:9000/oauth2/introspect
# Response: proceeds into authentication chain
```

---

## Step 6: Securing Header Trust Boundaries

When using `X-Tenant-ID`, clients must not be allowed to forge tenant headers directly from the public internet.

1. Configure your edge proxy (NGINX / Envoy / ALB) to **strip** `X-Tenant-ID` from incoming external requests.
2. In `application.properties`, restrict header evaluation to trusted proxies:
   ```properties
   tenant.resolution.enforce-trusted-proxy-for-header=true
   tenant.resolution.header-trusted-sources=10.0.0.1,10.0.0.2,127.0.0.1
   ```

---

## Related Guides
- [Machine-to-Machine Client Registration](02-m2m-client-credentials.md)
- [User Profiles & Dynamic Claims](04-user-profiles-and-dynamic-claims.md)
- [Detailed Multi-Tenancy Specification](../features/03-multi-tenancy.md)
