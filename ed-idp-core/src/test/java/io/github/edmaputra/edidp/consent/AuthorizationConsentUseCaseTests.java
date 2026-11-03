package io.github.edmaputra.edidp.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.edmaputra.edidp.audit.AuditEventPort;
import io.github.edmaputra.edidp.audit.SecurityAuditAction;
import io.github.edmaputra.edidp.audit.SecurityAuditEvent;
import io.github.edmaputra.edidp.audit.SecurityAuditStatus;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationConsentServiceTests {

  @Mock
  private ConsentStore consentStore;

  @Mock
  private AuditEventPort auditEventPort;

  private AuthorizationConsentService useCase;

  @BeforeEach
  void setUp() {
    useCase = new AuthorizationConsentService(consentStore, auditEventPort);
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
  void approveConsentSavesConsentWithRequestedScopesAndAudits() {
    // Arrange
    String principalName = "demo-user";
    String registeredClientId = "client-id";
    Set<String> requestedScopes = Set.of("read", "profile");

    CheckConsentCommand command = new CheckConsentCommand(
        principalName,
        registeredClientId,
        requestedScopes);

    // Act
    useCase.approveConsent(command, "demo-client");

    // Assert
    verify(consentStore).saveConsent(
        principalName,
        registeredClientId,
        requestedScopes);

    ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
    verify(auditEventPort).record(captor.capture());
    SecurityAuditEvent event = captor.getValue();
    assertThat(event.action()).isEqualTo(SecurityAuditAction.CONSENT_GRANTED);
    assertThat(event.status()).isEqualTo(SecurityAuditStatus.SUCCESS);
    assertThat(event.principal()).isEqualTo(principalName);
    assertThat(event.clientIdentifier()).isEqualTo("demo-client");
    assertThat(event.details().get("approved_scopes")).isEqualTo(requestedScopes);
  }

  @Test
  void approveConsentSingleArgDelegatesWithRegisteredClientId() {
    // Arrange
    String principalName = "demo-user";
    String registeredClientId = "client-id";
    Set<String> requestedScopes = Set.of("read");

    CheckConsentCommand command = new CheckConsentCommand(
        principalName,
        registeredClientId,
        requestedScopes);

    // Act
    useCase.approveConsent(command);

    // Assert
    verify(consentStore).saveConsent(principalName, registeredClientId, requestedScopes);
    ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
    verify(auditEventPort).record(captor.capture());
    SecurityAuditEvent event = captor.getValue();
    assertThat(event.action()).isEqualTo(SecurityAuditAction.CONSENT_GRANTED);
    assertThat(event.clientIdentifier()).isEqualTo(registeredClientId);
  }

  @Test
  void denyConsentRecordsConsentDeniedAuditEvent() {
    // Act
    useCase.denyConsent("demo-user", "demo-client", "user_denied");

    // Assert
    ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
    verify(auditEventPort).record(captor.capture());
    SecurityAuditEvent event = captor.getValue();
    assertThat(event.action()).isEqualTo(SecurityAuditAction.CONSENT_DENIED);
    assertThat(event.status()).isEqualTo(SecurityAuditStatus.SUCCESS);
    assertThat(event.principal()).isEqualTo("demo-user");
    assertThat(event.clientIdentifier()).isEqualTo("demo-client");
    assertThat(event.details().get("reason")).isEqualTo("user_denied");
  }

  @Test
  void fallbackConstructorDoesNotThrowOnApprove() {
    AuthorizationConsentService fallback = new AuthorizationConsentService(consentStore);
    CheckConsentCommand command = new CheckConsentCommand("user", "client", Set.of("read"));

    fallback.approveConsent(command);

    verify(consentStore).saveConsent("user", "client", Set.of("read"));
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
