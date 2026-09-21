package io.github.edmaputra.edidp.clients;

import java.util.Set;

/**
 * Result record for client credential authentication evaluations.
 *
 * @param clientId           the client identifier
 * @param registeredClientId internal registered client identifier
 * @param scopes             granted scopes
 * @param authenticated      whether client credentials were authenticated
 * @author edmaputra
 * @since 0.0.1
 */
public record ClientAuthenticationResult(
    String clientId,
    String registeredClientId,
    Set<String> scopes,
    boolean authenticated) {

  public ClientAuthenticationResult {
    scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
  }

  public static ClientAuthenticationResult failed(String clientId) {
    return new ClientAuthenticationResult(clientId, null, Set.of(), false);
  }

  public static ClientAuthenticationResult success(
      String clientId,
      String registeredClientId,
      Set<String> scopes) {
    return new ClientAuthenticationResult(
        clientId,
        registeredClientId,
        scopes,
        true);
  }
}
