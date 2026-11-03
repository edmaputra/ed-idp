package io.github.edmaputra.edidp.e2e.protocol;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import io.github.edmaputra.edidp.e2e.BaseE2ETest;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end browser and protocol test verifying the OAuth 2.1 Authorization Code Flow
 * with PKCE (Proof Key for Code Exchange) using a public client.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@DisplayName("OAuth 2.1 Authorization Code Flow with PKCE E2E")
class OAuth2PkceAuthorizationFlowE2EIT extends BaseE2ETest {

  private static final String PKCE_CLIENT_ID = "pkce-public-client";
  private static final String REDIRECT_URI = "http://127.0.0.1:9000/login/oauth2/code/pkce-public-client";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should execute end-to-end PKCE authorization code flow and exchange for tokens")
  void shouldAuthorizeAndExchangeTokensWithPkce() throws Exception {
    String codeVerifier = "e2e-verifier-" + UUID.randomUUID() + "-sample-pkce-string-0123456789";
    String codeChallenge = toS256CodeChallenge(codeVerifier);
    String expectedState = "pkce-e2e-state-" + UUID.randomUUID();

    String authorizeUrl = url("/oauth2/authorize"
        + "?response_type=code"
        + "&client_id=" + URLEncoder.encode(PKCE_CLIENT_ID, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
        + "&scope=" + URLEncoder.encode("openid profile email", StandardCharsets.UTF_8)
        + "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8)
        + "&code_challenge_method=S256"
        + "&state=" + URLEncoder.encode(expectedState, StandardCharsets.UTF_8));

    // 1. Navigate browser to authorization endpoint
    open(authorizeUrl);

    // 2. Perform interactive login as demo user
    $("#username").setValue("demo-user@example.com");
    $("#password").setValue("demo-password");
    $("button[type='submit']").click();

    // 3. Handle interactive consent page if presented
    if ($("button.btn-approve").is(visible)) {
      $("button.btn-approve").click();
    }

    // 4. Validate that callback redirect was emitted with authorization code and state
    Selenide.Wait().until(driver -> driver.getCurrentUrl().contains("code="));
    String callbackLocation = WebDriverRunner.url();
    assertThat(callbackLocation).isNotBlank();

    URI callbackUri = URI.create(callbackLocation);
    Map<String, String> queryParams = extractQueryParams(callbackUri);

    assertThat(queryParams.get("state")).isEqualTo(expectedState);
    String authorizationCode = queryParams.get("code");
    assertThat(authorizationCode).isNotBlank();

    // 5. Exchange authorization code and code_verifier for OAuth tokens at /oauth2/token
    String formBody = String.format(
        "grant_type=authorization_code&client_id=%s&code=%s&redirect_uri=%s&code_verifier=%s",
        URLEncoder.encode(PKCE_CLIENT_ID, StandardCharsets.UTF_8),
        URLEncoder.encode(authorizationCode, StandardCharsets.UTF_8),
        URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8),
        URLEncoder.encode(codeVerifier, StandardCharsets.UTF_8)
    );

    HttpClient httpClient = HttpClient.newHttpClient();
    HttpRequest tokenRequest = HttpRequest.newBuilder()
        .uri(URI.create(url("/oauth2/token")))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(formBody))
        .build();

    HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
    assertThat(tokenResponse.statusCode()).isEqualTo(200);

    JsonNode tokenBody = objectMapper.readTree(tokenResponse.body());
    assertThat(tokenBody.path("access_token").asText()).isNotBlank();
    assertThat(tokenBody.path("token_type").asText()).isEqualToIgnoringCase("Bearer");
    assertThat(tokenBody.path("expires_in").asLong()).isGreaterThan(0);
    assertThat(tokenBody.path("scope").asText()).contains("openid");
    assertThat(tokenBody.path("id_token").asText()).isNotBlank();
  }

  private static String toS256CodeChallenge(String codeVerifier) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
  }

  private static Map<String, String> extractQueryParams(URI uri) {
    Map<String, String> params = new HashMap<>();
    String rawQuery = uri.getRawQuery();
    if (rawQuery == null || rawQuery.isBlank()) {
      return params;
    }
    Arrays.stream(rawQuery.split("&")).forEach(pair -> {
      int idx = pair.indexOf('=');
      if (idx > 0) {
        String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
        String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
        params.put(key, val);
      }
    });
    return params;
  }
}
