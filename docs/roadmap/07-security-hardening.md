# Roadmap — Security Hardening

An IdP is a high-value target. Beyond correct protocol implementation, it needs operational security controls.

## Proposed capabilities

### Signing key management
- **Automated key rotation** with overlap (publish next key in JWKS before cutover).
- Key storage in a **KMS/HSM** (AWS KMS, Vault, PKCS#11) instead of in-process keys.
- Per-tenant key isolation; configurable algorithms (RS256/ES256/EdDSA).

### Rate limiting & abuse protection
- Per-IP / per-client / per-tenant rate limits on token, authorize, introspect endpoints.
- Brute-force lockout and CAPTCHA (cross-ref [Authentication](01-authentication.md)).
- Bot detection on registration and login.

### Threat detection & response
- Anomaly detection (impossible travel, credential stuffing patterns).
- Breached-password detection (HaveIBeenPwned k-anonymity).
- Automated responses: forced re-auth, session revocation, lockout.

### Token & session security
- Sender-constrained tokens (DPoP, mTLS).
- Refresh-token reuse detection → revoke token family (extends current rotation).
- Configurable session idle/absolute timeouts; global "sign out everywhere".

### Input & transport hardening
- Strict redirect-URI matching, PKCE-only enforcement option for public clients.
- Security headers (HSTS, CSP), CSRF on interactive endpoints.
- Secrets management (no plaintext secrets in config); encrypted attribute storage at rest.

### Secure defaults & audits
- Periodic dependency/CVE scanning; secure-by-default configuration profiles.
- Pen-test / threat-model documentation.

## Fit
A rate-limiting filter ahead of the security chains; a `KeyManagementPort` abstracting JWKS sources; a `ThreatSignalPort` feeding the [adaptive auth](01-authentication.md) decision.

## Why it matters
A compromised IdP compromises every downstream application. Key rotation, rate limiting, and reuse detection are baseline production requirements.
