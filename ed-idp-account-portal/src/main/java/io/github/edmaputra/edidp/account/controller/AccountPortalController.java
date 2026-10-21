package io.github.edmaputra.edidp.account.controller;

import io.github.edmaputra.edidp.account.client.AccountPortalApiClient;
import io.github.edmaputra.edidp.account.dto.ChangePasswordDto;
import io.github.edmaputra.edidp.account.dto.UpdateProfileDto;
import io.github.edmaputra.edidp.account.dto.UserProfileDto;
import io.github.edmaputra.edidp.account.exception.AccountPortalApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller managing the user self-service account portal web interface.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AccountPortalController {

  private final AccountPortalApiClient apiClient;

  @GetMapping("/")
  public String root() {
    return "redirect:/account";
  }

  @GetMapping("/account")
  public String accountIndex(
      @RegisteredOAuth2AuthorizedClient("edidp") OAuth2AuthorizedClient authorizedClient,
      @AuthenticationPrincipal OAuth2User user,
      Model model) {

    String accessToken = authorizedClient.getAccessToken().getTokenValue();
    String tenant = extractTenant(user);

    UserProfileDto profile = apiClient.getProfile(accessToken, tenant);

    model.addAttribute("profile", profile);
    if (!model.containsAttribute("updateProfileForm")) {
      model.addAttribute("updateProfileForm", new UpdateProfileDto(profile.fullName(), profile.department()));
    }
    if (!model.containsAttribute("changePasswordForm")) {
      model.addAttribute("changePasswordForm", new ChangePasswordDto("", "", ""));
    }
    model.addAttribute("tenant", tenant);

    return "account/index";
  }

  @PostMapping("/account/profile")
  public String updateProfile(
      @RegisteredOAuth2AuthorizedClient("edidp") OAuth2AuthorizedClient authorizedClient,
      @AuthenticationPrincipal OAuth2User user,
      @Valid @ModelAttribute("updateProfileForm") UpdateProfileDto form,
      BindingResult bindingResult,
      RedirectAttributes redirectAttributes) {

    if (bindingResult.hasErrors()) {
      redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.updateProfileForm", bindingResult);
      redirectAttributes.addFlashAttribute("updateProfileForm", form);
      redirectAttributes.addFlashAttribute("errorMessage", "Please correct the errors in the profile form.");
      return "redirect:/account";
    }

    try {
      String accessToken = authorizedClient.getAccessToken().getTokenValue();
      String tenant = extractTenant(user);
      apiClient.updateProfile(accessToken, tenant, form);
      redirectAttributes.addFlashAttribute("profileSuccess", "Your profile details have been successfully updated.");
    } catch (AccountPortalApiException ex) {
      log.warn("Failed to update profile: {}", ex.getMessage());
      redirectAttributes.addFlashAttribute("errorMessage", "Failed to update profile: " + ex.getMessage());
    }

    return "redirect:/account";
  }

  @PostMapping("/account/password")
  public String changePassword(
      @RegisteredOAuth2AuthorizedClient("edidp") OAuth2AuthorizedClient authorizedClient,
      @AuthenticationPrincipal OAuth2User user,
      @Valid @ModelAttribute("changePasswordForm") ChangePasswordDto form,
      BindingResult bindingResult,
      RedirectAttributes redirectAttributes) {

    if (bindingResult.hasErrors()) {
      redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.changePasswordForm", bindingResult);
      redirectAttributes.addFlashAttribute("changePasswordForm", form);
      redirectAttributes.addFlashAttribute("errorMessage", "Please provide all required password fields.");
      return "redirect:/account";
    }

    if (!form.newPassword().equals(form.confirmPassword())) {
      redirectAttributes.addFlashAttribute("changePasswordForm", form);
      redirectAttributes.addFlashAttribute("errorMessage", "New password and confirmation password do not match.");
      return "redirect:/account";
    }

    try {
      String accessToken = authorizedClient.getAccessToken().getTokenValue();
      String tenant = extractTenant(user);
      apiClient.changePassword(accessToken, tenant, form);
      redirectAttributes.addFlashAttribute("passwordSuccess", "Your password has been changed successfully.");
    } catch (AccountPortalApiException ex) {
      log.warn("Failed to change password: {}", ex.getMessage());
      redirectAttributes.addFlashAttribute("errorMessage", "Password update failed: " + ex.getMessage());
    }

    return "redirect:/account";
  }

  @GetMapping("/logged-out")
  public String loggedOut() {
    return "account/logged-out";
  }

  private String extractTenant(OAuth2User user) {
    if (user != null && user.getAttribute("tenant_id") != null) {
      return user.getAttribute("tenant_id").toString();
    }
    return "demo";
  }
}
