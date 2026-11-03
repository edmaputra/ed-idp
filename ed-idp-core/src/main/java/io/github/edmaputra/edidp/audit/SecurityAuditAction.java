package io.github.edmaputra.edidp.audit;

/**
 * Enumeration of security-relevant audit event actions across identity and OAuth 2.1 lifecycles.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public enum SecurityAuditAction {
  /**
   * Primary authentication succeeded for a user principal.
   */
  LOGIN_SUCCESS,

  /**
   * Primary authentication failed for a user principal.
   */
  LOGIN_FAILURE,

  /**
   * Step-up Multi-Factor Authentication challenge triggered.
   */
  MFA_CHALLENGE,

  /**
   * Multi-Factor Authentication verification succeeded.
   */
  MFA_VERIFY_SUCCESS,

  /**
   * Multi-Factor Authentication verification failed.
   */
  MFA_VERIFY_FAILURE,

  /**
   * Principal session invalidated / user signed out.
   */
  LOGOUT,

  /**
   * OAuth 2.1 / OIDC token response successfully issued.
   */
  TOKEN_ISSUED,

  /**
   * OAuth 2.1 token issuance request failed or rejected.
   */
  TOKEN_FAILED,

  /**
   * RFC 7009 token revocation request processed.
   */
  TOKEN_REVOKED,

  /**
   * RFC 7662 token introspection query executed.
   */
  TOKEN_INTROSPECTED,

  /**
   * User approved consent for requested OAuth 2.1 client scopes.
   */
  CONSENT_GRANTED,

  /**
   * User rejected or denied consent for requested OAuth 2.1 client scopes.
   */
  CONSENT_DENIED,

  /**
   * User profile attributes modified.
   */
  USER_PROFILE_UPDATED,

  /**
   * User credentials / password altered.
   */
  USER_PASSWORD_CHANGED
}
