package io.github.edmaputra.edidp.consent;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsentStore {

  private final OAuth2AuthorizationConsentService consentService;

  public Set<String> getAuthorizedScopes(String principalName, String registeredClientId) {
    OAuth2AuthorizationConsent consent = consentService.findById(registeredClientId, principalName);
    if (consent == null) {
      return Set.of();
    }

    Set<String> scopes = consent.getAuthorities().stream()
        .map(authority -> authority.getAuthority())
        .collect(java.util.stream.Collectors.toSet());

    return scopes;
  }

  public boolean isMissingConsent(String principalName, String registeredClientId, Set<String> requestedScopes) {
    Set<String> authorizedScopes = getAuthorizedScopes(principalName, registeredClientId);

    for (String requestedScope : requestedScopes) {
      if (!authorizedScopes.contains(requestedScope)) {
        return true;
      }
    }

    return false;
  }

  public void saveConsent(String principalName, String registeredClientId, Set<String> authorizedScopes) {
    OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(
        registeredClientId,
        principalName);

    for (String scope : authorizedScopes) {
      builder.authority(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope));
    }

    consentService.save(builder.build());
  }
}
