# Roadmap — Developer & Admin Experience

The platform's value is multiplied by how easily developers integrate and admins operate it.

## Proposed capabilities

### Admin console (UI)
- Tenant-scoped web console: manage users, clients, roles, policies, claim rules, branding.
- Dashboards for sessions, token activity, and audit events (cross-ref [Operability](08-operability.md)).

### Self-service developer portal
- Register applications, view/rotate credentials, configure redirect URIs and scopes.
- Interactive "try a flow" sandbox; sample integrations.

### Branding & customization
- Per-tenant theming of login/consent pages (logo, colors, copy).
- Customizable, localized email/SMS templates.
- Internationalization (i18n) of all user-facing surfaces.

### APIs & SDKs
- Stable, versioned management REST API (OpenAPI spec).
- Client SDKs / quickstarts for common stacks (Java, JS/TS, Python, Go, mobile).
- Webhooks/event streaming for identity events (user.created, login.succeeded, token.revoked).

### Documentation & onboarding
- Auto-generated API reference; integration guides per flow and per language.
- Migration guides (from/to other IdPs).
- Local dev mode (current H2 setup) plus seeded sandbox tenants.

### Templating of consent/login
- Move the current static consent/login pages to a themeable template system with per-tenant overrides.

## Fit
Add a management-API slice with controllers backed by existing slice services; a notification-template component and per-tenant theme config; an event-publishing component for webhooks.

## Why it matters
Adoption depends on integration speed. Good admin tooling and developer ergonomics reduce support load and make the IdP self-serve for tenants.
