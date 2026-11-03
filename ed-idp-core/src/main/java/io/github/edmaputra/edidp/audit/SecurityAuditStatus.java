package io.github.edmaputra.edidp.audit;

/**
 * Outcome status of a security audit event.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public enum SecurityAuditStatus {
  /**
   * Action completed successfully.
   */
  SUCCESS,

  /**
   * Action failed, was rejected, or was aborted.
   */
  FAILURE
}
