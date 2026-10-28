package io.github.edmaputra.edidp.users;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests for {@link UserSelfServiceRestController}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
class UserSelfServiceRestControllerTests {

  @Mock
  private UserSelfService userSelfService;

  private MockMvc mockMvc;
  private JwtAuthenticationToken principal;

  @BeforeEach
  void setUp() {
    UserSelfServiceRestController controller = new UserSelfServiceRestController(userSelfService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
        .build();

    Jwt jwt = new Jwt(
        "test-token",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        Map.of("alg", "none"),
        Map.of("sub", "alice@example.com", "tenant_id", "demo")
    );
    principal = new JwtAuthenticationToken(jwt);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(principal);
    SecurityContextHolder.setContext(context);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void getMyProfile_returnsProfileJson() throws Exception {
    UserProfileResponse response = new UserProfileResponse(
        "alice@example.com", "alice@example.com", "Alice", "Security", "demo", List.of("ROLE_USER"), List.of()
    );

    when(userSelfService.getMyProfile("alice@example.com", "demo")).thenReturn(response);

    mockMvc.perform(get("/api/v1/users/me").principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice@example.com"))
        .andExpect(jsonPath("$.fullName").value("Alice"))
        .andExpect(jsonPath("$.department").value("Security"))
        .andExpect(jsonPath("$.tenantId").value("demo"));

    verify(userSelfService).getMyProfile("alice@example.com", "demo");
  }

  @Test
  void updateMyProfile_updatesAndReturnsUpdatedProfile() throws Exception {
    UserProfileResponse updated = new UserProfileResponse(
        "alice@example.com", "alice@example.com", "Alice Smith", "DevOps", "demo", List.of(), List.of()
    );

    when(userSelfService.updateMyProfile(eq("alice@example.com"), eq("demo"), any(UpdateProfileRequest.class)))
        .thenReturn(updated);

    String jsonBody = """
        {
          "fullName": "Alice Smith",
          "department": "DevOps"
        }
        """;

    mockMvc.perform(put("/api/v1/users/me")
            .principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Alice Smith"))
        .andExpect(jsonPath("$.department").value("DevOps"));

    verify(userSelfService).updateMyProfile(eq("alice@example.com"), eq("demo"), any(UpdateProfileRequest.class));
  }

  @Test
  void changeMyPassword_returnsNoContent_onSuccess() throws Exception {
    String jsonBody = """
        {
          "currentPassword": "oldPassword123",
          "newPassword": "newPassword123",
          "confirmPassword": "newPassword123"
        }
        """;

    mockMvc.perform(post("/api/v1/users/me/password")
            .principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody))
        .andExpect(status().isNoContent());

    verify(userSelfService).changeMyPassword(eq("alice@example.com"), eq("demo"), any(ChangePasswordRequest.class));
  }

  @Test
  void changeMyPassword_returnsBadRequest_whenCurrentPasswordIncorrect() throws Exception {
    doThrow(new IllegalArgumentException("Current password is incorrect"))
        .when(userSelfService).changeMyPassword(eq("alice@example.com"), eq("demo"), any(ChangePasswordRequest.class));

    String jsonBody = """
        {
          "currentPassword": "wrongPassword",
          "newPassword": "newPassword123",
          "confirmPassword": "newPassword123"
        }
        """;

    mockMvc.perform(post("/api/v1/users/me/password")
            .principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.error_description").value("Current password is incorrect"));
  }

  @Test
  void getMyProfile_returnsBadRequest_whenJwtIsNull() throws Exception {
    SecurityContextHolder.clearContext();
    mockMvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.error_description").value("JWT subject claim is missing"));
  }
}
