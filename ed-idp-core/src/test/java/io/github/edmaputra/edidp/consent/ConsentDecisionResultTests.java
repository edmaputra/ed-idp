package io.github.edmaputra.edidp.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConsentDecisionResult}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class ConsentDecisionResultTests {

  @Test
  void factories_andNullHandling() {
    ConsentDecisionResult noConsent = ConsentDecisionResult.noConsentNeeded(Set.of("openid", "profile"));
    assertThat(noConsent.consentRequired()).isFalse();
    assertThat(noConsent.previouslyAuthorizedScopes()).containsExactlyInAnyOrder("openid", "profile");
    assertThat(noConsent.error()).isNull();

    ConsentDecisionResult needed = ConsentDecisionResult.consentNeeded();
    assertThat(needed.consentRequired()).isTrue();
    assertThat(needed.previouslyAuthorizedScopes()).isEmpty();
    assertThat(needed.error()).isNull();

    ConsentDecisionResult errorResult = ConsentDecisionResult.withError("Invalid client");
    assertThat(errorResult.consentRequired()).isFalse();
    assertThat(errorResult.previouslyAuthorizedScopes()).isEmpty();
    assertThat(errorResult.error()).isEqualTo("Invalid client");

    ConsentDecisionResult nullScopes = new ConsentDecisionResult(false, null, null);
    assertThat(nullScopes.previouslyAuthorizedScopes()).isEmpty();
  }
}
