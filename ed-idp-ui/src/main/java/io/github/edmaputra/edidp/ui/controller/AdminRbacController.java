package io.github.edmaputra.edidp.ui.controller;

import io.github.edmaputra.edidp.ui.UiProperties;
import io.github.edmaputra.edidp.ui.service.AdminRbacService;
import io.github.edmaputra.edidp.ui.service.AdminRbacService.CreateGroupCommand;
import io.github.edmaputra.edidp.ui.service.AdminRbacService.CreateRoleCommand;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller managing Roles, Groups, and Permissions views in the admin console.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
@RequestMapping("${edidp.ui.base-path:/admin}/rbac")
public class AdminRbacController {

  private final AdminRbacService adminRbacService;
  private final UiProperties uiProperties;

  public AdminRbacController(AdminRbacService adminRbacService, UiProperties uiProperties) {
    this.adminRbacService = Objects.requireNonNull(adminRbacService, "adminRbacService must not be null");
    this.uiProperties = Objects.requireNonNull(uiProperties, "uiProperties must not be null");
  }

  @GetMapping
  public String listRbac(Model model) {
    populateRbacModel(model);
    return "admin/rbac/list";
  }

  @GetMapping("/roles/table")
  public String rolesTable(Model model) {
    model.addAttribute("roles", adminRbacService.listRoles());
    model.addAttribute("basePath", uiProperties.basePath());
    return "admin/rbac/list :: rolesTableBody";
  }

  @PostMapping("/roles")
  public String createRole(
      @RequestParam String name,
      @RequestParam(required = false, defaultValue = "") String description,
      @RequestParam(required = false, defaultValue = "demo") String tenantId,
      @RequestParam(required = false) List<String> permissions,
      Model model) {
    adminRbacService.createRole(new CreateRoleCommand(
        name,
        description,
        tenantId,
        permissions != null ? permissions : List.of()
    ));
    return rolesTable(model);
  }

  @DeleteMapping("/roles/{roleId}")
  public String deleteRole(
      @PathVariable String roleId,
      @RequestParam(required = false, defaultValue = "demo") String tenantId,
      Model model) {
    adminRbacService.deleteRole(roleId, tenantId);
    return rolesTable(model);
  }

  @GetMapping("/roles/{roleId}/edit")
  public String editRoleModal(@PathVariable String roleId, Model model) {
    var role = adminRbacService.getRole(roleId)
        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
    model.addAttribute("role", role);
    model.addAttribute("availablePermissions", adminRbacService.listPermissions());
    model.addAttribute("basePath", uiProperties.basePath());
    return "admin/rbac/edit-role-modal :: editRoleModal";
  }

  @PostMapping("/roles/{roleId}")
  public String updateRole(
      @PathVariable String roleId,
      @RequestParam(required = false, defaultValue = "") String description,
      @RequestParam(required = false) List<String> permissions,
      Model model) {
    adminRbacService.updateRole(new AdminRbacService.UpdateRoleCommand(
        roleId,
        description,
        permissions != null ? permissions : List.of()
    ));
    return rolesTable(model);
  }

  @GetMapping("/groups/table")
  public String groupsTable(Model model) {
    model.addAttribute("groups", adminRbacService.listGroups());
    model.addAttribute("basePath", uiProperties.basePath());
    return "admin/rbac/list :: groupsTableBody";
  }

  @PostMapping("/groups")
  public String createGroup(
      @RequestParam String name,
      @RequestParam(required = false, defaultValue = "") String description,
      @RequestParam(required = false, defaultValue = "demo") String tenantId,
      @RequestParam(required = false) List<String> roleIds,
      Model model) {
    adminRbacService.createGroup(new CreateGroupCommand(
        name,
        description,
        tenantId,
        roleIds != null ? roleIds : List.of()
    ));
    return groupsTable(model);
  }

  @DeleteMapping("/groups/{groupId}")
  public String deleteGroup(
      @PathVariable String groupId,
      @RequestParam(required = false, defaultValue = "demo") String tenantId,
      Model model) {
    adminRbacService.deleteGroup(groupId, tenantId);
    return groupsTable(model);
  }

  @GetMapping("/groups/{groupId}/edit")
  public String editGroupModal(@PathVariable String groupId, Model model) {
    var group = adminRbacService.getGroup(groupId)
        .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
    model.addAttribute("group", group);
    model.addAttribute("availableRoles", adminRbacService.listRoles());
    model.addAttribute("basePath", uiProperties.basePath());
    return "admin/rbac/edit-group-modal :: editGroupModal";
  }

  @PostMapping("/groups/{groupId}")
  public String updateGroup(
      @PathVariable String groupId,
      @RequestParam(required = false, defaultValue = "") String description,
      @RequestParam(required = false) List<String> roleIds,
      Model model) {
    adminRbacService.updateGroup(new AdminRbacService.UpdateGroupCommand(
        groupId,
        description,
        roleIds != null ? roleIds : List.of()
    ));
    return groupsTable(model);
  }

  @PostMapping("/groups/{groupId}/members")
  public String addGroupMember(
      @PathVariable String groupId,
      @RequestParam String username,
      @RequestParam(required = false, defaultValue = "demo") String tenantId,
      Model model) {
    adminRbacService.addGroupMember(groupId, username, tenantId);
    return groupsTable(model);
  }

  @GetMapping("/permissions/table")
  public String permissionsTable(Model model) {
    model.addAttribute("permissions", adminRbacService.listPermissions());
    model.addAttribute("basePath", uiProperties.basePath());
    return "admin/rbac/list :: permissionsCatalogBody";
  }

  @PostMapping("/permissions")
  public String createPermission(
      @RequestParam String code,
      @RequestParam String name,
      @RequestParam(required = false, defaultValue = "") String description,
      @RequestParam(required = false, defaultValue = "GENERAL") String category,
      @RequestParam(required = false, defaultValue = "demo") String tenantId,
      Model model) {
    adminRbacService.createPermission(new AdminRbacService.CreatePermissionCommand(
        code,
        name,
        description,
        category,
        tenantId
    ));
    return permissionsTable(model);
  }

  @DeleteMapping("/permissions/{id}")
  public String deletePermission(
      @PathVariable String id,
      Model model) {
    adminRbacService.deletePermission(id);
    return permissionsTable(model);
  }

  @GetMapping("/permissions/{id}/edit")
  public String editPermissionModal(@PathVariable String id, Model model) {
    var permission = adminRbacService.getPermissionById(id)
        .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + id));
    model.addAttribute("permission", permission);
    model.addAttribute("basePath", uiProperties.basePath());
    return "admin/rbac/edit-permission-modal :: editPermissionModal";
  }

  @PostMapping("/permissions/{id}")
  public String updatePermission(
      @PathVariable String id,
      @RequestParam String name,
      @RequestParam(required = false, defaultValue = "") String description,
      @RequestParam(required = false, defaultValue = "GENERAL") String category,
      Model model) {
    adminRbacService.updatePermission(new AdminRbacService.UpdatePermissionCommand(
        id,
        name,
        description,
        category
    ));
    return permissionsTable(model);
  }

  private void populateRbacModel(Model model) {
    model.addAttribute("roles", adminRbacService.listRoles());
    model.addAttribute("groups", adminRbacService.listGroups());
    model.addAttribute("permissions", adminRbacService.listPermissions());
    model.addAttribute("basePath", uiProperties.basePath());
  }
}
