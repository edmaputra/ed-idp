package io.github.edmaputra.edidp.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import io.github.edmaputra.edidp.account.client.AccountPortalApiClient;
import io.github.edmaputra.edidp.account.dto.ChangePasswordDto;
import io.github.edmaputra.edidp.account.dto.MfaSetupDto;
import io.github.edmaputra.edidp.account.dto.MfaStatusDto;
import io.github.edmaputra.edidp.account.dto.UpdateProfileDto;
import io.github.edmaputra.edidp.account.dto.UserProfileDto;
import io.github.edmaputra.edidp.account.exception.AccountPortalApiException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Tests for {@link AccountPortalController}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
class AccountPortalControllerTests {

  @Mock
  private AccountPortalApiClient apiClient;

  private MockMvc mockMvc;
  private OAuth2AuthorizedClient mockAuthorizedClient;
  private OAuth2User mockUser;

  @BeforeEach
  void setUp() {
    AccountPortalController controller = new AccountPortalController(apiClient);

    ClientRegistration registration = ClientRegistration.withRegistrationId("edidp")
        .clientId("ed-idp-account-portal")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .authorizationUri("http://localhost:9000/oauth2/authorize")
        .tokenUri("http://localhost:9000/oauth2/token")
        .build();

    OAuth2AccessToken token = new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        "mock-access-token",
        Instant.now(),
        Instant.now().plusSeconds(300)
    );

    mockAuthorizedClient = new OAuth2AuthorizedClient(registration, "user", token);
    mockUser = new DefaultOAuth2User(
        Collections.emptyList(),
        Map.of("sub", "alice", "tenant_id", "demo"),
        "sub"
    );

