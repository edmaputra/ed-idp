package io.github.edmaputra.edidp.ui.api;

import io.github.edmaputra.edidp.ui.service.AdminClientService;
import io.github.edmaputra.edidp.ui.service.AdminRbacService;
import io.github.edmaputra.edidp.ui.service.AdminTenantService;
import io.github.edmaputra.edidp.ui.service.AdminUserService;
import io.github.edmaputra.edidp.ui.service.AdminUserService.CreateUserCommand;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Controller for IAM administrative operations (Users, Clients, Tenants).
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping("${edidp.ui.base-path:/admin}/api")
public class AdminIamRestController {

  private final AdminUserService adminUserService;
  private final AdminClientService adminClientService;
  private final AdminTenantService adminTenantService;
  private final io.github.edmaputra.edidp.ui.service.AdminRbacService adminRbacService;

  public AdminIamRestController(
      AdminUserService adminUserService,
      AdminClientService adminClientService,
      AdminTenantService adminTenantService,
      io.github.edmaputra.edidp.ui.service.AdminRbacService adminRbacService) {
    this.adminUserService = Objects.requireNonNull(adminUserService, "adminUserService must not be null");
    this.adminClientService = Objects.requireNonNull(adminClientService, "adminClientService must not be null");
    this.adminTenantService = Objects.requireNonNull(adminTenantService, "adminTenantService must not be null");
    this.adminRbacService = Objects.requireNonNull(adminRbacService, "adminRbacService must not be null");
  }

  // --- Users IAM Endpoints ---

  @GetMapping("/users")
  public List<AdminUserService.AdminUserSummary> listUsers() {
    return adminUserService.listUsers();
  }

  @PostMapping("/users")
  public ResponseEntity<Map<String, String>> createUser(@RequestBody CreateUserCommand command) {
    adminUserService.createUser(command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("message", "User created successfully", "username", command.username()));
  }

