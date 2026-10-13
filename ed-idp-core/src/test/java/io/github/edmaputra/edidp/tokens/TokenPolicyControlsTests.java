package io.github.edmaputra.edidp.tokens;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import io.github.edmaputra.edidp.integration.AuthServerIntegrationTests;
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
    properties = {
      "spring.datasource.url=jdbc:h2:mem:authdb_token_policy;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "app.token.access-token-time-to-live=90s",
      "app.token.client-credentials-allowed-scopes=read"
    })
class TokenPolicyControlsTests extends AuthServerIntegrationTests {

  @Test
  void configuredAccessTokenLifetimeIsAppliedToTokenEndpointResponse() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", "read");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
    ResponseEntity<String> response = restTemplate
        .withBasicAuth("demo-client", "demo-secret")
        .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = objectMapper.readTree(response.getBody());
    long expiresIn = body.path("expires_in").asLong();
    assertThat(expiresIn).isGreaterThan(0);
    assertThat(expiresIn).isLessThanOrEqualTo(90L);
    assertThat(expiresIn).isGreaterThanOrEqualTo(60L);
  }

  @Test
  void disallowedClientCredentialsScopeIsRejectedByPolicy() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", "write");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
    ResponseEntity<String> response = restTemplate
        .withBasicAuth("demo-client", "demo-secret")
        .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("error").asText()).isEqualTo("invalid_scope");
  }
}