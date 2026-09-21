package io.github.edmaputra.enhauthserv.claims;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import io.github.edmaputra.enhauthserv.integration.AuthServerIntegrationTests;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OidcUserInfoEndpointTests extends AuthServerIntegrationTests {

  @Test
  void userInfoReturnsStandardAndCustomClaimsForValidBearerToken() throws Exception {
    ResponseEntity<String> tokenResponse =
        exchangeAuthorizationCodeForTokens("openid profile email read");
    assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode tokenBody = objectMapper.readTree(tokenResponse.getBody());
    String accessToken = tokenBody.path("access_token").asText();
    assertThat(accessToken).isNotBlank();

    ResponseEntity<String> userInfoResponse = fetchUserInfo(accessToken);
    assertThat(userInfoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode userInfoBody = objectMapper.readTree(userInfoResponse.getBody());
    assertThat(userInfoBody.path("sub").asText()).isEqualTo("demo-user");
    assertThat(userInfoBody.path("preferred_username").asText()).isEqualTo("demo-user");
    assertThat(userInfoBody.path("name").asText()).isEqualTo("Demo User");
    assertThat(userInfoBody.path("email").asText()).isEqualTo("demo-user@example.com");
    assertThat(userInfoBody.path("email_verified").asBoolean()).isTrue();
    assertThat(userInfoBody.path("department").asText()).isEqualTo("engineering");
    assertThat(userInfoBody.path("tenant").asText()).isEqualTo("demo");
  }

  @Test
  void userInfoRejectsInvalidBearerToken() {
    ResponseEntity<String> userInfoResponse = fetchUserInfo("not.a.valid.token");

    assertThat(userInfoResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    String authenticateHeader = userInfoResponse.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
    if (authenticateHeader != null) {
      assertThat(authenticateHeader).contains("invalid_token");
    }
  }
}