  @GetMapping("/users/{username}")
  public ResponseEntity<AdminUserService.AdminUserSummary> getUser(@PathVariable String username) {
    return adminUserService.getUser(username)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @org.springframework.web.bind.annotation.PutMapping("/users/{username}")
  public ResponseEntity<Map<String, String>> updateUser(
      @PathVariable String username,
      @RequestBody AdminUserService.UpdateUserCommand command) {
    if (!username.equalsIgnoreCase(command.username())) {
      return ResponseEntity.badRequest().body(Map.of("error", "Username in path and body do not match"));
    }
    adminUserService.updateUser(command);
    return ResponseEntity.ok(Map.of("message", "User updated successfully", "username", username));
  }

  @PatchMapping("/users/{username}/status")
  public ResponseEntity<Map<String, Object>> setUserStatus(
      @PathVariable String username,
      @RequestParam boolean enabled) {
    adminUserService.setUserEnabled(username, enabled);
    return ResponseEntity.ok(Map.of("username", username, "enabled", enabled));
  }

  // --- Clients IAM Endpoints ---

  @GetMapping("/clients")
  public List<AdminClientService.AdminClientSummary> listClients() {
    return adminClientService.listClients();
  }

  @PostMapping("/clients")
  public ResponseEntity<Map<String, String>> createClient(@RequestBody AdminClientService.CreateClientCommand command) {
    adminClientService.createClient(command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("message", "Client registered successfully", "clientId", command.clientId()));
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/clients/{clientId}")
  public ResponseEntity<Map<String, String>> deleteClient(
      @PathVariable String clientId,
      @RequestParam(required = false, defaultValue = "demo") String tenantId) {
    adminClientService.deleteClient(clientId, tenantId);
    return ResponseEntity.ok(Map.of("message", "Client deleted successfully", "clientId", clientId));
  }

  // --- Tenants IAM Endpoints ---

  @GetMapping("/tenants")
  public List<AdminTenantService.AdminTenantSummary> listTenants() {
    return adminTenantService.listTenants();
  }

  // --- RBAC & Permissions Endpoints ---

  public record UpdatePermissionRequest(String name, String description, String category) {}

  @GetMapping("/permissions")
  public List<AdminRbacService.PermissionSummary> listPermissions(
      @RequestParam(required = false) String tenantId,
      @RequestParam(required = false) String category) {
    return adminRbacService.listPermissions(tenantId, category);
  }

  @GetMapping("/permissions/{id}")
  public ResponseEntity<AdminRbacService.PermissionSummary> getPermissionById(@PathVariable String id) {
    return adminRbacService.getPermissionById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/permissions/code/{code}")
  public ResponseEntity<AdminRbacService.PermissionSummary> getPermissionByCode(@PathVariable String code) {
    return adminRbacService.getPermissionByCode(code)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/permissions")
  public ResponseEntity<AdminRbacService.PermissionSummary> createPermission(
      @RequestBody AdminRbacService.CreatePermissionCommand command) {
    AdminRbacService.PermissionSummary created = adminRbacService.createPermission(command);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/permissions/{id}")
  public ResponseEntity<AdminRbacService.PermissionSummary> updatePermission(
      @PathVariable String id,
      @RequestBody UpdatePermissionRequest request) {
    AdminRbacService.PermissionSummary updated = adminRbacService.updatePermission(
        new AdminRbacService.UpdatePermissionCommand(id, request.name(), request.description(), request.category())
    );
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/permissions/{id}")
  public ResponseEntity<Void> deletePermission(@PathVariable String id) {
    adminRbacService.deletePermission(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/roles")
  public List<AdminRbacService.RoleSummary> listRoles() {
    return adminRbacService.listRoles();
  }

  @PostMapping("/roles")
  public ResponseEntity<Map<String, String>> createRole(@RequestBody AdminRbacService.CreateRoleCommand command) {
    adminRbacService.createRole(command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("message", "Role created successfully", "roleName", command.name()));
  }

  public record UpdateRoleRequest(String description, List<String> permissions) {}

  @GetMapping("/roles/{roleId}")
  public ResponseEntity<AdminRbacService.RoleDetail> getRole(@PathVariable String roleId) {
    return adminRbacService.getRole(roleId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PutMapping("/roles/{roleId}")
  public ResponseEntity<Map<String, String>> updateRole(
      @PathVariable String roleId,
      @RequestBody UpdateRoleRequest request) {
    adminRbacService.updateRole(new AdminRbacService.UpdateRoleCommand(
        roleId,
        request.description(),
        request.permissions()
    ));
    return ResponseEntity.ok(Map.of("message", "Role updated successfully", "roleId", roleId));
  }

  @DeleteMapping("/roles/{roleId}")
  public ResponseEntity<Map<String, String>> deleteRole(
      @PathVariable String roleId,
      @RequestParam(required = false, defaultValue = "demo") String tenantId) {
    adminRbacService.deleteRole(roleId, tenantId);
    return ResponseEntity.ok(Map.of("message", "Role deleted successfully", "roleId", roleId));
  }

  @GetMapping("/groups")
  public List<AdminRbacService.GroupSummary> listGroups() {
    return adminRbacService.listGroups();
  }

  public record UpdateGroupRequest(String description, List<String> roles) {}

  @GetMapping("/groups/{groupId}")
  public ResponseEntity<AdminRbacService.GroupDetail> getGroup(@PathVariable String groupId) {
    return adminRbacService.getGroup(groupId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/groups")
  public ResponseEntity<Map<String, String>> createGroup(@RequestBody AdminRbacService.CreateGroupCommand command) {
    adminRbacService.createGroup(command);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("message", "Group created successfully", "groupName", command.name()));
  }

  @PutMapping("/groups/{groupId}")
  public ResponseEntity<Map<String, String>> updateGroup(
      @PathVariable String groupId,
      @RequestBody UpdateGroupRequest request) {
    adminRbacService.updateGroup(new AdminRbacService.UpdateGroupCommand(
        groupId,
        request.description(),
        request.roles()
    ));
    return ResponseEntity.ok(Map.of("message", "Group updated successfully", "groupId", groupId));
  }

  @DeleteMapping("/groups/{groupId}")
  public ResponseEntity<Map<String, String>> deleteGroup(
      @PathVariable String groupId,
      @RequestParam(required = false, defaultValue = "demo") String tenantId) {
    adminRbacService.deleteGroup(groupId, tenantId);
    return ResponseEntity.ok(Map.of("message", "Group deleted successfully", "groupId", groupId));
  }

  @PostMapping("/groups/{groupId}/members")
  public ResponseEntity<Map<String, String>> addGroupMember(
      @PathVariable String groupId,
      @RequestParam String username,
      @RequestParam(required = false, defaultValue = "demo") String tenantId) {
    adminRbacService.addGroupMember(groupId, username, tenantId);
    return ResponseEntity.ok(Map.of("message", "Member added successfully", "username", username));
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/groups/{groupId}/members/{username}")
  public ResponseEntity<Map<String, String>> removeGroupMember(
      @PathVariable String groupId,
      @PathVariable String username) {
    adminRbacService.removeGroupMember(groupId, username);
    return ResponseEntity.ok(Map.of("message", "Member removed successfully", "username", username));
  }
}

