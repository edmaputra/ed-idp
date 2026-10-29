# Feature 15: Structured Security Audit Logging

Structured security audit logging provides an immutable, SIEM-ready audit trail of all security-critical operations across `ed-idp`.

---

## 1. Overview & Capabilities

The audit logging module (`io.github.edmaputra.edidp.audit`) emits structured JSON events capturing:
- **Authentication Lifecycle**: Primary form login success (`LOGIN_SUCCESS`) and failure (`LOGIN_FAILURE`).
- **Multi-Factor Authentication (MFA)**: Challenge prompts (`MFA_CHALLENGE`), verification successes (`MFA_VERIFY_SUCCESS`), and failures (`MFA_VERIFY_FAILURE`).
- **Session Termination**: Interactive or RP-initiated logout (`LOGOUT`).
- **OAuth 2.1 Token Issuance**: Successful token issuance (`TOKEN_ISSUED`) and rejected token requests (`TOKEN_FAILED`).
- **Token Maintenance**: RFC 7009 token revocation (`TOKEN_REVOKED`) and RFC 7662 token introspection (`TOKEN_INTROSPECTED`).
- **User Consent**: Scopes approved (`CONSENT_GRANTED`) and rejected/denied (`CONSENT_DENIED`).
- **User Self-Service**: Profile updates (`USER_PROFILE_UPDATED`) and credential alterations (`USER_PASSWORD_CHANGED`).

---

## 2. Event Model & Data Structure

Audit events are modeled as immutable Java records (`SecurityAuditEvent`):

```json
{
  "eventId": "bdb0367f-f571-4322-8563-c797779511e5",
  "timestamp": "2026-10-30T06:31:00.042073Z",
  "tenantId": "demo",
  "action": "TOKEN_ISSUED",
  "status": "SUCCESS",
  "principal": "demo-client",
  "clientIdentifier": "demo-client",
  "ipAddress": "127.0.0.1",
  "userAgent": "Java-http-client/25.0.4",
  "details": {
    "grant_type": "client_credentials",
    "scopes": ["read"],
    "token_type": "Bearer"
  }
}
```

### Sensitive Data Sanitization
Security audit details automatically redact passwords, client secrets, and raw token values (`[REDACTED]`), preventing secret leakage in log aggregators.

---

## 3. Architecture & Spring Modulith Integration

The audit module follows hexagonal architecture principles:

- **`AuditEventPort`**: Port interface enabling use cases to emit audit events.
- **`StructuredLoggingAuditEventAdapter`**: Adapter writing SIEM-ready JSON logs via SLF4J, enriching MDC context (`audit.event_id`, `audit.tenant_id`, `audit.action`, `audit.status`, `audit.principal`, `audit.ip_address`), saving to the `AuditEventRepository`, and publishing Spring application events.
- **`AuditEventRepository` / `InMemoryAuditEventRepository`**: Thread-safe bounded ledger (default capacity: 1000 items) supporting multi-tenant queries (`findByTenant`) and recent event inspection.
- **`SecurityAuditEventListener`**: Automatically translates Spring Security `AuthenticationSuccessEvent`, `AbstractAuthenticationFailureEvent`, and `LogoutSuccessEvent` into structured audit records.

Dependencies flow strictly according to Spring Modulith rules:
- `audit` depends only on `tenancy`.
- `oauth`, `tokens`, `consent`, and `users` modules declare explicit unidirectional dependencies on `audit`.
