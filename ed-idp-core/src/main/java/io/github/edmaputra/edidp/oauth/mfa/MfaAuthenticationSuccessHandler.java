package io.github.edmaputra.edidp.oauth.mfa;

import io.github.edmaputra.edidp.audit.AuditEventPort;
import io.github.edmaputra.edidp.audit.SecurityAuditAction;
import io.github.edmaputra.edidp.audit.SecurityAuditEvent;
import io.github.edmaputra.edidp.audit.SecurityAuditStatus;
import io.github.edmaputra.edidp.tenancy.TenantContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
  public static final String MFA_CHALLENGE_EXPIRES_AT_KEY = "MFA_CHALLENGE_EXPIRES_AT";
  public static final String MFA_ATTEMPT_COUNT_KEY = "MFA_ATTEMPT_COUNT";
  public static final long MFA_CHALLENGE_TTL_SECONDS = 300L;
  public static final int MAX_MFA_ATTEMPTS = 5;

  private final MfaChallengeService mfaChallengeService;
  private final SecurityContextRepository securityContextRepository;
  private final RequestCache requestCache;
  private final SavedRequestAwareAuthenticationSuccessHandler defaultHandler;
  private final String challengeUrl;
  private final String defaultTargetUrl;
  private final AuditEventPort auditEventPort;

  public MfaAuthenticationSuccessHandler(
      MfaChallengeService mfaChallengeService,
      SecurityContextRepository securityContextRepository,
      String challengeUrl,
      String defaultTargetUrl,
      AuditEventPort auditEventPort) {
    this.mfaChallengeService = Objects.requireNonNull(mfaChallengeService, "mfaChallengeService must not be null");
    this.securityContextRepository = (securityContextRepository != null)
        ? securityContextRepository
        : new HttpSessionSecurityContextRepository();
    this.challengeUrl = (challengeUrl != null && !challengeUrl.isBlank()) ? challengeUrl : "/login/mfa-challenge";
    this.defaultTargetUrl = (defaultTargetUrl != null && !defaultTargetUrl.isBlank()) ? defaultTargetUrl : "/";
    this.auditEventPort = (auditEventPort != null) ? auditEventPort : AuditEventPort.noop();
    this.requestCache = new HttpSessionRequestCache();
    this.defaultHandler = new SavedRequestAwareAuthenticationSuccessHandler();
    this.defaultHandler.setDefaultTargetUrl(this.defaultTargetUrl);
  }

  public MfaAuthenticationSuccessHandler(
      MfaChallengeService mfaChallengeService,
      SecurityContextRepository securityContextRepository,
      String challengeUrl,
      String defaultTargetUrl) {
    this(mfaChallengeService, securityContextRepository, challengeUrl, defaultTargetUrl, AuditEventPort.noop());
  }

  public MfaAuthenticationSuccessHandler(MfaChallengeService mfaChallengeService, AuditEventPort auditEventPort) {
    this(mfaChallengeService, new HttpSessionSecurityContextRepository(), "/login/mfa-challenge", "/", auditEventPort);
  }

  public MfaAuthenticationSuccessHandler(MfaChallengeService mfaChallengeService) {
    this(mfaChallengeService, new HttpSessionSecurityContextRepository(), "/login/mfa-challenge", "/", AuditEventPort.noop());
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

    // Sanitize credentials from authentication token before preserving in session
    Authentication sanitizedAuth = UsernamePasswordAuthenticationToken.authenticated(
        authentication.getPrincipal(),
        null,
        authentication.getAuthorities()
    );

    // Preserve pending authentication in session with TTL and attempt counter
    var session = request.getSession();
    session.setAttribute(MFA_PENDING_AUTH_SESSION_KEY, sanitizedAuth);
    session.setAttribute(MFA_TARGET_URL_SESSION_KEY, targetUrl);
    session.setAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY, Instant.now().plusSeconds(MFA_CHALLENGE_TTL_SECONDS));
    session.setAttribute(MFA_ATTEMPT_COUNT_KEY, 0);

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.MFA_CHALLENGE)
        .status(SecurityAuditStatus.SUCCESS)
        .principal(authentication.getName())
        .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
        .detail("target_url", targetUrl)
        .build();
    auditEventPort.record(auditEvent);

    // Clear SecurityContext until second factor is satisfied
    SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
    SecurityContextHolder.setContext(emptyContext);
    securityContextRepository.saveContext(emptyContext, request, response);

    response.sendRedirect(challengeUrl);
  }
}
