# EnhAuthServ (`ed-auth`) Documentation Portal

**EnhAuthServ** is a multi-tenant OAuth 2.1 and OpenID Connect 1.0 **Identity Provider (IdP)** built on **Java 21**, **Spring Boot 3.5.x**, **Spring Authorization Server**, and **Spring Modulith**. It issues and validates security tokens, isolates state per tenant, executes token lifetime and revocation policies, and supports dynamically-assembled identity claims.

---

## Documentation Navigation

```
docs/
├── README.md                      # Documentation home / portal (this file)
├── features-and-roadmap.md        # Executive summary & 6-phase strategic roadmap
│
├── architecture/                  # Architectural principles, module graphs & filter chains
│   └── 00-architecture.md         # System design, Modulith slices, security chains & sequences
│
├── how-to/                        # Task-oriented operational runbooks (by objective)
│   ├── README.md                  # Index of operational guides
│   ├── 01-tenant-onboarding.md
│   ├── 02-m2m-client-credentials.md
│   ├── 03-spa-pkce-login.md
│   ├── 04-user-profiles-and-dynamic-claims.md
│   ├── 05-resource-server-token-validation.md
│   └── 06-database-and-production-setup.md
│
├── features/                      # Deep-dive feature specifications & API contracts
│   ├── 01-oauth2-authorization-server.md
│   ├── 02-openid-connect.md
│   ├── 03-multi-tenancy.md
│   ├── 04-token-policy.md
│   ├── 05-dynamic-claims.md
│   ├── 06-token-introspection.md
│   ├── 07-token-revocation.md
│   ├── 08-consent.md
│   ├── 09-client-management.md
│   ├── 10-session-logout.md
│   ├── 11-persistence-schema.md
│   └── 12-configuration.md
│
└── roadmap/                       # Product backlog & capability planning
    ├── README.md                  # Roadmap overview & phasing summary
    ├── 01-authentication.md
    ├── 02-user-lifecycle.md
    ├── 03-federation.md
    ├── 04-authorization.md
    ├── 05-client-management.md
    ├── 06-protocols.md
    ├── 07-security-hardening.md
    ├── 08-operability.md
    ├── 09-compliance.md
    └── 10-developer-experience.md
```

---

## Core Guides

| Section | Description |
|---|---|
| [Features & Roadmap](features-and-roadmap.md) | Executive summary of current capabilities and the 6-phase product roadmap |
| [Architecture & System Design](architecture/00-architecture.md) | Modulith package slices, security filter ordering, and request lifecycle sequence diagrams |
| [How-To Operational Guides](how-to/README.md) | Task-oriented recipes for tenant onboarding, client registration, claims, and deployment |
| [Roadmap Backlog](roadmap/README.md) | Thematic breakdown of planned enterprise IdP capabilities |

---

## How-To Guides (Operational Runbooks)

| Objective | Guide | Audience |
|---|---|---|
| Onboard a new tenant | [how-to/01-tenant-onboarding.md](how-to/01-tenant-onboarding.md) | Tenant / Identity Admins |
| Register a backend service (M2M) | [how-to/02-m2m-client-credentials.md](how-to/02-m2m-client-credentials.md) | Backend / API Developers |
| Integrate an SPA / Mobile App with PKCE | [how-to/03-spa-pkce-login.md](how-to/03-spa-pkce-login.md) | Frontend / Mobile Developers |
| Provision users & dynamic claims | [how-to/04-user-profiles-and-dynamic-claims.md](how-to/04-user-profiles-and-dynamic-claims.md) | Identity Admins |
| Validate / revoke tokens in Resource Servers | [how-to/05-resource-server-token-validation.md](how-to/05-resource-server-token-validation.md) | Backend / Gateway Engineers |
| Deploy with PostgreSQL & secure Ingress | [how-to/06-database-and-production-setup.md](how-to/06-database-and-production-setup.md) | DevOps / SREs |

---

## Feature Index

