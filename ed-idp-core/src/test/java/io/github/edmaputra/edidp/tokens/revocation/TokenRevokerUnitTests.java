package io.github.edmaputra.edidp.tokens.revocation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * Unit tests for {@link TokenRevoker}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class TokenRevokerUnitTests {

  private OAuth2AuthorizationService authorizationService;
  private TokenRevoker tokenRevoker;

  @BeforeEach
  void setUp() {
    authorizationService = mock(OAuth2AuthorizationService.class);
    tokenRevoker = new TokenRevoker(authorizationService);
  }

  @Test
  void revokeToken_whenAuthorizationNotFound_isIdempotent() {
    when(authorizationService.findByToken("unknown-token", OAuth2TokenType.ACCESS_TOKEN))
        .thenReturn(null);

    tokenRevoker.revokeTokenForClient("unknown-token", "access_token", "client-1");

    verify(authorizationService, never()).save(any());
  }

  @Test
  void revokeToken_whenClientMismatch_doesNotRevoke() {
    RegisteredClient client = RegisteredClient.withId("client-owner")
        .clientId("demo")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://localhost/cb")
        .build();

    OAuth2Authorization auth = OAuth2Authorization.withRegisteredClient(client)
        .principalName("alice")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .attribute("test", "val")
        .build();

    when(authorizationService.findByToken("token-123", OAuth2TokenType.ACCESS_TOKEN))
        .thenReturn(auth);

    tokenRevoker.revokeTokenForClient("token-123", "access_token", "client-intruder");

    verify(authorizationService, never()).save(any());
  }

  @Test
  void revokeToken_withAccessTokenHint_invalidatesAccessToken() {
    RegisteredClient client = RegisteredClient.withId("client-1")
        .clientId("demo")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://localhost/cb")
        .build();

    OAuth2AccessToken accessToken = new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        "access-123",
        Instant.now(),
        Instant.now().plusSeconds(3600)
    );

    OAuth2Authorization auth = OAuth2Authorization.withRegisteredClient(client)
        .principalName("alice")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .token(accessToken)
        .attribute("test", "val")
        .build();

    when(authorizationService.findByToken("access-123", OAuth2TokenType.ACCESS_TOKEN))
        .thenReturn(auth);

    tokenRevoker.revokeTokenForClient("access-123", "access_token", "client-1");

    verify(authorizationService).save(any(OAuth2Authorization.class));
  }

  @Test
  void revokeToken_withRefreshTokenHint_invalidatesRefreshTokenAndSiblingAccessToken() {
    RegisteredClient client = RegisteredClient.withId("client-1")
        .clientId("demo")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://localhost/cb")
        .build();

    OAuth2AccessToken accessToken = new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        "access-sibling",
        Instant.now(),
        Instant.now().plusSeconds(3600)
    );

    OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
        "refresh-123",
        Instant.now()
    );

    OAuth2Authorization auth = OAuth2Authorization.withRegisteredClient(client)
        .principalName("alice")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .token(accessToken)
        .token(refreshToken)
        .attribute("test", "val")
        .build();

    when(authorizationService.findByToken("refresh-123", OAuth2TokenType.REFRESH_TOKEN))
        .thenReturn(auth);

    tokenRevoker.revokeTokenForClient("refresh-123", "refresh_token", "client-1");

    verify(authorizationService).save(any(OAuth2Authorization.class));
  }

  @Test
  void revokeToken_withRefreshTokenHintAndNullAccessToken() {
    RegisteredClient client = RegisteredClient.withId("client-1")
        .clientId("demo")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://localhost/cb")
        .build();

    OAuth2RefreshToken refreshToken = new OAuth2RefreshToken(
        "refresh-solo",
        Instant.now()
    );

    OAuth2Authorization auth = OAuth2Authorization.withRegisteredClient(client)
        .principalName("alice")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .token(refreshToken)
        .attribute("test", "val")
        .build();

    when(authorizationService.findByToken("refresh-solo", OAuth2TokenType.REFRESH_TOKEN))
        .thenReturn(auth);

    tokenRevoker.revokeTokenForClient("refresh-solo", "refresh_token", "client-1");

    verify(authorizationService).save(any(OAuth2Authorization.class));
  }

  @Test
  void revokeToken_withNullOrUnknownHint_queriesWithNullType() {
    RegisteredClient client = RegisteredClient.withId("client-1")
        .clientId("demo")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://localhost/cb")
        .build();

    OAuth2Authorization auth = OAuth2Authorization.withRegisteredClient(client)
        .principalName("alice")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .attribute("test", "val")
        .build();

    when(authorizationService.findByToken("any-token", null))
        .thenReturn(auth);

    tokenRevoker.revokeTokenForClient("any-token", null, "client-1");
    tokenRevoker.revokeTokenForClient("any-token", "unknown_hint", "client-1");

    verify(authorizationService, times(2)).save(any(OAuth2Authorization.class));
  }
}
