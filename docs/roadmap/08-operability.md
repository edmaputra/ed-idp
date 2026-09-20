# Roadmap — Operability & Platform

For production operation, the IdP needs observability, audit, and the ability to scale and run highly available.

## Proposed capabilities

### Audit logging
- Immutable, structured audit trail of security events: logins, token issuance, consent, revocation, admin actions, config changes.
- Per-tenant audit isolation; export to SIEM.

### Observability
- Metrics (Micrometer/Prometheus): token issuance rates, latency, error rates, active sessions.
- Distributed tracing (OpenTelemetry) across the request lifecycle.
- Structured application logging with correlation/tenant IDs.
- Health/readiness probes (Spring Actuator) and dashboards.

### Scalability & high availability
- Stateless horizontal scaling; externalized session store (Redis) instead of in-memory.
- Production datasource (PostgreSQL) with connection pooling and read replicas.
- Caching layer for client/JWKS/profile lookups.
- Distributed token/consent stores already JDBC-backed — validate under clustering.

### Resilience
- Graceful degradation, circuit breakers around external IdPs/notification providers.
- Backpressure and timeouts on upstream federation calls.

### Deployment & lifecycle
- Container images + Helm/Kubernetes manifests.
- Zero-downtime deploys; blue/green or rolling with key-rotation safety.
- Backup/restore and disaster-recovery runbooks for the identity datastore.

### Configuration management
- Per-tenant runtime configuration (currently global properties) stored and editable.
- Feature flags for progressive rollout of new auth methods/flows.

## Fit
Add Actuator + Micrometer + OTel; introduce an `AuditEventPort` emitted from use cases; move tenant config from static properties into a tenant-config entity loaded per request.

## Why it matters
Without audit and observability, security incidents are undetectable and unprovable. Without HA, the IdP is a single point of failure for every dependent application.
