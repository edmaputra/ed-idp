# Roadmap — Toward a Complete Identity Provider

This roadmap maps the full problem space of a production Identity Provider and positions EnhAuthServ's current capabilities against it. It is intended as a planning backlog: each item lists **what it is**, **why it matters**, and **how it would fit** this codebase's vertical-slice modular monolith.

## Current coverage snapshot

| Capability area | Status |
|---|---|
| OAuth2 core grants (code, PKCE, client_credentials, refresh) | ✅ Implemented |
| OIDC (discovery, userinfo, id_token, logout) | ✅ Implemented |
| Token introspection / revocation | ✅ Implemented |
| Multi-tenancy (state isolation, per-tenant issuer) | ✅ Implemented |
| Dynamic claims | ✅ Implemented |
| Configurable token policy | ✅ Implemented |
| User consent | ✅ Implemented |
| **Authentication factors (MFA, passwordless, social)** | ❌ Not yet |
| **User lifecycle & self-service** | ❌ Not yet |
| **Dynamic client registration / admin APIs** | ❌ Not yet |
| **External IdP federation / brokering** | ❌ Not yet |
| **Authorization (RBAC/ABAC, fine-grained)** | ⚠️ Scope-only |
| **Security hardening (key rotation, rate limiting, threat detection)** | ⚠️ Partial |
| **Operability (observability, audit, HA)** | ❌ Not yet |
| **Compliance & privacy (consent ledger, GDPR)** | ❌ Not yet |

## Roadmap documents

| Theme | File |
|---|---|
| Authentication & credentials | [01-authentication.md](01-authentication.md) |
| User lifecycle & self-service | [02-user-lifecycle.md](02-user-lifecycle.md) |
| Federation & identity brokering | [03-federation.md](03-federation.md) |
| Authorization & access management | [04-authorization.md](04-authorization.md) |
| Client & application management | [05-client-management.md](05-client-management.md) |
| Protocol & standards expansion | [06-protocols.md](06-protocols.md) |
| Security hardening | [07-security-hardening.md](07-security-hardening.md) |
| Operability & platform | [08-operability.md](08-operability.md) |
| Compliance, privacy & governance | [09-compliance.md](09-compliance.md) |
| Developer & admin experience | [10-developer-experience.md](10-developer-experience.md) |

## Suggested phasing

- **Phase 1 — Production readiness:** MFA (TOTP), key rotation, rate limiting, audit logging, dynamic client registration, admin API. *(see [01](01-authentication.md), [05](05-client-management.md), [07](07-security-hardening.md), [08](08-operability.md))*
- **Phase 2 — Enterprise identity:** Social login + SAML/OIDC federation, user self-service, RBAC, account linking. *(see [02](02-user-lifecycle.md), [03](03-federation.md), [04](04-authorization.md))*
- **Phase 3 — Advanced & differentiating:** Passwordless/WebAuthn, device & CIBA flows, fine-grained authorization, step-up auth, risk-based/adaptive auth. *(see [01](01-authentication.md), [04](04-authorization.md), [06](06-protocols.md))*
- **Phase 4 — Compliance & scale:** Consent ledger, GDPR tooling, HA/clustering, token exchange, SCIM provisioning. *(see [02](02-user-lifecycle.md), [06](06-protocols.md), [09](09-compliance.md))*
