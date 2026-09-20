# Roadmap — Federation & Identity Brokering

A complete IdP acts as a broker: users authenticate against external providers, and the IdP normalizes those identities into local subjects.

## Proposed capabilities

### Social login
- Google, Apple, Microsoft, GitHub, Facebook, LinkedIn, etc. (OAuth2/OIDC upstreams).

### Enterprise federation
- **OIDC upstream** — delegate to another OIDC provider per tenant.
- **SAML 2.0** — act as SAML SP (consume enterprise IdP assertions) and/or SAML IdP (issue assertions to SAML SPs).
- **LDAP / Active Directory** — bind/search authentication against corporate directories.
- **Kerberos / SPNEGO** for intranet SSO.

### Identity brokering & home-realm discovery
- Per-tenant configurable set of upstream IdPs.
- Home-realm discovery (route by email domain / tenant) to the right provider.

### Account linking
- Link multiple external identities to one local subject.
- Linking on login when emails match (with verification) vs. explicit user-initiated linking.

### Attribute mapping & JIT provisioning
- Map upstream claims/assertions → local `UserProfile` + `UserProfileAttribute`.
- Just-in-time user creation on first federated login.

## Fit
A new `federation` slice with a `FederationService` and an identity-provider connector abstraction (one implementation per protocol); per-tenant IdP configuration persisted as new entities; reuse the existing `claims` slice for attribute mapping.

## Why it matters
Enterprises require "log in with our existing IdP." Brokering + JIT provisioning is the difference between a standalone IdP and one that integrates into existing identity ecosystems.
