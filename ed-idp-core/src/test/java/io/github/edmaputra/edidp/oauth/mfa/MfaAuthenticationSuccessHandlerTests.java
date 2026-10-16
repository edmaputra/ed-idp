package io.github.edmaputra.edidp.oauth.mfa;

import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_PENDING_AUTH_SESSION_KEY;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_TARGET_URL_SESSION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Unit tests verifying MfaAuthenticationSuccessHandler interception, session preservation, and delegation.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class MfaAuthenticationSuccessHandlerTests {

  private MfaChallengeService mfaChallengeService;
  private MfaAuthenticationSuccessHandler handler;

  @BeforeEach
  void setUp() {
    mfaChallengeService = mock(MfaChallengeService.class);
    handler = new MfaAuthenticationSuccessHandler(
        mfaChallengeService,
        new HttpSessionSecurityContextRepository(),
        "/login/mfa-challenge",
        "/custom-target"
    );
  }

  @Test
  void nonMfaUserRedirectsToDefaultTargetWithoutChallengeInterception() throws IOException, ServletException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    Authentication auth = new UsernamePasswordAuthenticationToken(
        "alice@example.com",
        "password",
        List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );

    when(mfaChallengeService.isMfaRequired("alice@example.com")).thenReturn(false);

    handler.onAuthenticationSuccess(request, response, auth);

    assertThat(response.getRedirectedUrl()).isEqualTo("/custom-target");
    assertThat(request.getSession().getAttribute(MFA_PENDING_AUTH_SESSION_KEY)).isNull();
  }

  @Test
  void mfaEnrolledUserInterceptsAuthenticationAndRedirectsToChallenge() throws IOException, ServletException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    Authentication auth = new UsernamePasswordAuthenticationToken(
        "bob@example.com",
        "password",
        List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );

    SecurityContext existingContext = SecurityContextHolder.createEmptyContext();
    existingContext.setAuthentication(auth);
    SecurityContextHolder.setContext(existingContext);

    when(mfaChallengeService.isMfaRequired("bob@example.com")).thenReturn(true);

    handler.onAuthenticationSuccess(request, response, auth);

    assertThat(response.getRedirectedUrl()).isEqualTo("/login/mfa-challenge");
    assertThat(request.getSession().getAttribute(MFA_PENDING_AUTH_SESSION_KEY)).isEqualTo(auth);
    assertThat(request.getSession().getAttribute(MFA_TARGET_URL_SESSION_KEY)).isEqualTo("/custom-target");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
