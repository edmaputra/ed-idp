---
description: "Guidance for OAuth2/OIDC controller and consent-flow edits in ed-idp"
applyTo: "src/main/java/io/github/edmaputra/edidp/controller/**,src/main/java/io/github/edmaputra/edidp/config/**,src/test/java/io/github/edmaputra/edidp/**"
---

Keep controller code thin and delegate business rules to application use cases through ports and wiring in [UseCaseWiringConfig.java](src/main/java/io/github/edmaputra/edidp/config/UseCaseWiringConfig.java).

For OAuth2 and OIDC endpoint changes, follow the behaviors documented in [README.md](README.md) and keep redirects, scopes, and tenant handling consistent with the existing auth-server flows.

Do not introduce direct dependencies from controllers into repositories, entities, or domain internals. If a controller needs data or decisions, add or reuse an application port instead.

For tests in these paths, prefer Mockito plus AssertJ for unit coverage and [AuthServerIntegrationTests.java](src/test/java/io/github/edmaputra/edidp/integration/AuthServerIntegrationTests.java) for browser-like or HTTP flow coverage.

When adjusting auth flows, run a focused Maven test command for the touched class or slice before widening validation.