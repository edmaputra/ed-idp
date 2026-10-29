package io.github.edmaputra.edidp.users;

import io.github.edmaputra.iam.domain.security.PasswordValidator;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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

  public DefaultUserSelfService(
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      PasswordValidator passwordValidator) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    this.passwordValidator = Objects.requireNonNull(passwordValidator, "passwordValidator must not be null");
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

    return getMyProfile(subject, cleanTenant);
  }

  @Override
  @Transactional
  public void changeMyPassword(String subject, String tenantId, ChangePasswordRequest request) {
    try {
      passwordValidator.validatePassword(request.newPassword(), subject);
    } catch (RuntimeException ex) {
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
      throw new IllegalArgumentException("User not found: " + subject);
    }

    String currentHash = hashes.get(0);
    if (currentHash == null || currentHash.isBlank()) {
      throw new IllegalArgumentException("User has no current password set");
    }

    if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
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
  }
}
