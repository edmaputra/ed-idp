package io.github.edmaputra.edidp.tenancy;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates request URI path segments and header data to identify the target tenant.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class ResolveTenantService {

  private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
  private static final Pattern TENANT_PATH_PATTERN = Pattern.compile("^/t/([A-Za-z0-9_-]+)(/.*)?$");
  private static final Pattern TENANT_MACHINE_ENDPOINT_PATTERN =
      Pattern.compile("^/t/([A-Za-z0-9_-]+)/(oauth2/introspect|oauth2/revoke)$");

  private final TenantResolutionPolicy policy;

  public ResolveTenantService(TenantResolutionPolicy policy) {
    this.policy = policy;
  }

  public TenantResolutionResult resolve(String requestUri, String tenantHeaderValue, String remoteAddr) {
    Optional<String> pathTenant = Optional.empty();
    Optional<String> rewrittenPath = Optional.empty();

    if (policy.pathEnabled() && requestUri != null) {
      Matcher machineEndpointMatcher = TENANT_MACHINE_ENDPOINT_PATTERN.matcher(requestUri);
      if (machineEndpointMatcher.matches()) {
        pathTenant = Optional.of(machineEndpointMatcher.group(1));
        rewrittenPath = Optional.of("/" + machineEndpointMatcher.group(2));
      } else {
        pathTenant = resolveTenantFromRequestPath(requestUri);
      }
    }

    Optional<String> headerTenant = resolveTenantFromHeader(tenantHeaderValue, remoteAddr);
    if (headerTenant.isPresent()) {
      return TenantResolutionResult.resolved(
          headerTenant.get(), rewrittenPath, TenantResolutionResult.TenantSource.HEADER);
    }

    if (pathTenant.isPresent()) {
      return TenantResolutionResult.resolved(
          pathTenant.get(), rewrittenPath, TenantResolutionResult.TenantSource.PATH);
    }

    return TenantResolutionResult.none(rewrittenPath, policy.requireExplicitTenant());
  }

  private Optional<String> resolveTenantFromRequestPath(String requestUri) {
    if (requestUri == null || requestUri.isBlank()) {
      return Optional.empty();
    }

    Matcher matcher = TENANT_PATH_PATTERN.matcher(requestUri);
    if (!matcher.matches()) {
      return Optional.empty();
    }

    String tenantId = matcher.group(1);
    if (!isValidTenantId(tenantId)) {
      return Optional.empty();
    }
    return Optional.of(tenantId);
  }

  private Optional<String> resolveTenantFromHeader(String tenantId, String remoteAddr) {
    if (!policy.headerEnabled()) {
      return Optional.empty();
    }

    if (tenantId == null || tenantId.isBlank()) {
      return Optional.empty();
    }

    if (policy.enforceTrustedProxyForHeader() && !isTrustedHeaderSource(remoteAddr)) {
      return Optional.empty();
    }

    if (!isValidTenantId(tenantId)) {
      return Optional.empty();
    }

    return Optional.of(tenantId);
  }

  private boolean isTrustedHeaderSource(String remoteAddr) {
    if (remoteAddr == null || remoteAddr.isBlank()) {
      return false;
    }
    return policy.trustedHeaderSources().contains(remoteAddr.trim());
  }

  private boolean isValidTenantId(String tenantId) {
    if (tenantId == null) {
      return false;
    }
    return TENANT_ID_PATTERN.matcher(tenantId).matches();
  }
}
