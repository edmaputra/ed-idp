# How-To: Database Setup & Production Hardening

This guide covers preparing, deploying, and hardening **EnhAuthServ (`ed-auth`)** for production environments.

---

## 1. Database Migration: Switching to PostgreSQL / MySQL

While the default runtime uses an in-memory H2 database, production requires a durable relational database.

### Option A: PostgreSQL Setup (Recommended)

1. Add the PostgreSQL driver dependency if running in custom packaging, or supply it to the runtime classpath.
2. Configure `application.properties` (or environment variables):
   ```properties
   spring.datasource.url=jdbc:postgresql://postgres-host:5432/enhauth_db
   spring.datasource.username=enhauth_user
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
spring.datasource.url=jdbc:mysql://mysql-host:3306/enhauth_db?preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
spring.datasource.username=enhauth_user
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

### 1. Canonical Issuer URI
Ensure the public HTTPS URL is configured as the base issuer:
```properties
app.issuer-uri=https://auth.example.com
```

### 2. Forwarded Headers
Ensure Spring Security honors TLS termination from the reverse proxy:
```properties
server.forward-headers-strategy=framework
```

### 3. Securing Header-Based Multi-Tenancy
If utilizing `X-Tenant-ID` for internal routing, prevent external clients from spoofing tenant identities:
1. **At the Gateway**: Strip any `X-Tenant-ID` header arriving from untrusted public clients.
2. **In Application Config**: Enforce trusted reverse proxy sources:
   ```properties
   tenant.resolution.enforce-trusted-proxy-for-header=true
   tenant.resolution.header-trusted-sources=10.0.0.1,10.0.0.2,127.0.0.1
   ```

### 4. Enforcing Strict Tenancy
Reject any request that fails to supply a valid tenant identifier:
```properties
tenant.resolution.require-explicit-tenant=true
```

---

## 4. Health Checks & Kubernetes Probes

EnhAuthServ includes `spring-boot-starter-actuator` for production observability.

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
# logging.level.io.github.edmaputra.enhauthserv=DEBUG
```

---

## Related Guides
- [Tenant Onboarding](01-tenant-onboarding.md)
- [Resource Server Token Validation](05-resource-server-token-validation.md)
- [Persistence Schema Specification](../features/11-persistence-schema.md)
- [Configuration Reference](../features/12-configuration.md)
