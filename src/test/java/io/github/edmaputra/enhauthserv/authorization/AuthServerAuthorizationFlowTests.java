package io.github.edmaputra.enhauthserv.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.github.edmaputra.enhauthserv.integration.AuthServerIntegrationTests;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServerAuthorizationFlowTests extends AuthServerIntegrationTests {

  @Test
  void authorizationCodeGrantReturnsAccessToken() throws Exception {
    ResponseEntity<String> tokenResponse = exchangeAuthorizationCodeForTokens("openid read");

    assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode tokenBody = objectMapper.readTree(tokenResponse.getBody());
    assertThat(tokenBody.path("access_token").asText()).isNotBlank();
    assertThat(tokenBody.path("refresh_token").asText()).isNotBlank();
    assertThat(tokenBody.path("token_type").asText()).isEqualToIgnoringCase("Bearer");
    assertThat(tokenBody.path("expires_in").asLong()).isGreaterThan(0);
    assertThat(tokenBody.path("scope").asText()).contains("openid");
    assertThat(tokenBody.path("scope").asText()).contains("read");
  }

  @Test
  void refreshTokenGrantReturnsNewAccessTokenAndRotatesRefreshToken() throws Exception {
    ResponseEntity<String> authorizationCodeTokenResponse = exchangeAuthorizationCodeForTokens("read");
    assertThat(authorizationCodeTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode initialTokenBody = objectMapper.readTree(authorizationCodeTokenResponse.getBody());
    String firstRefreshToken = initialTokenBody.path("refresh_token").asText();

    ResponseEntity<String> refreshResponse = exchangeRefreshToken(firstRefreshToken, "read", true);
    assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode refreshBody = objectMapper.readTree(refreshResponse.getBody());
    String rotatedRefreshToken = refreshBody.path("refresh_token").asText();

    assertThat(refreshBody.path("access_token").asText()).isNotBlank();
    assertThat(rotatedRefreshToken).isNotBlank();
    assertThat(rotatedRefreshToken).isNotEqualTo(firstRefreshToken);
    assertThat(refreshBody.path("token_type").asText()).isEqualToIgnoringCase("Bearer");

    ResponseEntity<String> oldTokenReuseResponse = exchangeRefreshToken(firstRefreshToken, "read", true);
    assertThat(oldTokenReuseResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode oldTokenReuseBody = objectMapper.readTree(oldTokenReuseResponse.getBody());
    assertThat(oldTokenReuseBody.path("error").asText()).isEqualTo("invalid_grant");
  }
}