| # | Capability Area | Specification | Summary |
|---|---|---|---|
| 1 | OAuth 2.0 Authorization Server | [features/01-oauth2-authorization-server.md](features/01-oauth2-authorization-server.md) | Grant types (`authorization_code`, PKCE, `client_credentials`, `refresh_token`), endpoints, and filter chains |
| 2 | OpenID Connect (OIDC) | [features/02-openid-connect.md](features/02-openid-connect.md) | Discovery metadata, per-tenant JWKS, ID Tokens, and UserInfo endpoint |
| 3 | Multi-Tenancy | [features/03-multi-tenancy.md](features/03-multi-tenancy.md) | Path/header resolution, `TenantContext`, dynamic issuers, and schema namespacing |
| 4 | Token Policy & Lifetimes | [features/04-token-policy.md](features/04-token-policy.md) | Configurable TTLs, refresh token rotation, and machine scope whitelisting |
| 5 | Dynamic Claims | [features/05-dynamic-claims.md](features/05-dynamic-claims.md) | Dynamic claim inclusion rules targeting `USERINFO`, `ID_TOKEN`, or `ACCESS_TOKEN` |
| 6 | Token Introspection (RFC 7662) | [features/06-token-introspection.md](features/06-token-introspection.md) | Resource server token inspection, scope guards, and JSON responses |
| 7 | Token Revocation (RFC 7009) | [features/07-token-revocation.md](features/07-token-revocation.md) | Access/refresh token revocation, client authorization, and cache invalidation |
| 8 | User Consent | [features/08-consent.md](features/08-consent.md) | Interactive scope consent approval flow and tenant-scoped consent storage |
| 9 | Client Management & Bootstrap | [features/09-client-management.md](features/09-client-management.md) | Development client and user seeding (`demo-client`, `pkce-public-client`, `demo-user`) |
| 10 | Session & Logout | [features/10-session-logout.md](features/10-session-logout.md) | Form login, OIDC RP-initiated logout (`/connect/logout`), and post-logout landing |
| 11 | Persistence & Schema | [features/11-persistence-schema.md](features/11-persistence-schema.md) | Spring Data JPA, Flyway migrations (`V0_0_1_001` through `007`), and RDBMS setup |
| 12 | Configuration Reference | [features/12-configuration.md](features/12-configuration.md) | Complete reference of application, security, token, and tenancy properties |

---

## Roadmap Themes

| Theme | Document | Key Deliverables |
|---|---|---|
| Authentication & Factors | [roadmap/01-authentication.md](roadmap/01-authentication.md) | MFA (TOTP), WebAuthn/Passkeys, Magic Links, Brute-force protection |
| User Lifecycle | [roadmap/02-user-lifecycle.md](roadmap/02-user-lifecycle.md) | Self-registration, password reset, account verification, SCIM provisioning |
| Identity Federation | [roadmap/03-federation.md](roadmap/03-federation.md) | Social login (Google/GitHub/Apple), Enterprise SAML 2.0 Web SSO, account linking |
| Authorization & Access | [roadmap/04-authorization.md](roadmap/04-authorization.md) | Fine-grained RBAC, Contextual ABAC, OpenFGA / Cedar policy engines |
| Client Management | [roadmap/05-client-management.md](roadmap/05-client-management.md) | Dynamic Client Registration (RFC 7591), Client Admin APIs, Secret rotation |
| Protocols & Standards | [roadmap/06-protocols.md](roadmap/06-protocols.md) | Device Authorization Flow (RFC 8628), CIBA, DPoP (RFC 9449), MTLS (RFC 8705) |
| Security Hardening | [roadmap/07-security-hardening.md](roadmap/07-security-hardening.md) | Automated key rotation, Token-bucket rate limiting, Redis token blacklisting |
| Operability & Platform | [roadmap/08-operability.md](roadmap/08-operability.md) | OpenTelemetry metrics/tracing, Prometheus exposition, Structured SIEM audit logs |
| Compliance & Privacy | [roadmap/09-compliance.md](roadmap/09-compliance.md) | Immutable consent ledger, GDPR data export & right-to-be-forgotten |
| Developer Experience | [roadmap/10-developer-experience.md](roadmap/10-developer-experience.md) | Admin web console, Interactive API explorer, SDKs and client libraries |

---

## Quick Start

```bash
./mvnw clean package          # Build & package
./mvnw spring-boot:run        # Run locally (port 9000)
./mvnw test                   # Run all tests
```

- **H2 Console**: `http://localhost:9000/h2-console` (JDBC URL `jdbc:h2:mem:authdb`, user `sa`)
- **OIDC Discovery**: `GET http://localhost:9000/t/demo/.well-known/openid-configuration`
- **JWK Set**: `GET http://localhost:9000/t/demo/oauth2/jwks`

### Built-in Test Credentials

| Type | Client / User | Secret | Notes |
|---|---|---|---|
| Confidential Client | `demo-client` | `demo-secret` | HTTP Basic or POST body auth; all grant types |
| Public Client (PKCE) | `pkce-public-client` | — | `authorization_code` with PKCE mandatory (`S256`) |
| End User | `demo-user` | `demo-password` | Form login session; role `ROLE_USER` |

---

## Technology Stack

- **Runtime**: Java 21
- **Framework**: Spring Boot 3.5.x, Spring Security 6.x, Spring Authorization Server
- **Modularity**: Spring Modulith 1.4.x
- **Persistence**: Spring Data JPA, Spring JDBC, Flyway
- **Database**: H2 (in-memory dev default), swappable for PostgreSQL / MySQL
- **Cryptography**: Nimbus JOSE JWT / JWK
