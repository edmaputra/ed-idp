# Onboarding Guide — ed-idp

Welcome 👋 This is your **guided learning path** for getting productive in ed-idp.

ed-idp is a **multi-tenant OAuth 2.1 / OpenID Connect Identity Provider** built on Spring Authorization Server, structured as a **Vertical-Slice Modular Monolith** using **Spring Modulith**.

> This file is the *map*. It tells you **what to read, in what order, and which code to open** so knowledge builds up layer by layer. All reference material lives in [docs/](docs/); this guide routes you through it.

**Suggested pace:** ~3–4 focused days. Don't skip the checkpoints — running things beats reading about them.

---

## Stage 0 — Orientation & First Run (≈ half a day)

**Goal:** The application runs on your machine and you can issue your first token.

1. Read the top-level [README.md](README.md) — elevator pitch and built-in clients.
2. Read the project guidance: [CLAUDE.md](CLAUDE.md) — build commands, key concepts, built-in test credentials, and testing patterns.
3. Review the high-level roadmap: [docs/features-and-roadmap.md](docs/features-and-roadmap.md).
4. Build and run:
   ```bash
   ./mvnw clean package          # Build & test
   ./mvnw spring-boot:run        # Run on port 9000
   ```
5. Explore the running server:
   - H2 Console: `http://localhost:9000/h2-console` (JDBC URL `jdbc:h2:mem:authdb`, user `sa`)
   - OIDC Discovery: `GET http://localhost:9000/t/demo/.well-known/openid-configuration`
   - JWK Set: `GET http://localhost:9000/t/demo/oauth2/jwks`

✅ **Checkpoint:** Run the `client_credentials` grant against `POST /oauth2/token` using `demo-client` / `demo-secret` and receive an access token:
```bash
curl -s -u demo-client:demo-secret \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&scope=read" \
  http://localhost:9000/oauth2/token | jq
```

---

## Stage 1 — The Mental Model: Vertical-Slice Modular Monolith (≈ half a day)

**Goal:** Understand how the codebase is partitioned by business feature and how Spring Modulith boundaries are declared and enforced.

1. Read [docs/architecture/00-architecture.md](docs/architecture/00-architecture.md) — system design, module map, and request lifecycle.
2. Inspect `package-info.java` across the slices to see the `@ApplicationModule` declarations and allowed dependencies:
   - Foundation slices with zero dependencies: `tenancy/`, `users/`, `shared/`
   - Protocol & Security slice: `oauth/`
   - Domain slices: `clients/`, `authorization/`, `consent/`, `claims/`, `tokens/`
3. Inspect how Spring Security configures ordered filter chains in [`src/main/java/io/github/edmaputra/edidp/oauth/SecurityConfig.java`](src/main/java/io/github/edmaputra/edidp/oauth/SecurityConfig.java).

**The Feature Slices Cheat-Sheet**:

| Slice | Package | Primary Responsibility | Allowed Dependencies |
|---|---|---|---|
| `tenancy` | `tenancy/` | Request tenant resolution, `TenantContext`, dynamic issuer URLs | None |
| `users` | `users/` | User profiles, dynamic attributes, JPA repositories | None |
| `shared` | `shared/` | Shared controllers (e.g. `/logged-out`) | None |
| `oauth` | `oauth/` | Spring Security chains, OIDC metadata, JWKs, tenant JDBC repos | `tenancy`, `shared` |
| `clients` | `clients/` | Client registration bootstrap, client authentication, scope checks | `tenancy`, `oauth` |
| `authorization` | `authorization/` | Scope validation rules and authorization policy | `clients` |
| `consent` | `consent/` | User consent decision flow and persistence | `tenancy`, `oauth` |
| `claims` | `claims/` | Dynamic claim assembly and rule filtering | `users`, `tenancy` |
| `tokens` | `tokens/` | Token policy, RFC 7662 introspection, RFC 7009 revocation | `authorization`, `clients`, `tenancy`, `oauth` |

✅ **Checkpoint:** Explain why `claims` may depend on `users` and `tenancy`, but `users` has zero dependencies on `claims`.

---

## Stage 2 — The Core: OAuth 2.1 & OIDC Token Issuance (≈ 1 day)

**Goal:** Follow an Authorization Code + PKCE flow end-to-end and understand Spring Authorization Server integration.

1. Read [docs/features/01-oauth2-authorization-server.md](docs/features/01-oauth2-authorization-server.md) — supported grant types, PKCE requirements, and token endpoints.
2. Read [docs/features/02-openid-connect.md](docs/features/02-openid-connect.md) — OIDC discovery metadata, ID tokens, and UserInfo.
3. Open [`src/main/java/io/github/edmaputra/edidp/oauth/SecurityConfig.java`](src/main/java/io/github/edmaputra/edidp/oauth/SecurityConfig.java) to study:
   - `@Order(1)` Tenant machine endpoints
   - `@Order(2)` Authorization server core with `jwtTokenCustomizer` and `userInfoMapper`
   - `@Order(3)` Base machine endpoints
   - `@Order(4)` Default web security chain with form login
4. Walk the integration tests:
   - [`AuthServerAuthorizationFlowTests`](src/test/java/io/github/edmaputra/edidp/authorization/AuthServerAuthorizationFlowTests.java)
   - [`AuthServerPkceFlowTests`](src/test/java/io/github/edmaputra/edidp/authorization/AuthServerPkceFlowTests.java)
   - Shared base: [`AuthServerIntegrationTests`](src/test/java/io/github/edmaputra/edidp/integration/AuthServerIntegrationTests.java)

