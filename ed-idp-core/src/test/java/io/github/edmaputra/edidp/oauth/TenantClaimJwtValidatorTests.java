package io.github.edmaputra.edidp.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.net.URL;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit tests for {@link TenantClaimJwtValidator}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class TenantClaimJwtValidatorTests {

  private final TenantClaimJwtValidator validator = new TenantClaimJwtValidator();

  @Test
  void validate_succeeds_whenTenantIdClaimMatchesTenantContext() {
    Jwt jwt = new Jwt(
        "token-val",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("alg", "none"),
        Map.of("tenant_id", "acme", "sub", "alice")
    );

    TenantContext.runWithTenant("acme", () -> {
      OAuth2TokenValidatorResult result = validator.validate(jwt);
      assertThat(result.hasErrors()).isFalse();
    });
  }

  @Test
  void validate_fails_whenTenantIdClaimDiffersFromTenantContext() {
    Jwt jwt = new Jwt(
        "token-val",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("alg", "none"),
        Map.of("tenant_id", "other-tenant", "sub", "alice")
    );

    TenantContext.runWithTenant("acme", () -> {
      OAuth2TokenValidatorResult result = validator.validate(jwt);
      assertThat(result.hasErrors()).isTrue();
      assertThat(result.getErrors()).anyMatch(err -> "cross_tenant_access_denied".equals(err.getErrorCode()));
    });
  }

  @Test
  void validate_fallsBackToIssuerTenant_whenTenantClaimIsMissing() throws Exception {
    Jwt jwt = new Jwt(
        "token-val",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("alg", "none"),
        Map.of("iss", new URL("http://localhost:9000/t/acme"), "sub", "alice")
    );

    TenantContext.runWithTenant("acme", () -> {
      OAuth2TokenValidatorResult result = validator.validate(jwt);
      assertThat(result.hasErrors()).isFalse();
    });
  }

  @Test
  void validate_defaultsToDemo_whenNoIssuerOrTenantClaim() {
    Jwt jwt = new Jwt(
        "token-val",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("alg", "none"),
        Map.of("sub", "alice")
    );

    TenantContext.runWithTenant("demo", () -> {
      OAuth2TokenValidatorResult result = validator.validate(jwt);
      assertThat(result.hasErrors()).isFalse();
    });
  }
}
