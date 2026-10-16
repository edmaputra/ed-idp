package io.github.edmaputra.edidp.oauth.mfa;

import io.github.edmaputra.iam.adapter.security.SecurityContextAccessor;
import io.github.edmaputra.iam.adapter.security.jwt.JwtProperties;
import io.github.edmaputra.iam.adapter.security.jwt.JwtTokenProvider;
import io.github.edmaputra.iam.application.port.out.TokenProviderPort;
import io.github.edmaputra.iam.domain.security.CurrentActorProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridge configuration satisfying ed-iam-auth internal token contract without activating
 * ed-iam's resource server servlet filter chain.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
public class IamAuthBridgeConfig {

  @Bean
  @ConditionalOnMissingBean(TokenProviderPort.class)
  TokenProviderPort iamTokenProviderPort(
      @Value("${iam.jwt.secret:iam-default-fallback-mfa-encryption-key-for-credentials-at-rest!}") String jwtSecret) {
    JwtProperties properties = JwtProperties.of(jwtSecret, 3600L, 86400L);
    return new JwtTokenProvider(properties);
  }

  @Bean
  @ConditionalOnMissingBean(CurrentActorProvider.class)
  CurrentActorProvider iamCurrentActorProvider() {
    return new SecurityContextAccessor();
  }
}
