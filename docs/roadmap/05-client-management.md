# Roadmap — Client & Application Management

Clients are seeded in code via `clients/ClientBootstrapService`. A complete IdP lets tenant admins manage their own client/application registrations.

## Proposed capabilities

### Dynamic Client Registration
- **RFC 7591** dynamic client registration endpoint.
- **RFC 7592** client configuration management (read/update/delete registration).
- Software statements / initial access tokens to control who may register.

### Admin client CRUD
- Tenant-scoped API + UI to create/update/rotate/disable clients.
- Manage redirect URIs, grant types, auth methods, scopes, token settings per client.

### Client credential management
- Client secret rotation with overlap window.
- `private_key_jwt` and `tls_client_auth` (mTLS) client authentication.
- Per-client signing/encryption key registration (`jwks_uri`).

### Client policies
- Per-client token TTLs and refresh rotation overrides (generalize `TokenPolicyProperties`).
- Per-client consent requirements and trusted (skip-consent) flag.
- Sender-constrained tokens: DPoP (RFC 9449) and mTLS-bound tokens (RFC 8705).

### Application catalog
- Self-service developer portal: register apps, view credentials, test flows.
- Application metadata (logo, description) shown on the consent screen.

## Fit
Add registration/admin controllers to the `clients` slice; persist client policy alongside `oauth2_registered_client` (tenant-scoped).

## Why it matters
Manual client provisioning doesn't scale. Dynamic registration and admin APIs are required for any multi-team or multi-tenant deployment.