✅ **Checkpoint:** Run `./mvnw -Dtest=AuthServerPkceFlowTests test` and verify that the test suite passes.

---

## Stage 3 — Core Differentiators: Tenancy, Policy & Dynamic Claims (≈ 1 day)

### 3a. Multi-Tenancy
- Read [docs/features/03-multi-tenancy.md](docs/features/03-multi-tenancy.md).
- Trace request resolution:
  - `TenantContextFilter` (highest precedence servlet filter)
  - `ResolveTenantService` (evaluates `X-Tenant-ID` header and `/t/{tenant}/` path prefix)
  - `TenantContext` (thread-local state holder with guaranteed cleanup)
  - `TenantIssuerService` (computes `{baseIssuer}/t/{tenantId}`)
  - Tenant-aware repository wrappers: `TenantAwareRegisteredClientRepository`, `TenantAwareOAuth2AuthorizationService`, `TenantAwareOAuth2AuthorizationConsentService`.

### 3b. Token Policy & Lifetimes
- Read [docs/features/04-token-policy.md](docs/features/04-token-policy.md).
- Inspect [`TokenPolicyProperties`](src/main/java/io/github/edmaputra/edidp/tokens/TokenPolicyProperties.java) (`app.token.*`).
- See how refresh token rotation and client credentials scope whitelisting are verified in [`TokenPolicyControlsTests`](src/test/java/io/github/edmaputra/edidp/tokens/TokenPolicyControlsTests.java).

### 3c. Dynamic Claims
- Read [docs/features/05-dynamic-claims.md](docs/features/05-dynamic-claims.md).
- Understand the data model: `UserProfile` + `UserProfileAttribute` + `ClaimInclusionRule` (`ClaimTarget`: `USERINFO`, `ID_TOKEN`, `ACCESS_TOKEN`).
- Study [`UserClaimsService`](src/main/java/io/github/edmaputra/edidp/claims/UserClaimsService.java) and verify reserved JWT claim safeguards.

✅ **Checkpoint:** Trace how an attribute with target `ACCESS_TOKEN` is injected into the JWT access token by `jwtTokenCustomizer`.

---

## Stage 4 — Supporting Protocols & Flows (≈ half a day)

Each capability is encapsulated in its own vertical slice:

| Feature | Documentation | Key Classes |
|---|---|---|
| Token Introspection (RFC 7662) | [docs/features/06-token-introspection.md](docs/features/06-token-introspection.md) | `tokens.introspection.OAuth2TokenIntrospectionController`, `IntrospectTokenService` |
| Token Revocation (RFC 7009) | [docs/features/07-token-revocation.md](docs/features/07-token-revocation.md) | `tokens.revocation.OAuth2TokenRevocationController`, `RevokeTokenService` |
| User Consent | [docs/features/08-consent.md](docs/features/08-consent.md) | `consent.OAuth2AuthorizationConsentController`, `AuthorizationConsentService` |
| Client Management & Bootstrap | [docs/features/09-client-management.md](docs/features/09-client-management.md) | `clients.ClientBootstrapService`, `ClientScopeService` |
| Session & Logout | [docs/features/10-session-logout.md](docs/features/10-session-logout.md) | `shared.LoggedOutController`, SAS `/connect/logout` |

---

## Stage 5 — Persistence, Migrations & Configuration (≈ half a day)

1. Read [docs/features/11-persistence-schema.md](docs/features/11-persistence-schema.md).
2. Inspect Flyway migrations in [`src/main/resources/db/migration/`](src/main/resources/db/migration/) (`V0_0_1_001` through `007`). Notice how `007` introduces the `tenant_id` column and composite indexes across all tables.
3. Review the complete property reference in [docs/features/12-configuration.md](docs/features/12-configuration.md).

---

## Stage 6 — Product Roadmap & Future Contributions

Review the strategic roadmap to understand future development directions:
- Executive Summary: [docs/features-and-roadmap.md](docs/features-and-roadmap.md)
- Roadmap Overview & Phasing: [docs/roadmap/README.md](docs/roadmap/README.md)
- Phased themes:
  - Phase 1: [Authentication & MFA](docs/roadmap/01-authentication.md), [Security Hardening](docs/roadmap/07-security-hardening.md), [Client Management](docs/roadmap/05-client-management.md)
  - Phase 2: [User Lifecycle](docs/roadmap/02-user-lifecycle.md), [Identity Federation](docs/roadmap/03-federation.md)
  - Phase 3: [Advanced Protocols & Passwordless](docs/roadmap/06-protocols.md)
  - Phase 4: [Fine-Grained Authorization](docs/roadmap/04-authorization.md)
  - Phase 5: [Operability & High Availability](docs/roadmap/08-operability.md)
  - Phase 6: [Compliance & Privacy](docs/roadmap/09-compliance.md), [Developer Experience](docs/roadmap/10-developer-experience.md)

---

## Quick Reference

| Resource | Path |
|---|---|
| Documentation Portal | [docs/README.md](docs/README.md) |
| Architecture & System Design | [docs/architecture/00-architecture.md](docs/architecture/00-architecture.md) |
| How-To Operational Guides | [docs/how-to/](docs/how-to/) |
| Features & Phased Roadmap | [docs/features-and-roadmap.md](docs/features-and-roadmap.md) |
| Feature Specifications | [docs/features/](docs/features/) |
| Roadmap Backlog | [docs/roadmap/](docs/roadmap/) |
| Developer Instructions | [CLAUDE.md](CLAUDE.md) |
| Application Entry Point | [`src/main/java/io/github/edmaputra/edidp/Application.java`](src/main/java/io/github/edmaputra/edidp/Application.java) |
