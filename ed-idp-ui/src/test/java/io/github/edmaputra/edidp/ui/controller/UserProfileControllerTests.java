package io.github.edmaputra.edidp.ui.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import io.github.edmaputra.edidp.ui.UiProperties;
import io.github.edmaputra.edidp.ui.service.UserProfileService;
import io.github.edmaputra.edidp.ui.service.UserProfileService.ChangePasswordCommand;
import io.github.edmaputra.edidp.ui.service.UserProfileService.UpdateProfileCommand;
import io.github.edmaputra.edidp.ui.service.UserProfileService.UserProfileDto;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests for {@link UserProfileController}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
class UserProfileControllerTests {

  @Mock
  private UserProfileService userProfileService;

  @Mock
  private Principal principal;

  private UiProperties uiProperties;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    uiProperties = new UiProperties(true, "/admin");
    UserProfileController controller = new UserProfileController(uiProperties, userProfileService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void profilePage_loadsExistingProfileAndReturnsView() throws Exception {
    when(principal.getName()).thenReturn("alice@example.com");
    UserProfileDto profile = new UserProfileDto(
        "alice@example.com", "alice@example.com", "Alice", "Dev", "demo", List.of("ROLE_USER"), List.of()
    );
    when(userProfileService.getProfile("alice@example.com")).thenReturn(Optional.of(profile));

    mockMvc.perform(get("/admin/profile").principal(principal))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/profile/index"))
        .andExpect(model().attribute("profile", profile))
        .andExpect(model().attribute("basePath", "/admin"));

    verify(userProfileService).getProfile("alice@example.com");
  }

  @Test
  void profilePage_fallsBackToDefault_whenPrincipalIsNull() throws Exception {
    when(userProfileService.getProfile("admin@example.com")).thenReturn(Optional.empty());

    mockMvc.perform(get("/admin/profile"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/profile/index"))
        .andExpect(model().attributeExists("profile"));

    verify(userProfileService).getProfile("admin@example.com");
  }

  @Test
  void updateProfile_executesServiceAndReturnsProfileCardFragment() throws Exception {
    when(principal.getName()).thenReturn("alice@example.com");
    UserProfileDto updated = new UserProfileDto(
        "alice@example.com", "alice@example.com", "Alice Updated", "Engineering", "demo", List.of(), List.of()
    );
    when(userProfileService.getProfile("alice@example.com")).thenReturn(Optional.of(updated));

    mockMvc.perform(post("/admin/profile/update")
            .principal(principal)
            .param("fullName", "Alice Updated")
            .param("department", "Engineering"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/profile/index :: profileCard"))
        .andExpect(model().attribute("profile", updated));

    verify(userProfileService).updateProfile(any(UpdateProfileCommand.class));
  }

  @Test
  void changePassword_success_returnsOkStatus() throws Exception {
    when(principal.getName()).thenReturn("alice@example.com");

    mockMvc.perform(post("/admin/profile/change-password")
            .principal(principal)
            .param("currentPassword", "oldpass123")
            .param("newPassword", "newpass123")
            .param("confirmPassword", "newpass123"))
        .andExpect(status().isOk())
        .andExpect(content().string("Password updated successfully"));

    verify(userProfileService).changePassword(any(ChangePasswordCommand.class));
  }

  @Test
  void changePassword_failure_returnsBadRequestStatus() throws Exception {
    when(principal.getName()).thenReturn("alice@example.com");
    doThrow(new IllegalArgumentException("Current password is incorrect"))
        .when(userProfileService).changePassword(any(ChangePasswordCommand.class));

    mockMvc.perform(post("/admin/profile/change-password")
            .principal(principal)
            .param("currentPassword", "wrongpass")
            .param("newPassword", "newpass123")
            .param("confirmPassword", "newpass123"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("Current password is incorrect"));
  }
}
