package io.github.edmaputra.edidp.tokens.revocation;

import io.github.edmaputra.edidp.clients.ClientAuthenticationService;
import io.github.edmaputra.edidp.clients.ClientAuthenticationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

/**
 * Service coordinating client authentication, scope validation, and token revocation logic.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
@RequiredArgsConstructor
public class RevokeTokenService {

  private final ClientAuthenticationService clientAuthenticationService;
  private final TokenRevoker tokenRevoker;
  private final RevocationAuthorizationService revocationAuthorizationService;

  public RevokeTokenResult revoke(RevokeTokenCommand command) {
    if (command.token() == null || command.token().isBlank()) {
      return error(
          RevokeTokenResult.Status.BAD_REQUEST,
          "invalid_request",
          "Missing required parameter: token");
    }

    ClientAuthenticationResult authentication =
        clientAuthenticationService.authenticateBasic(command.authorizationHeader());
    if (!authentication.authenticated()) {
      return error(
          RevokeTokenResult.Status.UNAUTHORIZED,
          "invalid_client",
          "Client authentication failed");
    }

    if (!revocationAuthorizationService.canRevoke(authentication.scopes())) {
      return error(
          RevokeTokenResult.Status.FORBIDDEN,
          "invalid_scope",
          "Client does not have revocation scope");
    }

    tokenRevoker.revokeTokenForClient(
        command.token(),
        command.tokenTypeHint(),
        authentication.registeredClientId());

    return new RevokeTokenResult(RevokeTokenResult.Status.OK, Map.of());
  }

  private RevokeTokenResult error(
      RevokeTokenResult.Status status,
      String error,
      String errorDescription) {
    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("error", error);
    errorResponse.put("error_description", errorDescription);
    return new RevokeTokenResult(status, errorResponse);
  }
}
