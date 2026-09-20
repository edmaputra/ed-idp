# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./mvnw clean package          # Build
./mvnw spring-boot:run        # Run (port 9000)
./mvnw test                   # All tests
./mvnw -Dtest=ClassName test  # Single test class
```

H2 console available at `http://localhost:9000/h2-console` (JDBC URL: `jdbc:h2:mem:authdb`).

## Architecture

**Vertical-Slice Modular Monolith** enforced by Spring Modulith package annotations (`@ApplicationModule` in `package-info.java`):

| Module | Package | Responsibility | Allowed Dependencies |
|---|---|---|---|
| `tenancy` | `tenancy/` | Tenant resolution, `TenantContext`, dynamic issuer | None |
| `users` | `users/` | User profiles, dynamic attributes, JPA repositories | None |
| `shared` | `shared/` | Cross-cutting endpoints (`/logged-out`) | None |
| `oauth` | `oauth/` | Spring Security chains, OIDC metadata, JWKs, tenant JDBC repos | `tenancy`, `shared` |
| `clients` | `clients/` | Client bootstrap, auth, scope checks | `tenancy`, `oauth` |
| `authorization` | `authorization/` | Scope validation & authorization policies | `clients` |
| `consent` | `consent/` | User consent checks & approval flow | `tenancy`, `oauth` |
| `claims` | `claims/` | Dynamic claim assembly & inclusion rules | `users`, `tenancy` |
| `tokens` | `tokens/` | Token policy, introspection (RFC 7662), revocation (RFC 7009) | `authorization`, `clients`, `tenancy`, `oauth` |

## Key Domain Concepts

- **Multi-tenancy**: Tenant resolved per-request via `TenantContextFilter` (highest precedence filter) → `TenantContext` (thread-local). Resolution order: HTTP header (`X-Tenant-ID`) → path prefix (`/t/{tenant}/`). Controlled by `tenant.resolution.*` properties. Dynamic issuer resolved by `TenantIssuerService`.
- **Token policy**: Configurable via `app.token.*` (access TTL, refresh TTL, rotation, client credentials allowed scopes). Properties class: `TokenPolicyProperties`.
- **Dynamic claims**: `UserClaimsService` assembles OIDC claims from `UserProfile` + `UserProfileAttribute` + `ClaimInclusionRule`. Claims route to `USERINFO`, `ID_TOKEN`, or `ACCESS_TOKEN` per `ClaimTarget`, guarded against reserved JWT claims.
- **Tenant-aware OAuth2 services**: `TenantAwareOAuth2AuthorizationService`, `TenantAwareOAuth2AuthorizationConsentService`, `TenantAwareRegisteredClientRepository` scope all OAuth2 state per tenant.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /oauth2/token` | Token issuance (all grant types) |
| `POST /oauth2/introspect` | RFC 7662 token introspection |
| `POST /oauth2/revoke` | RFC 7009 token revocation |
| `GET /oauth2/jwks` | Per-tenant JWK Set |
| `GET /.well-known/openid-configuration` | Per-tenant OIDC discovery |
| `GET /userinfo` | OIDC UserInfo |
| `GET /connect/logout` | RP-initiated logout |
| `GET /oauth2/authorize` | Authorization Code / PKCE |

## Testing Patterns

- Integration tests extend `AuthServerIntegrationTests` (provides `TestRestTemplate`, OAuth flow helpers, CSRF extraction).
- Unit tests use Mockito + AssertJ.
- H2 state leaks between Spring contexts — use a unique `spring.datasource.url` in `@TestPropertySource` when overriding properties.
- Unauthenticated `/oauth2/token` falls through to the login page rather than returning `invalid_client`; pass invalid credentials explicitly to force the error response.
- Custom introspection/revocation endpoints require the auth/consent services to be registered explicitly in `SecurityConfig`.

## Built-in Test Credentials

| Type | Client ID | Secret/Method |
|---|---|---|
| Confidential | `demo-client` | `demo-secret` (basic auth) |
| Public (PKCE) | `pkce-public-client` | none |
| User | `demo-user` | `demo-password` |

## Database

Flyway manages schema (`src/main/resources/db/migration/`, 7 versions). Default: H2 in-memory. Switch to a real DB by overriding `spring.datasource.*` properties.
