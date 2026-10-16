package io.github.edmaputra.edidp.oauth.mfa;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * Authentication success handler intercepting form login when MFA is enabled on the user account,
 * pausing session establishment and redirecting to the intermediate MFA challenge flow.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class MfaAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  public static final String MFA_PENDING_AUTH_SESSION_KEY = "MFA_PENDING_AUTH";
  public static final String MFA_TARGET_URL_SESSION_KEY = "MFA_TARGET_URL";

  private final MfaChallengeService mfaChallengeService;
  private final SecurityContextRepository securityContextRepository;
  private final RequestCache requestCache;
  private final SavedRequestAwareAuthenticationSuccessHandler defaultHandler;
  private final String challengeUrl;
  private final String defaultTargetUrl;

  public MfaAuthenticationSuccessHandler(
      MfaChallengeService mfaChallengeService,
      SecurityContextRepository securityContextRepository,
      String challengeUrl,
      String defaultTargetUrl) {
    this.mfaChallengeService = Objects.requireNonNull(mfaChallengeService, "mfaChallengeService must not be null");
    this.securityContextRepository = (securityContextRepository != null)
        ? securityContextRepository
        : new HttpSessionSecurityContextRepository();
    this.challengeUrl = (challengeUrl != null && !challengeUrl.isBlank()) ? challengeUrl : "/login/mfa-challenge";
    this.defaultTargetUrl = (defaultTargetUrl != null && !defaultTargetUrl.isBlank()) ? defaultTargetUrl : "/";
    this.requestCache = new HttpSessionRequestCache();
    this.defaultHandler = new SavedRequestAwareAuthenticationSuccessHandler();
    this.defaultHandler.setDefaultTargetUrl(this.defaultTargetUrl);
  }

  public MfaAuthenticationSuccessHandler(MfaChallengeService mfaChallengeService) {
    this(mfaChallengeService, new HttpSessionSecurityContextRepository(), "/login/mfa-challenge", "/");
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) throws IOException, ServletException {

    if (!mfaChallengeService.isMfaRequired(authentication.getName())) {
      defaultHandler.onAuthenticationSuccess(request, response, authentication);
      return;
    }

    // Capture target redirect URL from saved request cache
    SavedRequest savedRequest = requestCache.getRequest(request, response);
    String targetUrl = (savedRequest != null) ? savedRequest.getRedirectUrl() : this.defaultTargetUrl;

    // Preserve pending authentication in session
    request.getSession().setAttribute(MFA_PENDING_AUTH_SESSION_KEY, authentication);
    request.getSession().setAttribute(MFA_TARGET_URL_SESSION_KEY, targetUrl);

    // Clear SecurityContext until second factor is satisfied
    SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
    SecurityContextHolder.setContext(emptyContext);
    securityContextRepository.saveContext(emptyContext, request, response);

    response.sendRedirect(challengeUrl);
  }
}
