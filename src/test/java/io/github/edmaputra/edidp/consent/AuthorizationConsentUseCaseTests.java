package io.github.edmaputra.edidp.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.edmaputra.edidp.consent.AuthorizationConsentService;
import io.github.edmaputra.edidp.consent.CheckConsentCommand;
import io.github.edmaputra.edidp.consent.ConsentDecisionResult;
import io.github.edmaputra.edidp.consent.ConsentStore;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationConsentServiceTests {

  @Mock
  private ConsentStore consentStore;

  private AuthorizationConsentService useCase;

  @BeforeEach
  void setUp() {
    useCase = new AuthorizationConsentService(consentStore);
  }

  @Test
  void checkConsentReturnsConsentNotRequiredWhenUserHasAuthorizedAllScopes() {
    // Arrange
    String principalName = "demo-user";
    String registeredClientId = "client-id";
    Set<String> requestedScopes = Set.of("read", "profile");
    Set<String> authorizedScopes = Set.of("read", "profile", "email");

    when(consentStore.isMissingConsent(
        principalName,
        registeredClientId,
        requestedScopes))
        .thenReturn(false);

    when(consentStore.getAuthorizedScopes(
        principalName,
        registeredClientId))
        .thenReturn(authorizedScopes);

    CheckConsentCommand command = new CheckConsentCommand(
        principalName,
        registeredClientId,
        requestedScopes);

    // Act
    ConsentDecisionResult result = useCase.checkConsent(command);

    // Assert
    assertThat(result.consentRequired()).isFalse();
    assertThat(result.previouslyAuthorizedScopes()).containsAll(authorizedScopes);
    assertThat(result.error()).isNull();
  }

  @Test
  void checkConsentReturnsConsentRequiredWhenUserMissingConsentForAnyScope() {
    // Arrange
    String principalName = "demo-user";
    String registeredClientId = "client-id";
    Set<String> requestedScopes = Set.of("read", "profile", "email");

    when(consentStore.isMissingConsent(
        principalName,
        registeredClientId,
        requestedScopes))
        .thenReturn(true);

    CheckConsentCommand command = new CheckConsentCommand(
        principalName,
        registeredClientId,
        requestedScopes);

    // Act
    ConsentDecisionResult result = useCase.checkConsent(command);

    // Assert
    assertThat(result.consentRequired()).isTrue();
    assertThat(result.previouslyAuthorizedScopes()).isEmpty();
    assertThat(result.error()).isNull();
  }

  @Test
  void checkConsentReturnsConsentNotRequiredWhenNoExistingAuthorization() {
    // Arrange
    String principalName = "demo-user";
    String registeredClientId = "client-id";
    Set<String> requestedScopes = Set.of("read");

    when(consentStore.isMissingConsent(
        principalName,
        registeredClientId,
        requestedScopes))
        .thenReturn(false);

    when(consentStore.getAuthorizedScopes(
        principalName,
        registeredClientId))
        .thenReturn(Set.of());

    CheckConsentCommand command = new CheckConsentCommand(
        principalName,
        registeredClientId,
        requestedScopes);

    // Act
    ConsentDecisionResult result = useCase.checkConsent(command);

    // Assert
    assertThat(result.consentRequired()).isFalse();
    assertThat(result.previouslyAuthorizedScopes()).isEmpty();
  }

  @Test
  void approveConsentSavesConsentWithRequestedScopes() {
    // Arrange
    String principalName = "demo-user";
    String registeredClientId = "client-id";
    Set<String> requestedScopes = Set.of("read", "profile");

    CheckConsentCommand command = new CheckConsentCommand(
        principalName,
        registeredClientId,
        requestedScopes);

    // Act
    useCase.approveConsent(command);

    // Assert
    verify(consentStore).saveConsent(
        principalName,
        registeredClientId,
        requestedScopes);
  }

  @Test
  void checkConsentThrowsWhenCommandHasNullPrincipalName() {
    // Act & Assert
    assertThatThrownBy(() -> new CheckConsentCommand(
            null,
            "client-id",
            Set.of("read")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("principalName cannot be null");
  }

  @Test
  void checkConsentThrowsWhenCommandHasNullRegisteredClientId() {
    // Act & Assert
    assertThatThrownBy(() -> new CheckConsentCommand(
            "demo-user",
            null,
            Set.of("read")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("registeredClientId cannot be null");
  }

  @Test
  void checkConsentThrowsWhenCommandHasNullScopes() {
    // Act & Assert
    assertThatThrownBy(() -> new CheckConsentCommand(
            "demo-user",
            "client-id",
            null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requestedScopes cannot be null");
  }
}
