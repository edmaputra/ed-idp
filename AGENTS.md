# Agent Guide: ed-idp

Central navigation manifest and architectural constraints for AI agents working in `ed-idp`.

---

## 1. Stack & Versions

- **Language**: Java 25
- **Framework**: Spring Boot 4.1.1, Spring Authorization Server, Spring Modulith 2.1.0
- **Testing & Automation**: JUnit 5, AssertJ, Selenide 7.7.3 (Selenium 4), Maven Failsafe Plugin
- **Build Tool**: Maven Wrapper (`./mvnw`)
- **Database / Persistence**: Spring Data JPA, Spring JDBC, Flyway Migrations, H2 (test/dev)
- **Standards**: Inherited from `.agents/rules/java-kotlin/` and `.agents/rules/shared/`

---

## 2. Architecture: Multi-Module & Vertical-Slice Modular Monolith

The codebase is organized as a multi-module Maven reactor:
- `ed-idp-core`: Core OAuth 2.1 & OIDC Authorization Server, token policy, claims, dynamic tenancy, and security filter chains.
- `ed-idp-ui`: Admin Console web application, HTMX fragments, and UI security filter chain.
- `ed-idp-e2e`: Selenide (Selenium 4) end-to-end browser and protocol test automation suite.

Within `ed-idp-core`, capability slices are governed by Spring Modulith (`@ApplicationModule` in each slice's `package-info.java`):

| Module Slice | Package | Responsibility |
|---|---|---|
| `tenancy` | `io.github.edmaputra.edidp.tenancy` | Request tenant resolution, Java 25 `ScopedValue` `TenantContext`, dynamic issuer URL generation. |
| `users` | `io.github.edmaputra.edidp.users` | User profiles, dynamic user attributes, Spring Data JPA repositories. |
| `shared` | `io.github.edmaputra.edidp.shared` | Cross-cutting endpoints (`/logged-out`). |
| `oauth` | `io.github.edmaputra.edidp.oauth` | Multi-tier Spring Security filter chains, OIDC metadata, JWKs endpoints, tenant-aware JDBC repositories. |
| `clients` | `io.github.edmaputra.edidp.clients` | Registered client bootstrap, client authentication service, scope checking. |
| `authorization` | `io.github.edmaputra.edidp.authorization` | Scope validation commands and authorization policy evaluation. |
| `consent` | `io.github.edmaputra.edidp.consent` | Interactive OAuth2 consent review, user scope approval flow, consent store. |
| `claims` | `io.github.edmaputra.edidp.claims` | Dynamic profile claim assembly and target routing (`USERINFO`, `ID_TOKEN`, `ACCESS_TOKEN`). |
| `tokens` | `io.github.edmaputra.edidp.tokens` | RFC 7662 Token Introspection, RFC 7009 Token Revocation, token lifetime and rotation policies. |

Dependencies flow strictly along declared `@ApplicationModule(allowedDependencies = ...)` boundaries. Unidirectional dependency flow is strictly enforced.

---

## 3. Key Commands

- **Build Package**: `./mvnw clean package`
- **Run Unit Tests (Fast, no browser)**: `./mvnw test`
- **Run Single Unit Test**: `./mvnw -Dtest=ClassName test`
- **Run Full Verification (Unit + E2E Browser)**: `./mvnw clean verify`
- **Run E2E Browser Tests Only**: `./mvnw verify -pl ed-idp-e2e`
- **Run Single E2E Test**: `./mvnw test -pl ed-idp-e2e -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false`
- **Inspect Failure Screenshots & Reports**: Look in `ed-idp-e2e/target/selenide-reports/`
- **Generate Coverage Summary**: `python3 .github/scripts/generate-jacoco-summary.py --output target/coverage-summary.md`
- **Run Application**: `./mvnw spring-boot:run` (default port: `9000`)
- **Refresh Structure Map**: `python3 .agents/scripts/scan-structure.py --force`

---

## 4. Agent Guidelines & Engineering Standards

All contributions MUST strictly comply with standards defined in `.agents/rules/`:

1. **Clean Code & Modern Java (`rules/java-kotlin/clean-code.md`)**:
   - **Records**: Use Java `record`s for domain entities, value objects, commands, events, and DTOs.
   - **Compact Constructors**: Validate invariants directly within compact constructors for fail-fast behavior.
   - **Mandatory Type-Level Javadoc**: Every class, interface, record, and enum MUST have Javadoc with:
     - `@author edmaputra`
     - `@since 0.0.1` (or current project version).
   - **Constructor Injection**: Inject dependencies via constructors with `final` fields. Never use field injection (`@Autowired` on fields).
   - **Null Safety**: Never return `null`; use `Optional<T>` or empty collections (`List.of()`, `Set.of()`).

2. **Testing & Browser Automation (`docs/how-to/07-end-to-end-testing.md`)**:
   - **Test Tier Separation**: Unit and WebMvc slice tests run under Surefire during `mvn test` without starting browser instances. End-to-end integration tests live in `ed-idp-e2e` and run under Failsafe during `mvn verify`.
   - **Page Object Model (POM)**: All browser UI interactions must be encapsulated in Page Objects (`io.github.edmaputra.edidp.e2e.pages.*`) with fluent navigation chaining.
   - **Failure Diagnostics**: Selenide automatically captures screenshots (`.png`) and HTML page source (`.html`) on test failure into `ed-idp-e2e/target/selenide-reports/`.
   - **Dynamic Ports**: E2E tests must bind to random ports (`webEnvironment = RANDOM_PORT`) and resolve endpoints via `baseUrl()` or `url(path)`.

3. **Logging & Observability (`rules/java-kotlin/logging-and-observability.md`)**:
   - Use SLF4J with parameterized messages (`{}` placeholders). Never use `System.out` or `e.printStackTrace()`.
   - Propagate MDC context (e.g. `tenantId`) during request handling and ensure cleanup in `finally` blocks.

4. **Multi-Tenancy & Security (`rules/java-kotlin/multi-tenancy-and-audit.md` & `rules/java-kotlin/security-standards.md`)**:
   - Context propagation via Java 25 `ScopedValue` (`TenantContext`).
   - Every persistence operation and query must be strictly scoped by `tenant_id`.

5. **Automated Structure Discovery (`rules/shared/project-structure-standards.md`)**:
   - The project structure map is cached at `.agents/project-structure.json` (git-ignored).
   - Antigravity lifecycle hooks automatically refresh the map if missing.

6. **CI Pipeline & Code Coverage (`rules/shared/git-and-ci-standards.md`)**:
   - Automated CI runs via `.github/workflows/ci.yml` targeting Java 25.
   - Target a minimum of **80% line coverage** on core business and domain logic.
   - Multi-module JaCoCo coverage summaries are generated via `python3 .agents/scripts/coverage/generate-jacoco-summary.py` and posted idempotently on PRs.
