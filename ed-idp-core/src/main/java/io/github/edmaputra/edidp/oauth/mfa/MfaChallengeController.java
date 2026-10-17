package io.github.edmaputra.edidp.oauth.mfa;

import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MAX_MFA_ATTEMPTS;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_ATTEMPT_COUNT_KEY;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_CHALLENGE_EXPIRES_AT_KEY;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_PENDING_AUTH_SESSION_KEY;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_TARGET_URL_SESSION_KEY;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller providing the interactive MFA login challenge page and verification endpoint.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
@RequestMapping("/login/mfa-challenge")
public class MfaChallengeController {

  private final MfaChallengeService mfaChallengeService;
  private final SecurityContextRepository securityContextRepository;

  public MfaChallengeController(MfaChallengeService mfaChallengeService) {
    this.mfaChallengeService = Objects.requireNonNull(mfaChallengeService, "mfaChallengeService must not be null");
    this.securityContextRepository = new HttpSessionSecurityContextRepository();
  }

  @GetMapping
  public String challengeView(
      @RequestParam(value = "error", required = false) String error,
      HttpSession session,
      Model model) {
    if ("expired".equals(error) || "locked".equals(error)) {
      return "login-mfa-challenge";
    }

    Authentication pendingAuth = (Authentication) session.getAttribute(MFA_PENDING_AUTH_SESSION_KEY);
    if (pendingAuth == null) {
      return "redirect:/login";
    }

    Instant expiresAt = (Instant) session.getAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY);
    if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
      clearMfaSession(session);
      return "redirect:/login/mfa-challenge?error=expired";
    }

    model.addAttribute("username", pendingAuth.getName());
    return "login-mfa-challenge";
  }

  @PostMapping
  public String verifyChallenge(
      @RequestParam("code") String code,
      HttpServletRequest request,
      HttpServletResponse response,
      HttpSession session) {

    Authentication pendingAuth = (Authentication) session.getAttribute(MFA_PENDING_AUTH_SESSION_KEY);
    if (pendingAuth == null) {
      return "redirect:/login";
    }

    Instant expiresAt = (Instant) session.getAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY);
    if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
      clearMfaSession(session);
      return "redirect:/login/mfa-challenge?error=expired";
    }

    Integer attempts = (Integer) session.getAttribute(MFA_ATTEMPT_COUNT_KEY);
    int currentAttempts = (attempts != null) ? attempts : 0;
    if (currentAttempts >= MAX_MFA_ATTEMPTS) {
      clearMfaSession(session);
      return "redirect:/login/mfa-challenge?error=locked";
    }

    boolean verified = mfaChallengeService.verifyCode(pendingAuth.getName(), code);
    if (!verified) {
      currentAttempts++;
      if (currentAttempts >= MAX_MFA_ATTEMPTS) {
        clearMfaSession(session);
        return "redirect:/login/mfa-challenge?error=locked";
      }
      session.setAttribute(MFA_ATTEMPT_COUNT_KEY, currentAttempts);
      return "redirect:/login/mfa-challenge?error=invalid_code";
    }

    // Enrich authenticated principal with MFA verification marker
    Set<GrantedAuthority> authorities = new HashSet<>(pendingAuth.getAuthorities());
    authorities.add(new SimpleGrantedAuthority("MFA_VERIFIED"));

    UsernamePasswordAuthenticationToken fullyAuthenticated =
        UsernamePasswordAuthenticationToken.authenticated(
            pendingAuth.getPrincipal(),
            pendingAuth.getCredentials(),
            authorities
        );

    SecurityContext newContext = SecurityContextHolder.createEmptyContext();
    newContext.setAuthentication(fullyAuthenticated);
    SecurityContextHolder.setContext(newContext);
    securityContextRepository.saveContext(newContext, request, response);

    String targetUrl = (String) session.getAttribute(MFA_TARGET_URL_SESSION_KEY);
    clearMfaSession(session);

    String safeRedirect = sanitizeTargetUrl(targetUrl, request);
    return "redirect:" + safeRedirect;
  }

  static String sanitizeTargetUrl(String targetUrl, HttpServletRequest request) {
    if (targetUrl == null || targetUrl.isBlank()) {
      return "/";
    }

    if (isSafeLocalUrl(targetUrl)) {
      return targetUrl;
    }

    try {
      java.net.URI uri = java.net.URI.create(targetUrl);
      if (uri.isAbsolute() && uri.getHost() != null && request != null) {
        String serverName = request.getServerName();
        if (serverName != null && uri.getHost().equalsIgnoreCase(serverName)) {
          int serverPort = request.getServerPort();
          int uriPort = uri.getPort();
          boolean portMatches = (uriPort == -1)
              || (uriPort == serverPort)
              || ("http".equalsIgnoreCase(uri.getScheme()) && serverPort == 80 && uriPort == 80)
              || ("https".equalsIgnoreCase(uri.getScheme()) && serverPort == 443 && uriPort == 443);

          if (portMatches) {
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            String candidate = (path != null ? path : "/") + (query != null ? "?" + query : "");
            if (isSafeLocalUrl(candidate)) {
              return candidate;
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Malformed URI, fall back to root
    }

    return "/";
  }

  static boolean isSafeLocalUrl(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    return url.startsWith("/")
        && !url.startsWith("//")
        && !url.contains("\\")
        && !url.contains("\r")
        && !url.contains("\n");
  }

  private void clearMfaSession(HttpSession session) {
    session.removeAttribute(MFA_PENDING_AUTH_SESSION_KEY);
    session.removeAttribute(MFA_TARGET_URL_SESSION_KEY);
    session.removeAttribute(MFA_CHALLENGE_EXPIRES_AT_KEY);
    session.removeAttribute(MFA_ATTEMPT_COUNT_KEY);
  }
}
