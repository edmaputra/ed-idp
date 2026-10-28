package io.github.edmaputra.edidp.ui.service;

import io.github.edmaputra.iam.domain.security.PasswordValidator;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC implementation of {@link UserProfileService} providing self-service profile updates
 * and credential management for authenticated users.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class JdbcUserProfileService implements UserProfileService {

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;
  private final PasswordValidator passwordValidator;

  public JdbcUserProfileService(
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
  public Optional<UserProfileDto> getProfile(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }

    String sql = """
        SELECT u.id, u.email, u.full_name AS iam_full_name,
               p.username, p.full_name AS profile_full_name, p.department, p.tenant
        FROM iam_user u
        LEFT JOIN user_profiles p ON LOWER(u.email) = LOWER(p.email) OR LOWER(u.email) = LOWER(p.username)
        WHERE u.id = ? OR LOWER(u.email) = LOWER(?) OR LOWER(p.username) = LOWER(?)
        """;

    UUID userUuid = toUuid(username);
    List<UserProfileDto> results = jdbcTemplate.query(
        sql,
        (rs, rowNum) -> {
          String rawId = rs.getString("id");
          String email = rs.getString("email");
          String rawTenant = rs.getString("tenant");
          String tenantId = (rawTenant != null && !rawTenant.isBlank()) ? rawTenant : "demo";

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

          return new UserProfileDto(
              email != null ? email : username,
              email != null ? email : "",
              fullName,
              department,
              tenantId,
              roles,
              groups
          );
        },
        userUuid, username, username
    );

    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
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
  public void updateProfile(UpdateProfileCommand command) {
    UUID userUuid = toUuid(command.username());
    Timestamp now = Timestamp.from(Instant.now());

    // 1. Update iam_user full_name
    jdbcTemplate.update("""
        UPDATE iam_user
        SET full_name = ?, updated_at = ?
        WHERE id = ? OR LOWER(email) = LOWER(?)
        """,
        command.fullName(), now, userUuid, command.username()
    );

    // 2. Update user_profiles
    int updated = jdbcTemplate.update("""
        UPDATE user_profiles
        SET full_name = ?, department = ?, updated_at = ?
        WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)
        """,
        command.fullName(),
        command.department(),
        Instant.now().getEpochSecond(),
        command.username(),
        command.username()
    );

    if (updated == 0) {
      jdbcTemplate.update("""
          INSERT INTO user_profiles (username, full_name, email, email_verified, locale, zoneinfo, department, tenant, updated_at)
          VALUES (?, ?, ?, true, 'en-US', 'UTC', ?, 'demo', ?)
          """,
          command.username(),
          command.fullName(),
          command.username(),
          command.department(),
          Instant.now().getEpochSecond()
      );
    }
  }

  @Override
  @Transactional
  public void changePassword(ChangePasswordCommand command) {
    // 1. Validate password policy via PasswordValidator
    try {
      passwordValidator.validatePassword(command.newPassword(), command.username());
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(ex.getMessage() != null ? ex.getMessage() : "Password does not meet policy requirements", ex);
    }

    // 2. Query current password hash from iam_user
    UUID userUuid = toUuid(command.username());
    List<String> hashes = jdbcTemplate.query(
        "SELECT password_hash FROM iam_user WHERE id = ? OR LOWER(email) = LOWER(?)",
        (rs, rowNum) -> rs.getString("password_hash"),
        userUuid,
        command.username()
    );

    if (hashes.isEmpty()) {
      throw new IllegalArgumentException("User not found: " + command.username());
    }

    String currentHash = hashes.get(0);
    if (currentHash == null || currentHash.isBlank()) {
      throw new IllegalArgumentException("User has no current password set");
    }

    // 3. Verify current password matches
    if (!passwordEncoder.matches(command.currentPassword(), currentHash)) {
      throw new IllegalArgumentException("Current password is incorrect");
    }

    // 4. Update password hash in iam_user
    String encodedNewPassword = passwordEncoder.encode(command.newPassword());
    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update(
        "UPDATE iam_user SET password_hash = ?, updated_at = ? WHERE id = ? OR LOWER(email) = LOWER(?)",
        encodedNewPassword,
        now,
        userUuid,
        command.username()
    );
  }
}
