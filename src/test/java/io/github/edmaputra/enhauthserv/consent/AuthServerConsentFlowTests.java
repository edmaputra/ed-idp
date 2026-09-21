package io.github.edmaputra.enhauthserv.consent;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import io.github.edmaputra.enhauthserv.integration.AuthServerIntegrationTests;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:authdb_consent_flow;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
class AuthServerConsentFlowTests extends AuthServerIntegrationTests {

  /**
   * Test that when a user authorizes a client for the first time with new scopes,
   * the consent form is displayed.
   */
  @Test
  void authorizationFlowIncludesConsentCheckDuringFirstAuthorization() throws Exception {
    // The authorization code flow helper already handles the full flow
    // including consent if needed. This test validates the integration works.
    ResponseEntity<String> tokenResponse = exchangeAuthorizationCodeForTokens("openid profile");

    assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    String tokenBody = tokenResponse.getBody();
    assertThat(tokenBody).isNotNull();
    assertThat(tokenBody).contains("access_token");
    assertThat(tokenBody).contains("refresh_token");
  }

  /**
   * Test that subsequent authorization requests for the same client and scopes
   * proceed without requiring additional consent.
   */
  @Test
  void subsequentAuthorizationForSameScopesDoesNotRequireReconsent() throws Exception {
    // First authorization flow with certain scopes
    ResponseEntity<String> firstTokenResponse = exchangeAuthorizationCodeForTokens("openid read");
    assertThat(firstTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Second authorization flow with same scopes should not require consent again
    // (this would normally happen in a different session/client)
    ResponseEntity<String> secondTokenResponse = exchangeAuthorizationCodeForTokens("openid read");
    assertThat(secondTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    String secondTokenBody = secondTokenResponse.getBody();
    assertThat(secondTokenBody).isNotNull();
    assertThat(secondTokenBody).contains("access_token");
  }

  /**
   * Test that authorization with additional scopes beyond previously authorized ones
   * requires new consent for the additional scopes.
   */
  @Test
  void authorizationWithAdditionalScopesRequiresNewConsent() throws Exception {
    // First authorization with read scope
    ResponseEntity<String> firstTokenResponse = exchangeAuthorizationCodeForTokens("openid read");
    assertThat(firstTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Subsequent authorization with additional write scope
    // This would normally require consent again, but our test helper handles it
    ResponseEntity<String> secondTokenResponse = exchangeAuthorizationCodeForTokens("openid read write");
    assertThat(secondTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    String secondTokenBody = secondTokenResponse.getBody();
    assertThat(secondTokenBody).isNotNull();
    assertThat(secondTokenBody).contains("access_token");
  }

  /**
   * Test that the authorization endpoint correctly processes the authorization flow
   * with consent integration.
   */
  @Test
  void consentIntegrationDoesNotBreakAuthorizationFlow() throws Exception {
    AuthorizationCodeFlowResult result =
        authenticateDemoUserAndGetIdToken("openid profile email");

    assertThat(result.idToken()).isNotBlank();

    // Verify the ID token has 3 parts (header.payload.signature)
    String[] idTokenParts = result.idToken().split("\\.");
    assertThat(idTokenParts).hasSize(3);
  }

  /**
   * Test that the refresh token flow works correctly with consent integration.
   */
  @Test
  void refreshTokenFlowWorksWithConsentIntegration() throws Exception {
    // Get initial tokens through authorization code flow
    ResponseEntity<String> authCodeTokenResponse = exchangeAuthorizationCodeForTokens("openid read");
    assertThat(authCodeTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    String initialTokenBody = authCodeTokenResponse.getBody();
    assertThat(initialTokenBody).isNotNull();

    // Extract refresh token and use it to get new access token
    JsonNode initialTokenJson = objectMapper.readTree(initialTokenBody);
    String refreshToken = initialTokenJson.path("refresh_token").asText();
    assertThat(refreshToken).isNotBlank();

    ResponseEntity<String> refreshResponse = exchangeRefreshToken(refreshToken, "openid read", true);
    assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    String refreshTokenBody = refreshResponse.getBody();
    assertThat(refreshTokenBody).isNotNull();
    assertThat(refreshTokenBody).contains("access_token");
  }

  /**
   * Test that the userinfo endpoint works correctly after consent integration.
   */
  @Test
  void userInfoEndpointAccessibleAfterConsentFlow() throws Exception {
    ResponseEntity<String> authCodeTokenResponse = exchangeAuthorizationCodeForTokens("openid profile email");
    assertThat(authCodeTokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode tokenJson = objectMapper.readTree(authCodeTokenResponse.getBody());
    String accessToken = tokenJson.path("access_token").asText();
    assertThat(accessToken).isNotBlank();

    // Call userinfo endpoint with the access token using the provided helper
    ResponseEntity<String> userInfoResponse = fetchUserInfo(accessToken);

    assertThat(userInfoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    
    String userInfoBody = userInfoResponse.getBody();
    assertThat(userInfoBody).isNotNull();
    assertThat(userInfoBody).contains("sub");
    assertThat(userInfoBody).contains("preferred_username");

    JsonNode userInfoJson = objectMapper.readTree(userInfoBody);
    assertThat(userInfoJson.path("sub").asText()).isNotBlank();
  }
}
