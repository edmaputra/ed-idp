package io.github.edmaputra.edidp.e2e.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import io.github.edmaputra.edidp.e2e.BaseE2ETest;
import io.github.edmaputra.edidp.e2e.pages.OAuthConsentPage;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * End-to-end browser tests for the interactive OAuth 2.1 User Consent screen,
 * verifying permission inspection, selective scope toggling, authorization approval,
 * and user consent denial.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@DisplayName("OAuth 2.1 Interactive Consent Flow E2E")
class OAuthConsentFlowE2EIT extends BaseE2ETest {

  private static final String CLIENT_ID = "demo-client";
  private static final String CLIENT_SECRET = "demo-secret";
  private static final String REDIRECT_URI = "http://127.0.0.1:9000/login/oauth2/code/demo-client";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should display consent screen and redirect with access_denied when user denies")
  void shouldRedirectWithAccessDeniedWhenUserDenies() {
    String state = "deny-state-" + UUID.randomUUID();
    AtomicReference<String> redirectRef = new AtomicReference<>();

    page().onResponse(response -> {
      String location = response.headerValue("location");
      if (location != null && location.contains("/login/oauth2/code/demo-client")) {
        redirectRef.set(location);
      }
    });

    OAuthConsentPage consentPage = new OAuthConsentPage(page(), baseUrl());
    consentPage.navigate(CLIENT_ID, REDIRECT_URI, "openid profile email", state);

    // Wait for login form or consent screen to be ready
    page().waitForSelector("#username, h1:has-text('Authorization Consent')");
    if (page().locator("#username").isVisible()) {
      page().locator("#username").fill("demo-user@example.com");
      page().locator("#password").fill("demo-password");
      page().locator("button[type='submit']").click();
    }

    page().waitForSelector("h1:has-text('Authorization Consent')");
    assertThat(consentPage.isDisplayed()).isTrue();
    assertThat(consentPage.getClientName()).contains("Demo Client");
    assertThat(consentPage.getPrincipalName()).contains("demo-user@example.com");
    assertThat(consentPage.isScopeChecked("openid")).isTrue();
    assertThat(consentPage.isScopeChecked("profile")).isTrue();
    assertThat(consentPage.isScopeChecked("email")).isTrue();

    // User denies consent
    consentPage.deny();

    page().waitForCondition(() -> redirectRef.get() != null);
    String redirectLocation = redirectRef.get();
    assertThat(redirectLocation).isNotNull();

    Map<String, String> params = extractQueryParams(redirectLocation);
    assertThat(params.get("error")).isEqualTo("access_denied");
    assertThat(params.get("state")).isEqualTo(state);
  }

  @Test
  @DisplayName("Should approve consent with selective scopes, issue code, and exchange for tokens")
  void shouldApproveSelectiveScopesAndIssueTokens() throws Exception {
    String state = "approve-state-" + UUID.randomUUID();
    AtomicReference<String> codeRedirectRef = new AtomicReference<>();

    page().onResponse(response -> {
      String location = response.headerValue("location");
      if (location != null && location.contains("code=")) {
        codeRedirectRef.set(location);
      }
    });

    OAuthConsentPage consentPage = new OAuthConsentPage(page(), baseUrl());
    consentPage.navigate(CLIENT_ID, REDIRECT_URI, "openid profile email", state);

    // Wait for login form or consent screen to be ready
    page().waitForSelector("#username, h1:has-text('Authorization Consent')");
    if (page().locator("#username").isVisible()) {
      page().locator("#username").fill("demo-user@example.com");
      page().locator("#password").fill("demo-password");
      page().locator("button[type='submit']").click();
    }

    page().waitForSelector("h1:has-text('Authorization Consent')");
    assertThat(consentPage.isDisplayed()).isTrue();

    // Selectively uncheck 'email' scope
    consentPage.setScope("email", false);
    assertThat(consentPage.isScopeChecked("email")).isFalse();
    assertThat(consentPage.isScopeChecked("openid")).isTrue();
    assertThat(consentPage.isScopeChecked("profile")).isTrue();

    // User approves selected scopes
    consentPage.approve();

    page().waitForCondition(() -> codeRedirectRef.get() != null);
    String redirectLocation = codeRedirectRef.get();
    assertThat(redirectLocation).isNotNull();

    Map<String, String> params = extractQueryParams(redirectLocation);
    assertThat(params.get("state")).isEqualTo(state);
    String code = params.get("code");
    assertThat(code).isNotBlank();

    // Exchange code for tokens at /oauth2/token using demo-client credentials
    FormData form = FormData.create()
        .set("grant_type", "authorization_code")
        .set("code", code)
        .set("redirect_uri", REDIRECT_URI);

    String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
        (CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));

    APIResponse tokenResponse = page().request().post(url("/oauth2/token"),
        RequestOptions.create()
            .setHeader("Content-Type", "application/x-www-form-urlencoded")
            .setHeader("Authorization", basicAuth)
            .setForm(form));

    assertThat(tokenResponse.status()).isEqualTo(200);

    JsonNode tokenJson = objectMapper.readTree(tokenResponse.text());
    assertThat(tokenJson.hasNonNull("access_token")).isTrue();
    assertThat(tokenJson.hasNonNull("id_token")).isTrue();

    String grantedScope = tokenJson.path("scope").asText();
    assertThat(grantedScope).contains("openid");
    assertThat(grantedScope).contains("profile");
    assertThat(grantedScope).doesNotContain("email");
  }

  private Map<String, String> extractQueryParams(String uriString) {
    Map<String, String> params = new HashMap<>();
    if (uriString == null || !uriString.contains("?")) {
      return params;
    }
    String query = uriString.substring(uriString.indexOf('?') + 1);
    Arrays.stream(query.split("&"))
        .map(pair -> pair.split("=", 2))
        .forEach(entry -> {
          String key = URLDecoder.decode(entry[0], StandardCharsets.UTF_8);
          String value = entry.length > 1 ? URLDecoder.decode(entry[1], StandardCharsets.UTF_8) : "";
          params.put(key, value);
        });
    return params;
  }
}
