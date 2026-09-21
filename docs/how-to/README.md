# How-To Guides (Operational Runbooks)

This section provides task-oriented, step-by-step operational runbooks for developing with and administering **ed-idp**. Each guide addresses a specific objective from start to finish.

---

## Guides by Objective

```
docs/how-to/
├── 01-tenant-onboarding.md                # Provision and isolate a new tenant namespace
├── 02-m2m-client-credentials.md           # Register & authenticate a backend M2M service
├── 03-spa-pkce-login.md                   # Integrate a Single-Page App or Mobile App with PKCE
├── 04-user-profiles-and-dynamic-claims.md # Provision users and route dynamic claims to tokens
├── 05-resource-server-token-validation.md # Introspect and revoke tokens from downstream services
└── 06-database-and-production-setup.md    # Deploy with PostgreSQL, Flyway, and reverse proxies
```

### 1. Tenant & Organization Management
- **[01. Tenant Onboarding](01-tenant-onboarding.md)**: How to define a new tenant partition (e.g. `acme-corp`), configure path vs. header resolution, verify per-tenant metadata discovery, and enforce strict tenant isolation.

### 2. Application & Client Integration
- **[02. Machine-to-Machine Client Credentials](02-m2m-client-credentials.md)**: How to register backend confidential service accounts in `oauth2_registered_client`, issue access tokens via HTTP Basic or POST body authentication, and work within scope whitelists.
- **[03. Single-Page Application (SPA) & Mobile PKCE Login](03-spa-pkce-login.md)**: How to register public clients, generate PKCE code challenges (`S256`), execute the interactive browser authorization flow, and exchange authorization codes for JWT tokens.

### 3. Identity & Token Customization
- **[04. User Profiles & Dynamic Claim Routing](04-user-profiles-and-dynamic-claims.md)**: How to provision user accounts, attach arbitrary custom key-value attributes, and configure database-driven `claim_inclusion_rules` targeting `USERINFO`, `ID_TOKEN`, or `ACCESS_TOKEN`.

### 4. Downstream API & Gateway Integration
- **[05. Resource Server Token Validation & Revocation](05-resource-server-token-validation.md)**: How to integrate backend resource servers and API gateways to validate tokens via RFC 7662 Introspection, handle proactive token revocation via RFC 7009, and trigger OIDC single logout.

### 5. Deployment & Production Operations
- **[06. Database & Production Setup](06-database-and-production-setup.md)**: How to switch from in-memory H2 to production PostgreSQL / MySQL, run Flyway migrations, tune token lifetimes, configure Actuator health probes, and secure ingress reverse proxies.

---

## Related Documentation
- [Documentation Portal](../README.md)
- [Executive Features & Roadmap](../features-and-roadmap.md)
- [Architecture & System Design](../architecture/00-architecture.md)
- [Feature Specifications](../features/)
