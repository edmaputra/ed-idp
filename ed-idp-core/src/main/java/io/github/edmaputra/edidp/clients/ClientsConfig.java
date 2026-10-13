package io.github.edmaputra.edidp.clients;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * Spring configuration for client management and seed data initialization.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
public class ClientsConfig {

  @Bean
  @Order(1)
  @ConditionalOnProperty(name = "edidp.seeding.enabled", havingValue = "true", matchIfMissing = true)
  CommandLineRunner demoRegisteredClientSeeder(ClientBootstrapService clientBootstrapService) {
    return args -> clientBootstrapService.ensureDefaultClients();
  }
}
