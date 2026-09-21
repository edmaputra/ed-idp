package io.github.edmaputra.enhauthserv.tenancy;

import java.util.Optional;

/**
 * Scoped-value tenant context holder using Java 25 ScopedValue.
 */
public final class TenantContext {

  public static final ScopedValue<String> CURRENT_TENANT = ScopedValue.newInstance();

  private TenantContext() {
  }

  public static Optional<String> getCurrentTenant() {
    return CURRENT_TENANT.isBound() ? Optional.ofNullable(CURRENT_TENANT.get()) : Optional.empty();
  }

  public static String getCurrentTenantOrDefault(String fallbackTenant) {
    return CURRENT_TENANT.isBound() ? CURRENT_TENANT.get() : fallbackTenant;
  }

  public static void runWithTenant(String tenantId, Runnable runnable) {
    if (tenantId == null || tenantId.isBlank()) {
      runnable.run();
    } else {
      ScopedValue.where(CURRENT_TENANT, tenantId).run(runnable);
    }
  }

  public static <T, X extends Throwable> T callWithTenant(String tenantId, ScopedValue.CallableOp<T, X> operation) throws X {
    if (tenantId == null || tenantId.isBlank()) {
      return operation.call();
    } else {
      return ScopedValue.where(CURRENT_TENANT, tenantId).call(operation);
    }
  }
}