package io.github.edmaputra.edidp.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.github.edmaputra.edidp.integration.AuthServerIntegrationTests;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServerPkceFlowTests extends AuthServerIntegrationTests {

  @Test
  void authorizationCodeWithPkceReturnsAccessTokenForPublicClient() throws Exception {
    String codeVerifier = "u4xwWfYjYvV6NDjIhKXqYJg8kSM2nB9A2Q9cR7uL6tQ";
    String codeChallenge = toS256CodeChallenge(codeVerifier);

    ResponseEntity<String> tokenResponse = exchangeAuthorizationCodeForTokensWithPkce(
        "openid read",
        "S256",
        codeChallenge,
        codeVerifier);

    assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode tokenBody = objectMapper.readTree(tokenResponse.getBody());
    assertThat(tokenBody.path("access_token").asText()).isNotBlank();
    assertThat(tokenBody.path("token_type").asText()).isEqualToIgnoringCase("Bearer");
    assertThat(tokenBody.path("scope").asText()).contains("openid");
    assertThat(tokenBody.path("scope").asText()).contains("read");
  }

  @Test
  void authorizationRequestWithoutCodeChallengeIsRejectedForPkceClient() throws Exception {
    String redirectUri = "http://127.0.0.1:9000/login/oauth2/code/pkce-public-client";
    String authorizePath = "/oauth2/authorize"
        + "?response_type=code"
        + "&client_id=pkce-public-client"
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&scope=" + URLEncoder.encode("openid", StandardCharsets.UTF_8)
        + "&state=pkce-state";

    HttpResponse<String> authorizeResponse = java.net.http.HttpClient.newBuilder()
        .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
        .build()
        .send(
            HttpRequest.newBuilder().uri(appUri(authorizePath)).GET().build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(authorizeResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
    String location = authorizeResponse.headers().firstValue("location").orElseThrow();
    assertThat(location).startsWith(redirectUri);
    assertThat(location).contains("error=invalid_request");
    assertThat(location).doesNotContain("code=");
  }

  @Test
  void authorizationCodeWithPkceInvalidCodeVerifierIsRejected() throws Exception {
    String codeVerifier = "l8W2WMLV3P2nMBP2CyA0JRFQ6y8A7USjF0z1Xk6mP9D";
    String codeChallenge = toS256CodeChallenge(codeVerifier);
    String mismatchedVerifier = "y7V3JfLk9Lkq8R4mQaB1nPtD2wLs4sXp8nQm2zTd5Hy";

    ResponseEntity<String> tokenResponse = exchangeAuthorizationCodeForTokensWithPkce(
        "openid",
        "S256",
        codeChallenge,
        mismatchedVerifier);

    assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode tokenBody = objectMapper.readTree(tokenResponse.getBody());
    assertThat(tokenBody.path("error").asText()).isIn("invalid_grant", "invalid_request");
  }

  private static String toS256CodeChallenge(String codeVerifier) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
  }
}
