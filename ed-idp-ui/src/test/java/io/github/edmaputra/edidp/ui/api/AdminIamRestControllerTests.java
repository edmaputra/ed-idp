package io.github.edmaputra.edidp.ui.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.edmaputra.edidp.ui.service.AdminClientService;
import io.github.edmaputra.edidp.ui.service.AdminRbacService;
import io.github.edmaputra.edidp.ui.service.AdminTenantService;
import io.github.edmaputra.edidp.ui.service.AdminUserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests for {@link AdminIamRestController}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
class AdminIamRestControllerTests {

  @Mock
  private AdminUserService adminUserService;

  @Mock
  private AdminClientService adminClientService;

  @Mock
  private AdminTenantService adminTenantService;

  @Mock
  private AdminRbacService adminRbacService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(
        new AdminIamRestController(adminUserService, adminClientService, adminTenantService, adminRbacService)
    ).build();
  }

  @Test
  void listUsers_returnsOk() throws Exception {
    when(adminUserService.listUsers()).thenReturn(List.of(
        new AdminUserService.AdminUserSummary("john", true, "demo", "John", "j@ex.com", List.of("ROLE_USER"), List.of(), "IT")
    ));

    mockMvc.perform(get("/admin/api/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("john"));
  }

  @Test
  void getUser_whenExists_returnsUser() throws Exception {
    var user = new AdminUserService.AdminUserSummary(
        "alice", true, "demo", "Alice Smith", "alice@example.com",
        List.of("ROLE_USER"), List.of("Engineering"), "IT"
    );
    when(adminUserService.getUser("alice")).thenReturn(Optional.of(user));

    mockMvc.perform(get("/admin/api/users/alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.groups[0]").value("Engineering"));
  }

  @Test
  void getUser_whenNotFound_returns404() throws Exception {
    when(adminUserService.getUser("unknown")).thenReturn(Optional.empty());

    mockMvc.perform(get("/admin/api/users/unknown"))
        .andExpect(status().isNotFound());
  }

  @Test
  void createUser_returnsCreated() throws Exception {
    mockMvc.perform(post("/admin/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "newuser",
                  "password": "secretPassword",
                  "fullName": "New User",
                  "email": "new@example.com",
                  "department": "Engineering",
                  "tenantId": "demo",
                  "role": "ROLE_USER"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("newuser"));

    verify(adminUserService).createUser(any(AdminUserService.CreateUserCommand.class));
  }

  @Test
  void updateUser_success() throws Exception {
    mockMvc.perform(put("/admin/api/users/alice")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "alice",
                  "fullName": "Alice Updated",
                  "email": "alice_updated@example.com",
                  "department": "Platform",
                  "tenantId": "demo",
                  "enabled": true,
                  "roles": ["ROLE_ADMIN"],
                  "groups": ["Engineering"]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"));

    verify(adminUserService).updateUser(any(AdminUserService.UpdateUserCommand.class));
  }

  @Test
  void updateUser_mismatchedUsername_returnsBadRequest() throws Exception {
    mockMvc.perform(put("/admin/api/users/bob")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "username": "alice",
                  "fullName": "Alice",
                  "email": "a@ex.com",
                  "department": "Platform",
                  "tenantId": "demo",
                  "enabled": true,
                  "roles": [],
                  "groups": []
                }
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void setUserStatus_updatesStatus() throws Exception {
    mockMvc.perform(patch("/admin/api/users/alice/status").param("enabled", "false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));

    verify(adminUserService).setUserEnabled("alice", false);
  }

  @Test
  void listClients_returnsOk() throws Exception {
    when(adminClientService.listClients()).thenReturn(List.of());

    mockMvc.perform(get("/admin/api/clients"))
        .andExpect(status().isOk());
  }

  @Test
  void deleteClient_returnsOk() throws Exception {
    mockMvc.perform(delete("/admin/api/clients/my-client").param("tenantId", "demo"))
        .andExpect(status().isOk());

    verify(adminClientService).deleteClient("my-client", "demo");
  }

  @Test
  void listTenants_returnsOk() throws Exception {
    when(adminTenantService.listTenants()).thenReturn(List.of());

    mockMvc.perform(get("/admin/api/tenants"))
        .andExpect(status().isOk());
  }

  @Test
  void listRoles_returnsOk() throws Exception {
    when(adminRbacService.listRoles()).thenReturn(List.of());

    mockMvc.perform(get("/admin/api/roles"))
        .andExpect(status().isOk());
  }

  @Test
  void listGroups_returnsOk() throws Exception {
    when(adminRbacService.listGroups()).thenReturn(List.of());

    mockMvc.perform(get("/admin/api/groups"))
        .andExpect(status().isOk());
  }

  @Test
  void addGroupMember_returnsOk() throws Exception {
    mockMvc.perform(post("/admin/api/groups/group-eng/members")
            .param("username", "alice")
            .param("tenantId", "demo"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"));

    verify(adminRbacService).addGroupMember("group-eng", "alice", "demo");
  }

  @Test
  void removeGroupMember_returnsOk() throws Exception {
    mockMvc.perform(delete("/admin/api/groups/group-eng/members/alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"));

    verify(adminRbacService).removeGroupMember("group-eng", "alice");
  }

  @Test
  void listPermissions_withParams_returnsOk() throws Exception {
    var perm = new AdminRbacService.PermissionSummary(
        "perm-1", "clients:read", "View Clients", "Desc", "CLIENTS", true, null
    );
    when(adminRbacService.listPermissions("demo", "CLIENTS")).thenReturn(List.of(perm));

    mockMvc.perform(get("/admin/api/permissions")
            .param("tenantId", "demo")
            .param("category", "CLIENTS"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("clients:read"))
        .andExpect(jsonPath("$[0].category").value("CLIENTS"));
  }

  @Test
  void getPermissionById_whenFound_returnsOk() throws Exception {
    var perm = new AdminRbacService.PermissionSummary(
        "perm-1", "clients:read", "View Clients", "Desc", "CLIENTS", true, null
    );
    when(adminRbacService.getPermissionById("perm-1")).thenReturn(Optional.of(perm));

    mockMvc.perform(get("/admin/api/permissions/perm-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("clients:read"));
  }

  @Test
  void getPermissionById_whenNotFound_returns404() throws Exception {
    when(adminRbacService.getPermissionById("unknown")).thenReturn(Optional.empty());

    mockMvc.perform(get("/admin/api/permissions/unknown"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getPermissionByCode_whenFound_returnsOk() throws Exception {
    var perm = new AdminRbacService.PermissionSummary(
        "perm-1", "clients:read", "View Clients", "Desc", "CLIENTS", true, null
    );
    when(adminRbacService.getPermissionByCode("clients:read")).thenReturn(Optional.of(perm));

    mockMvc.perform(get("/admin/api/permissions/code/clients:read"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("perm-1"));
  }

  @Test
  void createPermission_returnsCreated() throws Exception {
    var created = new AdminRbacService.PermissionSummary(
        "perm-new", "reports:export", "Export Reports", "Desc", "REPORTS", false, "demo"
    );
    when(adminRbacService.createPermission(any(AdminRbacService.CreatePermissionCommand.class))).thenReturn(created);

    mockMvc.perform(post("/admin/api/permissions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "code": "reports:export",
                  "name": "Export Reports",
                  "description": "Desc",
                  "category": "REPORTS",
                  "tenantId": "demo"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("reports:export"))
        .andExpect(jsonPath("$.systemPermission").value(false));
  }

  @Test
  void updatePermission_returnsOk() throws Exception {
    var updated = new AdminRbacService.PermissionSummary(
        "perm-custom", "reports:export", "Updated Name", "Updated Desc", "REPORTS", false, "demo"
    );
    when(adminRbacService.updatePermission(any(AdminRbacService.UpdatePermissionCommand.class))).thenReturn(updated);

    mockMvc.perform(put("/admin/api/permissions/perm-custom")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Updated Name",
                  "description": "Updated Desc",
                  "category": "REPORTS"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Name"));
  }

  @Test
  void deletePermission_returnsNoContent() throws Exception {
    mockMvc.perform(delete("/admin/api/permissions/perm-custom"))
        .andExpect(status().isNoContent());

    verify(adminRbacService).deletePermission("perm-custom");
  }

  @Test
  void getRole_whenFound_returnsOk() throws Exception {
    var role = new AdminRbacService.RoleDetail("role-admin", "ROLE_ADMIN", "Admin", "demo", List.of("perm-1"));
    when(adminRbacService.getRole("role-admin")).thenReturn(Optional.of(role));

    mockMvc.perform(get("/admin/api/roles/role-admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("ROLE_ADMIN"));
  }

  @Test
  void updateRole_returnsOk() throws Exception {
    mockMvc.perform(put("/admin/api/roles/role-admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "description": "Updated Admin",
                  "permissions": ["perm-1", "perm-2"]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Role updated successfully"));

    verify(adminRbacService).updateRole(any(AdminRbacService.UpdateRoleCommand.class));
  }

  @Test
  void getGroup_whenFound_returnsOk() throws Exception {
    var group = new AdminRbacService.GroupDetail("group-eng", "Engineering", "Desc", "demo", List.of("role-user"));
    when(adminRbacService.getGroup("group-eng")).thenReturn(Optional.of(group));

    mockMvc.perform(get("/admin/api/groups/group-eng"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Engineering"));
  }

  @Test
  void updateGroup_returnsOk() throws Exception {
    mockMvc.perform(put("/admin/api/groups/group-eng")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "description": "Updated Group",
                  "roles": ["role-admin"]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Group updated successfully"));

    verify(adminRbacService).updateGroup(any(AdminRbacService.UpdateGroupCommand.class));
  }
}
