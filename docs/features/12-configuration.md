# Feature 12 — Configuration Reference

All runtime configuration lives in `src/main/resources/application.properties` and is overridable via environment/profile properties.

## Server & issuer

| Property | Default | Description |
|---|---|---|
| `spring.application.name` | `enhauthserv` | Application name |
| `server.port` | `9000` | HTTP port |
| `app.issuer-uri` | `http://localhost:9000` | Base OAuth2/OIDC issuer; tenants get `{issuer}/t/{tenant}` |

## Token policy (`app.token.*`)

| Property | Default | Description |
|---|---|---|
| `access-token-time-to-live` | `5m` | Access token TTL |
| `refresh-token-time-to-live` | `7d` | Refresh token TTL |
| `reuse-refresh-tokens` | `false` | Rotate refresh tokens when `false` |
| `client-credentials-allowed-scopes` | `read,write,introspection,revocation` | Allowed `client_credentials` scopes |

See [Token Policy](04-token-policy.md).

## Tenant resolution (`tenant.resolution.*`)

Tenant can be resolved from the request path (`/t/{tenant}/`) or from a trusted `X-Tenant-ID` header when header resolution is enabled.

| Property | Default | Description |
|---|---|---|
| `header-enabled` | `true` | Allow tenant resolution from the header |
| `path-enabled` | `true` | Resolve tenant from `/t/{tenant}/` |
| `require-explicit-tenant` | `false` | Reject requests without a resolved tenant |
| `enforce-trusted-proxy-for-header` | `false` | Only honor header resolution from trusted sources |
| `header-name` | `X-Tenant-ID` | Request header name |
| `header-trusted-sources` | `127.0.0.1,::1,0:0:0:0:0:0:0:1` | Allowed remote addresses for header resolution when trusted-proxy enforcement is on |

See [Multi-Tenancy](03-multi-tenancy.md).

## Datasource & migrations

| Property | Default | Description |
|---|---|---|
| `spring.datasource.url` | `jdbc:h2:mem:authdb` | JDBC URL |
| `spring.datasource.driver-class-name` | `org.h2.Driver` | Driver |
| `spring.datasource.username` | `sa` | DB user |
| `spring.datasource.password` | *(empty)* | DB password |
| `spring.flyway.enabled` | `true` | Run migrations on startup |
| `spring.flyway.locations` | `classpath:db/migration` | Migration scripts |
| `spring.h2.console.enabled` | `true` | Enable H2 web console |
| `spring.h2.console.path` | `/h2-console` | H2 console path |

## Implementation

| Property group | Bound / consumed by |
|---|---|
| `app.token.*` | [`tokens/TokenPolicyProperties`](../../src/main/java/io/github/edmaputra/enhauthserv/tokens/TokenPolicyProperties.java) → `oauth/SecurityConfig.tokenSettings(...)` + `jwtTokenCustomizer(...)` |
| `app.issuer-uri` | `oauth/SecurityConfig.authorizationServerSettings(...)`, `oauth/metadata/TenantOidcMetadataController` |
| `tenant.resolution.*` | [`tenancy/TenantContextFilter`](../../src/main/java/io/github/edmaputra/enhauthserv/tenancy/TenantContextFilter.java) constructor → `TenantResolutionPolicy` |
| `spring.datasource.*`, `spring.flyway.*` | Spring Boot autoconfiguration (JDBC stores + Flyway migrations) |

Notes from the code:

- `TokenPolicyProperties` is a `@ConfigurationProperties("app.token")` bean activated by `@EnableConfigurationProperties(TokenPolicyProperties.class)` on `oauth/SecurityConfig`.
- `tenant.resolution.*` values are injected as constructor `@Value`s on `TenantContextFilter` and passed into a `TenantResolutionPolicy`.
