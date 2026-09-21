package io.github.edmaputra.edidp.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import io.github.edmaputra.edidp.integration.AuthServerIntegrationTests;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServerMetadataTests extends AuthServerIntegrationTests {

  @Test
  void oidcMetadataExposesConfiguredIssuerAndCoreEndpoints() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/.well-known/openid-configuration", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("issuer").asText()).isEqualTo(issuerUri);
    assertThat(body.path("token_endpoint").asText()).isNotBlank();
    assertThat(body.path("userinfo_endpoint").asText()).isNotBlank();
    assertThat(body.path("end_session_endpoint").asText()).isNotBlank();
    assertThat(body.path("jwks_uri").asText()).isNotBlank();
  }

  @Test
  void jwkSetEndpointReturnsAtLeastOneKey() throws Exception {
    ResponseEntity<String> response = restTemplate.getForEntity("/oauth2/jwks", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("keys").isArray()).isTrue();
    assertThat(body.path("keys").size()).isGreaterThan(0);
  }

  @Test
  void tenantMetadataExposesTenantScopedIssuerAndCoreEndpoints() throws Exception {
    String tenant = "demo";
    ResponseEntity<String> response =
        restTemplate.getForEntity("/t/{tenant}/.well-known/openid-configuration", String.class, tenant);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = objectMapper.readTree(response.getBody());
    String expectedTenantIssuer = issuerUri + "/t/" + tenant;
    assertThat(body.path("issuer").asText()).isEqualTo(expectedTenantIssuer);
    assertThat(body.path("token_endpoint").asText()).isEqualTo(expectedTenantIssuer + "/oauth2/token");
    assertThat(body.path("userinfo_endpoint").asText()).isEqualTo(expectedTenantIssuer + "/userinfo");
    assertThat(body.path("end_session_endpoint").asText()).isEqualTo(expectedTenantIssuer + "/connect/logout");
    assertThat(body.path("jwks_uri").asText()).isEqualTo(expectedTenantIssuer + "/oauth2/jwks");
  }

  @Test
  void tenantJwkSetEndpointReturnsAtLeastOneKey() throws Exception {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/t/{tenant}/oauth2/jwks", String.class, "demo");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("keys").isArray()).isTrue();
    assertThat(body.path("keys").size()).isGreaterThan(0);
  }
}
