# How-To: User Provisioning & Dynamic Claim Routing

This guide walks through creating user accounts, adding custom attributes, and routing them dynamically to **UserInfo**, **ID Tokens**, and **Access Tokens** using ed-idp's database-driven claim rules.

---

## Overview

Unlike traditional identity providers with static schemas, ed-idp allows you to:
1. Attach arbitrary key-value attributes to any user.
2. Direct each attribute to one or more token destinations:
   - `USERINFO` — returned in the `GET /userinfo` profile response.
   - `ID_TOKEN` — embedded in the OpenID Connect JWT ID Token for client consumption.
   - `ACCESS_TOKEN` — embedded in the Bearer JWT Access Token for API/resource server authorization.
3. Automatically safeguard against overriding reserved JWT/OIDC claims (`sub`, `iss`, `aud`, `exp`, etc.).

---

## Step 1: Create User Credentials

User accounts are stored in Spring Security's `users` and `authorities` tables, scoped by `tenant_id`:

```sql
-- Insert User with BCrypt password (e.g. password: 'UserSecret123!')
INSERT INTO users (username, password, enabled, tenant_id)
VALUES (
    'bob-engineer',
    '{bcrypt}$2a$10$w8T0iL6X8t0W2f/e5dOq1e2i3u4y5t6r7e8w9q0p1o2i3u4y5t6r7',
    true,
    'demo'
);

-- Assign Role
INSERT INTO authorities (username, authority, tenant_id)
VALUES (
    'bob-engineer',
    'ROLE_USER',
    'demo'
);
```

---

## Step 2: Create the Base User Profile

Create standard OpenID Connect profile metadata in `user_profiles`:

```sql
INSERT INTO user_profiles (
    username,
    name,
    email,
    email_verified,
    locale,
    zoneinfo,
    department,
    tenant
) VALUES (
    'bob-engineer',
    'Bob Engineer',
    'bob@example.com',
    true,
    'en-US',
    'America/New_York',
    'infrastructure',
    'demo'
);
```

---

## Step 3: Attach Custom Attributes

Insert arbitrary custom attributes into `user_profile_attributes`:

```sql
-- 1. Cost Center (Needed by billing microservices)
INSERT INTO user_profile_attributes (tenant_id, username, attribute_key, attribute_value)
VALUES ('demo', 'bob-engineer', 'cost_center', 'CC-ENG-904');

-- 2. Clearance Level (Needed by access gateways)
INSERT INTO user_profile_attributes (tenant_id, username, attribute_key, attribute_value)
VALUES ('demo', 'bob-engineer', 'clearance_level', 'top-secret');

-- 3. Theme Preference (Only needed by web UI)
INSERT INTO user_profile_attributes (tenant_id, username, attribute_key, attribute_value)
VALUES ('demo', 'bob-engineer', 'ui_theme', 'dark');
```

---

## Step 4: Configure Dynamic Claim Inclusion Rules

Define where each `attribute_key` should be routed by inserting rules into `claim_inclusion_rules`. The `targets` column accepts a comma-separated list of `USERINFO`, `ID_TOKEN`, `ACCESS_TOKEN`:

```sql
-- Route 'cost_center' to ACCESS_TOKEN and ID_TOKEN
INSERT INTO claim_inclusion_rules (tenant_id, attribute_key, targets)
VALUES ('demo', 'cost_center', 'ACCESS_TOKEN,ID_TOKEN')
ON CONFLICT (tenant_id, attribute_key) DO UPDATE SET targets = EXCLUDED.targets;

-- Route 'clearance_level' only to ACCESS_TOKEN (APIs need it, client doesn't)
INSERT INTO claim_inclusion_rules (tenant_id, attribute_key, targets)
VALUES ('demo', 'clearance_level', 'ACCESS_TOKEN')
ON CONFLICT (tenant_id, attribute_key) DO UPDATE SET targets = EXCLUDED.targets;

-- Route 'ui_theme' only to USERINFO (client app fetches it on startup)
INSERT INTO claim_inclusion_rules (tenant_id, attribute_key, targets)
VALUES ('demo', 'ui_theme', 'USERINFO')
ON CONFLICT (tenant_id, attribute_key) DO UPDATE SET targets = EXCLUDED.targets;
```

---

## Step 5: Verify the Output Across Targets

When Bob logs in via the Authorization Code flow, the token customizer and UserInfo mapper automatically compile the claims:

### 1. In the Decoded JWT Access Token (`ACCESS_TOKEN` Target)
Resource servers receive:
```json
{
  "sub": "bob-engineer",
  "cost_center": "CC-ENG-904",
  "clearance_level": "top-secret",
  "scope": ["openid", "profile", "read"]
}
```
*(Notice `ui_theme` is NOT present here).*

### 2. In the Decoded JWT ID Token (`ID_TOKEN` Target)
The frontend application receives:
```json
{
  "sub": "bob-engineer",
  "name": "Bob Engineer",
  "email": "bob@example.com",
  "cost_center": "CC-ENG-904"
}
```
*(Notice `clearance_level` and `ui_theme` are NOT present here).*

### 3. In the `/userinfo` Response (`USERINFO` Target)
```bash
curl -s http://localhost:9000/userinfo \
  -H "Authorization: Bearer <access_token>" | jq
```
```json
{
  "sub": "bob-engineer",
  "name": "Bob Engineer",
  "email": "bob@example.com",
  "email_verified": true,
  "locale": "en-US",
  "zoneinfo": "America/New_York",
  "ui_theme": "dark"
}
```

---

## Reserved Claim Protections

ed-idp prevents custom attributes from overriding core protocol claims. If a rule or attribute specifies any of the following keys, it is safely ignored:

```text
sub, iss, aud, exp, iat, nbf, jti, scope, client_id,
azp, token_type, auth_time, nonce, at_hash, c_hash, sid, amr, acr
```

---

## Related Guides
- [SPA & Mobile PKCE Integration](03-spa-pkce-login.md)
- [Dynamic Claims Feature Specification](../features/05-dynamic-claims.md)
- [Persistence & Schema Guide](../features/11-persistence-schema.md)
