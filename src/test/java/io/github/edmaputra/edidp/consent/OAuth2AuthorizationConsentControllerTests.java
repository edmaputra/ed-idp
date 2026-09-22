package io.github.edmaputra.edidp.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthorizationConsentControllerTests {

  @Mock
  private AuthorizationConsentService authorizationConsentService;

  @Mock
  private RegisteredClientRepository registeredClientRepository;

  @Mock
  private Authentication authentication;

  private OAuth2AuthorizationConsentController controller;

  @BeforeEach
  void setUp() {
    controller = new OAuth2AuthorizationConsentController(
        authorizationConsentService,
        registeredClientRepository);
  }

  @Test
  void denyActionRedirectsToRedirectUriWithAccessDeniedError() {
    when(authentication.getName()).thenReturn("demo-user");

    String view = controller.approveConsent(
        "demo-client",
        "http://client.example.com/callback",
        "openid profile email",
        "test-state",
        new String[]{"openid"},
        "deny",
        authentication);

    assertThat(view).contains("redirect:http://client.example.com/callback");
    assertThat(view).contains("error=access_denied");
    assertThat(view).contains("state=test-state");
    verify(authorizationConsentService, never()).approveConsent(any());
  }

  @Test
  void approveActionOnlyApprovesSubmittedScopes() {
    when(authentication.getName()).thenReturn("demo-user");
    RegisteredClient client = RegisteredClient.withId("internal-uuid-123")
        .clientId("demo-client")
        .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://client.example.com/callback")
        .scope("openid")
        .scope("profile")
        .scope("email")
        .build();
    when(registeredClientRepository.findByClientId("demo-client")).thenReturn(client);

    String view = controller.approveConsent(
        "demo-client",
        "http://client.example.com/callback",
        "openid profile email",
        "xyz-state",
        new String[]{"openid", "profile"},
        "approve",
        authentication);

    ArgumentCaptor<CheckConsentCommand> captor = ArgumentCaptor.forClass(CheckConsentCommand.class);
    verify(authorizationConsentService).approveConsent(captor.capture());

    CheckConsentCommand captured = captor.getValue();
    assertThat(captured.principalName()).isEqualTo("demo-user");
    assertThat(captured.registeredClientId()).isEqualTo("internal-uuid-123");
    assertThat(captured.requestedScopes()).containsExactlyInAnyOrder("openid", "profile");

    assertThat(view).contains("redirect:/oauth2/authorize");
    assertThat(view).contains("client_id=demo-client");
    assertThat(view).contains("scope=openid%20profile");
    assertThat(view).contains("consent_approved=true");
    assertThat(view).contains("state=xyz-state");
  }

  @Test
  void approveActionWithEmptyScopesRedirectsWithAccessDeniedError() {
    when(authentication.getName()).thenReturn("demo-user");
    RegisteredClient client = RegisteredClient.withId("internal-uuid-123")
        .clientId("demo-client")
        .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://client.example.com/callback")
        .scope("openid")
        .build();
    when(registeredClientRepository.findByClientId("demo-client")).thenReturn(client);

    String view = controller.approveConsent(
        "demo-client",
        "http://client.example.com/callback",
        "openid profile",
        "xyz-state",
        new String[]{},
        "approve",
        authentication);

    assertThat(view).contains("redirect:http://client.example.com/callback");
    assertThat(view).contains("error=access_denied");
    verify(authorizationConsentService, never()).approveConsent(any());
  }

  @Test
  void consentFormForUnknownClientReturnsConsentError() {
    when(authentication.getName()).thenReturn("demo-user");
    when(registeredClientRepository.findByClientId("unknown-client")).thenReturn(null);

    Model model = new ConcurrentModel();
    String view = controller.consentForm(
        "unknown-client",
        "openid",
        null,
        "http://client.example.com/callback",
        "state",
        authentication,
        model);

    assertThat(view).isEqualTo("consent-error");
    assertThat(model.getAttribute("error")).isEqualTo("Unknown client");
  }

  @Test
  void consentFormForPreviouslyAuthorizedUserRedirectsToAuthorize() {
    when(authentication.getName()).thenReturn("demo-user");
    RegisteredClient client = RegisteredClient.withId("uuid-456")
        .clientId("demo-client")
        .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://client.example.com/callback")
        .scope("openid")
        .build();
    when(registeredClientRepository.findByClientId("demo-client")).thenReturn(client);
    when(authorizationConsentService.checkConsent(any()))
        .thenReturn(ConsentDecisionResult.noConsentNeeded(Set.of("openid")));

    Model model = new ConcurrentModel();
    String view = controller.consentForm(
        "demo-client",
        "openid",
        null,
        "http://client.example.com/callback",
        "test-state",
        authentication,
        model);

    assertThat(view).contains("redirect:/oauth2/authorize");
    assertThat(view).contains("client_id=demo-client");
    assertThat(view).contains("scope=openid");
  }
}
