package io.github.edmaputra.edidp.oauth.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import io.github.edmaputra.edidp.integration.AuthServerIntegrationTests;
import io.github.edmaputra.iam.domain.auth.mfa.TotpGenerator;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * End-to-end integration tests verifying MFA challenge interception, TOTP verification,
 * and OIDC "amr" claim enrichment.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MfaLoginIntegrationTests extends AuthServerIntegrationTests {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private UserMfaRepository userMfaRepository;

  @AfterEach
  void cleanUp() {
    userRepository.findByEmail("demo-user@example.com")
        .ifPresent(u -> userMfaRepository.deleteByUserId(u.getId()));
  }

  @Test
  void loginInterceptsMfaChallengeAndEnrichesAmrClaimOnSuccess() throws Exception {
    User user = userRepository.findByEmail("demo-user@example.com").orElseThrow();
    String secret = TotpGenerator.generateSecret();
    String rawBackupCode = "BACKUP99";
    UserMfa mfa = UserMfa.create(
        user.getId(),
        secret,
        new java.util.ArrayList<>(List.of(TotpGenerator.hashBackupCode(rawBackupCode)))
    );
    mfa.activate();
    userMfaRepository.save(mfa);

    CookieManager cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

    HttpClient client = HttpClient.newBuilder()
        .cookieHandler(cookieManager)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    // 1. Start OAuth2 authorization flow
    String redirectUri = "http://127.0.0.1:9000/login/oauth2/code/demo-client";
    String authorizePath = "/oauth2/authorize"
        + "?response_type=code"
        + "&client_id=" + URLEncoder.encode("demo-client", StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&scope=" + URLEncoder.encode("openid profile", StandardCharsets.UTF_8)
        + "&state=test-mfa-state";

    HttpResponse<String> authorizeResponse = client.send(
        HttpRequest.newBuilder().uri(appUri(authorizePath)).GET().build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(authorizeResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
    String loginLocation = authorizeResponse.headers().firstValue("location").orElseThrow();

    // 2. Fetch login page
    HttpResponse<String> loginPageResponse = client.send(
        HttpRequest.newBuilder().uri(appUri(loginLocation)).GET().build(),
        HttpResponse.BodyHandlers.ofString());

    String csrfToken = extractCsrfToken(loginPageResponse.body());

    // 3. Submit credentials -> should redirect to /login/mfa-challenge
    String loginRequestBody = "username=" + urlEncode("demo-user@example.com")
        + "&password=" + urlEncode("demo-password")
        + "&_csrf=" + urlEncode(csrfToken);

    HttpResponse<String> loginSubmitResponse = client.send(
        HttpRequest.newBuilder()
            .uri(appUri("/login"))
            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(loginRequestBody))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(loginSubmitResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
    String mfaRedirect = loginSubmitResponse.headers().firstValue("location").orElseThrow();
    assertThat(mfaRedirect).contains("/login/mfa-challenge");

    // 4. View challenge page
    HttpResponse<String> challengePageResponse = client.send(
        HttpRequest.newBuilder().uri(appUri(mfaRedirect)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertThat(challengePageResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(challengePageResponse.body()).contains("Two-Factor Verification");

    String challengeCsrf = extractCsrfToken(challengePageResponse.body());

    // 5. Submit invalid code -> redirects with error
    String invalidChallengeBody = "code=000000&_csrf=" + urlEncode(challengeCsrf);
    HttpResponse<String> invalidSubmitResponse = client.send(
        HttpRequest.newBuilder()
            .uri(appUri("/login/mfa-challenge"))
            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(invalidChallengeBody))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(invalidSubmitResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
    assertThat(invalidSubmitResponse.headers().firstValue("location").orElseThrow())
        .contains("/login/mfa-challenge?error=invalid_code");

    // 6. Submit valid TOTP code
    String validTotp = TotpGenerator.generateCurrentTotp(secret);
    String validChallengeBody = "code=" + urlEncode(validTotp) + "&_csrf=" + urlEncode(challengeCsrf);

    HttpResponse<String> validSubmitResponse = client.send(
        HttpRequest.newBuilder()
            .uri(appUri("/login/mfa-challenge"))
            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(validChallengeBody))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(validSubmitResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
    String nextStepLocation = validSubmitResponse.headers().firstValue("location").orElseThrow();

    // 7. Follow redirects to callback
    String callbackLocation = null;
    for (int i = 0; i < 5; i++) {
      HttpResponse<String> stepResponse = client.send(
          HttpRequest.newBuilder().uri(appUri(nextStepLocation)).GET().build(),
          HttpResponse.BodyHandlers.ofString());

      assertThat(stepResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
      String stepLoc = stepResponse.headers().firstValue("location").orElseThrow();
      if (stepLoc.startsWith(redirectUri)) {
        callbackLocation = stepLoc;
        break;
      }
      nextStepLocation = stepLoc;
    }

    assertThat(callbackLocation).isNotNull();
    String authorizationCode = extractQueryParam(URI.create(callbackLocation), "code");

    // 8. Exchange code for tokens
    HttpHeaders tokenHeaders = new HttpHeaders();
    tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
    tokenForm.add("grant_type", "authorization_code");
    tokenForm.add("code", authorizationCode);
    tokenForm.add("redirect_uri", redirectUri);

    HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(tokenForm, tokenHeaders);
    ResponseEntity<String> tokenResponse = restTemplate
        .withBasicAuth("demo-client", "demo-secret")
        .postForEntity("/oauth2/token", tokenRequest, String.class);

    assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode tokenJson = objectMapper.readTree(tokenResponse.getBody());
    String idTokenString = tokenJson.path("id_token").asText();
    assertThat(idTokenString).isNotBlank();

    // 9. Verify "amr" claim in ID token
    SignedJWT parsedJwt = SignedJWT.parse(idTokenString);
    List<String> amrClaims = parsedJwt.getJWTClaimsSet().getStringListClaim("amr");
    assertThat(amrClaims).containsExactly("pwd", "mfa", "totp");
  }

  @Test
  void loginWithoutMfaSetsAmrClaimToPwdOnly() throws Exception {
    AuthorizationCodeFlowResult result = authenticateDemoUserAndGetIdToken("openid profile");
    SignedJWT parsedJwt = SignedJWT.parse(result.idToken());
    List<String> amrClaims = parsedJwt.getJWTClaimsSet().getStringListClaim("amr");
    assertThat(amrClaims).containsExactly("pwd");
  }
}
