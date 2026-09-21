package io.github.edmaputra.enhauthserv.integration;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@AutoConfigureTestRestTemplate
public abstract class AuthServerIntegrationTests {

  private static final Pattern CSRF_INPUT_PATTERN = Pattern.compile(
      "name=\"_csrf\"\\s+type=\"hidden\"\\s+value=\"([^\"]+)\"");

  @Autowired
  protected TestRestTemplate restTemplate;

  @Autowired
  protected ObjectMapper objectMapper;

  @Value("${app.issuer-uri}")
  protected String issuerUri;

  @LocalServerPort
  protected int port;

  public static final class AuthorizationCodeFlowResult {

    private final HttpClient client;

    private final String idToken;

    private AuthorizationCodeFlowResult(HttpClient client, String idToken) {
      this.client = client;
      this.idToken = idToken;
    }

    public HttpClient client() {
      return this.client;
    }

    public String idToken() {
      return this.idToken;
    }
  }

  protected URI appUri(String pathOrAbsoluteUrl) {
    if (pathOrAbsoluteUrl.startsWith("http://") || pathOrAbsoluteUrl.startsWith("https://")) {
      return URI.create(pathOrAbsoluteUrl);
    }
    String normalizedPath = pathOrAbsoluteUrl.startsWith("/")
        ? pathOrAbsoluteUrl
        : "/" + pathOrAbsoluteUrl;
    return URI.create("http://localhost:" + port + normalizedPath);
  }

  protected String extractCsrfToken(String html) {
    Matcher matcher = CSRF_INPUT_PATTERN.matcher(html);
    if (!matcher.find()) {
      throw new IllegalStateException("Unable to extract CSRF token from login page");
    }
    return matcher.group(1);
  }

  protected String extractQueryParam(URI uri, String key) {
    String query = uri.getQuery();
    if (query == null || query.isBlank()) {
      throw new IllegalStateException("Expected query parameters in URI: " + uri);
    }

    for (String pair : query.split("&")) {
      String[] split = pair.split("=", 2);
      String currentKey = java.net.URLDecoder.decode(split[0], StandardCharsets.UTF_8);
      String currentValue = split.length > 1
          ? java.net.URLDecoder.decode(split[1], StandardCharsets.UTF_8)
          : "";
      if (key.equals(currentKey)) {
        return currentValue;
      }
    }

    throw new IllegalStateException("Missing query parameter '" + key + "' in URI: " + uri);
  }

  protected String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  protected ResponseEntity<String> exchangeAuthorizationCodeForTokens(String scope) throws Exception {
    return exchangeAuthorizationCodeForTokens(
        "demo-client",
        "demo-secret",
        "http://127.0.0.1:9000/login/oauth2/code/demo-client",
        scope,
        null,
        null,
        null,
        true);
  }

