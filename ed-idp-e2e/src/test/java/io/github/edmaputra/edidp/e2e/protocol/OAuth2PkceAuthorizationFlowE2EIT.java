package io.github.edmaputra.edidp.e2e.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import io.github.edmaputra.edidp.e2e.BaseE2ETest;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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

    AtomicReference<String> callbackLocationRef = new AtomicReference<>();
    page().onResponse(response -> {
      String location = response.headerValue("location");
      if (location != null && location.contains("/login/oauth2/code/")) {
        callbackLocationRef.set(location);
      }
    });

    String authorizeUrl = url("/oauth2/authorize"
        + "?response_type=code"
        + "&client_id=" + URLEncoder.encode(PKCE_CLIENT_ID, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
        + "&scope=" + URLEncoder.encode("openid profile email", StandardCharsets.UTF_8)
        + "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8)
        + "&code_challenge_method=S256"
        + "&state=" + URLEncoder.encode(expectedState, StandardCharsets.UTF_8));

    // 1. Navigate browser to authorization endpoint
    page().navigate(authorizeUrl);

    // 2. Perform interactive login as demo user
    page().locator("#username").fill("demo-user@example.com");
    page().locator("#password").fill("demo-password");
    page().locator("button[type='submit']").click();

    // 3. Handle interactive consent page if presented
    if (page().locator("button.btn-approve").isVisible()) {
      page().locator("button.btn-approve").click();
    }

    // 4. Validate that callback redirect was emitted with authorization code and state
    page().waitForCondition(() -> callbackLocationRef.get() != null);
    String callbackLocation = callbackLocationRef.get();
    assertThat(callbackLocation).isNotBlank();

    URI callbackUri = URI.create(callbackLocation);
    Map<String, String> queryParams = extractQueryParams(callbackUri);

    assertThat(queryParams.get("state")).isEqualTo(expectedState);
    String authorizationCode = queryParams.get("code");
    assertThat(authorizationCode).isNotBlank();

    // 5. Exchange authorization code and code_verifier for OAuth tokens at /oauth2/token
    FormData form = FormData.create()
        .set("grant_type", "authorization_code")
        .set("client_id", PKCE_CLIENT_ID)
        .set("code", authorizationCode)
        .set("redirect_uri", REDIRECT_URI)
        .set("code_verifier", codeVerifier);

    APIResponse tokenResponse = page().request().post(url("/oauth2/token"),
        RequestOptions.create()
            .setHeader("Content-Type", "application/x-www-form-urlencoded")
            .setForm(form)
    );

    assertThat(tokenResponse.status()).isEqualTo(200);

    JsonNode tokenBody = objectMapper.readTree(tokenResponse.text());
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
