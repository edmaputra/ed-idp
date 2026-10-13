package io.github.edmaputra.edidp.ui.service;

import java.util.List;
import java.util.Optional;

/**
 * Service managing RBAC roles, their assigned permissions, and system catalog.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface AdminRbacService {

  /**
   * System or custom tenant permission summary matching ed-iam catalog.
   *
   * @param id permission ID
   * @param code unique permission code (e.g. clients:read, PATIENT_EXPORT)
   * @param name human-readable display name
   * @param description explanation of entitlement
   * @param category domain category (e.g. CLIENTS, USERS, RBAC, GENERAL)
   * @param systemPermission whether this is an immutable system permission
   * @param tenantId tenant owning this custom permission (null for global)
   */
  record PermissionSummary(
      String id,
      String code,
      String name,
      String description,
      String category,
      boolean systemPermission,
      String tenantId
  ) {
    public PermissionSummary {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Permission ID must not be blank");
      }
      if (code == null || code.isBlank()) {
        code = name;
      }
      if (name == null || name.isBlank()) {
        name = code;
      }
      if (category == null || category.isBlank()) {
        category = "GENERAL";
      }
    }

    public PermissionSummary(String id, String name, String description) {
      this(id, name, name, description, "GENERAL", true, null);
    }
  }

  /**
   * Role summary with its assigned permission names.
   *
   * @param id role ID
   * @param name role name (e.g. ROLE_ADMIN)
   * @param description role description
   * @param tenantId tenant owning this role
   * @param permissions list of assigned permission names
   */
  record RoleSummary(
      String id,
      String name,
      String description,
      String tenantId,
      List<String> permissions
  ) {
    public RoleSummary {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Role ID must not be blank");
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Role name must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (permissions == null) {
        permissions = List.of();
      }
    }
  }

  /**
   * Group summary with its assigned roles and members.
   *
   * @param id group ID
   * @param name group name
   * @param description group description
   * @param tenantId tenant owning this group
   * @param roles list of assigned role names
   * @param memberCount number of user members
   */
  record GroupSummary(
      String id,
      String name,
      String description,
      String tenantId,
      List<String> roles,
      int memberCount
  ) {
    public GroupSummary {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Group ID must not be blank");
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Group name must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (roles == null) {
        roles = List.of();
      }
    }
  }

  /**
   * Command to create a new role with associated permissions.
   *
   * @param name role name
   * @param description description
   * @param tenantId tenant ID
   * @param permissionIds list of permission IDs to grant
   */
  record CreateRoleCommand(
      String name,
      String description,
      String tenantId,
      List<String> permissionIds
  ) {
    public CreateRoleCommand {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Role name must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (permissionIds == null) {
        permissionIds = List.of();
      }
    }
  }

  /**
   * Detailed role configuration including permission IDs.
   *
   * @param id role ID
   * @param name role name
   * @param description role description
   * @param tenantId tenant ID
   * @param permissionIds list of granted permission IDs
   * @author edmaputra
   * @since 0.0.1
   */
  record RoleDetail(
      String id,
      String name,
      String description,
      String tenantId,
      List<String> permissionIds
  ) {
    public RoleDetail {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Role ID must not be blank");
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Role name must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (permissionIds == null) {
        permissionIds = List.of();
      }
    }
  }

  /**
   * Command to update an existing role.
   *
   * @param id role ID
   * @param description updated description
   * @param permissionIds list of granted permission IDs
   * @author edmaputra
   * @since 0.0.1
   */
  record UpdateRoleCommand(
      String id,
      String description,
      List<String> permissionIds
  ) {
    public UpdateRoleCommand {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Role ID must not be blank");
      }
      if (permissionIds == null) {
        permissionIds = List.of();
      }
    }
  }

  /**
   * Command to create a new group.
   *
   * @param name group name
   * @param description description
   * @param tenantId tenant ID
   * @param roleIds list of role IDs to associate with this group
   * @author edmaputra
   * @since 0.0.1
   */
  record CreateGroupCommand(
      String name,
      String description,
      String tenantId,
      List<String> roleIds
  ) {
    public CreateGroupCommand {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Group name must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (roleIds == null) {
        roleIds = List.of();
      }
    }
  }

  /**
   * Detailed group configuration including assigned role IDs.
   *
   * @param id group ID
   * @param name group name
   * @param description group description
   * @param tenantId tenant ID
   * @param roleIds list of assigned role IDs
   * @author edmaputra
   * @since 0.0.1
   */
  record GroupDetail(
      String id,
      String name,
      String description,
      String tenantId,
      List<String> roleIds
  ) {
    public GroupDetail {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Group ID must not be blank");
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Group name must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (roleIds == null) {
        roleIds = List.of();
      }
    }
  }

  /**
   * Command to update an existing group.
   *
   * @param id group ID
   * @param description updated description
   * @param roleIds list of assigned role IDs
   * @author edmaputra
   * @since 0.0.1
   */
  record UpdateGroupCommand(
      String id,
      String description,
      List<String> roleIds
  ) {
    public UpdateGroupCommand {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Group ID must not be blank");
      }
      if (roleIds == null) {
        roleIds = List.of();
      }
    }
  }

  /**
   * Command to create a new custom permission.
   *
   * @param code unique programmatic permission code (e.g. reports:export)
   * @param name human-readable permission name
   * @param description description of privilege
   * @param category domain category (e.g. REPORTS, CLIENTS, USERS, GENERAL)
   * @param tenantId tenant ID (null or blank for default)
   * @author edmaputra
   * @since 0.0.1
   */
  record CreatePermissionCommand(
      String code,
      String name,
      String description,
      String category,
      String tenantId
  ) {
    public CreatePermissionCommand {
      if (code == null || code.isBlank()) {
        throw new IllegalArgumentException("Permission code must not be blank");
      }
      if (name == null || name.isBlank()) {
        name = code;
      }
      if (category == null || category.isBlank()) {
        category = "GENERAL";
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
    }
  }

  /**
   * Command to update an existing custom permission.
   *
   * @param id permission ID
   * @param name updated display name
   * @param description updated description
   * @param category updated category
   * @author edmaputra
   * @since 0.0.1
   */
  record UpdatePermissionCommand(
      String id,
      String name,
      String description,
      String category
  ) {
    public UpdatePermissionCommand {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Permission ID must not be blank");
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("Permission name must not be blank");
      }
      if (category == null || category.isBlank()) {
        category = "GENERAL";
      }
    }
  }

  /**
   * Lists all system permissions.
   *
   * @return list of permissions
   */
  List<PermissionSummary> listPermissions();

  /**
   * Lists permissions filtered optionally by tenant and category.
   *
   * @param tenantId optional tenant ID
   * @param category optional category
   * @return list of matching permissions
   */
  List<PermissionSummary> listPermissions(String tenantId, String category);

  /**
   * Retrieves a permission by its unique ID.
   *
   * @param id permission ID
   * @return optional permission summary
   */
  Optional<PermissionSummary> getPermissionById(String id);

  /**
   * Retrieves a permission by its unique programmatic code.
   *
   * @param code permission code
   * @return optional permission summary
   */
  Optional<PermissionSummary> getPermissionByCode(String code);

  /**
   * Creates a new custom permission.
   *
   * @param command creation command
   * @return created permission summary
   */
  PermissionSummary createPermission(CreatePermissionCommand command);

  /**
   * Updates an existing custom permission.
   *
   * @param command update command
   * @return updated permission summary
   */
  PermissionSummary updatePermission(UpdatePermissionCommand command);

  /**
   * Deletes a custom permission by its ID.
   *
   * @param id permission ID
   */
  void deletePermission(String id);

  /**
   * Lists all roles in the directory.
   *
   * @return list of roles
   */
  List<RoleSummary> listRoles();

  /**
   * Counts total roles.
   *
   * @return role count
   */
  long countRoles();

  /**
   * Creates a new role.
   *
   * @param command creation command
   */
  void createRole(CreateRoleCommand command);

  /**
   * Deletes a role by ID and tenant ID.
   *
   * @param roleId role ID
   * @param tenantId tenant ID
   */
  void deleteRole(String roleId, String tenantId);

  /**
   * Retrieves a role by ID including its granted permission IDs.
   *
   * @param id role ID
   * @return optional role detail
   */
  Optional<RoleDetail> getRole(String id);

  /**
   * Updates an existing role's description and granted permissions.
   *
   * @param command update command
   */
  void updateRole(UpdateRoleCommand command);

  /**
   * Lists all groups in the directory.
   *
   * @return list of groups
   */
  List<GroupSummary> listGroups();

  /**
   * Counts total groups.
   *
   * @return group count
   */
  long countGroups();

  /**
   * Creates a new group.
   *
   * @param command creation command
   */
  void createGroup(CreateGroupCommand command);

  /**
   * Deletes a group by ID and tenant ID.
   *
   * @param groupId group ID
   * @param tenantId tenant ID
   */
  void deleteGroup(String groupId, String tenantId);

  /**
   * Retrieves a group by ID including its assigned role IDs.
   *
   * @param id group ID
   * @return optional group detail
   */
  Optional<GroupDetail> getGroup(String id);

  /**
   * Updates an existing group's description and assigned roles.
   *
   * @param command update command
   */
  void updateGroup(UpdateGroupCommand command);

  /**
   * Adds a member username to a group.
   *
   * @param groupId group ID
   * @param username username
   * @param tenantId tenant ID
   */
  void addGroupMember(String groupId, String username, String tenantId);

  /**
   * Removes a member username from a group.
   *
   * @param groupId group ID
   * @param username username
   */
  void removeGroupMember(String groupId, String username);

  /**
   * Gets group names assigned to a user.
   *
   * @param username username
   * @return list of group names
   */
  List<String> getUserGroupNames(String username);
}