  protected AuthorizationCodeFlowResult authenticateDemoUserAndGetIdToken(String scope) throws Exception {
    CookieManager cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

    HttpClient client = HttpClient.newBuilder()
      .cookieHandler(cookieManager)
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();

    String authorizePath = "/oauth2/authorize"
        + "?response_type=code"
        + "&client_id=" + URLEncoder.encode("demo-client", StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode("http://127.0.0.1:9000/login/oauth2/code/demo-client",
            StandardCharsets.UTF_8)
        + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8)
        + "&state=test-state";

    HttpResponse<String> authorizeResponse = client.send(
        HttpRequest.newBuilder()
            .uri(appUri(authorizePath))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(authorizeResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
    String loginLocation = authorizeResponse.headers().firstValue("location").orElseThrow();
    assertThat(loginLocation).contains("/login");

    HttpResponse<String> loginPageResponse = client.send(
        HttpRequest.newBuilder()
            .uri(appUri(loginLocation))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(loginPageResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
    String csrfToken = extractCsrfToken(loginPageResponse.body());

    String loginRequestBody = "username=" + urlEncode("demo-user")
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

    String callbackLocation = null;
    String nextLocation = loginSubmitResponse.headers().firstValue("location").orElseThrow();
    for (int i = 0; i < 5; i++) {
      HttpResponse<String> authorizationStepResponse = client.send(
          HttpRequest.newBuilder()
              .uri(appUri(nextLocation))
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());

      assertThat(authorizationStepResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());

      String stepLocation = authorizationStepResponse.headers().firstValue("location").orElseThrow();
      if (stepLocation.startsWith("http://127.0.0.1:9000/login/oauth2/code/demo-client")) {
        callbackLocation = stepLocation;
        break;
      }
      nextLocation = stepLocation;
    }

    assertThat(callbackLocation).isNotNull();

    URI callbackUri = URI.create(callbackLocation);
    assertThat(callbackUri.getQuery()).contains("code=");

    String authorizationCode = extractQueryParam(callbackUri, "code");
    assertThat(authorizationCode).isNotBlank();

    HttpHeaders tokenHeaders = new HttpHeaders();
    tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
    tokenForm.add("grant_type", "authorization_code");
    tokenForm.add("code", authorizationCode);
    tokenForm.add("redirect_uri", "http://127.0.0.1:9000/login/oauth2/code/demo-client");

    HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(tokenForm, tokenHeaders);
    ResponseEntity<String> tokenResponse = restTemplate
      .withBasicAuth("demo-client", "demo-secret")
      .postForEntity("/oauth2/token", tokenRequest, String.class);
    assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode tokenBody = objectMapper.readTree(tokenResponse.getBody());
    String idToken = tokenBody.path("id_token").asText();
    assertThat(idToken).isNotBlank();

    return new AuthorizationCodeFlowResult(client, idToken);
    }

  protected ResponseEntity<String> exchangeAuthorizationCodeForTokensWithPkce(
      String scope,
      String codeChallengeMethod,
      String codeChallenge,
      String codeVerifier) throws Exception {
    return exchangeAuthorizationCodeForTokens(
        "pkce-public-client",
        null,
        "http://127.0.0.1:9000/login/oauth2/code/pkce-public-client",
        scope,
        codeChallengeMethod,
        codeChallenge,
        codeVerifier,
        false);
  }

  private ResponseEntity<String> exchangeAuthorizationCodeForTokens(
      String clientId,
      String clientSecret,
      String redirectUri,
      String scope,
      String codeChallengeMethod,
      String codeChallenge,
      String codeVerifier,
      boolean authenticateClient) throws Exception {
    CookieManager cookieManager = new CookieManager();
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

    HttpClient client = HttpClient.newBuilder()
        .cookieHandler(cookieManager)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    String authorizePath = "/oauth2/authorize"
        + "?response_type=code"
        + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8)
        + "&state=test-state";

    if (codeChallenge != null && !codeChallenge.isBlank()) {
      authorizePath += "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8);
      authorizePath += "&code_challenge_method="
          + URLEncoder.encode(codeChallengeMethod == null ? "S256" : codeChallengeMethod,
              StandardCharsets.UTF_8);
    }

    HttpResponse<String> authorizeResponse = client.send(
        HttpRequest.newBuilder()
            .uri(appUri(authorizePath))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(authorizeResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
    String loginLocation = authorizeResponse.headers().firstValue("location").orElseThrow();
    assertThat(loginLocation).contains("/login");

    HttpResponse<String> loginPageResponse = client.send(
        HttpRequest.newBuilder()
            .uri(appUri(loginLocation))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(loginPageResponse.statusCode()).isEqualTo(HttpStatus.OK.value());
    String csrfToken = extractCsrfToken(loginPageResponse.body());

    String loginRequestBody = "username=" + urlEncode("demo-user")
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

    String callbackLocation = null;
    String nextLocation = loginSubmitResponse.headers().firstValue("location").orElseThrow();
    for (int i = 0; i < 5; i++) {
      HttpResponse<String> authorizationStepResponse = client.send(
          HttpRequest.newBuilder()
              .uri(appUri(nextLocation))
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());

      assertThat(authorizationStepResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());

      String stepLocation = authorizationStepResponse.headers().firstValue("location").orElseThrow();
      if (stepLocation.startsWith(redirectUri)) {
        callbackLocation = stepLocation;
        break;
      }
      nextLocation = stepLocation;
    }

    assertThat(callbackLocation).isNotNull();

    URI callbackUri = URI.create(callbackLocation);
    assertThat(callbackUri.getHost()).isIn("127.0.0.1", "localhost");
    assertThat(callbackUri.getQuery()).contains("code=");

    String authorizationCode = extractQueryParam(callbackUri, "code");
    assertThat(authorizationCode).isNotBlank();

    HttpHeaders tokenHeaders = new HttpHeaders();
    tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> tokenForm = new LinkedMultiValueMap<>();
    tokenForm.add("grant_type", "authorization_code");
    tokenForm.add("code", authorizationCode);
    tokenForm.add("redirect_uri", redirectUri);
    if (!authenticateClient) {
      tokenForm.add("client_id", clientId);
    }
    if (codeVerifier != null && !codeVerifier.isBlank()) {
      tokenForm.add("code_verifier", codeVerifier);
    }

    HttpEntity<MultiValueMap<String, String>> tokenRequest = new HttpEntity<>(tokenForm, tokenHeaders);

    if (!authenticateClient) {
      return restTemplate.postForEntity("/oauth2/token", tokenRequest, String.class);
    }

    return restTemplate
        .withBasicAuth(clientId, clientSecret)
        .postForEntity("/oauth2/token", tokenRequest, String.class);
  }

  protected ResponseEntity<String> exchangeRefreshToken(
      String refreshToken,
      String scope,
      boolean authenticateClient) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "refresh_token");
    form.add("refresh_token", refreshToken);
    if (scope != null && !scope.isBlank()) {
      form.add("scope", scope);
    }

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
    if (!authenticateClient) {
      return restTemplate.postForEntity("/oauth2/token", request, String.class);
    }

    return restTemplate
        .withBasicAuth("demo-client", "demo-secret")
        .postForEntity("/oauth2/token", request, String.class);
  }

  protected ResponseEntity<String> fetchUserInfo(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);

    HttpEntity<Void> request = new HttpEntity<>(headers);
    return restTemplate.exchange("/userinfo", HttpMethod.GET, request, String.class);
  }
}
