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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC implementation of {@link AdminRbacService} aligned with ed-iam unified schema.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class JdbcAdminRbacService implements AdminRbacService {

  private final JdbcTemplate jdbcTemplate;

  public JdbcAdminRbacService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
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
  public List<PermissionSummary> listPermissions() {
    return listPermissions(null, null);
  }

  @Override
  public List<PermissionSummary> listPermissions(String tenantId, String category) {
    StringBuilder sql = new StringBuilder("""
        SELECT id, code, name, description, category, is_system_permission, tenant_id
        FROM iam_permission
        WHERE 1=1
        """);
    List<Object> params = new ArrayList<>();
    if (tenantId != null && !tenantId.isBlank()) {
      sql.append(" AND (tenant_id = ? OR tenant_id IS NULL)");
      params.add(toUuid(tenantId));
    }
    if (category != null && !category.isBlank()) {
      sql.append(" AND category = ?");
      params.add(category);
    }
    sql.append(" ORDER BY category ASC, code ASC");

    return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new PermissionSummary(
        rs.getString("id"),
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("category"),
        rs.getBoolean("is_system_permission"),
        rs.getString("tenant_id")
    ), params.toArray());
  }

  @Override
  public Optional<PermissionSummary> getPermissionById(String id) {
    UUID uuid = toUuid(id);
    String sql = """
        SELECT id, code, name, description, category, is_system_permission, tenant_id
        FROM iam_permission
        WHERE id = ? OR code = ?
        """;
    List<PermissionSummary> results = jdbcTemplate.query(sql, (rs, rowNum) -> new PermissionSummary(
        rs.getString("id"),
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("category"),
        rs.getBoolean("is_system_permission"),
        rs.getString("tenant_id")
    ), uuid, id);
    return results.stream().findFirst();
  }

  @Override
  public Optional<PermissionSummary> getPermissionByCode(String code) {
    String sql = """
        SELECT id, code, name, description, category, is_system_permission, tenant_id
        FROM iam_permission
        WHERE code = ?
        """;
    List<PermissionSummary> results = jdbcTemplate.query(sql, (rs, rowNum) -> new PermissionSummary(
        rs.getString("id"),
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("category"),
        rs.getBoolean("is_system_permission"),
        rs.getString("tenant_id")
    ), code);
    return results.stream().findFirst();
  }

  @Override
  @Transactional
  public PermissionSummary createPermission(CreatePermissionCommand command) {
    UUID id = UUID.randomUUID();
    UUID tenantUuid = toUuid(command.tenantId());
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update("""
        INSERT INTO iam_permission (id, tenant_id, code, name, description, category, is_system_permission, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, false, ?, ?)
        """,
        id,
        tenantUuid,
        command.code().trim(),
        command.name().trim(),
        command.description(),
        command.category().trim().toUpperCase(),
        now,
        now
    );
    return new PermissionSummary(
        id.toString(),
        command.code().trim(),
        command.name().trim(),
        command.description(),
        command.category().trim().toUpperCase(),
        false,
        command.tenantId()
    );
  }

  @Override
  @Transactional
  public PermissionSummary updatePermission(UpdatePermissionCommand command) {
    PermissionSummary existing = getPermissionById(command.id())
        .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + command.id()));
    if (existing.systemPermission()) {
      throw new IllegalStateException("System permissions cannot be modified");
    }
    Timestamp now = Timestamp.from(Instant.now());
    UUID permUuid = toUuid(command.id());
    jdbcTemplate.update("""
        UPDATE iam_permission
        SET name = ?, description = ?, category = ?, updated_at = ?
        WHERE (id = ? OR code = ?) AND is_system_permission = false
        """,
        command.name().trim(),
        command.description(),
        command.category().trim().toUpperCase(),
        now,
        permUuid,
        command.id()
    );
    return new PermissionSummary(
        existing.id(),
        existing.code(),
        command.name().trim(),
        command.description(),
        command.category().trim().toUpperCase(),
        false,
        existing.tenantId()
    );
  }

  @Override
  @Transactional
  public void deletePermission(String id) {
    PermissionSummary existing = getPermissionById(id)
        .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + id));
    if (existing.systemPermission()) {
      throw new IllegalStateException("System permissions cannot be deleted");
    }
    UUID permUuid = toUuid(id);
    jdbcTemplate.update("DELETE FROM iam_permission WHERE (id = ? OR code = ?) AND is_system_permission = false", permUuid, id);
  }

  @Override
  public List<RoleSummary> listRoles() {
    String sql = """
        SELECT r.id, r.code, r.name, r.description, r.tenant_id, rp.permission AS perm_code
        FROM iam_role r
        LEFT JOIN iam_role_permission rp ON r.id = rp.role_id
        ORDER BY r.code ASC
        """;

    Map<String, RoleAggregation> map = new LinkedHashMap<>();
    jdbcTemplate.query(sql, rs -> {
      String id = rs.getString("id");
      String code = rs.getString("code");
      String name = rs.getString("name");
      String desc = rs.getString("description");
      String tenantId = rs.getString("tenant_id");
      String perm = rs.getString("perm_code");

      String displayName = (code != null && !code.isBlank()) ? code : name;
      RoleAggregation agg = map.computeIfAbsent(id, k -> new RoleAggregation(id, displayName, desc, tenantId));
      if (perm != null && !agg.permissions.contains(perm)) {
        agg.permissions.add(perm);
      }
    });

    List<RoleSummary> result = new ArrayList<>();
    for (RoleAggregation agg : map.values()) {
      result.add(new RoleSummary(agg.id, agg.name, agg.description, agg.tenantId, List.copyOf(agg.permissions)));
    }
    return result;
  }

  @Override
  public long countRoles() {
    Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM iam_role", Long.class);
    return count != null ? count : 0L;
  }

  @Override
  @Transactional
  public void createRole(CreateRoleCommand command) {
    UUID roleId = UUID.randomUUID();
    String roleName = command.name().trim();
    if (!roleName.startsWith("ROLE_")) {
      roleName = "ROLE_" + roleName.toUpperCase();
    }
    UUID tenantUuid = toUuid(command.tenantId());
    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update("""
        INSERT INTO iam_role (id, tenant_id, code, name, description, is_system_role, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, false, ?, ?)
        """,
        roleId,
        tenantUuid,
        roleName,
        roleName,
        command.description(),
        now,
        now
    );

    if (command.permissionIds() != null && !command.permissionIds().isEmpty()) {
      for (String permIdOrCode : command.permissionIds()) {
        String permCode = resolvePermissionCode(permIdOrCode);
        jdbcTemplate.update(
            "INSERT INTO iam_role_permission (role_id, permission) VALUES (?, ?)",
            roleId,
            permCode
        );
      }
    }
  }

  @Override
  @Transactional
  public void deleteRole(String roleId, String tenantId) {
    UUID rUuid = toUuid(roleId);
    UUID tUuid = toUuid(tenantId);
    if (tUuid != null) {
      jdbcTemplate.update("DELETE FROM iam_role WHERE (id = ? OR code = ?) AND tenant_id = ?", rUuid, roleId, tUuid);
    } else {
      jdbcTemplate.update("DELETE FROM iam_role WHERE id = ? OR code = ?", rUuid, roleId);
    }
  }

  @Override
  public Optional<RoleDetail> getRole(String id) {
    UUID rUuid = toUuid(id);
    String sql = """
        SELECT r.id, r.code, r.name, r.description, r.tenant_id, rp.permission AS perm_code
        FROM iam_role r
        LEFT JOIN iam_role_permission rp ON r.id = rp.role_id
        WHERE r.id = ? OR r.code = ?
        """;
    List<String> permissionCodes = new ArrayList<>();
    String[] meta = new String[4];
    boolean[] found = new boolean[1];

    jdbcTemplate.query(sql, rs -> {
      found[0] = true;
      if (meta[0] == null) {
        meta[0] = rs.getString("id");
        meta[1] = rs.getString("code") != null ? rs.getString("code") : rs.getString("name");
        meta[2] = rs.getString("description");
        meta[3] = rs.getString("tenant_id");
      }
      String pCode = rs.getString("perm_code");
      if (pCode != null && !permissionCodes.contains(pCode)) {
        permissionCodes.add(pCode);
      }
    }, rUuid, id);

    if (!found[0]) {
      return Optional.empty();
    }
    return Optional.of(new RoleDetail(meta[0], meta[1], meta[2], meta[3], List.copyOf(permissionCodes)));
  }

  @Override
  @Transactional
  public void updateRole(UpdateRoleCommand command) {
    UUID rUuid = toUuid(command.id());
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        "UPDATE iam_role SET description = ?, updated_at = ? WHERE id = ? OR code = ?",
        command.description(),
        now,
        rUuid,
        command.id()
    );

    // Delete existing role permissions
    jdbcTemplate.update("DELETE FROM iam_role_permission WHERE role_id IN (SELECT id FROM iam_role WHERE id = ? OR code = ?)", rUuid, command.id());

    // Resolve UUID for role_id
    UUID targetRoleId = resolveRoleId(command.id());
    if (command.permissionIds() != null && !command.permissionIds().isEmpty()) {
      for (String permIdOrCode : command.permissionIds()) {
        String permCode = resolvePermissionCode(permIdOrCode);
        jdbcTemplate.update(
            "INSERT INTO iam_role_permission (role_id, permission) VALUES (?, ?)",
            targetRoleId,
            permCode
        );
      }
    }
  }

  @Override
  public List<GroupSummary> listGroups() {
    String sql = """
        SELECT g.id, g.code, g.name, g.description, g.tenant_id, r.code AS role_code,
               (SELECT count(*) FROM iam_user_group_membership gm WHERE gm.group_id = g.id) AS member_count
        FROM iam_group g
        LEFT JOIN iam_group_role_assignment gra ON g.id = gra.group_id
        LEFT JOIN iam_role r ON gra.role_id = r.id
        ORDER BY g.code ASC
        """;

    Map<String, GroupAggregation> map = new LinkedHashMap<>();
    jdbcTemplate.query(sql, rs -> {
      String id = rs.getString("id");
      String code = rs.getString("code");
      String name = rs.getString("name");
      String desc = rs.getString("description");
      String tenantId = rs.getString("tenant_id");
      int memberCount = rs.getInt("member_count");
      String role = rs.getString("role_code");

      String displayName = (name != null && !name.isBlank()) ? name : code;
      GroupAggregation agg = map.computeIfAbsent(id, k -> new GroupAggregation(id, displayName, desc, tenantId, memberCount));
      if (role != null && !agg.roles.contains(role)) {
        agg.roles.add(role);
      }
    });

    List<GroupSummary> result = new ArrayList<>();
    for (GroupAggregation agg : map.values()) {
      result.add(new GroupSummary(agg.id, agg.name, agg.description, agg.tenantId, List.copyOf(agg.roles), agg.memberCount));
    }
    return result;
  }

  @Override
  public long countGroups() {
    Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM iam_group", Long.class);
    return count != null ? count : 0L;
  }

  @Override
  @Transactional
  public void createGroup(CreateGroupCommand command) {
    UUID groupId = UUID.randomUUID();
    UUID tenantUuid = toUuid(command.tenantId());
    String code = command.name().trim().toUpperCase().replace(" ", "_");
    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update("""
        INSERT INTO iam_group (id, tenant_id, code, name, description, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        groupId,
        tenantUuid,
        code,
        command.name().trim(),
        command.description(),
        now,
        now
    );

    if (command.roleIds() != null && !command.roleIds().isEmpty()) {
      for (String roleIdOrCode : command.roleIds()) {
        UUID roleUuid = resolveRoleId(roleIdOrCode);
        jdbcTemplate.update("""
            INSERT INTO iam_group_role_assignment (id, group_id, role_id, tenant_id, inherit_children, created_at)
            VALUES (?, ?, ?, ?, true, ?)
            """,
            UUID.randomUUID(),
            groupId,
            roleUuid,
            tenantUuid,
            now
        );
      }
    }
  }

  @Override
  @Transactional
  public void deleteGroup(String groupId, String tenantId) {
    UUID gUuid = toUuid(groupId);
    UUID tUuid = toUuid(tenantId);
    if (tUuid != null) {
      jdbcTemplate.update("DELETE FROM iam_group WHERE (id = ? OR code = ?) AND tenant_id = ?", gUuid, groupId, tUuid);
    } else {
      jdbcTemplate.update("DELETE FROM iam_group WHERE id = ? OR code = ?", gUuid, groupId);
    }
  }

  @Override
  public Optional<GroupDetail> getGroup(String id) {
    UUID gUuid = toUuid(id);
    String sql = """
        SELECT g.id, g.code, g.name, g.description, g.tenant_id, gra.role_id
        FROM iam_group g
        LEFT JOIN iam_group_role_assignment gra ON g.id = gra.group_id
        WHERE g.id = ? OR g.code = ?
        """;
    List<String> roleIds = new ArrayList<>();
    String[] meta = new String[4];
    boolean[] found = new boolean[1];

    jdbcTemplate.query(sql, rs -> {
      found[0] = true;
      if (meta[0] == null) {
        meta[0] = rs.getString("id");
        meta[1] = rs.getString("name") != null ? rs.getString("name") : rs.getString("code");
        meta[2] = rs.getString("description");
        meta[3] = rs.getString("tenant_id");
      }
      String rId = rs.getString("role_id");
      if (rId != null && !roleIds.contains(rId)) {
        roleIds.add(rId);
      }
    }, gUuid, id);

    if (!found[0]) {
      return Optional.empty();
    }
    return Optional.of(new GroupDetail(meta[0], meta[1], meta[2], meta[3], List.copyOf(roleIds)));
  }

  @Override
  @Transactional
  public void updateGroup(UpdateGroupCommand command) {
    UUID gUuid = toUuid(command.id());
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        "UPDATE iam_group SET description = ?, updated_at = ? WHERE id = ? OR code = ?",
        command.description(),
        now,
        gUuid,
        command.id()
    );

    UUID actualGroupId = resolveGroupId(command.id());
    jdbcTemplate.update("DELETE FROM iam_group_role_assignment WHERE group_id = ?", actualGroupId);

    if (command.roleIds() != null && !command.roleIds().isEmpty()) {
      UUID tenantUuid = resolveGroupTenantId(actualGroupId);
      for (String roleIdOrCode : command.roleIds()) {
        UUID roleUuid = resolveRoleId(roleIdOrCode);
        jdbcTemplate.update("""
            INSERT INTO iam_group_role_assignment (id, group_id, role_id, tenant_id, inherit_children, created_at)
            VALUES (?, ?, ?, ?, true, ?)
            """,
            UUID.randomUUID(),
            actualGroupId,
            roleUuid,
            tenantUuid,
            now
        );
      }
    }
  }

  @Override
  @Transactional
  public void addGroupMember(String groupId, String username, String tenantId) {
    UUID actualGroupId = resolveGroupId(groupId);
    UUID userUuid = resolveUserId(username);
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        """
        INSERT INTO iam_user_group_membership (group_id, user_id, joined_at)
        SELECT ?, ?, ?
        WHERE NOT EXISTS (SELECT 1 FROM iam_user_group_membership WHERE group_id = ? AND user_id = ?)
        """,
        actualGroupId, userUuid, now, actualGroupId, userUuid
    );
  }

  @Override
  @Transactional
  public void removeGroupMember(String groupId, String username) {
    UUID actualGroupId = resolveGroupId(groupId);
    UUID userUuid = resolveUserId(username);
    jdbcTemplate.update(
        "DELETE FROM iam_user_group_membership WHERE group_id = ? AND user_id = ?",
        actualGroupId, userUuid
    );
  }

  @Override
  public List<String> getUserGroupNames(String username) {
    UUID userUuid = resolveUserId(username);
    String sql = """
        SELECT g.name
        FROM iam_group g
        JOIN iam_user_group_membership gm ON g.id = gm.group_id
        WHERE gm.user_id = ?
        ORDER BY g.name ASC
        """;
    return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("name"), userUuid);
  }

  private String resolvePermissionCode(String permIdOrCode) {
    UUID pUuid = toUuid(permIdOrCode);
    List<String> codes = jdbcTemplate.query(
        "SELECT code FROM iam_permission WHERE id = ? OR code = ?",
        (rs, rowNum) -> rs.getString("code"),
        pUuid, permIdOrCode
    );
    return codes.isEmpty() ? permIdOrCode : codes.getFirst();
  }

  private UUID resolveRoleId(String roleIdOrCode) {
    UUID rUuid = toUuid(roleIdOrCode);
    List<UUID> ids = jdbcTemplate.query(
        "SELECT id FROM iam_role WHERE id = ? OR code = ? OR name = ?",
        (rs, rowNum) -> (UUID) rs.getObject("id"),
        rUuid, roleIdOrCode, roleIdOrCode
    );
    return ids.isEmpty() ? (rUuid != null ? rUuid : UUID.randomUUID()) : ids.getFirst();
  }

  private UUID resolveGroupId(String groupIdOrCode) {
    UUID gUuid = toUuid(groupIdOrCode);
    List<UUID> ids = jdbcTemplate.query(
        "SELECT id FROM iam_group WHERE id = ? OR code = ? OR name = ?",
        (rs, rowNum) -> (UUID) rs.getObject("id"),
        gUuid, groupIdOrCode, groupIdOrCode
    );
    return ids.isEmpty() ? (gUuid != null ? gUuid : UUID.randomUUID()) : ids.getFirst();
  }

  private UUID resolveUserId(String username) {
    UUID uUuid = toUuid(username);
    List<UUID> ids = jdbcTemplate.query(
        "SELECT id FROM iam_user WHERE id = ? OR email = ?",
        (rs, rowNum) -> (UUID) rs.getObject("id"),
        uUuid, username
    );
    return ids.isEmpty() ? (uUuid != null ? uUuid : toUuid(username)) : ids.getFirst();
  }

  private UUID resolveGroupTenantId(UUID groupId) {
    List<UUID> ids = jdbcTemplate.query(
        "SELECT tenant_id FROM iam_group WHERE id = ?",
        (rs, rowNum) -> (UUID) rs.getObject("tenant_id"),
        groupId
    );
    return ids.isEmpty() || ids.getFirst() == null ? UUID.fromString("00000000-0000-0000-0000-000000000001") : ids.getFirst();
  }

  private static final class RoleAggregation {
    final String id;
    final String name;
    final String description;
    final String tenantId;
    final List<String> permissions = new ArrayList<>();

    RoleAggregation(String id, String name, String description, String tenantId) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.tenantId = tenantId;
    }
  }

  private static final class GroupAggregation {
    final String id;
    final String name;
    final String description;
    final String tenantId;
    final int memberCount;
    final List<String> roles = new ArrayList<>();

    GroupAggregation(String id, String name, String description, String tenantId, int memberCount) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.tenantId = tenantId;
      this.memberCount = memberCount;
    }
  }
}
