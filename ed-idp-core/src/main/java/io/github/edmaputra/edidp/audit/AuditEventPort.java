package io.github.edmaputra.edidp.audit;

/**
 * Outbound port for recording structured security audit events.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface AuditEventPort {

  /**
   * Records a security audit event to available audit sinks (e.g. structured logs,
   * in-memory ledger, event publishers).
   *
   * @param event the audit event to record
   */
  void record(SecurityAuditEvent event);

  /**
   * Returns a no-operation implementation of {@link AuditEventPort}.
   *
   * @return no-op port
   */
  static AuditEventPort noop() {
    return event -> {};
  }
}
