package io.github.edmaputra.edidp.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.edmaputra.edidp.authorization.AuthorizationPolicyResult;
import io.github.edmaputra.edidp.authorization.AuthorizationPolicyService;
import io.github.edmaputra.edidp.authorization.ValidateScopeCommand;
import io.github.edmaputra.edidp.clients.ClientScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@ExtendWith(MockitoExtension.class)
class AuthorizationPolicyServiceTests {

  @Mock
  private ClientScopeService clientScopeService;

  private AuthorizationPolicyService useCase;

  @BeforeEach
  void setUp() {
    useCase = new AuthorizationPolicyService(clientScopeService);
  }

  @Test
  void validateScopeReturnsAuthorizedWhenClientHasRequiredScope() {
    // Arrange
    String clientId = "demo-client";
    String requiredScope = "introspection";
    when(clientScopeService.clientHasScope(clientId, requiredScope)).thenReturn(true);

    ValidateScopeCommand command = new ValidateScopeCommand(
        clientId,
        AuthorizationGrantType.CLIENT_CREDENTIALS,
        requiredScope);

    // Act
    AuthorizationPolicyResult result = useCase.validateScope(command);

    // Assert
    assertThat(result.authorized()).isTrue();
    assertThat(result.error()).isNull();
    assertThat(result.errorDescription()).isNull();
  }

  @Test
  void validateScopeReturnsUnauthorizedWhenClientMissingRequiredScope() {
    // Arrange
    String clientId = "demo-client";
    String requiredScope = "introspection";
    when(clientScopeService.clientHasScope(clientId, requiredScope)).thenReturn(false);

    ValidateScopeCommand command = new ValidateScopeCommand(
        clientId,
        AuthorizationGrantType.CLIENT_CREDENTIALS,
        requiredScope);

    // Act
    AuthorizationPolicyResult result = useCase.validateScope(command);

    // Assert
    assertThat(result.authorized()).isFalse();
    assertThat(result.error()).isEqualTo("unauthorized_client");
    assertThat(result.errorDescription()).contains(requiredScope);
  }

  @Test
  void validateScopeThrowsWhenCommandHasNullClientId() {
    // Act & Assert
    assertThatThrownBy(() -> new ValidateScopeCommand(
            null,
            AuthorizationGrantType.CLIENT_CREDENTIALS,
            "introspection"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId cannot be null");
  }

  @Test
  void validateScopeThrowsWhenCommandHasNullGrantType() {
    // Act & Assert
    assertThatThrownBy(() -> new ValidateScopeCommand(
            "demo-client",
            null,
            "introspection"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("grantType cannot be null");
  }

  @Test
  void validateScopeThrowsWhenCommandHasNullScope() {
    // Act & Assert
    assertThatThrownBy(() -> new ValidateScopeCommand(
            "demo-client",
            AuthorizationGrantType.CLIENT_CREDENTIALS,
            null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requiredScope cannot be null");
  }
}
