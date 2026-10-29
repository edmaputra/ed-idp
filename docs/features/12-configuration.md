# Feature 12 — Configuration Reference

All runtime configuration lives in `src/main/resources/application.properties` and is fully overridable via standard Spring Boot environment variables (`${VAR:default}`).

## Server, Ingress & Endpoints

| Property | Environment Variable | Default | Description |
|---|---|---|---|
| `spring.application.name` | — | `ed-idp` / `ed-idp-account-portal` | Application name |
| `server.port` | `PORT` | `9000` (core) / `9001` (portal) | HTTP server port |
| `server.forward-headers-strategy` | `FORWARD_HEADERS_STRATEGY` | `framework` | Process `X-Forwarded-*` headers behind TLS reverse proxy |
| `app.issuer-uri` | `APP_ISSUER_URI` | `http://localhost:9000` | Base OAuth2/OIDC issuer; tenants get `{issuer}/t/{tenant}` |
| `app.client.account-portal.base-url` | `ACCOUNT_PORTAL_BASE_URL` | `http://localhost:9001` | Base URL of Account Portal for dynamic client redirect URI provisioning |
| `edidp.auth-server-url` | `EDIDP_AUTH_SERVER_URL` | `http://localhost:9000` | Authorization Server URL configured in Account Portal |
| `edidp.seeding.enabled` | `EDIDP_SEEDING_ENABLED` | `true` | Bootstrap demo clients and default seed users on startup |

## Token Policy (`app.token.*`)

| Property | Environment Variable | Default | Description |
|---|---|---|---|
| `app.token.access-token-time-to-live` | `APP_TOKEN_ACCESS_TOKEN_TTL` | `5m` | Access token TTL |
| `app.token.refresh-token-time-to-live` | `APP_TOKEN_REFRESH_TOKEN_TTL` | `7d` | Refresh token TTL |
| `app.token.reuse-refresh-tokens` | `APP_TOKEN_REUSE_REFRESH_TOKENS` | `false` | Rotate refresh tokens when `false` |
| `app.token.client-credentials-allowed-scopes` | `APP_TOKEN_CLIENT_CREDENTIALS_ALLOWED_SCOPES` | `read,write,introspection,revocation` | Allowed `client_credentials` scopes |

See [Token Policy](04-token-policy.md).

## Tenant Resolution (`tenant.resolution.*`)

Tenant can be resolved from the request path (`/t/{tenant}/`) or from a trusted `X-Tenant-ID` header when header resolution is enabled.

| Property | Environment Variable | Default | Description |
|---|---|---|---|
| `tenant.resolution.header-enabled` | `TENANT_RESOLUTION_HEADER_ENABLED` | `true` | Allow tenant resolution from header |
| `tenant.resolution.path-enabled` | `TENANT_RESOLUTION_PATH_ENABLED` | `true` | Resolve tenant from `/t/{tenant}/` |
| `tenant.resolution.require-explicit-tenant` | `TENANT_RESOLUTION_REQUIRE_EXPLICIT_TENANT` | `false` | Reject requests without a resolved tenant |
| `tenant.resolution.enforce-trusted-proxy-for-header` | `TENANT_RESOLUTION_ENFORCE_TRUSTED_PROXY` | `false` | Only honor header resolution from trusted sources |
| `tenant.resolution.header-name` | `TENANT_RESOLUTION_HEADER_NAME` | `X-Tenant-ID` | Request header name |
| `tenant.resolution.header-trusted-sources` | `TENANT_RESOLUTION_HEADER_TRUSTED_SOURCES` | `127.0.0.1,::1,0:0:0:0:0:0:0:1` | Allowed remote addresses for header resolution when trusted-proxy enforcement is on |

See [Multi-Tenancy](03-multi-tenancy.md).

## Datasource & Migrations

| Property | Environment Variable | Default | Description |
|---|---|---|---|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:authdb...` | JDBC URL |
| `spring.datasource.driver-class-name` | `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | `org.h2.Driver` | Driver class name |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `sa` | Database user |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | *(empty)* | Database password |
| `spring.liquibase.enabled` | `SPRING_LIQUIBASE_ENABLED` | `true` | Run Liquibase migrations on startup |
| `spring.h2.console.enabled` | `SPRING_H2_CONSOLE_ENABLED` | `true` | Enable H2 web console (disable in production) |
| `iam.jwt.secret` | `IAM_JWT_SECRET` | *(internal secret)* | Encryption key for credentials at rest |

## Implementation

| Property Group | Bound / Consumed By |
|---|---|
| `app.token.*` | `tokens/TokenPolicyProperties` → `oauth/SecurityConfig.tokenSettings(...)` + `jwtTokenCustomizer(...)` |
| `app.issuer-uri` | `oauth/SecurityConfig.authorizationServerSettings(...)`, `oauth/metadata/TenantOidcMetadataController`, `clients/ClientBootstrapService` |
| `app.client.account-portal.base-url` | `clients/ClientBootstrapService` |
| `tenant.resolution.*` | `tenancy/TenantContextFilter` constructor → `TenantResolutionPolicy` |
| `spring.datasource.*`, `spring.liquibase.*` | Spring Boot autoconfiguration & Liquibase |
| `edidp.auth-server-url` | `ed-idp-account-portal/application.properties` client registrations & providers |
