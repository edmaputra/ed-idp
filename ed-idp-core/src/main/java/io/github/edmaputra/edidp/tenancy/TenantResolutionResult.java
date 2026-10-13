package io.github.edmaputra.edidp.tenancy;

import java.util.Optional;

/**
 * Encapsulates the outcome of a tenant resolution evaluation against an incoming request.
 *
 * @param tenantId       optional resolved tenant identifier
 * @param rewrittenPath  optional rewritten path for downstream dispatch
 * @param invalidRequest whether the resolution outcome marks the request as invalid
 * @param tenantSource   the origin source through which the tenant was identified
 * @author edmaputra
 * @since 0.0.1
 */
public record TenantResolutionResult(
    Optional<String> tenantId,
    Optional<String> rewrittenPath,
    boolean invalidRequest,
    TenantSource tenantSource) {

  public static TenantResolutionResult none(Optional<String> rewrittenPath, boolean invalidRequest) {
    return new TenantResolutionResult(Optional.empty(), rewrittenPath, invalidRequest, TenantSource.NONE);
  }

  public static TenantResolutionResult resolved(
      String tenantId,
      Optional<String> rewrittenPath,
      TenantSource tenantSource) {
    return new TenantResolutionResult(Optional.of(tenantId), rewrittenPath, false, tenantSource);
  }

  public enum TenantSource {
    HEADER,
    PATH,
    NONE
  }
}
