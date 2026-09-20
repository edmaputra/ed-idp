# Roadmap — Compliance, Privacy & Governance

An IdP is a custodian of personal data and must support regulatory and governance obligations.

## Proposed capabilities

### Consent & privacy ledger
- Immutable record of every consent grant/withdrawal (who, what scopes, when, version of terms).
- Per-tenant privacy policy / terms versioning; re-consent on policy change.
- User-facing "manage my data & consents" surface (cross-ref [User Lifecycle](02-user-lifecycle.md)).

### GDPR / privacy rights
- **Right to access** — export all data held about a subject.
- **Right to erasure** — delete/anonymize a subject and cascade across tenants.
- **Data portability** — machine-readable export.
- **Data minimization** — only collect/emit claims that are needed (the [claim inclusion rules](../features/05-dynamic-claims.md) are a foundation).
- Configurable data-residency per tenant.

### Regulatory frameworks
- **SOC 2 / ISO 27001** — audit logging + access controls as evidence.
- **HIPAA** — PHI handling controls where identity carries health context.
- **FAPI / Open Banking** — high-assurance profiles (cross-ref [Protocols](06-protocols.md)).
- **eIDAS / NIST 800-63** — assurance-level (AAL/IAL) classification and `acr` mapping.

### Data retention & lifecycle
- Configurable retention for tokens, audit logs, inactive accounts.
- Automated purge of expired authorizations/consents.
- PII encryption at rest and field-level redaction in logs.

### Governance
- Admin approval workflows for sensitive changes (new client, scope grants).
- Segregation of duties / least-privilege admin roles.
- Tamper-evident audit export for compliance attestation.

## Fit
A `ConsentLedgerService` (extending the `consent` slice) and a `DataSubjectRequestService`; retention jobs; encryption at the persistence layer. Tenant isolation already in place supports data-residency boundaries.

## Why it matters
Handling real user identities triggers legal obligations. Consent ledgers, erasure, and audit evidence are prerequisites for operating in regulated markets.
