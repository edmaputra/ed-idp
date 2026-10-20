package io.github.edmaputra.edidp.oauth;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Token validator ensuring that the JWT tenant matches the request's active {@link TenantContext}.
 * Prevents cross-tenant token usage.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class TenantClaimJwtValidator implements OAuth2TokenValidator<Jwt> {

  private static final OAuth2Error MISMATCH_ERROR = new OAuth2Error(
      "cross_tenant_access_denied",
      "Token tenant does not match active request tenant",
      null
  );

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    String currentTenant = TenantContext.getCurrentTenantOrDefault("demo");
    String tokenTenant = jwt.getClaimAsString("tenant_id");

    if (tokenTenant == null || tokenTenant.isBlank()) {
      String iss = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
      if (iss.contains("/t/")) {
        tokenTenant = iss.substring(iss.lastIndexOf("/t/") + 3);
      } else {
        tokenTenant = "demo";
      }
    }

    if (!currentTenant.equalsIgnoreCase(tokenTenant)) {
      return OAuth2TokenValidatorResult.failure(MISMATCH_ERROR);
    }

    return OAuth2TokenValidatorResult.success();
  }
}
