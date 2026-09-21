package io.github.edmaputra.enhauthserv.tokens;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import io.github.edmaputra.enhauthserv.integration.AuthServerIntegrationTests;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServerTokenEndpointTests extends AuthServerIntegrationTests {

  @Test
  void clientCredentialsGrantReturnsAccessToken() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", "read");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("demo-client", "demo-secret")
            .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("access_token").asText()).isNotBlank();
    assertThat(body.path("token_type").asText()).isEqualToIgnoringCase("Bearer");
    assertThat(body.path("expires_in").asLong()).isGreaterThan(0);
    assertThat(body.path("scope").asText()).contains("read");
  }

  @Test
  void invalidClientSecretIsRejected() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("demo-client", "wrong-secret")
            .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    String responseBody = response.getBody();
    if (responseBody != null && !responseBody.isBlank()) {
      assertThat(responseBody).contains("invalid_client");
    }
  }

  @Test
  void clientCredentialsWithInvalidScopeIsRejected() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", "admin");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("demo-client", "demo-secret")
            .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("error").asText()).isEqualTo("invalid_scope");
  }

  @Test
  void unsupportedGrantTypeIsRejected() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("username", "demo-user");
    form.add("password", "demo-password");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("demo-client", "demo-secret")
            .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("error").asText()).isEqualTo("unsupported_grant_type");
  }

  @Test
  void missingGrantTypeIsRejected() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("scope", "read");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("demo-client", "demo-secret")
            .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("error").asText()).isEqualTo("invalid_request");
  }

  @Test
  void invalidRefreshTokenIsRejected() throws Exception {
    ResponseEntity<String> response = exchangeRefreshToken("not-a-valid-refresh-token", "read", true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("error").asText()).isEqualTo("invalid_grant");
  }

  @Test
  void missingRefreshTokenParameterIsRejected() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "refresh_token");
    form.add("scope", "read");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("demo-client", "demo-secret")
            .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("error").asText()).isEqualTo("invalid_request");
  }

  @Test
  void refreshTokenWithInvalidClientCredentialsIsRejected() throws Exception {
    ResponseEntity<String> authorizationCodeTokenResponse = exchangeAuthorizationCodeForTokens("read");
    assertThat(authorizationCodeTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode initialTokenBody = objectMapper.readTree(authorizationCodeTokenResponse.getBody());
    String refreshToken = initialTokenBody.path("refresh_token").asText();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "refresh_token");
    form.add("refresh_token", refreshToken);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("demo-client", "wrong-secret")
            .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    String responseBody = response.getBody();
    if (responseBody != null && !responseBody.isBlank()) {
      assertThat(responseBody).contains("invalid_client");
    }
  }
}
