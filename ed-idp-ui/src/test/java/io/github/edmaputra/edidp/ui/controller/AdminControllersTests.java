package io.github.edmaputra.edidp.ui.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import io.github.edmaputra.edidp.ui.UiProperties;
import io.github.edmaputra.edidp.ui.service.AdminClientService;
import io.github.edmaputra.edidp.ui.service.AdminRbacService;
import io.github.edmaputra.edidp.ui.service.AdminTenantService;
import io.github.edmaputra.edidp.ui.service.AdminUserService;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests for {@link AdminDashboardController},
 * {@link AdminClientController},
 * and {@link AdminTenantController}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
class AdminControllersTests {

  @Mock
  private AdminClientService adminClientService;

  @Mock
  private AdminTenantService adminTenantService;

  @Mock
  private AdminUserService adminUserService;

  @Mock
  private io.github.edmaputra.edidp.ui.service.AdminRbacService adminRbacService;

  private UiProperties uiProperties;
  private MockMvc dashboardMockMvc;
  private MockMvc clientMockMvc;
  private MockMvc tenantMockMvc;
  private MockMvc userMockMvc;
  private MockMvc rbacMockMvc;

  @BeforeEach
  void setUp() {
    uiProperties = new UiProperties(true, "/admin");
    dashboardMockMvc = MockMvcBuilders.standaloneSetup(
        new AdminDashboardController(uiProperties, adminClientService, adminTenantService, adminUserService, adminRbacService)).build();

    clientMockMvc = MockMvcBuilders.standaloneSetup(
        new AdminClientController(uiProperties, adminClientService)).build();

    tenantMockMvc = MockMvcBuilders.standaloneSetup(
        new AdminTenantController(uiProperties, adminTenantService)).build();

    userMockMvc = MockMvcBuilders.standaloneSetup(
        new AdminUserController(uiProperties, adminUserService, adminRbacService)).build();

    rbacMockMvc = MockMvcBuilders.standaloneSetup(
        new AdminRbacController(adminRbacService, uiProperties)).build();
  }

