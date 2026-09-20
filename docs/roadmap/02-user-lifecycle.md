# Roadmap — User Lifecycle & Self-Service

Users are currently seeded in code. A complete IdP owns the full user lifecycle and exposes self-service so users and admins are not dependent on database edits.

## Proposed capabilities

### Registration & onboarding
- Self-service sign-up with email/phone verification.
- Invitation-based onboarding (admin invites → user completes profile).
- Progressive profiling (collect attributes over time).

### Profile self-service
- View/update profile and `UserProfileAttribute`s.
- Manage consents and connected applications.
- Manage enrolled MFA devices and sessions.

### Credential recovery
- Forgot-password (email/SMS reset link or OTP).
- Account recovery via backup codes / recovery contact.

### Account lifecycle states
- States: pending → active → suspended → deactivated → deleted.
- Admin actions: lock/unlock, force logout, force password reset, require re-consent.

### Provisioning & deprovisioning
- **SCIM 2.0** (RFC 7643/7644) server for automated user/group provisioning from HR/IdP systems.
- Bulk import/export.
- Just-in-time (JIT) provisioning on first federated login (see [Federation](03-federation.md)).

### Groups & organizations
- Group membership, org/team hierarchy, per-tenant directory management.

### Email/notification service
- Transactional email/SMS abstraction (verification, reset, security alerts).

## Fit
Extend the `users` slice with new entities/repositories for lifecycle state and verification tokens, a `UserManagementService`, and admin controllers; add a notification component with pluggable providers (SMTP, SES, Twilio). All remain tenant-scoped via `tenancy/TenantContext`.

## Why it matters
Self-service registration, recovery, and SCIM provisioning are what turn a token server into a usable identity platform and eliminate manual user administration.
