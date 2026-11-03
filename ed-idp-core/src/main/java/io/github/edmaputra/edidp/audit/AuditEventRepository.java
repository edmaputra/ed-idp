package io.github.edmaputra.edidp.audit;

import java.util.List;

/**
 * Repository interface defining operations for persisting and querying security audit events.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface AuditEventRepository {

  /**
   * Persists the given audit event.
   *
   * @param event audit event to store
   */
  void save(SecurityAuditEvent event);

  /**
   * Returns recent audit events across all tenants up to the specified limit.
   *
   * @param limit maximum number of events to return
   * @return list of audit events ordered newest first
   */
  List<SecurityAuditEvent> findRecent(int limit);

  /**
   * Returns recent audit events scoped to a specific tenant up to the specified limit.
   *
   * @param tenantId tenant identifier
   * @param limit maximum number of events to return
   * @return list of tenant audit events ordered newest first
   */
  List<SecurityAuditEvent> findByTenant(String tenantId, int limit);

  /**
   * Clears stored audit events.
   */
  void clear();
}
