package io.github.edmaputra.edidp.account.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration for HTTP and API clients in the User Account Portal.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
public class PortalClientConfig {

  @Bean
  @ConditionalOnMissingBean
  public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
