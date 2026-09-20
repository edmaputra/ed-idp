# Roadmap — Authorization & Access Management

Authorization today is limited to OAuth2 **scopes** and a single `ROLE_USER`. A complete IdP offers richer, policy-driven access models that resource servers can rely on.

## Proposed capabilities

### Role-Based Access Control (RBAC)
- First-class roles and permissions, role hierarchies, per-tenant role catalogs.
- Roles surfaced as claims (`roles`, `permissions`) routed via the existing [dynamic claims](../features/05-dynamic-claims.md) targets.

### Attribute-Based Access Control (ABAC)
- Policy decisions from user/resource/environment attributes.
- Policy language (e.g., Cedar / OPA-Rego / XACML-style) evaluated at token issuance or via a decision endpoint.

### Fine-Grained Authorization (ReBAC)
- Relationship-based model (Google Zanzibar style) for "user X can edit document Y".
- Authorization-check API for resource servers.

### Scope governance
- Scope catalog with descriptions and consent metadata.
- Per-client allowed-scope policy (generalize today's `client-credentials-allowed-scopes`).
- Audience-restricted tokens (`aud`) and resource indicators (RFC 8707).

### Delegation & impersonation
- OAuth2 **Token Exchange** (RFC 8693) for impersonation/delegation and service-to-service.
- Admin "log in as user" with full audit trail.

### Policy administration
- Admin UI/API to manage roles, permissions, and policies per tenant.

## Fit
An `AuthorizationDecisionService` in the `authorization` slice consulted during token customization and exposed as a decision endpoint; role/permission entities; a pluggable policy-engine component. Claims continue to flow through `claims/UserClaimsService`.

## Why it matters
Scopes alone can't express organizational access rules. RBAC/ABAC/ReBAC let the IdP be the authoritative source for *what a subject may do*, not just *who they are*.
