package io.github.edmaputra.edidp.authorization;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Command encapsulating the arguments for evaluating whether a client possesses an authorized scope.
 *
 * @param clientId      the client identifier, must not be null or blank
 * @param grantType     the authorization grant type being evaluated, must not be null
 * @param requiredScope the required OAuth2 scope, must not be null or blank
 * @author edmaputra
 * @since 0.0.1
 */
public record ValidateScopeCommand(
    String clientId,
    AuthorizationGrantType grantType,
    String requiredScope) {

  public ValidateScopeCommand {
    if (clientId == null || clientId.isBlank()) {
      throw new IllegalArgumentException("clientId cannot be null or blank");
    }
    if (grantType == null) {
      throw new IllegalArgumentException("grantType cannot be null");
    }
    if (requiredScope == null || requiredScope.isBlank()) {
      throw new IllegalArgumentException("requiredScope cannot be null or blank");
    }
  }
}
