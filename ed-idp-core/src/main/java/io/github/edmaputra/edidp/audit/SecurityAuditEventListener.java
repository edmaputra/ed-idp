package io.github.edmaputra.edidp.audit;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.util.Objects;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Spring Security event listener translating core framework authentication events into
 * structured security audit records.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class SecurityAuditEventListener {

  private final AuditEventPort auditEventPort;

  public SecurityAuditEventListener(AuditEventPort auditEventPort) {
    this.auditEventPort = Objects.requireNonNull(auditEventPort, "auditEventPort must not be null");
  }

  @EventListener
  public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
    Authentication auth = event.getAuthentication();
    if (auth == null) {
      return;
    }

    // Only audit interactive user authentications to prevent double-auditing OAuth2 token endpoint calls
    if (auth instanceof UsernamePasswordAuthenticationToken) {
      String ip = extractIpAddress(auth);
      SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
          .action(SecurityAuditAction.LOGIN_SUCCESS)
          .status(SecurityAuditStatus.SUCCESS)
          .principal(auth.getName())
          .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
          .ipAddress(ip)
          .detail("auth_type", auth.getClass().getSimpleName())
          .build();

      auditEventPort.record(auditEvent);
    }
  }

  @EventListener
  public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
    Authentication auth = event.getAuthentication();
    if (auth != null && !(auth instanceof UsernamePasswordAuthenticationToken)) {
      // Exclude OAuth2 client authentication failures which are handled by OAuth2 failure handlers
      return;
    }

    String principal = (auth != null && auth.getName() != null) ? auth.getName() : "unknown";
    String error = (event.getException() != null) ? event.getException().getMessage() : "Authentication failed";
    String ip = extractIpAddress(auth);

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.LOGIN_FAILURE)
        .status(SecurityAuditStatus.FAILURE)
        .principal(principal)
        .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
        .ipAddress(ip)
        .detail("error", error)
        .detail("exception", event.getException() != null ? event.getException().getClass().getSimpleName() : "unknown")
        .build();

    auditEventPort.record(auditEvent);
  }

  @EventListener
  public void onLogoutSuccess(LogoutSuccessEvent event) {
    Authentication auth = event.getAuthentication();
    String principal = (auth != null && auth.getName() != null) ? auth.getName() : "anonymous";
    String ip = extractIpAddress(auth);

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.LOGOUT)
        .status(SecurityAuditStatus.SUCCESS)
        .principal(principal)
        .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
        .ipAddress(ip)
        .build();

    auditEventPort.record(auditEvent);
  }

  private static String extractIpAddress(Authentication authentication) {
    if (authentication != null && authentication.getDetails() instanceof WebAuthenticationDetails webDetails) {
      String remoteAddress = webDetails.getRemoteAddress();
      if (remoteAddress != null && !remoteAddress.isBlank()) {
        return remoteAddress;
      }
    }
    return AuditContextUtils.resolveClientIp();
  }
}
