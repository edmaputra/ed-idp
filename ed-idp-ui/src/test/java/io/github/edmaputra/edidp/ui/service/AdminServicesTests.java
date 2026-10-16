package io.github.edmaputra.edidp.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Tests for {@link JdbcAdminClientService}, {@link JdbcAdminTenantService},
 * {@link JdbcAdminUserService}, and {@link JdbcAdminRbacService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class AdminServicesTests {

  private JdbcTemplate jdbcTemplate;
  private JdbcAdminClientService adminClientService;
  private JdbcAdminTenantService adminTenantService;
  private RegisteredClientRepository registeredClientRepository;
  private PasswordEncoder passwordEncoder;
  private TokenSettings tokenSettings;

  @BeforeEach
  void setUp() {
    jdbcTemplate = mock(JdbcTemplate.class);
    registeredClientRepository = mock(RegisteredClientRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    tokenSettings = TokenSettings.builder().build();

    adminClientService = new JdbcAdminClientService(
        jdbcTemplate, registeredClientRepository, passwordEncoder, tokenSettings
    );
    adminTenantService = new JdbcAdminTenantService(jdbcTemplate);
  }

  @Test
  void countClients_returnsCountFromJdbc() {
    when(jdbcTemplate.queryForObject(eq("SELECT count(*) FROM oauth2_registered_client"), eq(Long.class)))
        .thenReturn(5L);

    assertThat(adminClientService.countClients()).isEqualTo(5L);
  }

  @Test
  void countTenants_returnsCountFromJdbc() {
    when(jdbcTemplate.queryForObject(
        eq("SELECT COUNT(DISTINCT COALESCE(tenant_id, 'demo')) FROM oauth2_registered_client"),
        eq(Long.class)))
        .thenReturn(2L);

    assertThat(adminTenantService.countTenants()).isEqualTo(2L);
  }

  @Test
  @SuppressWarnings("unchecked")
  void listClients_returnsListFromJdbc() {
    var dummyClient = new AdminClientService.AdminClientSummary(
        "id-1", "client-1", "Test Client", "client_secret_basic",
        "authorization_code", "openid,profile", "http://localhost/cb", "demo"
    );

    when(jdbcTemplate.query(any(String.class), any(RowMapper.class)))
        .thenReturn(List.of(dummyClient));

    List<AdminClientService.AdminClientSummary> results = adminClientService.listClients();
    assertThat(results).hasSize(1);
    assertThat(results.get(0).clientId()).isEqualTo("client-1");
  }

  @Test
  @SuppressWarnings("unchecked")
  void listTenants_returnsListFromJdbc() {
    var dummyTenant = new AdminTenantService.AdminTenantSummary("demo", 3L);

    when(jdbcTemplate.query(any(String.class), any(RowMapper.class)))
        .thenReturn(List.of(dummyTenant));

    List<AdminTenantService.AdminTenantSummary> results = adminTenantService.listTenants();
    assertThat(results).hasSize(1);
    assertThat(results.get(0).tenantId()).isEqualTo("demo");
    assertThat(results.get(0).clientCount()).isEqualTo(3L);
  }

  @Test
  void createClient_executesRepositorySaveAndTenantUpdate() {
    when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
    var command = new AdminClientService.CreateClientCommand(
        "new-client", "secret", "New App", "demo",
        "authorization_code", "openid,profile", "http://localhost:8080/cb", false
    );

    adminClientService.createClient(command);

    org.mockito.Mockito.verify(registeredClientRepository).save(any(RegisteredClient.class));
    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("UPDATE oauth2_registered_client SET tenant_id = ? WHERE id = ?"),
        eq("demo"), any(String.class)
    );
  }

  @Test
  void deleteClient_executesDelete() {
    adminClientService.deleteClient("client-123", "demo");

    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("DELETE FROM oauth2_registered_client WHERE client_id = ? AND tenant_id = ?"),
        eq("client-123"), eq("demo")
    );
  }

  @Test
  void countUsers_returnsCountFromJdbc() {
    var userService = new JdbcAdminUserService(jdbcTemplate, passwordEncoder);

    when(jdbcTemplate.queryForObject(eq("SELECT count(*) FROM iam_user"), eq(Long.class)))
        .thenReturn(8L);

    assertThat(userService.countUsers()).isEqualTo(8L);
  }

  @Test
  void createUser_executesInserts() {
    when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");
    var userService = new JdbcAdminUserService(jdbcTemplate, passwordEncoder);

    var command = new AdminUserService.CreateUserCommand(
        "alice", "secret", "Alice Smith", "alice@example.com", "finance", "demo", "ROLE_USER"
    );

    userService.createUser(command);

    org.mockito.Mockito.verify(jdbcTemplate).update(
        contains("INSERT INTO iam_user"),
        any(), eq("alice@example.com"), eq("hashed-secret"), eq("Alice Smith"), any(), any()
    );
  }

  @Test
  void mfaOperations_delegateProperly() {
    var delegate = mock(io.github.edmaputra.iam.adapter.rest.MfaOperationsDelegate.class);
    var userRepo = mock(io.github.edmaputra.iam.domain.repository.UserRepository.class);
    var userService = new JdbcAdminUserService(jdbcTemplate, passwordEncoder, delegate, userRepo);

    var userUuid = java.util.UUID.randomUUID();
    var userId = new io.github.edmaputra.iam.domain.model.UserId(userUuid);
    var user = new io.github.edmaputra.iam.domain.model.User(
        userId, "alice@example.com", "hash", "Alice", io.github.edmaputra.iam.domain.model.UserStatus.ACTIVE,
        false, java.time.Instant.now(), java.time.Instant.now()
    );

    when(userRepo.findByEmail("alice@example.com")).thenReturn(java.util.Optional.of(user));
    when(delegate.getStatus(userId)).thenReturn(new io.github.edmaputra.iam.application.model.MfaStatusResponse(true, java.time.Instant.now()));
    when(delegate.initiateSetup(userId, "ed-idp")).thenReturn(new io.github.edmaputra.iam.application.model.MfaSetupResponse("SECRET", "otpauth://...", List.of()));

    assertThat(userService.getMfaStatus("alice@example.com").enabled()).isTrue();
    assertThat(userService.initiateMfaSetup("alice@example.com").secret()).isEqualTo("SECRET");

    userService.activateMfa("alice@example.com", "123456");
    org.mockito.Mockito.verify(delegate).activate(userId, "123456");

    userService.disableMfa("alice@example.com", "secret-code");
    org.mockito.Mockito.verify(delegate).disable(userId, "secret-code");
  }

  @Test
  void countRoles_returnsCountFromJdbc() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    when(jdbcTemplate.queryForObject(eq("SELECT count(*) FROM iam_role"), eq(Long.class)))
        .thenReturn(4L);

    assertThat(rbacService.countRoles()).isEqualTo(4L);
  }

  @Test
  void countGroups_returnsCountFromJdbc() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    when(jdbcTemplate.queryForObject(eq("SELECT count(*) FROM iam_group"), eq(Long.class)))
        .thenReturn(2L);

    assertThat(rbacService.countGroups()).isEqualTo(2L);
  }

  @Test
  void createRole_executesInserts() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var command = new AdminRbacService.CreateRoleCommand(
        "AUDITOR", "Auditor role", "demo", List.of("perm-clients-read")
    );

    rbacService.createRole(command);

    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("""
        INSERT INTO iam_role (id, tenant_id, code, name, description, is_system_role, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, false, ?, ?)
        """),
        any(), any(), eq("ROLE_AUDITOR"), eq("ROLE_AUDITOR"), eq("Auditor role"), any(), any()
    );
  }

  @Test
  void updateUser_executesUpdatesAndRoleGroupAssignments() {
    var userService = new JdbcAdminUserService(jdbcTemplate, passwordEncoder);

    var command = new AdminUserService.UpdateUserCommand(
        "alice", "Alice Updated", "alice.updated@example.com", "finance", "demo", true,
        List.of("ROLE_ADMIN"), List.of("Engineering")
    );

    userService.updateUser(command);

    // Verify iam_user table update
    org.mockito.Mockito.verify(jdbcTemplate).update(
        contains("UPDATE iam_user"),
        eq("ACTIVE"), eq("Alice Updated"), eq("alice.updated@example.com"), any(), any(), eq("alice")
    );

    // Verify group members delete
    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("DELETE FROM iam_user_group_membership WHERE user_id = ?"),
        any(Object.class)
    );
  }

  @Test
  void createPermission_executesInsertAndReturnsSummary() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var command = new AdminRbacService.CreatePermissionCommand(
        "reports:export", "Export Reports", "Allow export", "REPORTS", "demo"
    );

    var result = rbacService.createPermission(command);

    assertThat(result.code()).isEqualTo("reports:export");
    assertThat(result.name()).isEqualTo("Export Reports");
    assertThat(result.category()).isEqualTo("REPORTS");
    assertThat(result.systemPermission()).isFalse();
    assertThat(result.tenantId()).isEqualTo("demo");

    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("""
        INSERT INTO iam_permission (id, tenant_id, code, name, description, category, is_system_permission, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, false, ?, ?)
        """),
        any(), any(), eq("reports:export"), eq("Export Reports"), eq("Allow export"),
        eq("REPORTS"), any(), any()
    );
  }

  @Test
  @SuppressWarnings("unchecked")
  void updatePermission_whenCustom_updatesAndReturnsSummary() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var existing = new AdminRbacService.PermissionSummary(
        "perm-custom", "reports:export", "Export Reports", "Old desc", "REPORTS", false, "demo"
    );
    when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), eq("perm-custom")))
        .thenReturn(List.of(existing));

    var command = new AdminRbacService.UpdatePermissionCommand(
        "perm-custom", "Export Compliance Reports", "New desc", "REPORTS"
    );

    var updated = rbacService.updatePermission(command);

    assertThat(updated.name()).isEqualTo("Export Compliance Reports");
    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("""
        UPDATE iam_permission
        SET name = ?, description = ?, category = ?, updated_at = ?
        WHERE (id = ? OR code = ?) AND is_system_permission = false
        """),
        eq("Export Compliance Reports"), eq("New desc"), eq("REPORTS"), any(), any(), eq("perm-custom")
    );
  }

  @Test
  @SuppressWarnings("unchecked")
  void updatePermission_whenSystemPermission_throwsIllegalStateException() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var systemPerm = new AdminRbacService.PermissionSummary(
        "perm-clients-read", "clients:read", "View Clients", "Desc", "CLIENTS", true, null
    );
    when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), eq("perm-clients-read")))
        .thenReturn(List.of(systemPerm));

    var command = new AdminRbacService.UpdatePermissionCommand(
        "perm-clients-read", "Hacked View", "Hacked", "CLIENTS"
    );

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () -> rbacService.updatePermission(command)
    );
  }

  @Test
  @SuppressWarnings("unchecked")
  void deletePermission_whenCustom_executesDelete() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var existing = new AdminRbacService.PermissionSummary(
        "perm-custom", "reports:export", "Export Reports", "Desc", "REPORTS", false, "demo"
    );
    when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), eq("perm-custom")))
        .thenReturn(List.of(existing));

    rbacService.deletePermission("perm-custom");

    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("DELETE FROM iam_permission WHERE (id = ? OR code = ?) AND is_system_permission = false"),
        any(), eq("perm-custom")
    );
  }

  @Test
  @SuppressWarnings("unchecked")
  void deletePermission_whenSystemPermission_throwsIllegalStateException() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var systemPerm = new AdminRbacService.PermissionSummary(
        "perm-clients-read", "clients:read", "View Clients", "Desc", "CLIENTS", true, null
    );
    when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), eq("perm-clients-read")))
        .thenReturn(List.of(systemPerm));

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () -> rbacService.deletePermission("perm-clients-read")
    );
  }

  @Test
  void updateRole_executesUpdatesAndRolePermissions() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var command = new AdminRbacService.UpdateRoleCommand(
        "role-admin", "Administrator with updated scope", List.of("perm-clients-read", "perm-users-read")
    );

    rbacService.updateRole(command);

    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("UPDATE iam_role SET description = ?, updated_at = ? WHERE id = ? OR code = ?"),
        eq("Administrator with updated scope"), any(), any(), eq("role-admin")
    );
    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("DELETE FROM iam_role_permission WHERE role_id IN (SELECT id FROM iam_role WHERE id = ? OR code = ?)"),
        any(), eq("role-admin")
    );
  }

  @Test
  void updateGroup_executesUpdatesAndGroupRoles() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var command = new AdminRbacService.UpdateGroupCommand(
        "group-eng", "Engineering team with devops role", List.of("role-user", "role-admin")
    );

    rbacService.updateGroup(command);

    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("UPDATE iam_group SET description = ?, updated_at = ? WHERE id = ? OR code = ?"),
        eq("Engineering team with devops role"), any(), any(), eq("group-eng")
    );
    org.mockito.Mockito.verify(jdbcTemplate).update(
        eq("DELETE FROM iam_group_role_assignment WHERE group_id = ?"),
        any(Object.class)
    );
  }
}
