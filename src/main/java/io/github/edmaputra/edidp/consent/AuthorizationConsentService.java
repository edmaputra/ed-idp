package io.github.edmaputra.edidp.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service orchestrating OAuth 2.1 user consent evaluations and persistence.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
@RequiredArgsConstructor
public class AuthorizationConsentService {

  private final ConsentStore consentStore;

  public ConsentDecisionResult checkConsent(CheckConsentCommand command) {
    if (consentStore.isMissingConsent(
        command.principalName(),
        command.registeredClientId(),
        command.requestedScopes())) {
      return ConsentDecisionResult.consentNeeded();
    }

    var authorizedScopes = consentStore.getAuthorizedScopes(
        command.principalName(),
        command.registeredClientId());

    return ConsentDecisionResult.noConsentNeeded(authorizedScopes);
  }

  public void approveConsent(CheckConsentCommand command) {
    consentStore.saveConsent(
        command.principalName(),
        command.registeredClientId(),
        command.requestedScopes());
  }
}
