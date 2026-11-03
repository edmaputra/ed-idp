package io.github.edmaputra.edidp.consent;

import io.github.edmaputra.edidp.audit.AuditEventPort;
import io.github.edmaputra.edidp.audit.SecurityAuditAction;
import io.github.edmaputra.edidp.audit.SecurityAuditEvent;
import io.github.edmaputra.edidp.audit.SecurityAuditStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service orchestrating OAuth 2.1 user consent evaluations, persistence, and audit logging.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class AuthorizationConsentService {

  private final ConsentStore consentStore;
  private final AuditEventPort auditEventPort;

  @Autowired
  public AuthorizationConsentService(ConsentStore consentStore, AuditEventPort auditEventPort) {
    this.consentStore = consentStore;
    this.auditEventPort = (auditEventPort != null) ? auditEventPort : AuditEventPort.noop();
  }

  public AuthorizationConsentService(ConsentStore consentStore) {
    this(consentStore, AuditEventPort.noop());
  }

  public ConsentDecisionResult checkConsent(CheckConsentCommand command) {
    if (consentStore.isMissingConsent(
        command.principalName(),
        command.registeredClientId(),
        command.requestedScopes())) {
      return ConsentDecisionResult.consentNeeded();
    }

    var authorizedScopes = consentStore.getAuthorizedScopes(
        command.principalName(),
        command.registeredClientId());

    return ConsentDecisionResult.noConsentNeeded(authorizedScopes);
  }

  /**
   * Approves user consent for the given command parameters and records an audit event.
   *
   * @param command          the consent check command containing principal, registered client id, and scopes
   * @param clientIdentifier the public OAuth2 client identifier for audit logging
   */
  public void approveConsent(CheckConsentCommand command, String clientIdentifier) {
    consentStore.saveConsent(
        command.principalName(),
        command.registeredClientId(),
        command.requestedScopes());

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.CONSENT_GRANTED)
        .status(SecurityAuditStatus.SUCCESS)
        .principal(command.principalName())
        .clientIdentifier(clientIdentifier != null && !clientIdentifier.isBlank() ? clientIdentifier : command.registeredClientId())
        .detail("approved_scopes", command.requestedScopes())
        .build();
    auditEventPort.record(auditEvent);
  }

  /**
   * Approves user consent for the given command parameters using the registered client id as identifier.
   *
   * @param command the consent check command
   */
  public void approveConsent(CheckConsentCommand command) {
    approveConsent(command, command.registeredClientId());
  }

  /**
   * Denies user consent for a client and records an audit event.
   *
   * @param principalName    the user denying consent
   * @param clientIdentifier the public OAuth2 client identifier
   * @param reason           the reason for denial (e.g. "user_denied", "empty_scopes")
   */
  public void denyConsent(String principalName, String clientIdentifier, String reason) {
    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.CONSENT_DENIED)
        .status(SecurityAuditStatus.SUCCESS)
        .principal(principalName)
        .clientIdentifier(clientIdentifier)
        .detail("reason", reason)
        .build();
    auditEventPort.record(auditEvent);
  }
}
