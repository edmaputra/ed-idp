# Agent Guide: ed-idp

Central navigation manifest and architectural constraints for AI agents working in `ed-idp`.

---

## 1. Stack & Versions

- **Language**: Java 25
- **Framework**: Spring Boot 4.1.1, Spring Authorization Server, Spring Modulith 2.1.0
- **Build Tool**: Maven Wrapper (`./mvnw`)
- **Database / Persistence**: Spring Data JPA, Spring JDBC, Flyway Migrations, H2 (test/dev)
- **Standards**: Inherited from `.agents/rules/java-kotlin/` and `.agents/rules/shared/`

---

## 2. Architecture: Vertical-Slice Modular Monolith

The codebase is organized into business capability slices governed by Spring Modulith (`@ApplicationModule` in each slice's `package-info.java`):

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
- **Run Tests**: `./mvnw test`
- **Run Single Test**: `./mvnw -Dtest=ClassName test`
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

2. **Logging & Observability (`rules/java-kotlin/logging-and-observability.md`)**:
   - Use SLF4J with parameterized messages (`{}` placeholders). Never use `System.out` or `e.printStackTrace()`.
   - Propagate MDC context (e.g. `tenantId`) during request handling and ensure cleanup in `finally` blocks.

3. **Multi-Tenancy & Security (`rules/java-kotlin/multi-tenancy-and-audit.md` & `rules/java-kotlin/security-standards.md`)**:
   - Context propagation via Java 25 `ScopedValue` (`TenantContext`).
   - Every persistence operation and query must be strictly scoped by `tenant_id`.

4. **Automated Structure Discovery (`rules/shared/project-structure-standards.md`)**:
   - The project structure map is cached at `.agents/project-structure.json` (git-ignored).
   - Antigravity lifecycle hooks automatically refresh the map if missing.
