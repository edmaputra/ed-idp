package io.github.edmaputra.edidp.oauth;

import io.github.edmaputra.edidp.audit.AuditEventPort;
import io.github.edmaputra.edidp.audit.SecurityAuditAction;
import io.github.edmaputra.edidp.audit.SecurityAuditEvent;
import io.github.edmaputra.edidp.audit.SecurityAuditStatus;
import io.github.edmaputra.edidp.tenancy.TenantContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AccessTokenResponseAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Authentication success handler decorating the default token endpoint success handler
 * to record structured security audit logs on token issuance.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AuditOAuth2AccessTokenResponseHandler implements AuthenticationSuccessHandler {

  private final AuthenticationSuccessHandler delegate;
  private final AuditEventPort auditEventPort;

  public AuditOAuth2AccessTokenResponseHandler(
      AuthenticationSuccessHandler delegate,
      AuditEventPort auditEventPort) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    this.auditEventPort = Objects.requireNonNull(auditEventPort, "auditEventPort must not be null");
  }

  public AuditOAuth2AccessTokenResponseHandler(AuditEventPort auditEventPort) {
    this(new OAuth2AccessTokenResponseAuthenticationSuccessHandler(), auditEventPort);
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) throws IOException, ServletException {

    if (authentication instanceof OAuth2AccessTokenAuthenticationToken tokenAuth) {
      RegisteredClient client = tokenAuth.getRegisteredClient();
      String clientId = (client != null) ? client.getClientId() : null;
      Object principalObj = tokenAuth.getPrincipal();
      String principal = (principalObj instanceof Authentication auth)
          ? auth.getName()
          : (principalObj != null ? principalObj.toString() : clientId);
      String grantType = request.getParameter("grant_type");

      SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
          .action(SecurityAuditAction.TOKEN_ISSUED)
          .status(SecurityAuditStatus.SUCCESS)
          .principal(principal)
          .clientIdentifier(clientId)
          .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
          .detail("grant_type", (grantType != null) ? grantType : "unknown")
          .detail("scopes", tokenAuth.getAccessToken().getScopes())
          .detail("token_type", tokenAuth.getAccessToken().getTokenType().getValue())
          .build();

      auditEventPort.record(auditEvent);
    }

    delegate.onAuthenticationSuccess(request, response, authentication);
  }
}
