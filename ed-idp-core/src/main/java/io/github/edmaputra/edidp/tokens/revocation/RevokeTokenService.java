package io.github.edmaputra.edidp.tokens.revocation;

import io.github.edmaputra.edidp.audit.AuditEventPort;
import io.github.edmaputra.edidp.audit.SecurityAuditAction;
import io.github.edmaputra.edidp.audit.SecurityAuditEvent;
import io.github.edmaputra.edidp.audit.SecurityAuditStatus;
import io.github.edmaputra.edidp.clients.ClientAuthenticationResult;
import io.github.edmaputra.edidp.clients.ClientAuthenticationService;
import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service coordinating client authentication, scope validation, and token revocation logic.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class RevokeTokenService {

  private final ClientAuthenticationService clientAuthenticationService;
  private final TokenRevoker tokenRevoker;
  private final RevocationAuthorizationService revocationAuthorizationService;
  private final AuditEventPort auditEventPort;

  @Autowired
  public RevokeTokenService(
      ClientAuthenticationService clientAuthenticationService,
      TokenRevoker tokenRevoker,
      RevocationAuthorizationService revocationAuthorizationService,
      AuditEventPort auditEventPort) {
    this.clientAuthenticationService = Objects.requireNonNull(clientAuthenticationService, "clientAuthenticationService must not be null");
    this.tokenRevoker = Objects.requireNonNull(tokenRevoker, "tokenRevoker must not be null");
    this.revocationAuthorizationService = Objects.requireNonNull(revocationAuthorizationService, "revocationAuthorizationService must not be null");
    this.auditEventPort = (auditEventPort != null) ? auditEventPort : AuditEventPort.noop();
  }

  public RevokeTokenService(
      ClientAuthenticationService clientAuthenticationService,
      TokenRevoker tokenRevoker,
      RevocationAuthorizationService revocationAuthorizationService) {
    this(clientAuthenticationService, tokenRevoker, revocationAuthorizationService, AuditEventPort.noop());
  }

  public RevokeTokenResult revoke(RevokeTokenCommand command) {
    if (command.token() == null || command.token().isBlank()) {
      return error(
          RevokeTokenResult.Status.BAD_REQUEST,
          "invalid_request",
          "Missing required parameter: token",
          "anonymous",
          null);
    }

    ClientAuthenticationResult authentication =
        clientAuthenticationService.authenticateBasic(command.authorizationHeader());
    if (!authentication.authenticated()) {
      return error(
          RevokeTokenResult.Status.UNAUTHORIZED,
          "invalid_client",
          "Client authentication failed",
          authentication.clientId() != null ? authentication.clientId() : "anonymous",
          authentication.clientId());
    }

    if (!revocationAuthorizationService.canRevoke(authentication.scopes())) {
      return error(
          RevokeTokenResult.Status.FORBIDDEN,
          "invalid_scope",
          "Client does not have revocation scope",
          authentication.clientId(),
          authentication.clientId());
    }

    tokenRevoker.revokeTokenForClient(
        command.token(),
        command.tokenTypeHint(),
        authentication.registeredClientId());

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.TOKEN_REVOKED)
        .status(SecurityAuditStatus.SUCCESS)
        .principal(authentication.clientId())
        .clientIdentifier(authentication.clientId())
        .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
        .detail("token_type_hint", command.tokenTypeHint() != null ? command.tokenTypeHint() : "unknown")
        .build();
    auditEventPort.record(auditEvent);

    return new RevokeTokenResult(RevokeTokenResult.Status.OK, Map.of());
  }

  private RevokeTokenResult error(
      RevokeTokenResult.Status status,
      String error,
      String errorDescription,
      String principal,
      String clientId) {
    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("error", error);
    errorResponse.put("error_description", errorDescription);

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.TOKEN_REVOKED)
        .status(SecurityAuditStatus.FAILURE)
        .principal(principal != null ? principal : "anonymous")
        .clientIdentifier(clientId)
        .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
        .detail("error", error)
        .detail("error_description", errorDescription)
        .build();
    auditEventPort.record(auditEvent);

    return new RevokeTokenResult(status, errorResponse);
  }
}
