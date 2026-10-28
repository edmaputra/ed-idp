package io.github.edmaputra.edidp.ui.controller;

import io.github.edmaputra.edidp.ui.UiProperties;
import io.github.edmaputra.edidp.ui.service.UserProfileService;
import io.github.edmaputra.edidp.ui.service.UserProfileService.ChangePasswordCommand;
import io.github.edmaputra.edidp.ui.service.UserProfileService.UpdateProfileCommand;
import io.github.edmaputra.edidp.ui.service.UserProfileService.UserProfileDto;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for authenticated user profile self-service and credential maintenance.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
@RequestMapping("${edidp.ui.base-path:/admin}/profile")
public class UserProfileController {

  private final UiProperties uiProperties;
  private final UserProfileService userProfileService;

  public UserProfileController(
      UiProperties uiProperties,
      UserProfileService userProfileService) {
    this.uiProperties = Objects.requireNonNull(uiProperties, "uiProperties must not be null");
    this.userProfileService = Objects.requireNonNull(userProfileService, "userProfileService must not be null");
  }

  @ModelAttribute("basePath")
  public String basePath() {
    return uiProperties.basePath();
  }

  @GetMapping
  public String profilePage(Model model, Principal principal) {
    String username = resolveUsername(principal);
    Optional<UserProfileDto> profileOpt = userProfileService.getProfile(username);
    UserProfileDto profile = profileOpt.orElseGet(() ->
        new UserProfileDto(username, username, username, "", "demo", List.of(), List.of())
    );
    model.addAttribute("profile", profile);
    return "admin/profile/index";
  }

  @PostMapping("/update")
  public String updateProfile(
      @RequestParam String fullName,
      @RequestParam(required = false, defaultValue = "") String department,
      Model model,
      Principal principal) {

    String username = resolveUsername(principal);
    userProfileService.updateProfile(new UpdateProfileCommand(username, fullName, department));

    Optional<UserProfileDto> profileOpt = userProfileService.getProfile(username);
    UserProfileDto profile = profileOpt.orElseGet(() ->
        new UserProfileDto(username, username, fullName, department, "demo", List.of(), List.of())
    );
    model.addAttribute("profile", profile);
    return "admin/profile/index :: profileCard";
  }

  @PostMapping("/change-password")
  public ResponseEntity<String> changePassword(
      @RequestParam String currentPassword,
      @RequestParam String newPassword,
      @RequestParam String confirmPassword,
      Principal principal) {

    String username = resolveUsername(principal);
    try {
      userProfileService.changePassword(new ChangePasswordCommand(
          username, currentPassword, newPassword, confirmPassword
      ));
      return ResponseEntity.ok("Password updated successfully");
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(ex.getMessage());
    }
  }

  private String resolveUsername(Principal principal) {
    return (principal != null && principal.getName() != null && !principal.getName().isBlank())
        ? principal.getName()
        : "admin@example.com";
  }
}
