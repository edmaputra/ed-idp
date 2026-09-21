package io.github.edmaputra.edidp.tokens;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Spring configuration providing {@link TokenSettings} beans configured by {@link TokenPolicyProperties}.
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
}

