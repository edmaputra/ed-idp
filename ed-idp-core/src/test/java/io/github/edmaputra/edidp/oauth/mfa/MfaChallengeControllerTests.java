package io.github.edmaputra.edidp.oauth.mfa;

import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MAX_MFA_ATTEMPTS;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_ATTEMPT_COUNT_KEY;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_CHALLENGE_EXPIRES_AT_KEY;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_PENDING_AUTH_SESSION_KEY;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_TARGET_URL_SESSION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * Unit tests verifying MfaChallengeController session checks, TTL expiration, brute-force lockout,
 * verification transitions, and open-redirect protection.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class MfaChallengeControllerTests {

  private MfaChallengeService mfaChallengeService;
  private MfaChallengeController controller;

  @BeforeEach
  void setUp() {
    mfaChallengeService = mock(MfaChallengeService.class);
    controller = new MfaChallengeController(mfaChallengeService);
  }

  @Test
  void challengeViewRedirectsToLoginWhenNoPendingAuth() {
    MockHttpSession session = new MockHttpSession();
    Model model = new ConcurrentModel();

    String view = controller.challengeView(null, session, model);
    assertThat(view).isEqualTo("redirect:/login");
  }

  @Test
  void challengeViewRendersDirectlyWhenErrorExpiredOrLocked() {
    MockHttpSession session = new MockHttpSession();
    Model model = new ConcurrentModel();

    assertThat(controller.challengeView("expired", session, model)).isEqualTo("login-mfa-challenge");
    assertThat(controller.challengeView("locked", session, model)).isEqualTo("login-mfa-challenge");
  }

  @Test
  void challengeViewRedirectsWhenExpiredAndClearsSession() {
    MockHttpSession session = new MockHttpSession();
    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("alice@example.com", null);
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().minusSeconds(10));
    Model model = new ConcurrentModel();

    String view = controller.challengeView(null, session, model);
    assertThat(view).isEqualTo("redirect:/login/mfa-challenge?error=expired");
    assertThat(session.getAttribute(MFA_PENDING_AUTH_SESSION_KEY)).isNull();
    assertThat(session.getAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY)).isNull();
  }

  @Test
  void challengeViewPopulatesModelAndRendersTemplate() {
    MockHttpSession session = new MockHttpSession();
    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("alice@example.com", null);
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().plusSeconds(300));
    Model model = new ConcurrentModel();

    String view = controller.challengeView(null, session, model);
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
  void verifyChallengeRedirectsWhenExpiredAndClearsSession() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();
    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("alice@example.com", null);
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().minusSeconds(1));

    String result = controller.verifyChallenge("123456", request, response, session);
    assertThat(result).isEqualTo("redirect:/login/mfa-challenge?error=expired");
    assertThat(session.getAttribute(MFA_PENDING_AUTH_SESSION_KEY)).isNull();
  }

  @Test
  void verifyChallengeTracksAttemptsAndRedirectsWithErrorWhenCodeInvalid() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();
    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("bob@example.com", null);
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().plusSeconds(300));
    session.setAttribute(MFA_ATTEMPT_COUNT_KEY, 0);

    when(mfaChallengeService.verifyCode("bob@example.com", "invalid")).thenReturn(false);

    String result = controller.verifyChallenge("invalid", request, response, session);
    assertThat(result).isEqualTo("redirect:/login/mfa-challenge?error=invalid_code");
    assertThat(session.getAttribute(MFA_ATTEMPT_COUNT_KEY)).isEqualTo(1);
  }

  @Test
  void verifyChallengeLocksAccountAfterMaxFailedAttempts() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();
    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("bob@example.com", null);
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().plusSeconds(300));
    session.setAttribute(MFA_ATTEMPT_COUNT_KEY, MAX_MFA_ATTEMPTS - 1);

    when(mfaChallengeService.verifyCode("bob@example.com", "invalid")).thenReturn(false);

    String result = controller.verifyChallenge("invalid", request, response, session);
    assertThat(result).isEqualTo("redirect:/login/mfa-challenge?error=locked");
    assertThat(session.getAttribute(MFA_PENDING_AUTH_SESSION_KEY)).isNull();
    assertThat(session.getAttribute(MFA_ATTEMPT_COUNT_KEY)).isNull();
  }

  @Test
  void verifyChallengeLocksImmediatelyWhenAlreadyAtMaxAttempts() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();
    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("bob@example.com", null);
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().plusSeconds(300));
    session.setAttribute(MFA_ATTEMPT_COUNT_KEY, MAX_MFA_ATTEMPTS);

    String result = controller.verifyChallenge("123456", request, response, session);
    assertThat(result).isEqualTo("redirect:/login/mfa-challenge?error=locked");
    assertThat(session.getAttribute(MFA_PENDING_AUTH_SESSION_KEY)).isNull();
  }

  @Test
  void verifyChallengeEnrichesAuthoritiesAndRedirectsToTargetUrl() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();

    Authentication pendingAuth = new UsernamePasswordAuthenticationToken(
        "carol@example.com",
        null,
        List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_TARGET_URL_SESSION_KEY, "/oauth2/authorize?client_id=demo");
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().plusSeconds(300));

    when(mfaChallengeService.verifyCode("carol@example.com", "654321")).thenReturn(true);

    String result = controller.verifyChallenge("654321", request, response, session);

    assertThat(result).isEqualTo("redirect:/oauth2/authorize?client_id=demo");
    assertThat(session.getAttribute(MFA_PENDING_AUTH_SESSION_KEY)).isNull();
    assertThat(session.getAttribute(MFA_TARGET_URL_SESSION_KEY)).isNull();
    assertThat(session.getAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY)).isNull();
    assertThat(session.getAttribute(MFA_ATTEMPT_COUNT_KEY)).isNull();

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getAuthorities()).extracting("authority")
        .contains("ROLE_USER", "MFA_VERIFIED");
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://malicious.com/phishing",
      "http://attacker.org",
      "//evil.com/steal",
      "/\\evil.com",
      "/login\r\nInjected-Header: value"
  })
  void verifyChallengePreventsOpenRedirectAndFallsBackToRoot(String untrustedUrl) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();

    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("dave@example.com", null);
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_TARGET_URL_SESSION_KEY, untrustedUrl);
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().plusSeconds(300));

    when(mfaChallengeService.verifyCode("dave@example.com", "112233")).thenReturn(true);

    String result = controller.verifyChallenge("112233", request, response, session);
    assertThat(result).isEqualTo("redirect:/");
  }

  @Test
  void verifyChallengeRedirectsToRootWhenTargetUrlNotSet() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpSession session = new MockHttpSession();

    Authentication pendingAuth = new UsernamePasswordAuthenticationToken("dave@example.com", null);
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, pendingAuth);
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().plusSeconds(300));

    when(mfaChallengeService.verifyCode("dave@example.com", "112233")).thenReturn(true);

    String result = controller.verifyChallenge("112233", request, response, session);
    assertThat(result).isEqualTo("redirect:/");
  }
}
