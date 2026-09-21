package io.github.edmaputra.edidp.tokens.revocation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

/**
 * Component executing token invalidation operations against the authorization store.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Component
@RequiredArgsConstructor
public class TokenRevoker {

  private final OAuth2AuthorizationService oauth2AuthorizationService;

  public void revokeTokenForClient(String token, String tokenTypeHint, String registeredClientId) {
    OAuth2TokenType tokenType = resolveTokenType(tokenTypeHint);
    OAuth2Authorization authorization = oauth2AuthorizationService.findByToken(token, tokenType);

    // RFC 7009 requires idempotent success for unknown tokens.
    if (authorization == null) {
      return;
    }

    // A client may only revoke tokens it owns.
    if (!registeredClientId.equals(authorization.getRegisteredClientId())) {
      return;
    }

    OAuth2Authorization.Builder builder = OAuth2Authorization.from(authorization);
    invalidateMatchingTokens(builder, authorization, token);
    oauth2AuthorizationService.save(builder.build());
  }

  private OAuth2TokenType resolveTokenType(String tokenTypeHint) {
    if ("access_token".equals(tokenTypeHint)) {
      return OAuth2TokenType.ACCESS_TOKEN;
    }
    if ("refresh_token".equals(tokenTypeHint)) {
      return OAuth2TokenType.REFRESH_TOKEN;
    }
    return null;
  }

  private void invalidateMatchingTokens(
      OAuth2Authorization.Builder builder,
      OAuth2Authorization authorization,
      String tokenValue) {
    OAuth2Authorization.Token<?> accessToken = authorization.getAccessToken();
    if (accessToken != null && tokenValue.equals(accessToken.getToken().getTokenValue())) {
      builder.invalidate(accessToken.getToken());
    }

    OAuth2Authorization.Token<?> refreshToken = authorization.getRefreshToken();
    if (refreshToken != null && tokenValue.equals(refreshToken.getToken().getTokenValue())) {
      builder.invalidate(refreshToken.getToken());

      // Revoking refresh tokens also invalidates sibling access tokens.
      if (accessToken != null) {
        builder.invalidate(accessToken.getToken());
      }
    }
  }
}

