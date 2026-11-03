package io.github.edmaputra.edidp.tokens.introspection;

import io.github.edmaputra.edidp.audit.AuditEventPort;
import io.github.edmaputra.edidp.audit.SecurityAuditAction;
import io.github.edmaputra.edidp.audit.SecurityAuditEvent;
import io.github.edmaputra.edidp.audit.SecurityAuditStatus;
import io.github.edmaputra.edidp.authorization.AuthorizationPolicyResult;
import io.github.edmaputra.edidp.authorization.AuthorizationPolicyService;
import io.github.edmaputra.edidp.authorization.ValidateScopeCommand;
import io.github.edmaputra.edidp.clients.ClientAuthenticationResult;
import io.github.edmaputra.edidp.clients.ClientAuthenticationService;
import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;

/**
 * Service coordinating client authentication, scope validation, and token introspection logic.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class IntrospectTokenService {

  private static final String INTROSPECTION_SCOPE = "introspection";

  private final ClientAuthenticationService clientAuthenticationService;
  private final TokenIntrospectionValidator tokenIntrospectionValidator;
  private final AuthorizationPolicyService authorizationPolicyService;
  private final AuditEventPort auditEventPort;

  @Autowired
  public IntrospectTokenService(
      ClientAuthenticationService clientAuthenticationService,
      TokenIntrospectionValidator tokenIntrospectionValidator,
      AuthorizationPolicyService authorizationPolicyService,
      AuditEventPort auditEventPort) {
    this.clientAuthenticationService = Objects.requireNonNull(clientAuthenticationService, "clientAuthenticationService must not be null");
    this.tokenIntrospectionValidator = Objects.requireNonNull(tokenIntrospectionValidator, "tokenIntrospectionValidator must not be null");
    this.authorizationPolicyService = Objects.requireNonNull(authorizationPolicyService, "authorizationPolicyService must not be null");
    this.auditEventPort = (auditEventPort != null) ? auditEventPort : AuditEventPort.noop();
  }

  public IntrospectTokenService(
      ClientAuthenticationService clientAuthenticationService,
      TokenIntrospectionValidator tokenIntrospectionValidator,
      AuthorizationPolicyService authorizationPolicyService) {
    this(clientAuthenticationService, tokenIntrospectionValidator, authorizationPolicyService, AuditEventPort.noop());
  }

  public IntrospectTokenResult introspect(IntrospectTokenCommand command) {
    if (command.token() == null || command.token().isEmpty()) {
      return error(
          IntrospectTokenResult.Status.BAD_REQUEST,
          "invalid_request",
          "Missing required parameter: token",
          "anonymous",
          null);
    }

    ClientAuthenticationResult authentication =
        clientAuthenticationService.authenticateBasic(command.authorizationHeader());
    if (!authentication.authenticated()) {
      return error(
          IntrospectTokenResult.Status.UNAUTHORIZED,
          "invalid_client",
          "Client authentication failed",
          authentication.clientId() != null ? authentication.clientId() : "anonymous",
          authentication.clientId());
    }

    // Validate client has introspection scope via authorization policy
    ValidateScopeCommand scopeValidation = new ValidateScopeCommand(
        authentication.clientId(),
        AuthorizationGrantType.CLIENT_CREDENTIALS,
        INTROSPECTION_SCOPE);
    AuthorizationPolicyResult policyResult = authorizationPolicyService.validateScope(scopeValidation);

    if (!policyResult.authorized()) {
      return error(
          IntrospectTokenResult.Status.FORBIDDEN,
          policyResult.error(),
          policyResult.errorDescription(),
          authentication.clientId(),
          authentication.clientId());
    }

    Map<String, Object> introspectionResponse = tokenIntrospectionValidator.introspect(command.token());

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.TOKEN_INTROSPECTED)
        .status(SecurityAuditStatus.SUCCESS)
        .principal(authentication.clientId())
        .clientIdentifier(authentication.clientId())
        .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
        .detail("active", introspectionResponse.get("active"))
        .build();
    auditEventPort.record(auditEvent);

    return new IntrospectTokenResult(IntrospectTokenResult.Status.OK, introspectionResponse);
  }

  private IntrospectTokenResult error(
      IntrospectTokenResult.Status status,
      String error,
      String errorDescription,
      String principal,
      String clientId) {
    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("error", error);
    errorResponse.put("error_description", errorDescription);

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.TOKEN_INTROSPECTED)
        .status(SecurityAuditStatus.FAILURE)
        .principal(principal != null ? principal : "anonymous")
        .clientIdentifier(clientId)
        .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
        .detail("error", error)
        .detail("error_description", errorDescription)
        .build();
    auditEventPort.record(auditEvent);

    return new IntrospectTokenResult(status, errorResponse);
  }
}
