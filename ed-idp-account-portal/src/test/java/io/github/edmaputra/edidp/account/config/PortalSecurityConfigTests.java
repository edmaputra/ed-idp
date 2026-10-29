package io.github.edmaputra.edidp.account.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.edmaputra.edidp.account.AccountPortalApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Tests for {@link PortalSecurityConfig}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(
    classes = AccountPortalApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
class PortalSecurityConfigTests {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private OAuth2AuthorizedClientManager authorizedClientManager;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();
  }

  @Test
  void unauthenticatedAccessToAccountRedirectsToOAuth2Login() throws Exception {
    mockMvc.perform(get("/account"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/oauth2/authorization/edidp"));
  }

  @Test
  void authorizedClientManagerBeanIsConfigured() {
    assertThat(authorizedClientManager).isNotNull();
  }

  @Test
  void unauthenticatedLogoutRedirectsToLoggedOut() throws Exception {
    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/logout")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/logged-out"));
  }

  @Test
  void authenticatedOidcLogoutRedirectsToAuthServerConnectLogout() throws Exception {
    org.springframework.security.oauth2.core.oidc.OidcIdToken idToken =
        new org.springframework.security.oauth2.core.oidc.OidcIdToken(
            "test-token",
            java.time.Instant.now(),
            java.time.Instant.now().plusSeconds(3600),
            java.util.Map.of("sub", "user-1", "aud", "ed-idp-account-portal"));
    org.springframework.security.oauth2.core.oidc.user.OidcUser oidcUser =
        new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(
            java.util.Collections.emptyList(), idToken);
    org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken auth =
        new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
            oidcUser, java.util.Collections.emptyList(), "edidp");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/logout")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth)))
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
            "Location", org.hamcrest.Matchers.startsWith("http://localhost:9000/connect/logout?")))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
            "Location", org.hamcrest.Matchers.containsString("id_token_hint=test-token")))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
            "Location", org.hamcrest.Matchers.containsString("post_logout_redirect_uri=")));
  }
}
