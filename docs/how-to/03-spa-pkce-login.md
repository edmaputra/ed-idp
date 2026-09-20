# How-To: Single-Page Application (SPA) & Mobile PKCE Integration

This guide walks through integrating public clients (React, Vue, Angular, Flutter, iOS, Android) using the **Authorization Code Grant with PKCE** (RFC 7636).

---

## Overview

Public clients run entirely on user devices and cannot securely store client secrets.
- **Client Authentication**: Configured as `none` (no `client_secret` required).
- **PKCE Mandatory**: EnhAuthServ strictly enforces Proof Key for Code Exchange (`code_challenge` / `code_verifier` with `S256`).
- **Interactive Flow**: Users authenticate via the browser session, approve scopes on the consent screen, and receive tokens back at their redirect URI.

---

## Step 1: Register the Public Client

Insert a client record into `oauth2_registered_client`:

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
    'spa-portal-client-uuid',
    'spa-portal',
    CURRENT_TIMESTAMP,
    NULL, -- No client secret for public clients
    'SPA Web Portal',
    'none',
    'authorization_code,refresh_token',
    'http://localhost:3000/callback,http://127.0.0.1:3000/callback',
    'http://localhost:3000/logged-out',
    'openid,profile,email,read',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.access-token-time-to-live":["java.time.Duration",300.0],"settings.token.reuse-refresh-tokens":false}',
    'demo'
);
```

> **Key Settings**:
> - `client_authentication_methods`: `'none'`
> - `settings.client.require-proof-key`: `true`
> - `settings.client.require-authorization-consent`: `true`

---

## Step 2: Generate PKCE Code Verifier and Challenge

Before sending the user to the authorization endpoint, your client application must generate a random `code_verifier` and compute its `code_challenge`:

### Code Verifier & Challenge Logic (JavaScript / WebCrypto)

```javascript
function generateRandomString(length = 64) {
  const charset = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  let result = '';
  const randomValues = new Uint8Array(length);
  crypto.getRandomValues(randomValues);
  for (let i = 0; i < length; i++) {
    result += charset[randomValues[i] % charset.length];
  }
  return result;
}

async function generateCodeChallenge(verifier) {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

// Example usage:
const codeVerifier = generateRandomString(); // Store in sessionStorage
const codeChallenge = await generateCodeChallenge(codeVerifier);
```

### Quick Shell Generator (for testing)
```bash
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d '=' | tr '+/' '-_')
CODE_CHALLENGE=$(echo -n "${CODE_VERIFIER}" | openssl dgst -sha256 -binary | openssl base64 -A | tr -d '=' | tr '+/' '-_')

echo "Verifier:  ${CODE_VERIFIER}"
echo "Challenge: ${CODE_CHALLENGE}"
```

---

## Step 3: Redirect User to Authorization Endpoint

Redirect the user's browser to `/oauth2/authorize`:

```text
http://localhost:9000/oauth2/authorize?
  response_type=code
  &client_id=spa-portal
  &redirect_uri=http://localhost:3000/callback
  &scope=openid%20profile%20email%20read
  &state=xyzState123
  &code_challenge=CODE_CHALLENGE_HERE
  &code_challenge_method=S256
```

> For a specific tenant, use: `http://localhost:9000/t/{tenant}/oauth2/authorize?...`

---

## Step 4: User Authentication & Consent

1. If not authenticated, the user is redirected to `/login` (default dev credentials: `demo-user` / `demo-password`).
2. After login, if the user has not previously consented to the requested scopes, EnhAuthServ renders the `/oauth2/authorize-consent` screen.
3. Upon approval, EnhAuthServ redirects the browser back to your `redirect_uri` with an authorization code:
   ```text
   http://localhost:3000/callback?code=SPLP2w...&state=xyzState123
   ```

---

## Step 5: Exchange Authorization Code for Tokens

From your application, execute a `POST` request to `/oauth2/token` passing the `code` and the original `code_verifier`:

```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "client_id=spa-portal" \
  -d "redirect_uri=http://localhost:3000/callback" \
  -d "code=AUTHORIZATION_CODE_HERE" \
  -d "code_verifier=ORIGINAL_CODE_VERIFIER_HERE" | jq
```

**Expected Response (`200 OK`):**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "refresh_token": "fLq9w1...",
  "id_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "openid profile email read"
}
```

---

## Step 6: Fetch User Profile via UserInfo Endpoint

Using the issued Bearer access token:

```bash
curl -s http://localhost:9000/userinfo \
  -H "Authorization: Bearer <access_token>" | jq
```

**Response (`200 OK`):**
```json
{
  "sub": "demo-user",
  "name": "Demo User",
  "email": "demo-user@example.com",
  "email_verified": true,
  "locale": "en-US",
  "zoneinfo": "Asia/Jakarta",
  "favorite_color": "blue"
}
```

---

## Step 7: Refreshing Tokens (Token Rotation)

When the access token expires, use the `refresh_token` to obtain a fresh pair:

```bash
curl -s -X POST http://localhost:9000/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token" \
  -d "client_id=spa-portal" \
  -d "refresh_token=REFRESH_TOKEN_HERE" | jq
```

> **Rotation Notice**: By default (`app.token.reuse-refresh-tokens=false`), each refresh call invalidates the previous refresh token and issues a new one. Always store the latest returned refresh token.

---

## Troubleshooting

| Issue | Cause | Fix |
|---|---|---|
| `400 Bad Request` (`invalid_grant` / PKCE failed) | `code_verifier` does not match `code_challenge`, or code expired (default lifespan: 5m). | Ensure `code_verifier` is stored across redirects and matches `S256` transform. |
| `302 Found` with `error=redirect_uri_mismatch` | Requested `redirect_uri` does not match database whitelist. | Add exact URI to `oauth2_registered_client.redirect_uris`. |
| Screen prompts consent repeatedly | Scopes were updated or consent record expired. | Once approved, returning users skip consent unless new scopes are requested. |

---

## Related Guides
- [Machine-to-Machine Client Registration](02-m2m-client-credentials.md)
- [User Profiles & Dynamic Claims](04-user-profiles-and-dynamic-claims.md)
- [OAuth 2.0 Feature Specification](../features/01-oauth2-authorization-server.md)
