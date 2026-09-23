package io.github.edmaputra.edidp.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for {@link ClientAuthenticationService} and {@link ClientScopeService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientAuthenticationAndScopeIntegrationTests {

  @Autowired
  private ClientAuthenticationService clientAuthenticationService;

  @Autowired
  private ClientScopeService clientScopeService;

  @Test
  void authenticateBasic_withValidCredentials_returnsSuccess() {
    // Arrange
    String raw = "demo-client:demo-secret";
    String authHeader = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

    // Act
    ClientAuthenticationResult result = clientAuthenticationService.authenticateBasic(authHeader);

    // Assert
    assertThat(result.authenticated()).isTrue();
    assertThat(result.clientId()).isEqualTo("demo-client");
    assertThat(result.registeredClientId()).isNotBlank();
    assertThat(result.scopes()).contains("read", "write", "openid");
  }

  @Test
  void authenticateBasic_withInvalidPassword_returnsFailedWithClientId() {
    // Arrange
    String raw = "demo-client:wrong-password";
    String authHeader = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

    // Act
    ClientAuthenticationResult result = clientAuthenticationService.authenticateBasic(authHeader);

    // Assert
    assertThat(result.authenticated()).isFalse();
    assertThat(result.clientId()).isEqualTo("demo-client");
    assertThat(result.registeredClientId()).isNull();
  }

  @Test
  void authenticateBasic_withUnknownClient_returnsFailedWithClientId() {
    // Arrange
    String raw = "nonexistent-client:some-secret";
    String authHeader = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

    // Act
    ClientAuthenticationResult result = clientAuthenticationService.authenticateBasic(authHeader);

    // Assert
    assertThat(result.authenticated()).isFalse();
    assertThat(result.clientId()).isEqualTo("nonexistent-client");
  }

  @Test
  void authenticateBasic_withNullOrEmptyHeader_returnsFailedWithNull() {
    // Act & Assert
    assertThat(clientAuthenticationService.authenticateBasic(null).authenticated()).isFalse();
    assertThat(clientAuthenticationService.authenticateBasic("").authenticated()).isFalse();
    assertThat(clientAuthenticationService.authenticateBasic("Bearer some-token").authenticated()).isFalse();
  }

  @Test
  void authenticateBasic_withMalformedBase64OrMissingColon_returnsFailed() {
    // Arrange: malformed base64
    ClientAuthenticationResult malformedBase64 = clientAuthenticationService.authenticateBasic("Basic !!!not-base64!!!");
    assertThat(malformedBase64.authenticated()).isFalse();

    // Arrange: no colon in decoded text
    String noColon = "Basic " + Base64.getEncoder().encodeToString("just-client-id".getBytes(StandardCharsets.UTF_8));
    ClientAuthenticationResult missingColon = clientAuthenticationService.authenticateBasic(noColon);
    assertThat(missingColon.authenticated()).isFalse();
  }

  @Test
  void clientCredentialsRecord_invariantsValidation() {
    // Assert
    assertThatThrownBy(() -> new ClientAuthenticationService.ClientCredentials(null, "secret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId cannot be null or blank");

    assertThatThrownBy(() -> new ClientAuthenticationService.ClientCredentials("  ", "secret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId cannot be null or blank");

    ClientAuthenticationService.ClientCredentials creds =
        new ClientAuthenticationService.ClientCredentials("my-client", "my-secret");
    assertThat(creds.clientId()).isEqualTo("my-client");
    assertThat(creds.clientSecret()).isEqualTo("my-secret");
  }

  @Test
  void clientScopeService_withKnownClient_returnsScopes() {
    // Act
    Set<String> scopes = clientScopeService.getClientScopes("demo-client");

    // Assert
    assertThat(scopes).contains("read", "write", "openid");
    assertThat(clientScopeService.clientHasScope("demo-client", "read")).isTrue();
    assertThat(clientScopeService.clientHasScope("demo-client", "nonexistent-scope")).isFalse();
  }

  @Test
  void clientScopeService_withUnknownClient_returnsEmptyAndFalse() {
    // Act & Assert
    assertThat(clientScopeService.getClientScopes("unknown-client")).isEmpty();
    assertThat(clientScopeService.clientHasScope("unknown-client", "read")).isFalse();
  }
}
