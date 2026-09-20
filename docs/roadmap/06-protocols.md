# Roadmap — Protocol & Standards Expansion

The server implements OAuth2 core, OIDC core, introspection (7662), and revocation (7009). A complete IdP covers the broader OAuth/OIDC standards surface needed by diverse client types.

## Proposed capabilities

### Additional OAuth2 grants & flows
- **Device Authorization Grant** (RFC 8628) — TVs, CLIs, IoT.
- **CIBA** (Client-Initiated Backchannel Authentication) — decoupled/push approval.
- **Token Exchange** (RFC 8693) — delegation/impersonation (see [Authorization](04-authorization.md)).
- **Resource Indicators** (RFC 8707) — audience-targeted tokens.

### OIDC extensions
- **Session Management** & **Front-Channel / Back-Channel Logout** — propagate logout to RPs.
- **Pairwise subject identifiers** (`sub` per client) for privacy.
- **JARM** — JWT-secured authorization responses.
- **Request objects** (RFC 9101 / PAR) — Pushed Authorization Requests (RFC 9126).
- **Claims request parameter** — per-request claim selection.
- **OIDC Federation** for multi-party trust.

### Hardening profiles
- **FAPI 1.0 / FAPI 2.0** compliance for high-assurance (financial/health) deployments.
- **DPoP** (RFC 9449) and **mTLS-bound tokens** (RFC 8705) — sender-constrained tokens.

### Provisioning & assertion standards
- **SCIM 2.0** (see [User Lifecycle](02-user-lifecycle.md)).
- **SAML 2.0** (see [Federation](03-federation.md)).

### Verifiable credentials (forward-looking)
- OID4VCI / OID4VP, decentralized identifiers (DIDs) for wallet-based identity.

## Fit
Each flow maps to a new use case + controller, reusing the tenant-aware authorization/token services. Spring Authorization Server already provides extension points for device/PAR/DPoP.

## Why it matters
Different clients (IoT, CLIs, high-assurance APIs) need different flows. Standards coverage determines which integrations are even possible.
