package io.github.edmaputra.edidp.consent;

import java.util.Set;

/**
 * Command encapsulating the parameters required to evaluate consent status for an authorization request.
 *
 * @param principalName      the end-user principal name, must not be null or blank
 * @param registeredClientId the internal registered client identifier, must not be null or blank
 * @param requestedScopes    the set of OAuth2 scopes requested, must not be null or empty
 * @author edmaputra
 * @since 0.0.1
 */
public record CheckConsentCommand(
    String principalName,
    String registeredClientId,
    Set<String> requestedScopes) {

  public CheckConsentCommand {
    if (principalName == null || principalName.isBlank()) {
      throw new IllegalArgumentException("principalName cannot be null or blank");
    }
    if (registeredClientId == null || registeredClientId.isBlank()) {
      throw new IllegalArgumentException("registeredClientId cannot be null or blank");
    }
    if (requestedScopes == null || requestedScopes.isEmpty()) {
      throw new IllegalArgumentException("requestedScopes cannot be null or empty");
    }
    requestedScopes = Set.copyOf(requestedScopes);
  }
}
