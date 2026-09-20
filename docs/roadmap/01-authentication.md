# Roadmap — Authentication & Credentials

Today the IdP authenticates users with username/password form login (`demo-user`). A complete IdP must support multiple, composable authentication factors and modern passwordless methods.

## Proposed capabilities

### Multi-Factor Authentication (MFA)
- **TOTP** (RFC 6238) — authenticator apps; enrollment + verification step in the login flow.
- **WebAuthn / FIDO2 passkeys** — phishing-resistant hardware/platform authenticators.
- **Email / SMS OTP** — one-time codes as a second factor or recovery method.
- **Push notifications** — approve/deny prompts via a mobile app.
- **Recovery / backup codes**.

*Fit:* a new `authentication` (or `mfa`) slice with an `MfaChallengeService`; an additional Spring Security filter/`AuthenticationProvider` between credential check and session establishment.

### Passwordless authentication
- Magic links (email).
- Passkey-only login.
- OTP-only login.

### Adaptive / risk-based authentication
- Signals: device fingerprint, IP reputation/geo-velocity, new-device detection.
- Outcome: allow / step-up / deny. Step-up triggers MFA only when risk is elevated.

*Fit:* a `RiskEvaluationService` in the authentication slice consulted by the login flow; pluggable scorer components.

### Step-up authentication
- Require a stronger factor for sensitive scopes/operations (`acr`/`amr` claims, OIDC `acr_values`).

### Credential policy & hygiene
- Configurable password policy (length, complexity, breach-list check via k-anonymity).
- Password rotation / expiry, account lockout & throttling on failed attempts.
- Argon2id hashing option alongside BCrypt.

### Brute-force & bot protection
- Per-account and per-IP failed-attempt throttling, CAPTCHA on suspicion.

## Why it matters
MFA and passwordless are table stakes for any IdP handling real users; adaptive auth and lockout directly reduce account-takeover risk.
