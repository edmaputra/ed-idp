package io.github.edmaputra.edidp.tokens.introspection;

import io.github.edmaputra.edidp.authorization.AuthorizationPolicyService;
import io.github.edmaputra.edidp.authorization.AuthorizationPolicyResult;
import io.github.edmaputra.edidp.authorization.ValidateScopeCommand;
import io.github.edmaputra.edidp.clients.ClientAuthenticationService;
import io.github.edmaputra.edidp.clients.ClientAuthenticationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntrospectTokenService {

  private static final String INTROSPECTION_SCOPE = "introspection";

  private final ClientAuthenticationService clientAuthenticationService;
  private final TokenIntrospectionValidator tokenIntrospectionValidator;
  private final AuthorizationPolicyService authorizationPolicyService;

  public IntrospectTokenResult introspect(IntrospectTokenCommand command) {
    if (command.token() == null || command.token().isEmpty()) {
      return error(
          IntrospectTokenResult.Status.BAD_REQUEST,
          "invalid_request",
          "Missing required parameter: token");
    }

    ClientAuthenticationResult authentication =
        clientAuthenticationService.authenticateBasic(command.authorizationHeader());
    if (!authentication.authenticated()) {
      return error(
          IntrospectTokenResult.Status.UNAUTHORIZED,
          "invalid_client",
          "Client authentication failed");
    }

    // Validate client has introspection scope via authorization policy
    ValidateScopeCommand scopeValidation = new ValidateScopeCommand(
        authentication.clientId(),
        AuthorizationGrantType.CLIENT_CREDENTIALS,
        INTROSPECTION_SCOPE);
    AuthorizationPolicyResult policyResult = authorizationPolicyService.validateScope(scopeValidation);

    if (!policyResult.authorized()) {
      return error(
          IntrospectTokenResult.Status.FORBIDDEN,
          policyResult.error(),
          policyResult.errorDescription());
    }

    Map<String, Object> introspectionResponse = tokenIntrospectionValidator.introspect(command.token());
    return new IntrospectTokenResult(IntrospectTokenResult.Status.OK, introspectionResponse);
  }

  private IntrospectTokenResult error(
      IntrospectTokenResult.Status status,
      String error,
      String errorDescription) {
    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("error", error);
    errorResponse.put("error_description", errorDescription);
    return new IntrospectTokenResult(status, errorResponse);
  }
}
