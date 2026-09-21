package io.github.edmaputra.edidp.tenancy;

import java.util.Optional;

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
