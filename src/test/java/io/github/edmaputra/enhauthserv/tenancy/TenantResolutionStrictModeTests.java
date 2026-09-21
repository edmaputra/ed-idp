package io.github.edmaputra.enhauthserv.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import io.github.edmaputra.enhauthserv.integration.AuthServerIntegrationTests;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "tenant.resolution.require-explicit-tenant=true")
class TenantResolutionStrictModeTests extends AuthServerIntegrationTests {

  @Test
  void introspectWithoutTenantHeaderOrPathReturnsInvalidRequest() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth("demo-client", "demo-secret");

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", "any-token-value");

    ResponseEntity<String> response =
        restTemplate.postForEntity("/oauth2/introspect", new HttpEntity<>(form, headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("error").asText()).isEqualTo("invalid_request");
  }
}
