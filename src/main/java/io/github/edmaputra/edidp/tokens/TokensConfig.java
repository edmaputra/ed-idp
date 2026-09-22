package io.github.edmaputra.edidp.tokens;

import io.github.edmaputra.edidp.claims.ClaimType;
import io.github.edmaputra.edidp.claims.UserClaimsService;
import io.github.edmaputra.edidp.tenancy.TenantContext;
import io.github.edmaputra.edidp.tenancy.TenantIssuerService;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * Spring configuration providing token settings and token customization within the tokens module.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
@EnableConfigurationProperties(TokenPolicyProperties.class)
public class TokensConfig {

  @Bean
  TokenSettings tokenSettings(TokenPolicyProperties tokenPolicyProperties) {
    return TokenSettings.builder()
        .accessTokenTimeToLive(tokenPolicyProperties.getAccessTokenTimeToLive())
        .refreshTokenTimeToLive(tokenPolicyProperties.getRefreshTokenTimeToLive())
        .reuseRefreshTokens(tokenPolicyProperties.isReuseRefreshTokens())
        .build();
  }

  @Bean
  OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(
      UserClaimsService userClaimsService,
      TokenPolicyProperties tokenPolicyProperties,
      TenantIssuerService tenantIssuerService,
      @Value("${app.issuer-uri}") String baseIssuerUri) {
    return (context) -> {
      TenantContext.getCurrentTenant().ifPresent(tenantId -> {
        String tenantIssuer = tenantIssuerService.resolveTenantIssuer(baseIssuerUri, tenantId);
        context.getClaims().claim("iss", tenantIssuer);
      });

      if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
          validateClientCredentialsScopes(context.getAuthorizedScopes(), tokenPolicyProperties);
        }
        return;
      }

      var authorization = context.getAuthorization();
      if (authorization == null) {
        return;
      }

      String username = authorization.getPrincipalName();
      if (username == null || username.isBlank()) {
        return;
      }

      if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
        context.getClaims().claims((claims) ->
            claims.putAll(userClaimsService.getClaims(username, ClaimType.ID_TOKEN)));
        return;
      }

      if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
        context.getClaims().claims((claims) ->
            claims.putAll(userClaimsService.getClaims(username, ClaimType.ACCESS_TOKEN)));
      }
    };
  }

  private static void validateClientCredentialsScopes(
      Set<String> requestedScopes,
      TokenPolicyProperties tokenPolicyProperties) {
    if (requestedScopes == null || requestedScopes.isEmpty()) {
      return;
    }

    Set<String> normalizedAllowedScopes = new HashSet<>();
    for (String allowedScope : tokenPolicyProperties.getClientCredentialsAllowedScopes()) {
      normalizedAllowedScopes.add(allowedScope.toLowerCase(Locale.ROOT));
    }

    Set<String> disallowedScopes = new HashSet<>();
    for (String requestedScope : requestedScopes) {
      if (!normalizedAllowedScopes.contains(requestedScope.toLowerCase(Locale.ROOT))) {
        disallowedScopes.add(requestedScope);
      }
    }

    if (disallowedScopes.isEmpty()) {
      return;
    }

    String description = "Scope not allowed for client_credentials grant: "
        + String.join(", ", disallowedScopes);
    throw new OAuth2AuthenticationException(new OAuth2Error("invalid_scope", description, null));
  }
}
