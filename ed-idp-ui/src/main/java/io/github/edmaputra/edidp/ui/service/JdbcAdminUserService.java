package io.github.edmaputra.edidp.ui.service;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC implementation of {@link AdminUserService} integrating directly with {@code iam_user},
 * {@code user_profiles}, and canonical {@code iam_*} tables.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class JdbcAdminUserService implements AdminUserService {

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;

  public JdbcAdminUserService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
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
  public List<AdminUserSummary> listUsers() {
    String sql = """
        SELECT u.id, u.email, u.status, u.full_name AS iam_full_name,
               p.username, p.full_name, p.department, p.tenant
        FROM iam_user u
        LEFT JOIN user_profiles p ON LOWER(u.email) = LOWER(p.email) OR LOWER(u.email) = LOWER(p.username)
        ORDER BY u.email ASC
        """;

    Map<String, UserAggregation> map = new LinkedHashMap<>();
    jdbcTemplate.query(sql, rs -> {
      String id = rs.getString("id");
      String email = rs.getString("email");
      String status = rs.getString("status");
      boolean enabled = "ACTIVE".equalsIgnoreCase(status);
      String rawTenant = rs.getString("tenant");
      final String tenantId = (rawTenant != null && !rawTenant.isBlank()) ? rawTenant : "demo";

      String rawFullName = rs.getString("full_name");
      final String fullName = (rawFullName != null && !rawFullName.isBlank()) ? rawFullName : rs.getString("iam_full_name");
      String dept = rs.getString("department");

      UserAggregation agg = map.computeIfAbsent(
          email,
          k -> new UserAggregation(id, email, enabled, tenantId, fullName, email, dept)
      );
    });

    // Populate assigned roles from iam_user_role_assignment and iam_role
    String roleSql = """
        SELECT ura.user_id, r.code AS role_code
        FROM iam_user_role_assignment ura
        JOIN iam_role r ON ura.role_id = r.id
        """;
    jdbcTemplate.query(roleSql, rs -> {
      String rawUserId = rs.getString("user_id");
      String roleCode = rs.getString("role_code");
      if (roleCode != null) {
        for (UserAggregation agg : map.values()) {
          if (agg.id.equalsIgnoreCase(rawUserId) || agg.email.equalsIgnoreCase(rawUserId)) {
            if (!agg.roles.contains(roleCode)) {
              agg.roles.add(roleCode);
            }
          }
        }
      }
    });

    // Populate assigned group names from iam_group and iam_user_group_membership
    String groupSql = """
        SELECT gm.user_id, g.name AS group_name, g.code AS group_code
        FROM iam_user_group_membership gm
        JOIN iam_group g ON gm.group_id = g.id
        """;
    jdbcTemplate.query(groupSql, rs -> {
      String rawUserId = rs.getString("user_id");
      String groupName = rs.getString("group_name");
      String groupCode = rs.getString("group_code");
      String displayName = (groupName != null && !groupName.isBlank()) ? groupName : groupCode;

      for (UserAggregation agg : map.values()) {
        if (agg.id.equalsIgnoreCase(rawUserId) || agg.email.equalsIgnoreCase(rawUserId)) {
          if (displayName != null && !agg.groups.contains(displayName)) {
            agg.groups.add(displayName);
          }
        }
      }
    });

    List<AdminUserSummary> result = new ArrayList<>();
    for (UserAggregation agg : map.values()) {
      result.add(new AdminUserSummary(
          agg.email,
          agg.enabled,
          agg.tenantId,
          agg.fullName,
          agg.email,
          List.copyOf(agg.roles),
          List.copyOf(agg.groups),
          agg.department
      ));
    }
    return result;
  }

  @Override
  public Optional<AdminUserSummary> getUser(String username) {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }
    return listUsers().stream()
        .filter(u -> u.username().equalsIgnoreCase(username) || u.email().equalsIgnoreCase(username))
        .findFirst();
  }

  @Override
  public long countUsers() {
    Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM iam_user", Long.class);
    return count != null ? count : 0L;
  }

  @Override
  @Transactional
  public void createUser(CreateUserCommand command) {
    String email = command.email();
    if (email == null || email.isBlank()) {
      email = command.username();
    }
    UUID userUuid = toUuid(email);
    Timestamp now = Timestamp.from(Instant.now());
    String encodedPassword = passwordEncoder.encode(command.password());

    // 1. Insert into iam_user
    jdbcTemplate.update("""
        INSERT INTO iam_user (id, email, password_hash, full_name, status, is_platform_superadmin, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', false, ?, ?)
        """,
        userUuid, email, encodedPassword, command.fullName(), now, now
    );

    // 2. Insert into iam_user_identity
    jdbcTemplate.update("""
        INSERT INTO iam_user_identity (id, user_id, provider_type, external_subject_id, created_at)
        VALUES (?, ?, 'LOCAL', ?, ?)
        """,
        UUID.randomUUID(), userUuid, email, now
    );

    // 3. Assign role if specified
    String assignedRole = command.role();
    if (assignedRole != null && !assignedRole.isBlank()) {
      UUID roleUuid = resolveRoleId(assignedRole);
      if (roleUuid != null) {
        jdbcTemplate.update("""
            INSERT INTO iam_user_role_assignment (id, user_id, role_id, inherit_children, created_at)
            VALUES (?, ?, ?, true, ?)
            """,
            UUID.randomUUID(), userUuid, roleUuid, now
        );
      }
    }

    // 4. Insert into user_profiles
    jdbcTemplate.update(
        """
        INSERT INTO user_profiles (username, full_name, email, email_verified, locale, zoneinfo, department, tenant, updated_at)
        VALUES (?, ?, ?, true, 'en-US', 'UTC', ?, ?, ?)
        """,
        email,
        command.fullName(),
        email,
        command.department(),
        command.tenantId(),
        Instant.now().getEpochSecond()
    );
  }

  @Override
  @Transactional
  public void updateUser(UpdateUserCommand command) {
    String email = command.email();
    if (email == null || email.isBlank()) {
      email = command.username();
    }
    UUID userUuid = toUuid(command.username());
    Timestamp now = Timestamp.from(Instant.now());

    // 1. Update iam_user status and name
    String newStatus = command.enabled() ? "ACTIVE" : "SUSPENDED";
    jdbcTemplate.update("""
        UPDATE iam_user
        SET status = ?, full_name = ?, email = ?, updated_at = ?
        WHERE id = ? OR LOWER(email) = LOWER(?)
        """,
        newStatus, command.fullName(), email, now, userUuid, command.username()
    );

    // 2. Update user_profiles
    int updatedProfiles = jdbcTemplate.update(
        """
        UPDATE user_profiles
        SET full_name = ?, email = ?, department = ?, tenant = ?, updated_at = ?
        WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)
        """,
        command.fullName(),
        email,
        command.department(),
        command.tenantId(),
        Instant.now().getEpochSecond(),
        command.username(),
        command.username()
    );

    if (updatedProfiles == 0) {
      jdbcTemplate.update(
          """
          INSERT INTO user_profiles (username, full_name, email, email_verified, locale, zoneinfo, department, tenant, updated_at)
          VALUES (?, ?, ?, true, 'en-US', 'UTC', ?, ?, ?)
          """,
          email,
          command.fullName(),
          email,
          command.department(),
          command.tenantId(),
          Instant.now().getEpochSecond()
      );
    }

    // 3. Update roles in iam_user_role_assignment
    jdbcTemplate.update("DELETE FROM iam_user_role_assignment WHERE user_id = ?", userUuid);
    for (String role : command.roles()) {
      String cleanRole = role.trim();
      UUID roleUuid = resolveRoleId(cleanRole);
      if (roleUuid != null) {
        jdbcTemplate.update("""
            INSERT INTO iam_user_role_assignment (id, user_id, role_id, inherit_children, created_at)
            VALUES (?, ?, ?, true, ?)
            """,
            UUID.randomUUID(), userUuid, roleUuid, now
        );
      }
    }

    // 4. Update group memberships in iam_user_group_membership
    jdbcTemplate.update("DELETE FROM iam_user_group_membership WHERE user_id = ?", userUuid);
    for (String grp : command.groups()) {
      String cleanGrp = grp.trim();
      UUID groupUuid = resolveGroupId(cleanGrp);
      if (groupUuid != null) {
        jdbcTemplate.update(
            """
            INSERT INTO iam_user_group_membership (group_id, user_id, joined_at)
            SELECT ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM iam_user_group_membership WHERE group_id = ? AND user_id = ?)
            """,
            groupUuid,
            userUuid,
            now,
            groupUuid,
            userUuid
        );
      }
    }
  }

  @Override
  @Transactional
  public void setUserEnabled(String username, boolean enabled) {
    String status = enabled ? "ACTIVE" : "SUSPENDED";
    UUID userUuid = toUuid(username);
    jdbcTemplate.update(
        "UPDATE iam_user SET status = ? WHERE id = ? OR LOWER(email) = LOWER(?)",
        status,
        userUuid,
        username
    );
  }

  private UUID resolveRoleId(String roleCodeOrName) {
    UUID rUuid = toUuid(roleCodeOrName);
    String cleanCode = roleCodeOrName.startsWith("ROLE_") ? roleCodeOrName : "ROLE_" + roleCodeOrName.toUpperCase();
    List<UUID> ids = jdbcTemplate.query(
        "SELECT id FROM iam_role WHERE id = ? OR code = ? OR code = ? OR name = ?",
        (rs, rowNum) -> (UUID) rs.getObject("id"),
        rUuid, cleanCode, roleCodeOrName, roleCodeOrName
    );
    return ids.isEmpty() ? null : ids.getFirst();
  }

  private UUID resolveGroupId(String groupCodeOrName) {
    UUID gUuid = toUuid(groupCodeOrName);
    List<UUID> ids = jdbcTemplate.query(
        "SELECT id FROM iam_group WHERE id = ? OR code = ? OR name = ?",
        (rs, rowNum) -> (UUID) rs.getObject("id"),
        gUuid, groupCodeOrName, groupCodeOrName
    );
    return ids.isEmpty() ? null : ids.getFirst();
  }

  private static final class UserAggregation {
    final String id;
    final String email;
    final boolean enabled;
    final String tenantId;
    final String fullName;
    final String username;
    final String department;
    final List<String> roles = new ArrayList<>();
    final List<String> groups = new ArrayList<>();

    UserAggregation(
        String id,
        String email,
        boolean enabled,
        String tenantId,
        String fullName,
        String username,
        String department) {
      this.id = id;
      this.email = email;
      this.enabled = enabled;
      this.tenantId = tenantId;
      this.fullName = fullName;
      this.username = username;
      this.department = department;
    }
  }
}
