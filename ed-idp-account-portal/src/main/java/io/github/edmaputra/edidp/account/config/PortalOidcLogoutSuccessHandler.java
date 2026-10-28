package io.github.edmaputra.edidp.account.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Custom OIDC RP-initiated logout success handler that redirects the user-agent to the
 * Authorization Server's end_session_endpoint ({@code /connect/logout}) with an
 * {@code id_token_hint} and {@code post_logout_redirect_uri} so that single sign-out occurs.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class PortalOidcLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

  private static final Logger log = LoggerFactory.getLogger(PortalOidcLogoutSuccessHandler.class);

  private final String authServerUrl;

  public PortalOidcLogoutSuccessHandler(String authServerUrl) {
    this.authServerUrl = (authServerUrl != null && !authServerUrl.isBlank())
        ? trimTrailingSlash(authServerUrl.trim())
        : "http://localhost:9000";
    setDefaultTargetUrl("/logged-out");
  }

  @Override
  protected String determineTargetUrl(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) {

    if (authentication instanceof OAuth2AuthenticationToken oauthToken
        && oauthToken.getPrincipal() instanceof OidcUser oidcUser
        && oidcUser.getIdToken() != null
        && oidcUser.getIdToken().getTokenValue() != null) {

      String idToken = oidcUser.getIdToken().getTokenValue();

      String postLogoutRedirectUri = ServletUriComponentsBuilder.fromRequest(request)
          .replacePath(request.getContextPath() + "/logged-out")
          .replaceQuery(null)
          .fragment(null)
          .build()
          .toUriString();

      String targetUrl = UriComponentsBuilder.fromUriString(authServerUrl + "/connect/logout")
          .queryParam("id_token_hint", idToken)
          .queryParam("post_logout_redirect_uri", postLogoutRedirectUri)
          .encode(StandardCharsets.UTF_8)
          .build()
          .toUriString();

      log.debug("Initiating OIDC RP-Initiated Logout to: {}", targetUrl);
      return targetUrl;
    }

    log.debug("No active OIDC session found; redirecting to default target URL: {}", getDefaultTargetUrl());
    return super.determineTargetUrl(request, response, authentication);
  }

  private static String trimTrailingSlash(String uri) {
    String trimmed = uri.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
