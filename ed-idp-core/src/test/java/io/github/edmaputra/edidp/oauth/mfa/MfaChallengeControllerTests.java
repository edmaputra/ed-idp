package io.github.edmaputra.edidp.oauth.mfa;

import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_PENDING_AUTH_SESSION_KEY;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_TARGET_URL_SESSION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * Unit tests verifying MfaChallengeController session checks, verification transitions, and redirection.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class MfaChallengeControllerTests {

  private MfaChallengeService mfaChallengeService;
  private SecurityContextRepository securityContextRepository;
  private MfaChallengeController controller;

  @BeforeEach
  void setUp() {
    mfaChallengeService = mock(MfaChallengeService.class);
    securityContextRepository = mock(SecurityContextRepository.class);
    controller = new MfaChallengeController(mfaChallengeService);
  }

  @Test
  void challengeViewRedirectsToLoginWhenNoPendingAuth() {
    MockHttpSession session = new MockHttpSession();
    Model model = new ConcurrentModel();

    String view = controller.challengeView(session, model);
    assertThat(view).isEqualTo("redirect:/login");
  }

  @Test
  void challengeViewPopulatesModelAndRendersTemplate() {
    MockHttpSession session = new MockHttpSession();
    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("alice@example.com", "creds");
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    Model model = new ConcurrentModel();

    String view = controller.challengeView(session, model);
    assertThat(view).isEqualTo("login-mfa-challenge");
    assertThat(model.getAttribute("username")).isEqualTo("alice@example.com");
  }

  @Test
  void verifyChallengeRedirectsToLoginWhenNoPendingAuth() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();

    String result = controller.verifyChallenge("123456", request, response, session);
    assertThat(result).isEqualTo("redirect:/login");
  }

  @Test
  void verifyChallengeRedirectsWithErrorWhenCodeInvalid() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();
    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("bob@example.com", "creds");
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);

    when(mfaChallengeService.verifyCode("bob@example.com", "invalid")).thenReturn(false);

    String result = controller.verifyChallenge("invalid", request, response, session);
    assertThat(result).isEqualTo("redirect:/login/mfa-challenge?error=invalid_code");
  }

  @Test
  void verifyChallengeEnrichesAuthoritiesAndRedirectsToTargetUrl() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();

    Authentication pendingAuth = new UsernamePasswordAuthenticationToken(
        "carol@example.com",
        "creds",
        List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_TARGET_URL_SESSION_KEY, "/oauth2/authorize?client_id=demo");

    when(mfaChallengeService.verifyCode("carol@example.com", "654321")).thenReturn(true);

    String result = controller.verifyChallenge("654321", request, response, session);

    assertThat(result).isEqualTo("redirect:/oauth2/authorize?client_id=demo");
    assertThat(session.getAttribute(MFA_PENDING_AUTH_SESSION_KEY)).isNull();
    assertThat(session.getAttribute(MFA_TARGET_URL_SESSION_KEY)).isNull();

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getAuthorities()).extracting("authority")
        .contains("ROLE_USER", "MFA_VERIFIED");
  }

  @Test
  void verifyChallengeRedirectsToRootWhenTargetUrlNotSet() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();

    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("dave@example.com", "creds");
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);

    when(mfaChallengeService.verifyCode("dave@example.com", "112233")).thenReturn(true);

    String result = controller.verifyChallenge("112233", request, response, session);

    assertThat(result).isEqualTo("redirect:/");
  }
}
