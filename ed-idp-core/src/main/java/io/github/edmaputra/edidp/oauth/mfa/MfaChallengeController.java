package io.github.edmaputra.edidp.oauth.mfa;

import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_PENDING_AUTH_SESSION_KEY;
import static io.github.edmaputra.edidp.oauth.mfa.MfaAuthenticationSuccessHandler.MFA_TARGET_URL_SESSION_KEY;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
  public String challengeView(HttpSession session, Model model) {
    Authentication pendingAuth = (Authentication) session.getAttribute(MFA_PENDING_AUTH_SESSION_KEY);
    if (pendingAuth == null) {
      return "redirect:/login";
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

    boolean verified = mfaChallengeService.verifyCode(pendingAuth.getName(), code);
    if (!verified) {
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
    session.removeAttribute(MFA_PENDING_AUTH_SESSION_KEY);
    session.removeAttribute(MFA_TARGET_URL_SESSION_KEY);

    if (targetUrl != null && !targetUrl.isBlank()) {
      return "redirect:" + targetUrl;
    }
    return "redirect:/";
  }
}
