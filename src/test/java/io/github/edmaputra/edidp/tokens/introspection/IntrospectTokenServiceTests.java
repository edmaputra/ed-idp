package io.github.edmaputra.edidp.tokens.introspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.edmaputra.edidp.authorization.AuthorizationPolicyService;
import io.github.edmaputra.edidp.authorization.AuthorizationPolicyResult;
import io.github.edmaputra.edidp.clients.ClientAuthenticationResult;
import io.github.edmaputra.edidp.clients.ClientAuthenticationService;
import io.github.edmaputra.edidp.tokens.introspection.IntrospectTokenCommand;
import io.github.edmaputra.edidp.tokens.introspection.IntrospectTokenResult;
import io.github.edmaputra.edidp.tokens.introspection.IntrospectTokenService;
import io.github.edmaputra.edidp.tokens.introspection.TokenIntrospectionValidator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntrospectTokenServiceTests {

  @Mock
  private ClientAuthenticationService clientAuthenticationService;

  @Mock
  private TokenIntrospectionValidator tokenIntrospectionValidator;

  @Mock
  private AuthorizationPolicyService authorizationPolicyUseCase;

  private IntrospectTokenService useCase;

  @BeforeEach
  void setUp() {
    useCase = new IntrospectTokenService(
        clientAuthenticationService,
        tokenIntrospectionValidator,
        authorizationPolicyUseCase);
  }

  @Test
  void missingTokenReturnsBadRequest() {
    IntrospectTokenResult result = useCase.introspect(new IntrospectTokenCommand("", "Basic abc"));

    assertThat(result.status()).isEqualTo(IntrospectTokenResult.Status.BAD_REQUEST);
    assertThat(result.body().get("error")).isEqualTo("invalid_request");
  }

  @Test
  void unauthenticatedClientReturnsUnauthorized() {
    when(clientAuthenticationService.authenticateBasic("Basic bad"))
        .thenReturn(ClientAuthenticationResult.failed(null));

    IntrospectTokenResult result = useCase.introspect(new IntrospectTokenCommand("token-value", "Basic bad"));

    assertThat(result.status()).isEqualTo(IntrospectTokenResult.Status.UNAUTHORIZED);
    assertThat(result.body().get("error")).isEqualTo("invalid_client");
  }

  @Test
  void clientWithoutIntrospectionScopeReturnsForbidden() {
    when(clientAuthenticationService.authenticateBasic("Basic abc"))
        .thenReturn(ClientAuthenticationResult.success(
            "demo-client",
            "registered-demo-client",
            Set.of("read")));
    when(authorizationPolicyUseCase.validateScope(org.mockito.ArgumentMatchers.any()))
        .thenReturn(AuthorizationPolicyResult.missingScope("introspection"));

    IntrospectTokenResult result = useCase.introspect(new IntrospectTokenCommand("token-value", "Basic abc"));

    assertThat(result.status()).isEqualTo(IntrospectTokenResult.Status.FORBIDDEN);
    assertThat(result.body().get("error")).isEqualTo("unauthorized_client");
  }

  @Test
  void successfulIntrospectionReturnsOk() {
    Map<String, Object> payload = Map.of("active", true, "client_id", "demo-client");
    when(clientAuthenticationService.authenticateBasic("Basic abc"))
        .thenReturn(ClientAuthenticationResult.success(
            "demo-client",
            "registered-demo-client",
            Set.of("introspection", "read")));
    when(authorizationPolicyUseCase.validateScope(org.mockito.ArgumentMatchers.any()))
        .thenReturn(AuthorizationPolicyResult.success());
    when(tokenIntrospectionValidator.introspect("token-value")).thenReturn(payload);

    IntrospectTokenResult result = useCase.introspect(new IntrospectTokenCommand("token-value", "Basic abc"));

    assertThat(result.status()).isEqualTo(IntrospectTokenResult.Status.OK);
    assertThat(result.body()).isEqualTo(payload);
  }
}
