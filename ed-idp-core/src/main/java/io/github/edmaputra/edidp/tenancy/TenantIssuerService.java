package io.github.edmaputra.edidp.tenancy;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Service for dynamically constructing per-tenant OpenID Connect and OAuth 2.1 issuer URLs.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class TenantIssuerService {

  private static final Pattern TENANT_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

  public String resolveTenantIssuer(String baseIssuer, String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return trimTrailingSlash(baseIssuer);
    }
    if (!TENANT_PATTERN.matcher(tenantId).matches()) {
      throw new IllegalArgumentException("Invalid tenant id");
    }
    return trimTrailingSlash(baseIssuer) + "/t/" + tenantId;
  }

  private String trimTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    if (value.endsWith("/")) {
      return value.substring(0, value.length() - 1);
    }
    return value;
  }
}