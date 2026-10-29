package io.github.edmaputra.edidp.account.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Unit tests for {@link PortalOidcLogoutSuccessHandler}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class PortalOidcLogoutSuccessHandlerTests {

  private PortalOidcLogoutSuccessHandler handler;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    handler = new PortalOidcLogoutSuccessHandler("http://localhost:9000");
    request = new MockHttpServletRequest();
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(9001);
    request.setRequestURI("/logout");
    response = new MockHttpServletResponse();
  }

  @Test
  void determineTargetUrl_withOidcUserAndIdToken_redirectsToAuthServerLogout() {
    OidcIdToken idToken = new OidcIdToken(
        "sample-token-value",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("sub", "user-123", "aud", "ed-idp-account-portal")
    );
    OidcUser oidcUser = new DefaultOidcUser(Collections.emptyList(), idToken);
    OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
        oidcUser,
        Collections.emptyList(),
        "edidp"
    );

    String targetUrl = handler.determineTargetUrl(request, response, auth);

    assertThat(targetUrl).startsWith("http://localhost:9000/connect/logout?");
    assertThat(targetUrl).contains("id_token_hint=sample-token-value");
    assertThat(targetUrl).contains("post_logout_redirect_uri=http://localhost:9001/logged-out");
  }

  @Test
  void determineTargetUrl_withNonOidcAuthentication_redirectsToDefaultLoggedOutUrl() {
    TestingAuthenticationToken auth = new TestingAuthenticationToken("user", "pass");

    String targetUrl = handler.determineTargetUrl(request, response, auth);

    assertThat(targetUrl).isEqualTo("/logged-out");
  }

  @Test
  void determineTargetUrl_withNullAuthentication_redirectsToDefaultLoggedOutUrl() {
    String targetUrl = handler.determineTargetUrl(request, response, null);

    assertThat(targetUrl).isEqualTo("/logged-out");
  }

  @Test
  void determineTargetUrl_trimsTrailingSlashFromAuthServerUrl() {
    PortalOidcLogoutSuccessHandler customHandler = new PortalOidcLogoutSuccessHandler("http://localhost:9000/");

    OidcIdToken idToken = new OidcIdToken(
        "sample-token-value",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("sub", "user-123")
    );
    OidcUser oidcUser = new DefaultOidcUser(Collections.emptyList(), idToken);
    OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(oidcUser, Collections.emptyList(), "edidp");

    String targetUrl = customHandler.determineTargetUrl(request, response, auth);

    assertThat(targetUrl).startsWith("http://localhost:9000/connect/logout?");
    assertThat(targetUrl).doesNotContain("9000//connect");
  }
}
