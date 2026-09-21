package io.github.edmaputra.edidp.authorization;

/**
 * Result model representing whether a client authorization policy check passed or failed.
 *
 * @param authorized       whether the client is authorized
 * @param error            standard OAuth2 error code
 * @param errorDescription detailed explanation of the policy decision
 * @author edmaputra
 * @since 0.0.1
 */
public record AuthorizationPolicyResult(
    boolean authorized,
    String error,
    String errorDescription) {

  public static AuthorizationPolicyResult success() {
    return new AuthorizationPolicyResult(true, null, null);
  }

  public static AuthorizationPolicyResult failure(String error, String errorDescription) {
    return new AuthorizationPolicyResult(false, error, errorDescription);
  }

  public static AuthorizationPolicyResult missingScope(String requiredScope) {
    return failure(
        "unauthorized_client",
        "Client is not authorized for scope: " + requiredScope);
  }
}
