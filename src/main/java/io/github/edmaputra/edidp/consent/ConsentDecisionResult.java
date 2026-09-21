package io.github.edmaputra.edidp.consent;

import java.util.Set;

/**
 * Result model representing whether user consent is needed or already satisfied.
 *
 * @param consentRequired            whether interactive user consent must be prompted
 * @param previouslyAuthorizedScopes scopes that have already been authorized by the user
 * @param error                      error message if consent evaluation failed
 * @author edmaputra
 * @since 0.0.1
 */
public record ConsentDecisionResult(
    boolean consentRequired,
    Set<String> previouslyAuthorizedScopes,
    String error) {

  public ConsentDecisionResult {
    previouslyAuthorizedScopes = previouslyAuthorizedScopes == null ? Set.of() : Set.copyOf(previouslyAuthorizedScopes);
  }

  public static ConsentDecisionResult noConsentNeeded(Set<String> authorizedScopes) {
    return new ConsentDecisionResult(false, authorizedScopes, null);
  }

  public static ConsentDecisionResult consentNeeded() {
    return new ConsentDecisionResult(true, Set.of(), null);
  }

  public static ConsentDecisionResult withError(String errorMessage) {
    return new ConsentDecisionResult(false, Set.of(), errorMessage);
  }
}
