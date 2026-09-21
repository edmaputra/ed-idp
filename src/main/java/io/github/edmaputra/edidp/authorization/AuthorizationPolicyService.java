package io.github.edmaputra.edidp.authorization;

import io.github.edmaputra.edidp.clients.ClientScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationPolicyService {

  private final ClientScopeService clientScopeService;

  public AuthorizationPolicyResult validateScope(ValidateScopeCommand command) {
    if (!clientScopeService.clientHasScope(command.clientId(), command.requiredScope())) {
      return AuthorizationPolicyResult.missingScope(command.requiredScope());
    }

    return AuthorizationPolicyResult.success();
  }
}
