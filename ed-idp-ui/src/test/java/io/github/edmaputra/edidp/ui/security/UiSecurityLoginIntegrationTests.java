package io.github.edmaputra.edidp.ui.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.edmaputra.edidp.ui.UiApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration test verifying form login and redirection in UI Add-on.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(
    classes = UiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "edidp.ui.enabled=true",
        "edidp.seeding.enabled=true"
    }
)
class UiSecurityLoginIntegrationTests {

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply(springSecurity())
        .build();
  }

  @Test
  void testLoginWithAdminUsernameOnly() throws Exception {
    MockHttpSession session = new MockHttpSession();
    MvcResult result = mockMvc.perform(post("/admin/login")
            .session(session)
            .param("username", "admin")
            .param("password", "admin-password")
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/dashboard"))
        .andReturn();

    mockMvc.perform(get("/admin/dashboard").session(session))
        .andExpect(status().isOk());
  }

  @Test
  void testLoginWithAdminEmail() throws Exception {
    MockHttpSession session = new MockHttpSession();
    mockMvc.perform(post("/admin/login")
            .session(session)
            .param("username", "admin@example.com")
            .param("password", "admin-password")
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/dashboard"));

    mockMvc.perform(get("/admin/dashboard").session(session))
        .andExpect(status().isOk());
  }

  @Test
  void testAccessAdminDirectlyRedirectsToLogin() throws Exception {
    mockMvc.perform(get("/admin"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login"));
  }

  @Test
  void testAccessRootRedirectsToDashboard() throws Exception {
    mockMvc.perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/dashboard"));
  }

  @Test
  void testAccessDefaultLoginRedirectsToAdminLogin() throws Exception {
    mockMvc.perform(get("/login"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login"));
  }
}