  @Test
  void loginView_returnsLoginTemplate() throws Exception {
    dashboardMockMvc.perform(get("/admin/login"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/login"));
  }

  @Test
  void dashboardView_populatesStats() throws Exception {
    when(adminClientService.countClients()).thenReturn(7L);
    when(adminTenantService.countTenants()).thenReturn(3L);
    when(adminUserService.countUsers()).thenReturn(12L);
    when(adminRbacService.countRoles()).thenReturn(4L);
    when(adminRbacService.countGroups()).thenReturn(2L);

    dashboardMockMvc.perform(get("/admin/dashboard"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/dashboard"))
        .andExpect(model().attribute("clientCount", 7L))
        .andExpect(model().attribute("tenantCount", 3L))
        .andExpect(model().attribute("userCount", 12L))
        .andExpect(model().attribute("roleCount", 4L))
        .andExpect(model().attribute("groupCount", 2L));
  }

  @Test
  void rbacListView_returnsList() throws Exception {
    when(adminRbacService.listRoles()).thenReturn(List.of());
    when(adminRbacService.listGroups()).thenReturn(List.of());
    when(adminRbacService.listPermissions()).thenReturn(List.of());

    rbacMockMvc.perform(get("/admin/rbac"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/list"))
        .andExpect(model().attributeExists("roles"))
        .andExpect(model().attributeExists("groups"))
        .andExpect(model().attributeExists("permissions"));
  }

  @Test
  void userListView_returnsList() throws Exception {
    when(adminUserService.listUsers()).thenReturn(List.of());

    userMockMvc.perform(get("/admin/users"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/users/list"))
        .andExpect(model().attributeExists("users"));
  }

  @Test
  void userTableFragment_returnsFragment() throws Exception {
    when(adminUserService.listUsers()).thenReturn(List.of());

    userMockMvc.perform(get("/admin/users/table"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/users/list :: userTableBody"))
        .andExpect(model().attributeExists("users"));
  }

  @Test
  void clientListView_returnsList() throws Exception {
    when(adminClientService.listClients()).thenReturn(List.of());

    clientMockMvc.perform(get("/admin/clients"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/clients/list"))
        .andExpect(model().attributeExists("clients"));
  }

  @Test
  void clientTableFragment_returnsFragment() throws Exception {
    when(adminClientService.listClients()).thenReturn(List.of());

    clientMockMvc.perform(get("/admin/clients/table"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/clients/list :: clientTableBody"))
        .andExpect(model().attributeExists("clients"));
  }

  @Test
  void tenantListView_returnsList() throws Exception {
    when(adminTenantService.listTenants()).thenReturn(List.of());

    tenantMockMvc.perform(get("/admin/tenants"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/tenants/list"))
        .andExpect(model().attributeExists("tenants"));
  }

  @Test
  void tenantTableFragment_returnsFragment() throws Exception {
    when(adminTenantService.listTenants()).thenReturn(List.of());

    tenantMockMvc.perform(get("/admin/tenants/table"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/tenants/list :: tenantTableBody"))
        .andExpect(model().attributeExists("tenants"));
  }

  @Test
  void editUserModal_returnsModalFragment() throws Exception {
    var user = new AdminUserService.AdminUserSummary(
        "alice", true, "demo", "Alice Smith", "alice@example.com",
        List.of("ROLE_USER"), List.of("Engineering"), "IT"
    );
    when(adminUserService.getUser("alice")).thenReturn(java.util.Optional.of(user));
    when(adminRbacService.listRoles()).thenReturn(List.of());
    when(adminRbacService.listGroups()).thenReturn(List.of());

    userMockMvc.perform(get("/admin/users/alice/edit"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/users/edit-modal :: editUserModal"))
        .andExpect(model().attribute("user", user))
        .andExpect(model().attributeExists("availableRoles"))
        .andExpect(model().attributeExists("availableGroups"));
  }

  @Test
  void updateUser_executesAndReturnsTable() throws Exception {
    when(adminUserService.listUsers()).thenReturn(List.of());

    userMockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/users/alice")
            .param("fullName", "Alice Updated")
            .param("email", "alice_new@example.com")
            .param("department", "Core")
            .param("tenantId", "demo")
            .param("enabled", "true")
            .param("roles", "ROLE_ADMIN", "ROLE_USER")
            .param("groups", "Engineering"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/users/list :: userTableBody"))
        .andExpect(model().attributeExists("users"));

    org.mockito.Mockito.verify(adminUserService).updateUser(any(AdminUserService.UpdateUserCommand.class));
  }

  @Test
  void permissionsTableFragment_returnsFragment() throws Exception {
    when(adminRbacService.listPermissions()).thenReturn(List.of());

    rbacMockMvc.perform(get("/admin/rbac/permissions/table"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/list :: permissionsCatalogBody"))
        .andExpect(model().attributeExists("permissions"));
  }

  @Test
  void createPermission_executesAndReturnsFragment() throws Exception {
    when(adminRbacService.listPermissions()).thenReturn(List.of());

    rbacMockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/rbac/permissions")
            .param("code", "reports:export")
            .param("name", "Export Reports")
            .param("description", "Allow report export")
            .param("category", "REPORTS")
            .param("tenantId", "demo"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/list :: permissionsCatalogBody"))
        .andExpect(model().attributeExists("permissions"));

    org.mockito.Mockito.verify(adminRbacService).createPermission(any(AdminRbacService.CreatePermissionCommand.class));
  }

  @Test
  void deletePermission_executesAndReturnsFragment() throws Exception {
    when(adminRbacService.listPermissions()).thenReturn(List.of());

    rbacMockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/admin/rbac/permissions/perm-123"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/list :: permissionsCatalogBody"))
        .andExpect(model().attributeExists("permissions"));

    org.mockito.Mockito.verify(adminRbacService).deletePermission("perm-123");
  }

  @Test
  void editRoleModal_returnsModalFragment() throws Exception {
    var role = new AdminRbacService.RoleDetail("role-1", "ROLE_ADMIN", "Admin", "demo", List.of("perm-1"));
    when(adminRbacService.getRole("role-1")).thenReturn(java.util.Optional.of(role));
    when(adminRbacService.listPermissions()).thenReturn(List.of());

    rbacMockMvc.perform(get("/admin/rbac/roles/role-1/edit"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/edit-role-modal :: editRoleModal"))
        .andExpect(model().attribute("role", role))
        .andExpect(model().attributeExists("availablePermissions"));
  }

  @Test
  void updateRole_executesAndReturnsTable() throws Exception {
    when(adminRbacService.listRoles()).thenReturn(List.of());

    rbacMockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/rbac/roles/role-1")
            .param("description", "Updated Admin")
            .param("permissions", "perm-1", "perm-2"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/list :: rolesTableBody"));

    org.mockito.Mockito.verify(adminRbacService).updateRole(any(AdminRbacService.UpdateRoleCommand.class));
  }

  @Test
  void editGroupModal_returnsModalFragment() throws Exception {
    var group = new AdminRbacService.GroupDetail("group-1", "Engineering", "Desc", "demo", List.of("role-1"));
    when(adminRbacService.getGroup("group-1")).thenReturn(java.util.Optional.of(group));
    when(adminRbacService.listRoles()).thenReturn(List.of());

    rbacMockMvc.perform(get("/admin/rbac/groups/group-1/edit"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/edit-group-modal :: editGroupModal"))
        .andExpect(model().attribute("group", group))
        .andExpect(model().attributeExists("availableRoles"));
  }

  @Test
  void updateGroup_executesAndReturnsTable() throws Exception {
    when(adminRbacService.listGroups()).thenReturn(List.of());

    rbacMockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/rbac/groups/group-1")
            .param("description", "Updated Group")
            .param("roleIds", "role-1"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/list :: groupsTableBody"));

    org.mockito.Mockito.verify(adminRbacService).updateGroup(any(AdminRbacService.UpdateGroupCommand.class));
  }

  @Test
  void editPermissionModal_returnsModalFragment() throws Exception {
    var perm = new AdminRbacService.PermissionSummary("perm-1", "reports:export", "Export", "Desc", "REPORTS", false, "demo");
    when(adminRbacService.getPermissionById("perm-1")).thenReturn(java.util.Optional.of(perm));

    rbacMockMvc.perform(get("/admin/rbac/permissions/perm-1/edit"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/edit-permission-modal :: editPermissionModal"))
        .andExpect(model().attribute("permission", perm));
  }

  @Test
  void updatePermission_executesAndReturnsTable() throws Exception {
    when(adminRbacService.listPermissions()).thenReturn(List.of());

    rbacMockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/rbac/permissions/perm-1")
            .param("name", "Updated Export")
            .param("description", "New Desc")
            .param("category", "REPORTS"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/rbac/list :: permissionsCatalogBody"));

    org.mockito.Mockito.verify(adminRbacService).updatePermission(any(AdminRbacService.UpdatePermissionCommand.class));
  }
}
