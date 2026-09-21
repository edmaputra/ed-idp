package io.github.edmaputra.edidp.clients;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
public class ClientsConfig {

  @Bean
  @Order(1)
  CommandLineRunner demoRegisteredClientSeeder(ClientBootstrapService clientBootstrapService) {
    return args -> clientBootstrapService.ensureDefaultClients();
  }
}
