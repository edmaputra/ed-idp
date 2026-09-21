package io.github.edmaputra.edidp.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import io.github.edmaputra.edidp.integration.AuthServerIntegrationTests;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OidcLogoutEndpointTests extends AuthServerIntegrationTests {

  @Test
  void rpInitiatedLogoutRedirectsToRegisteredPostLogoutRedirectUriAndInvalidatesSession() throws Exception {
    AuthorizationCodeFlowResult flow = authenticateDemoUserAndGetIdToken("openid read");

    String postLogoutRedirectUri = "http://127.0.0.1:9000/logged-out";
    String state = "logout-state";
    String requestBody = "id_token_hint=" + urlEncode(flow.idToken())
        + "&post_logout_redirect_uri=" + urlEncode(postLogoutRedirectUri)
        + "&state=" + urlEncode(state);

    HttpResponse<String> logoutResponse = flow.client().send(
      HttpRequest.newBuilder()
        .uri(appUri("/connect/logout"))
        .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build(),
      HttpResponse.BodyHandlers.ofString());

    assertThat(logoutResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());

    URI redirectedUri = URI.create(logoutResponse.headers().firstValue(HttpHeaders.LOCATION).orElseThrow());
    assertThat(redirectedUri.getPath()).isEqualTo("/logged-out");
    assertThat(redirectedUri.getQuery()).contains("state=" + state);

    HttpResponse<String> afterLogoutAuthorizeResponse = flow.client().send(
      HttpRequest.newBuilder()
        .uri(appUri("/oauth2/authorize?response_type=code"
            + "&client_id=demo-client"
            + "&redirect_uri=" + URLEncoder.encode("http://127.0.0.1:9000/login/oauth2/code/demo-client",
                StandardCharsets.UTF_8)
            + "&scope=" + URLEncoder.encode("openid read", StandardCharsets.UTF_8)
        + "&state=after-logout"))
        .GET()
        .build(),
      HttpResponse.BodyHandlers.ofString());

    assertThat(afterLogoutAuthorizeResponse.statusCode()).isEqualTo(HttpStatus.FOUND.value());
    assertThat(afterLogoutAuthorizeResponse.headers().firstValue(HttpHeaders.LOCATION).orElseThrow())
      .contains("/login");
  }
}
