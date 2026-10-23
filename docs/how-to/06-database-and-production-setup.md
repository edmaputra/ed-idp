# How-To: Database Setup & Production Hardening

This guide covers preparing, deploying, and hardening **ed-idp** for production environments.

---

## 1. Database Migration: Switching to PostgreSQL / MySQL

While the default runtime uses an in-memory H2 database, production requires a durable relational database.

### Option A: PostgreSQL Setup (Recommended)

1. Add the PostgreSQL driver dependency if running in custom packaging, or supply it to the runtime classpath.
2. Configure `application.properties` (or environment variables):
   ```properties
   spring.datasource.url=jdbc:postgresql://postgres-host:5432/ed_idp_db
   spring.datasource.username=ed_idp_user
   spring.datasource.password=${DB_PASSWORD}
   spring.datasource.driver-class-name=org.postgresql.Driver

   # Flyway executes migrations automatically on startup
   spring.flyway.enabled=true
   spring.flyway.locations=classpath:db/migration

   # Disable H2 console
   spring.h2.console.enabled=false
   ```

### Option B: MySQL Setup

```properties
spring.datasource.url=jdbc:mysql://mysql-host:3306/ed_idp_db?preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
spring.datasource.username=ed_idp_user
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.h2.console.enabled=false
```

### Automatic Flyway Schema Provisioning
On application boot, Flyway automatically runs:
- `V0_0_1_001`: OAuth2 authorization server tables
- `V0_0_1_002`: Spring Security user and authority tables
- `V0_0_1_003` - `006`: User profile, attributes, and dynamic claim rules
- `V0_0_1_007`: Multi-tenant discriminator columns (`tenant_id`) and composite indexes

---

## 2. Production Token Policies & Lifetimes

Customize token durations and refresh rotation policies in `application.properties` according to your organization's security baseline:

```properties
# Recommended Production Defaults
app.token.access-token-time-to-live=15m
app.token.refresh-token-time-to-live=30d
app.token.reuse-refresh-tokens=false

# Restrict machine client scopes to prevent privilege escalation
app.token.client-credentials-allowed-scopes=read,write,introspection,revocation
```

---

## 3. Reverse Proxy & Ingress Trust Boundaries

In production, the Authorization Server typically sits behind an Ingress Controller, API Gateway, or Reverse Proxy (NGINX, Envoy, Traefik, AWS ALB) that terminates TLS.

### 1. Canonical Issuer URI & Portal Endpoints
Ensure the public HTTPS URLs are configured as the base issuer and client endpoints (via environment variables or properties):
```properties
app.issuer-uri=https://auth.example.com
app.client.account-portal.base-url=https://account.example.com
```

In the Account Portal client application (`ed-idp-account-portal`):
```properties
edidp.auth-server-url=https://auth.example.com
```

### 2. Forwarded Headers
Ensure Spring Security honors TLS termination and host forwarding from the reverse proxy (e.g. Nginx, AWS ALB, Traefik, Cloudflare):
```properties
server.forward-headers-strategy=framework
```
When `server.forward-headers-strategy=framework` is enabled, Spring Boot registers standard `ForwardedHeaderFilter` processing `X-Forwarded-Proto`, `X-Forwarded-Host`, `X-Forwarded-Port`, and `X-Forwarded-Prefix`. This ensures OIDC discovery documents, redirect URLs, and session cookies automatically adapt to the HTTPS domain.

### 3. Automatic Client Redirect URI Provisioning
On startup, `ClientBootstrapService` registers default client applications (`demo-client`, `pkce-public-client`, `ed-idp-account-portal`). When `APP_ISSUER_URI` or `ACCOUNT_PORTAL_BASE_URL` are set, the authorization server automatically registers redirect and post-logout URIs for those production URLs, while continuing to preserve local development fallbacks:
- **`ed-idp-account-portal` redirect URI**: `${ACCOUNT_PORTAL_BASE_URL}/login/oauth2/code/ed-idp-account-portal`
- **`ed-idp-account-portal` post-logout URI**: `${ACCOUNT_PORTAL_BASE_URL}/logged-out`
- **`demo-client` redirect URI**: `${APP_ISSUER_URI}/login/oauth2/code/demo-client`
- **`pkce-public-client` redirect URI**: `${APP_ISSUER_URI}/login/oauth2/code/pkce-public-client`

### 4. Securing Header-Based Multi-Tenancy
If utilizing `X-Tenant-ID` for internal routing, prevent external clients from spoofing tenant identities:
1. **At the Gateway**: Strip any `X-Tenant-ID` header arriving from untrusted public clients.
2. **In Application Config**: Enforce trusted reverse proxy sources:
   ```properties
   tenant.resolution.enforce-trusted-proxy-for-header=true
   tenant.resolution.header-trusted-sources=10.0.0.1,10.0.0.2,127.0.0.1
   ```

### 5. Enforcing Strict Tenancy
Reject any request that fails to supply a valid tenant identifier:
```properties
tenant.resolution.require-explicit-tenant=true
```

---

## 4. Production Environment Variables Reference

Both `ed-idp` and `ed-idp-account-portal` support containerized, zero-configuration override via environment variables:

| Environment Variable | Target Property | Default | Purpose |
|---|---|---|---|
| `PORT` | `server.port` | `9000` (core) / `9001` (portal) | Container HTTP listener port |
| `FORWARD_HEADERS_STRATEGY` | `server.forward-headers-strategy` | `framework` | Enable `X-Forwarded-*` processing behind ingress |
| `APP_ISSUER_URI` | `app.issuer-uri` | `http://localhost:9000` | Canonical authorization server public HTTPS domain |
| `ACCOUNT_PORTAL_BASE_URL` | `app.client.account-portal.base-url` | `http://localhost:9001` | Canonical account portal public HTTPS domain |
| `EDIDP_AUTH_SERVER_URL` | `edidp.auth-server-url` | `http://localhost:9000` | Authorization server location used by Account Portal |
| `EDIDP_CLIENT_ID` | `spring.security.oauth2.client.registration.edidp.client-id` | `ed-idp-account-portal` | OAuth 2.1 client ID for portal |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:h2:mem:authdb...` | Production PostgreSQL/MySQL JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `sa` | Production database user |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | `""` | Production database password |
| `IAM_JWT_SECRET` | `iam.jwt.secret` | *(internal dev secret)* | Base64 HMAC secret for credentials encryption at rest |
| `EDIDP_SEEDING_ENABLED` | `edidp.seeding.enabled` | `true` | Seed default clients and demo users on startup |

---

## 4. Health Checks & Kubernetes Probes

ed-idp includes `spring-boot-starter-actuator` for production observability.

### Configure Probes in `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.probes.enabled=true
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
```

### Kubernetes Deployment Configuration:
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 9000
  initialDelaySeconds: 20
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 9000
  initialDelaySeconds: 10
  periodSeconds: 5
```

---

## 5. Production Logging Guidelines

In production, avoid logging sensitive tokens, authorization codes, or user credentials:

```properties
# Keep security logging at INFO in production
logging.level.org.springframework.security=INFO
logging.level.org.springframework.security.oauth2=INFO

# Enable DEBUG only temporarily when troubleshooting specific flow failures
# logging.level.io.github.edmaputra.edidp=DEBUG
```

---

## Related Guides
- [Tenant Onboarding](01-tenant-onboarding.md)
- [Resource Server Token Validation](05-resource-server-token-validation.md)
- [Persistence Schema Specification](../features/11-persistence-schema.md)
- [Configuration Reference](../features/12-configuration.md)
