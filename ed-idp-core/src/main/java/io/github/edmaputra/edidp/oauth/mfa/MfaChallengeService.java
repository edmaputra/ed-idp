package io.github.edmaputra.edidp.oauth.mfa;

import io.github.edmaputra.edidp.audit.AuditEventPort;
import io.github.edmaputra.edidp.audit.SecurityAuditAction;
import io.github.edmaputra.edidp.audit.SecurityAuditEvent;
import io.github.edmaputra.edidp.audit.SecurityAuditStatus;
import io.github.edmaputra.edidp.tenancy.TenantContext;
import io.github.edmaputra.iam.domain.auth.mfa.TotpGenerator;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating Multi-Factor Authentication challenges, checking enrollment requirements,
 * verifying RFC 6238 TOTP codes and backup recovery codes, and emitting security audit events.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class MfaChallengeService {

  private static final Logger log = LoggerFactory.getLogger(MfaChallengeService.class);
  private static final int TOTP_WINDOW_STEPS = 1;

  private final UserRepository userRepository;
  private final UserMfaRepository userMfaRepository;
  private final JdbcTemplate jdbcTemplate;
  private final AuditEventPort auditEventPort;

  @Autowired
  public MfaChallengeService(
      UserRepository userRepository,
      UserMfaRepository userMfaRepository,
      JdbcTemplate jdbcTemplate,
      AuditEventPort auditEventPort) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    this.userMfaRepository = Objects.requireNonNull(userMfaRepository, "userMfaRepository must not be null");
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.auditEventPort = (auditEventPort != null) ? auditEventPort : AuditEventPort.noop();
  }

  public MfaChallengeService(
      UserRepository userRepository,
      UserMfaRepository userMfaRepository,
      JdbcTemplate jdbcTemplate) {
    this(userRepository, userMfaRepository, jdbcTemplate, AuditEventPort.noop());
  }

  /**
   * Checks whether the user identified by username or email has MFA enrolled and active.
   *
   * @param usernameOrEmail the username or email of the user
   * @return true if MFA is enabled for this account
   */
  public boolean isMfaRequired(String usernameOrEmail) {
    if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
      return false;
    }
    return findUser(usernameOrEmail)
        .flatMap(user -> userMfaRepository.findByUserId(user.getId()))
        .map(UserMfa::isEnabled)
        .orElse(false);
  }

  /**
   * Verifies a 6-digit TOTP code or single-use emergency backup recovery code, recording
   * structured security audit logging events on success or failure.
   *
   * @param usernameOrEmail the user's username or email
   * @param code the submitted verification code
   * @param attempt the current attempt count
   * @return true if verification succeeds
   */
  @Transactional
  public boolean verifyCode(String usernameOrEmail, String code, int attempt) {
    boolean verified = doVerifyCode(usernameOrEmail, code);
    if (verified) {
      SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
          .action(SecurityAuditAction.MFA_VERIFY_SUCCESS)
          .status(SecurityAuditStatus.SUCCESS)
          .principal(usernameOrEmail)
          .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
          .build();
      auditEventPort.record(auditEvent);
    } else {
      SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
          .action(SecurityAuditAction.MFA_VERIFY_FAILURE)
          .status(SecurityAuditStatus.FAILURE)
          .principal(usernameOrEmail)
          .tenantId(TenantContext.getCurrentTenantOrDefault("demo"))
          .detail("attempt", attempt)
          .build();
      auditEventPort.record(auditEvent);
    }
    return verified;
  }

  /**
   * Verifies a 6-digit TOTP code or single-use emergency backup recovery code.
   *
   * @param usernameOrEmail the user's username or email
   * @param code the submitted verification code
   * @return true if verification succeeds
   */
  @Transactional
  public boolean verifyCode(String usernameOrEmail, String code) {
    return verifyCode(usernameOrEmail, code, 1);
  }

  private boolean doVerifyCode(String usernameOrEmail, String code) {
    if (usernameOrEmail == null || usernameOrEmail.isBlank() || code == null || code.isBlank()) {
      return false;
    }

    Optional<User> userOpt = findUser(usernameOrEmail);
    if (userOpt.isEmpty()) {
      log.warn("MFA verification rejected: user not found for [{}]", usernameOrEmail);
      return false;
    }

    User user = userOpt.get();
    Optional<UserMfa> mfaOpt = userMfaRepository.findByUserId(user.getId());
    if (mfaOpt.isEmpty() || !mfaOpt.get().isEnabled()) {
      log.warn("MFA verification rejected: MFA not active for user [{}]", usernameOrEmail);
      return false;
    }

    UserMfa mfa = mfaOpt.get();
    String normalizedCode = code.trim();

    // 1. Try TOTP code verification with +/- 1 window step and replay protection (RFC 6238 §5.2)
    java.util.OptionalLong verifiedStep = TotpGenerator.verifyTotpStep(
        mfa.getSecret(),
        normalizedCode,
        Instant.now(),
        TOTP_WINDOW_STEPS,
        mfa.getLastVerifiedStep()
    );
    if (verifiedStep.isPresent()) {
      mfa.markStepVerified(verifiedStep.getAsLong());
      userMfaRepository.save(mfa);
      log.info("MFA verification succeeded with TOTP code for user [{}]", usernameOrEmail);
      return true;
    }

    // 2. Try single-use backup recovery code
    String hashedBackupCode = TotpGenerator.hashBackupCode(normalizedCode);
    boolean backupConsumed = mfa.consumeBackupCode(hashedBackupCode);
    if (backupConsumed) {
      userMfaRepository.save(mfa);
      log.info("MFA verification succeeded with single-use backup code for user [{}]", usernameOrEmail);
      return true;
    }

    log.warn("MFA verification failed: invalid code for user [{}]", usernameOrEmail);
    return false;
  }

  private Optional<User> findUser(String usernameOrEmail) {
    return userRepository.findByEmail(usernameOrEmail)
        .or(() -> {
          List<String> emails = jdbcTemplate.query(
              "SELECT email FROM iam_user WHERE LOWER(email) = LOWER(?) LIMIT 1",
              (rs, rowNum) -> rs.getString("email"),
              usernameOrEmail);
          if (!emails.isEmpty()) {
            return userRepository.findByEmail(emails.getFirst());
          }
          return Optional.empty();
        });
  }
}
