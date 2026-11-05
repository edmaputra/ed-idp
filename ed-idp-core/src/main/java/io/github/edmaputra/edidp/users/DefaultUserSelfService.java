package io.github.edmaputra.edidp.users;

import io.github.edmaputra.edidp.audit.AuditEventPort;
import io.github.edmaputra.edidp.audit.SecurityAuditAction;
import io.github.edmaputra.edidp.audit.SecurityAuditEvent;
import io.github.edmaputra.edidp.audit.SecurityAuditStatus;
import io.github.edmaputra.iam.adapter.rest.MfaOperationsDelegate;
import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import io.github.edmaputra.iam.domain.security.PasswordValidator;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link UserSelfService} supporting multi-tenant profile
 * self-service and credential maintenance.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class DefaultUserSelfService implements UserSelfService {

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;
  private final PasswordValidator passwordValidator;
  private final AuditEventPort auditEventPort;
  private final MfaOperationsDelegate mfaOperationsDelegate;
  private final UserRepository userRepository;

  @Autowired
  public DefaultUserSelfService(
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      PasswordValidator passwordValidator,
      AuditEventPort auditEventPort,
      ObjectProvider<MfaOperationsDelegate> mfaOperationsDelegateProvider,
      ObjectProvider<UserRepository> userRepositoryProvider) {
    this(
        jdbcTemplate,
        passwordEncoder,
        passwordValidator,
        auditEventPort,
        mfaOperationsDelegateProvider != null ? mfaOperationsDelegateProvider.getIfAvailable() : null,
        userRepositoryProvider != null ? userRepositoryProvider.getIfAvailable() : null
    );
  }

  public DefaultUserSelfService(
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      PasswordValidator passwordValidator,
      AuditEventPort auditEventPort,
      MfaOperationsDelegate mfaOperationsDelegate,
      UserRepository userRepository) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    this.passwordValidator = Objects.requireNonNull(passwordValidator, "passwordValidator must not be null");
    this.auditEventPort = (auditEventPort != null) ? auditEventPort : AuditEventPort.noop();
    this.mfaOperationsDelegate = mfaOperationsDelegate;
    this.userRepository = userRepository;
  }

  public DefaultUserSelfService(
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      PasswordValidator passwordValidator,
      AuditEventPort auditEventPort) {
    this(jdbcTemplate, passwordEncoder, passwordValidator, auditEventPort, (MfaOperationsDelegate) null, (UserRepository) null);
  }

  public DefaultUserSelfService(
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      PasswordValidator passwordValidator) {
    this(jdbcTemplate, passwordEncoder, passwordValidator, AuditEventPort.noop(), (MfaOperationsDelegate) null, (UserRepository) null);
  }

  private static UUID toUuid(String val) {
    if (val == null || val.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(val.trim());
    } catch (IllegalArgumentException e) {
      return UUID.nameUUIDFromBytes(val.trim().getBytes(StandardCharsets.UTF_8));
    }
  }

  @Override
  public UserProfileResponse getMyProfile(String subject, String tenantId) {
    String cleanTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "demo";
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Subject must not be blank");
    }

    String sql = """
        SELECT u.id, u.email, u.full_name AS iam_full_name,
               p.username, p.full_name AS profile_full_name, p.department, p.tenant
        FROM iam_user u
        LEFT JOIN user_profiles p ON (LOWER(u.email) = LOWER(p.email) OR LOWER(u.email) = LOWER(p.username)) AND p.tenant = ?
        WHERE u.id = ? OR LOWER(u.email) = LOWER(?) OR (LOWER(p.username) = LOWER(?) AND p.tenant = ?)
        """;

    UUID userUuid = toUuid(subject);
    List<UserProfileResponse> results = jdbcTemplate.query(
        sql,
        (rs, rowNum) -> {
          String rawId = rs.getString("id");
          String email = rs.getString("email");
          String profileFullName = rs.getString("profile_full_name");
          String iamFullName = rs.getString("iam_full_name");
          String fullName = (profileFullName != null && !profileFullName.isBlank())
              ? profileFullName
              : (iamFullName != null ? iamFullName : "");

          String department = rs.getString("department");
          if (department == null) {
            department = "";
          }

          UUID targetUuid = toUuid(rawId);
          List<String> roles = fetchRoles(targetUuid);
          List<String> groups = fetchGroups(targetUuid);

          return new UserProfileResponse(
              email != null ? email : subject,
              email != null ? email : subject,
              fullName,
              department,
              cleanTenant,
              roles,
              groups
          );
        },
        cleanTenant, userUuid, subject, subject, cleanTenant
    );

    if (!results.isEmpty()) {
      return results.get(0);
    }

    return new UserProfileResponse(subject, subject, subject, "", cleanTenant, List.of(), List.of());
  }

  private List<String> fetchRoles(UUID userId) {
    if (userId == null) {
      return List.of();
    }
    String roleSql = """
        SELECT DISTINCT r.code
        FROM iam_user_role_assignment ura
        JOIN iam_role r ON ura.role_id = r.id
        WHERE ura.user_id = ?
        UNION
        SELECT DISTINCT r.code
        FROM iam_user_group_membership gm
        JOIN iam_group_role_assignment gra ON gm.group_id = gra.group_id
        JOIN iam_role r ON gra.role_id = r.id
        WHERE gm.user_id = ?
        """;

    List<String> roles = new ArrayList<>();
    jdbcTemplate.query(roleSql, rs -> {
      String code = rs.getString("code");
      if (code != null && !code.isBlank() && !roles.contains(code)) {
        roles.add(code);
      }
    }, userId, userId);
    return List.copyOf(roles);
  }

  private List<String> fetchGroups(UUID userId) {
    if (userId == null) {
      return List.of();
    }
    String groupSql = """
        SELECT g.name, g.code
        FROM iam_user_group_membership gm
        JOIN iam_group g ON gm.group_id = g.id
        WHERE gm.user_id = ?
        """;

    List<String> groups = new ArrayList<>();
    jdbcTemplate.query(groupSql, rs -> {
      String name = rs.getString("name");
      String code = rs.getString("code");
      String display = (name != null && !name.isBlank()) ? name : code;
      if (display != null && !groups.contains(display)) {
        groups.add(display);
      }
    }, userId);
    return List.copyOf(groups);
  }

  @Override
  @Transactional
  public UserProfileResponse updateMyProfile(String subject, String tenantId, UpdateProfileRequest request) {
    String cleanTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "demo";
    UUID userUuid = toUuid(subject);
    Timestamp now = Timestamp.from(Instant.now());

    // 1. Update iam_user full_name
    jdbcTemplate.update("""
        UPDATE iam_user
        SET full_name = ?, updated_at = ?
        WHERE id = ? OR LOWER(email) = LOWER(?)
        """,
        request.fullName(), now, userUuid, subject
    );

    // 2. Update user_profiles scoped to tenant
    int updated = jdbcTemplate.update("""
        UPDATE user_profiles
        SET full_name = ?, department = ?, updated_at = ?
        WHERE tenant = ? AND (LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?))
        """,
        request.fullName(),
        request.department(),
        Instant.now().getEpochSecond(),
        cleanTenant,
        subject,
        subject
    );

    if (updated == 0) {
      jdbcTemplate.update("""
          INSERT INTO user_profiles (username, full_name, email, email_verified, locale, zoneinfo, department, tenant, updated_at)
          VALUES (?, ?, ?, true, 'en-US', 'UTC', ?, ?, ?)
          """,
          subject,
          request.fullName(),
          subject,
          request.department(),
          cleanTenant,
          Instant.now().getEpochSecond()
      );
    }

    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.USER_PROFILE_UPDATED)
        .status(SecurityAuditStatus.SUCCESS)
        .principal(subject)
        .tenantId(cleanTenant)
        .detail("fullName", request.fullName())
        .detail("department", request.department())
        .build();
    auditEventPort.record(auditEvent);

    return getMyProfile(subject, cleanTenant);
  }

  @Override
  @Transactional
  public void changeMyPassword(String subject, String tenantId, ChangePasswordRequest request) {
    String cleanTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "demo";
    try {
      passwordValidator.validatePassword(request.newPassword(), subject);
    } catch (RuntimeException ex) {
      recordPasswordFailure(subject, cleanTenant, ex.getMessage() != null ? ex.getMessage() : "Password validation failed");
      throw new IllegalArgumentException(ex.getMessage() != null ? ex.getMessage() : "Password does not meet policy requirements", ex);
    }

    UUID userUuid = toUuid(subject);
    List<String> hashes = jdbcTemplate.query(
        "SELECT password_hash FROM iam_user WHERE id = ? OR LOWER(email) = LOWER(?)",
        (rs, rowNum) -> rs.getString("password_hash"),
        userUuid,
        subject
    );

    if (hashes.isEmpty()) {
      recordPasswordFailure(subject, cleanTenant, "User not found");
      throw new IllegalArgumentException("User not found: " + subject);
    }

    String currentHash = hashes.get(0);
    if (currentHash == null || currentHash.isBlank()) {
      recordPasswordFailure(subject, cleanTenant, "User has no current password set");
      throw new IllegalArgumentException("User has no current password set");
    }

    if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
      recordPasswordFailure(subject, cleanTenant, "Current password is incorrect");
      throw new IllegalArgumentException("Current password is incorrect");
    }

    String encodedNewPassword = passwordEncoder.encode(request.newPassword());
    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update(
        "UPDATE iam_user SET password_hash = ?, updated_at = ? WHERE id = ? OR LOWER(email) = LOWER(?)",
        encodedNewPassword,
        now,
        userUuid,
        subject
    );

    SecurityAuditEvent successEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.USER_PASSWORD_CHANGED)
        .status(SecurityAuditStatus.SUCCESS)
        .principal(subject)
        .tenantId(cleanTenant)
        .build();
    auditEventPort.record(successEvent);
  }

  private void recordPasswordFailure(String subject, String tenantId, String error) {
    SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.USER_PASSWORD_CHANGED)
        .status(SecurityAuditStatus.FAILURE)
        .principal(subject)
        .tenantId(tenantId)
        .detail("error", error)
        .build();
    auditEventPort.record(auditEvent);
  }

  @Override
  public UserMfaStatusResponse getMyMfaStatus(String subject, String tenantId) {
    requireMfaCollaborators();
    User user = findUser(subject);
    MfaStatusResponse status = mfaOperationsDelegate.getStatus(user.getId());
    return new UserMfaStatusResponse(status.enabled(), status.enrolledAt());
  }

  @Override
  @Transactional
  public MfaEnrollmentSetupResponse initiateMyMfaSetup(String subject, String tenantId) {
    requireMfaCollaborators();
    User user = findUser(subject);
    MfaSetupResponse setup = mfaOperationsDelegate.initiateSetup(user.getId(), user.getEmail());
    return new MfaEnrollmentSetupResponse(setup.secret(), setup.qrCodeUri(), setup.backupCodes());
  }

  @Override
  @Transactional
  public void activateMyMfa(String subject, String tenantId, String code) {
    requireMfaCollaborators();
    String cleanTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "demo";
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Verification code must not be blank.");
    }
    User user = findUser(subject);
    try {
      mfaOperationsDelegate.activate(user.getId(), code.trim());
      SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
          .action(SecurityAuditAction.MFA_ENROLLED)
          .status(SecurityAuditStatus.SUCCESS)
          .principal(subject)
          .tenantId(cleanTenant)
          .build();
      auditEventPort.record(auditEvent);
    } catch (RuntimeException ex) {
      SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
          .action(SecurityAuditAction.MFA_VERIFY_FAILURE)
          .status(SecurityAuditStatus.FAILURE)
          .principal(subject)
          .tenantId(cleanTenant)
          .detail("error", ex.getMessage() != null ? ex.getMessage() : "MFA activation failed")
          .build();
      auditEventPort.record(auditEvent);
      throw new IllegalArgumentException(ex.getMessage() != null ? ex.getMessage() : "Invalid verification code.");
    }
  }

  @Override
  @Transactional
  public void disableMyMfa(String subject, String tenantId, String code) {
    requireMfaCollaborators();
    String cleanTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "demo";
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Verification code or password must not be blank.");
    }
    User user = findUser(subject);
    try {
      mfaOperationsDelegate.disable(user.getId(), code.trim());
      SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
          .action(SecurityAuditAction.MFA_DISABLED)
          .status(SecurityAuditStatus.SUCCESS)
          .principal(subject)
          .tenantId(cleanTenant)
          .build();
      auditEventPort.record(auditEvent);
    } catch (RuntimeException ex) {
      SecurityAuditEvent auditEvent = SecurityAuditEvent.builder()
          .action(SecurityAuditAction.MFA_VERIFY_FAILURE)
          .status(SecurityAuditStatus.FAILURE)
          .principal(subject)
          .tenantId(cleanTenant)
          .detail("error", ex.getMessage() != null ? ex.getMessage() : "MFA disable failed")
          .build();
      auditEventPort.record(auditEvent);
      throw new IllegalArgumentException(ex.getMessage() != null ? ex.getMessage() : "Invalid verification code or password.");
    }
  }

  private User findUser(String subject) {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Subject must not be blank");
    }
    UUID userUuid = toUuid(subject);
    if (userRepository != null) {
      if (userUuid != null) {
        Optional<User> byId = userRepository.findById(new UserId(userUuid));
        if (byId.isPresent()) {
          return byId.get();
        }
      }
      Optional<User> byEmail = userRepository.findByEmail(subject);
      if (byEmail.isPresent()) {
        return byEmail.get();
      }
    }
    List<String> emails = jdbcTemplate.query(
        "SELECT email FROM iam_user WHERE id = ? OR LOWER(email) = LOWER(?) LIMIT 1",
        (rs, rowNum) -> rs.getString("email"),
        userUuid, subject);
    if (!emails.isEmpty() && userRepository != null) {
      return userRepository.findByEmail(emails.getFirst())
          .orElseThrow(() -> new IllegalArgumentException("User not found: " + subject));
    }
    throw new IllegalArgumentException("User not found: " + subject);
  }

  private void requireMfaCollaborators() {
    if (mfaOperationsDelegate == null || userRepository == null) {
      throw new IllegalStateException("MFA operations delegate and user repository must be configured.");
    }
  }
}
