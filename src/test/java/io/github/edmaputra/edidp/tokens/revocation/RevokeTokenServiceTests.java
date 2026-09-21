package io.github.edmaputra.edidp.tokens.revocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.edmaputra.edidp.clients.ClientAuthenticationResult;
import io.github.edmaputra.edidp.clients.ClientAuthenticationService;
import io.github.edmaputra.edidp.tokens.revocation.RevocationAuthorizationService;
import io.github.edmaputra.edidp.tokens.revocation.RevokeTokenCommand;
import io.github.edmaputra.edidp.tokens.revocation.RevokeTokenResult;
import io.github.edmaputra.edidp.tokens.revocation.RevokeTokenService;
import io.github.edmaputra.edidp.tokens.revocation.TokenRevoker;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokeTokenServiceTests {

  @Mock
  private ClientAuthenticationService clientAuthenticationService;

  @Mock
  private TokenRevoker tokenRevoker;

  @Mock
  private RevocationAuthorizationService revocationAuthorizationService;

  private RevokeTokenService useCase;

  @BeforeEach
  void setUp() {
    useCase = new RevokeTokenService(
        clientAuthenticationService,
        tokenRevoker,
        revocationAuthorizationService);
  }

  @Test
  void missingTokenReturnsBadRequest() {
    RevokeTokenResult result = useCase.revoke(new RevokeTokenCommand("", "access_token", "Basic abc"));

    assertThat(result.status()).isEqualTo(RevokeTokenResult.Status.BAD_REQUEST);
    assertThat(result.body().get("error")).isEqualTo("invalid_request");
  }

  @Test
  void unauthenticatedClientReturnsUnauthorized() {
    when(clientAuthenticationService.authenticateBasic("Basic bad"))
        .thenReturn(ClientAuthenticationResult.failed(null));

    RevokeTokenResult result = useCase.revoke(new RevokeTokenCommand("token-value", "access_token", "Basic bad"));

    assertThat(result.status()).isEqualTo(RevokeTokenResult.Status.UNAUTHORIZED);
    assertThat(result.body().get("error")).isEqualTo("invalid_client");
  }

  @Test
  void clientWithoutRevocationScopeReturnsForbidden() {
    when(clientAuthenticationService.authenticateBasic("Basic abc"))
        .thenReturn(ClientAuthenticationResult.success(
            "demo-client",
            "registered-demo-client",
            Set.of("read")));
    when(revocationAuthorizationService.canRevoke(Set.of("read"))).thenReturn(false);

    RevokeTokenResult result = useCase.revoke(new RevokeTokenCommand("token-value", "access_token", "Basic abc"));

    assertThat(result.status()).isEqualTo(RevokeTokenResult.Status.FORBIDDEN);
    assertThat(result.body().get("error")).isEqualTo("invalid_scope");
  }

  @Test
  void successfulRevocationDelegatesToTokenRevoker() {
    when(clientAuthenticationService.authenticateBasic("Basic abc"))
        .thenReturn(ClientAuthenticationResult.success(
            "demo-client",
            "registered-demo-client",
            Set.of("revocation", "read")));
    when(revocationAuthorizationService.canRevoke(Set.of("revocation", "read"))).thenReturn(true);

    RevokeTokenResult result = useCase.revoke(
        new RevokeTokenCommand("token-value", "refresh_token", "Basic abc"));

    assertThat(result.status()).isEqualTo(RevokeTokenResult.Status.OK);
    verify(tokenRevoker).revokeTokenForClient("token-value", "refresh_token", "registered-demo-client");
  }
}