    HandlerMethodArgumentResolver authorizedClientResolver = new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RegisteredOAuth2AuthorizedClient.class);
      }

      @Override
      public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return mockAuthorizedClient;
      }
    };

    HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
      }

      @Override
      public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return mockUser;
      }
    };

    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(authorizedClientResolver, authPrincipalResolver)
        .build();
  }

  @Test
  void root_redirectsToAccount() throws Exception {
    mockMvc.perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"));
  }

  @Test
  void loggedOut_returnsLoggedOutView() throws Exception {
    mockMvc.perform(get("/logged-out"))
        .andExpect(status().isOk())
        .andExpect(view().name("account/logged-out"));
  }

  @Test
  void accountIndex_loadsProfileAndPopulatesModel() throws Exception {
    UserProfileDto profile = new UserProfileDto(
        "alice",
        "alice@example.com",
        "Alice Smith",
        "Platform",
        "demo",
        List.of("ROLE_USER"),
        List.of("DEVELOPERS")
    );

    when(apiClient.getProfile("mock-access-token", "demo")).thenReturn(profile);
    when(apiClient.getMfaStatus("mock-access-token", "demo")).thenReturn(new MfaStatusDto(false, null));

    mockMvc.perform(get("/account"))
        .andExpect(status().isOk())
        .andExpect(view().name("account/index"))
        .andExpect(model().attribute("profile", profile))
        .andExpect(model().attribute("mfaStatus", new MfaStatusDto(false, null)))
        .andExpect(model().attribute("tenant", "demo"))
        .andExpect(model().attributeExists("updateProfileForm"))
        .andExpect(model().attributeExists("changePasswordForm"))
        .andExpect(model().attributeExists("mfaVerificationForm"))
        .andExpect(model().attributeExists("mfaDisableForm"));
  }

  @Test
  void updateProfile_success_redirectsWithFlashMessage() throws Exception {
    UserProfileDto updatedProfile = new UserProfileDto(
        "alice",
        "alice@example.com",
        "Alice Updated",
        "Platform Core",
        "demo",
        List.of("ROLE_USER"),
        List.of()
    );

    when(apiClient.updateProfile(eq("mock-access-token"), eq("demo"), any(UpdateProfileDto.class)))
        .thenReturn(updatedProfile);

    mockMvc.perform(post("/account/profile")
            .param("fullName", "Alice Updated")
            .param("department", "Platform Core"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attribute("profileSuccess", "Your profile details have been successfully updated."));

    verify(apiClient).updateProfile(eq("mock-access-token"), eq("demo"), eq(new UpdateProfileDto("Alice Updated", "Platform Core")));
  }

  @Test
  void updateProfile_apiError_redirectsWithErrorMessage() throws Exception {
    doThrow(new AccountPortalApiException(500, "Internal Server Error"))
        .when(apiClient).updateProfile(any(), any(), any());

    mockMvc.perform(post("/account/profile")
            .param("fullName", "Alice Updated")
            .param("department", "Platform Core"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attributeExists("errorMessage"));
  }

  @Test
  void changePassword_success_redirectsWithFlashMessage() throws Exception {
    mockMvc.perform(post("/account/password")
            .param("currentPassword", "oldPass123!")
            .param("newPassword", "newPass456!")
            .param("confirmPassword", "newPass456!"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attribute("passwordSuccess", "Your password has been changed successfully."));

    verify(apiClient).changePassword(eq("mock-access-token"), eq("demo"),
        eq(new ChangePasswordDto("oldPass123!", "newPass456!", "newPass456!")));
  }

  @Test
  void changePassword_passwordMismatch_redirectsWithErrorMessage() throws Exception {
    mockMvc.perform(post("/account/password")
            .param("currentPassword", "oldPass123!")
            .param("newPassword", "newPass456!")
            .param("confirmPassword", "differentPass!"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attribute("errorMessage", "New password and confirmation password do not match."));
  }

  @Test
  void changePassword_apiError_redirectsWithErrorMessage() throws Exception {
    doThrow(new AccountPortalApiException(400, "Current password is incorrect"))
        .when(apiClient).changePassword(any(), any(), any());

    mockMvc.perform(post("/account/password")
            .param("currentPassword", "wrongPass!")
            .param("newPassword", "newPass456!")
            .param("confirmPassword", "newPass456!"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attribute("errorMessage", "Password update failed: Current password is incorrect"));
  }

  @Test
  void initiateMfaSetup_success_setsFlashAttribute() throws Exception {
    MfaSetupDto setup = new MfaSetupDto("SECRET123", "otpauth://uri", List.of("c1", "c2"));
    when(apiClient.initiateMfaSetup("mock-access-token", "demo")).thenReturn(setup);

    mockMvc.perform(post("/account/mfa/setup"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attribute("mfaSetup", setup));

    verify(apiClient).initiateMfaSetup("mock-access-token", "demo");
  }

  @Test
  void initiateMfaSetup_apiError_setsErrorMessage() throws Exception {
    doThrow(new AccountPortalApiException(500, "Setup failed"))
        .when(apiClient).initiateMfaSetup(any(), any());

    mockMvc.perform(post("/account/mfa/setup"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attributeExists("mfaError"));
  }

  @Test
  void activateMfa_success_redirectsWithFlashMessage() throws Exception {
    mockMvc.perform(post("/account/mfa/activate")
            .param("code", "123456"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attribute("mfaSuccess", "Two-factor authentication has been successfully activated on your account."));

    verify(apiClient).activateMfa("mock-access-token", "demo", "123456");
  }

  @Test
  void activateMfa_blankCode_redirectsWithErrorMessage() throws Exception {
    mockMvc.perform(post("/account/mfa/activate")
            .param("code", "")
            .param("secret", "SECRET123")
            .param("qrCodeUri", "otpauth://uri"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attributeExists("mfaError"))
        .andExpect(flash().attributeExists("mfaSetup"));
  }

  @Test
  void activateMfa_apiError_preservesSetupAndSetsErrorMessage() throws Exception {
    doThrow(new AccountPortalApiException(400, "Invalid code"))
        .when(apiClient).activateMfa(any(), any(), any());

    mockMvc.perform(post("/account/mfa/activate")
            .param("code", "000000")
            .param("secret", "SECRET123")
            .param("qrCodeUri", "otpauth://uri"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attributeExists("mfaError"))
        .andExpect(flash().attributeExists("mfaSetup"));
  }

  @Test
  void disableMfa_success_redirectsWithFlashMessage() throws Exception {
    mockMvc.perform(post("/account/mfa/disable")
            .param("code", "123456"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attribute("mfaSuccess", "Two-factor authentication has been disabled for your account."));

    verify(apiClient).disableMfa("mock-access-token", "demo", "123456");
  }

  @Test
  void disableMfa_blankCode_redirectsWithErrorMessage() throws Exception {
    mockMvc.perform(post("/account/mfa/disable")
            .param("code", ""))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attributeExists("mfaError"));
  }

  @Test
  void disableMfa_apiError_redirectsWithErrorMessage() throws Exception {
    doThrow(new AccountPortalApiException(400, "Invalid code"))
        .when(apiClient).disableMfa(any(), any(), any());

    mockMvc.perform(post("/account/mfa/disable")
            .param("code", "wrongCode"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/account"))
        .andExpect(flash().attributeExists("mfaError"));
  }
}
