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
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Authentication failure handler recording structured security audit logs on rejected token
 * and client authentication requests and outputting RFC 6749 compliant error responses.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AuditOAuth2ErrorAuthenticationFailureHandler implements AuthenticationFailureHandler {

  private final AuditEventPort auditEventPort;
  private final HttpMessageConverter<OAuth2Error> errorResponseConverter = new OAuth2ErrorHttpMessageConverter();

  public AuditOAuth2ErrorAuthenticationFailureHandler(AuditEventPort auditEventPort) {
    this.auditEventPort = Objects.requireNonNull(auditEventPort, "auditEventPort must not be null");
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException exception) throws IOException, ServletException {

    String grantType = request.getParameter("grant_type");
    String clientId = request.getParameter("client_id");
    String errorCode = OAuth2ErrorCodes.SERVER_ERROR;
    String errorDescription = exception.getMessage();
    OAuth2Error error = null;

    if (exception instanceof OAuth2AuthenticationException oAuth2Exception) {
      error = oAuth2Exception.getError();
      if (error != null) {
        errorCode = error.getErrorCode();
        errorDescription = error.getDescription();
      }
    } else {
      error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, errorDescription, null);
    }

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.TOKEN_FAILED)
        .status(SecurityAuditStatus.FAILURE)
        .principal((clientId != null && !clientId.isBlank()) ? clientId : "anonymous")
        .clientIdentifier(clientId)
        .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
        .detail("error_code", errorCode)
        .detail("error_description", errorDescription)
        .detail("grant_type", (grantType != null) ? grantType : "unknown")
        .build();

    auditEventPort.record(auditEvent);

    ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
    if (OAuth2ErrorCodes.INVALID_CLIENT.equals(errorCode)) {
      httpResponse.setStatusCode(HttpStatus.UNAUTHORIZED);
    } else {
      httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
    }
    this.errorResponseConverter.write(error, null, httpResponse);
  }
}
