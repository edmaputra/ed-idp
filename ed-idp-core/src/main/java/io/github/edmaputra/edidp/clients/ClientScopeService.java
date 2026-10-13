package io.github.edmaputra.edidp.clients;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;

/**
 * Service for querying and validating scopes assigned to registered clients.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
@RequiredArgsConstructor
public class ClientScopeService {

  private final RegisteredClientRepository registeredClientRepository;

  public Set<String> getClientScopes(String clientId) {
    RegisteredClient client = registeredClientRepository.findByClientId(clientId);
    if (client == null) {
      return Set.of();
    }

    Set<String> scopes = client.getScopes();
    return scopes == null ? Set.of() : Set.copyOf(scopes);
  }

  public boolean clientHasScope(String clientId, String scope) {
    Set<String> clientScopes = getClientScopes(clientId);
    return clientScopes.contains(scope);
  }
}
