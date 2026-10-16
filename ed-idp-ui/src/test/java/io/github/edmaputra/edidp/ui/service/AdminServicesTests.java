package io.github.edmaputra.edidp.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.edmaputra.iam.adapter.rest.MfaOperationsDelegate;
import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserStatus;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
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

    verify(registeredClientRepository).save(any(RegisteredClient.class));
    verify(jdbcTemplate).update(
        eq("UPDATE oauth2_registered_client SET tenant_id = ? WHERE id = ?"),
        eq("demo"), any(String.class)
    );
  }

  @Test
  void deleteClient_executesDelete() {
    adminClientService.deleteClient("client-123", "demo");

    verify(jdbcTemplate).update(
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

    verify(jdbcTemplate).update(
        contains("INSERT INTO iam_user"),
        any(), eq("alice@example.com"), eq("hashed-secret"), eq("Alice Smith"), any(), any()
    );
  }

  @Test
  void mfaOperations_delegateProperly() {
    var delegate = mock(MfaOperationsDelegate.class);
    var userRepo = mock(UserRepository.class);
    var userService = new JdbcAdminUserService(jdbcTemplate, passwordEncoder, delegate, userRepo);

    var userUuid = UUID.randomUUID();
    var userId = new UserId(userUuid);
    var user = new User(
        userId, "alice@example.com", "hash", "Alice", UserStatus.ACTIVE,
        false, Instant.now(), Instant.now()
    );

    when(userRepo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(delegate.getStatus(userId)).thenReturn(new MfaStatusResponse(true, Instant.now()));
    when(delegate.initiateSetup(userId, "ed-idp")).thenReturn(new MfaSetupResponse("SECRET", "otpauth://...", List.of()));

    assertThat(userService.getMfaStatus("alice@example.com").enabled()).isTrue();
    assertThat(userService.initiateMfaSetup("alice@example.com").secret()).isEqualTo("SECRET");

    userService.activateMfa("alice@example.com", "123456");
    verify(delegate).activate(userId, "123456");

    userService.disableMfa("alice@example.com", "secret-code");
    verify(delegate).disable(userId, "secret-code");
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

    verify(jdbcTemplate).update(
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
    verify(jdbcTemplate).update(
        contains("UPDATE iam_user"),
        eq("ACTIVE"), eq("Alice Updated"), eq("alice.updated@example.com"), any(), any(), eq("alice")
    );

    // Verify group members delete
    verify(jdbcTemplate).update(
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

    verify(jdbcTemplate).update(
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
    verify(jdbcTemplate).update(
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

    assertThrows(
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

    verify(jdbcTemplate).update(
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

    assertThrows(
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

    verify(jdbcTemplate).update(
        eq("UPDATE iam_role SET description = ?, updated_at = ? WHERE id = ? OR code = ?"),
        eq("Administrator with updated scope"), any(), any(), eq("role-admin")
    );
    verify(jdbcTemplate).update(
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

    verify(jdbcTemplate).update(
        eq("UPDATE iam_group SET description = ?, updated_at = ? WHERE id = ? OR code = ?"),
        eq("Engineering team with devops role"), any(), any(), eq("group-eng")
    );
    verify(jdbcTemplate).update(
        eq("DELETE FROM iam_group_role_assignment WHERE group_id = ?"),
        any(Object.class)
    );
  }

  @Test
  @SuppressWarnings("unchecked")
  void listPermissions_withAndWithoutFilter_queriesCorrectly() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var dummy = new AdminRbacService.PermissionSummary("p1", "code1", "name1", "desc", "CAT", false, "demo");
    when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(dummy));
    when(jdbcTemplate.query(any(String.class), any(RowMapper.class))).thenReturn(List.of(dummy));

    List<AdminRbacService.PermissionSummary> resAll = rbacService.listPermissions();
    assertThat(resAll).hasSize(1);

    List<AdminRbacService.PermissionSummary> resFiltered = rbacService.listPermissions("demo", "CAT");
    assertThat(resFiltered).hasSize(1);

    List<AdminRbacService.PermissionSummary> resBlank = rbacService.listPermissions("", "");
    assertThat(resBlank).hasSize(1);
  }

  @Test
  @SuppressWarnings("unchecked")
  void getPermissionByIdAndCode_returnsExpected() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);
    var dummy = new AdminRbacService.PermissionSummary("p1", "code1", "name1", "desc", "CAT", false, "demo");

    when(jdbcTemplate.query(contains("WHERE id = ? OR code = ?"), any(RowMapper.class), any(), eq("p1")))
        .thenReturn(List.of(dummy));
    when(jdbcTemplate.query(contains("WHERE id = ? OR code = ?"), any(RowMapper.class), any(), eq("unknown")))
        .thenReturn(List.of());

    assertThat(rbacService.getPermissionById("p1")).contains(dummy);
    assertThat(rbacService.getPermissionById("unknown")).isEmpty();

    when(jdbcTemplate.query(contains("WHERE code = ?"), any(RowMapper.class), eq("code1")))
        .thenReturn(List.of(dummy));
    when(jdbcTemplate.query(contains("WHERE code = ?"), any(RowMapper.class), eq("unknown")))
        .thenReturn(List.of());

    assertThat(rbacService.getPermissionByCode("code1")).contains(dummy);
    assertThat(rbacService.getPermissionByCode("unknown")).isEmpty();
  }

  @Test
  void deleteRole_withAndWithoutTenantId() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);

    rbacService.deleteRole("role-1", "demo");
    verify(jdbcTemplate).update(eq("DELETE FROM iam_role WHERE (id = ? OR code = ?) AND tenant_id = ?"), any(), eq("role-1"), any());

    rbacService.deleteRole("role-2", null);
    verify(jdbcTemplate).update(eq("DELETE FROM iam_role WHERE id = ? OR code = ?"), any(), eq("role-2"));
  }

  @Test
  void getRole_foundAndNotFound() throws SQLException {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);

    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("id")).thenReturn("role-1");
      when(rs.getString("code")).thenReturn("ROLE_ADMIN");
      when(rs.getString("name")).thenReturn("Admin");
      when(rs.getString("description")).thenReturn("Admin role");
      when(rs.getString("tenant_id")).thenReturn("demo");
      when(rs.getString("perm_code")).thenReturn("perm-1", "perm-2");
      handler.processRow(rs);
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("FROM iam_role r"), any(RowCallbackHandler.class), any(), eq("role-1"));

    Optional<AdminRbacService.RoleDetail> roleOpt = rbacService.getRole("role-1");
    assertThat(roleOpt).isPresent();
    assertThat(roleOpt.get().name()).isEqualTo("ROLE_ADMIN");
    assertThat(roleOpt.get().permissionIds()).containsExactly("perm-1", "perm-2");

    Optional<AdminRbacService.RoleDetail> notFound = rbacService.getRole("unknown");
    assertThat(notFound).isEmpty();
  }

  @Test
  void listRoles_aggregatesRolesAndPermissions() throws SQLException {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);

    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("id")).thenReturn("r1", "r1", "r2");
      when(rs.getString("code")).thenReturn("ROLE_A", "ROLE_A", null);
      when(rs.getString("name")).thenReturn("NameA", "NameA", "ROLE_B");
      when(rs.getString("description")).thenReturn("DescA", "DescA", "DescB");
      when(rs.getString("tenant_id")).thenReturn("demo", "demo", "demo");
      when(rs.getString("perm_code")).thenReturn("p1", "p1", null);
      handler.processRow(rs);
      handler.processRow(rs);
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("FROM iam_role r"), any(RowCallbackHandler.class));

    List<AdminRbacService.RoleSummary> roles = rbacService.listRoles();
    assertThat(roles).hasSize(2);
    assertThat(roles.get(0).permissions()).containsExactly("p1");
    assertThat(roles.get(1).name()).isEqualTo("ROLE_B");
  }

  @Test
  void listGroups_aggregatesGroupsAndRoles() throws SQLException {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);

    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("id")).thenReturn("g1", "g1");
      when(rs.getString("code")).thenReturn("CODE1", "CODE1");
      when(rs.getString("name")).thenReturn("Group 1", "Group 1");
      when(rs.getString("description")).thenReturn("Desc", "Desc");
      when(rs.getString("tenant_id")).thenReturn("demo", "demo");
      when(rs.getInt("member_count")).thenReturn(3, 3);
      when(rs.getString("role_code")).thenReturn("ROLE_ADMIN", "ROLE_USER");
      handler.processRow(rs);
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("FROM iam_group g"), any(RowCallbackHandler.class));

    List<AdminRbacService.GroupSummary> groups = rbacService.listGroups();
    assertThat(groups).hasSize(1);
    assertThat(groups.get(0).name()).isEqualTo("Group 1");
    assertThat(groups.get(0).roles()).containsExactly("ROLE_ADMIN", "ROLE_USER");
    assertThat(groups.get(0).memberCount()).isEqualTo(3);
  }

  @Test
  void getGroup_foundAndNotFound() throws SQLException {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);

    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("id")).thenReturn("g1");
      when(rs.getString("code")).thenReturn("CODE1");
      when(rs.getString("name")).thenReturn("Group 1");
      when(rs.getString("description")).thenReturn("Desc");
      when(rs.getString("tenant_id")).thenReturn("demo");
      when(rs.getString("role_id")).thenReturn("r1");
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("FROM iam_group g"), any(RowCallbackHandler.class), any(), eq("g1"));

    Optional<AdminRbacService.GroupDetail> groupOpt = rbacService.getGroup("g1");
    assertThat(groupOpt).isPresent();
    assertThat(groupOpt.get().name()).isEqualTo("Group 1");
    assertThat(groupOpt.get().roleIds()).containsExactly("r1");

    Optional<AdminRbacService.GroupDetail> notFound = rbacService.getGroup("unknown");
    assertThat(notFound).isEmpty();
  }

  @Test
  void createGroup_withAndWithoutRoles() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);

    var cmdWithRoles = new AdminRbacService.CreateGroupCommand(
        "Engineering", "Eng group", "demo", List.of("role-admin")
    );
    rbacService.createGroup(cmdWithRoles);
    verify(jdbcTemplate).update(contains("INSERT INTO iam_group "), any(), any(), eq("ENGINEERING"), eq("Engineering"), eq("Eng group"), any(), any());

    var cmdWithoutRoles = new AdminRbacService.CreateGroupCommand(
        "Marketing", "Marketing group", "demo", List.of()
    );
    rbacService.createGroup(cmdWithoutRoles);
    verify(jdbcTemplate).update(contains("INSERT INTO iam_group "), any(), any(), eq("MARKETING"), eq("Marketing"), eq("Marketing group"), any(), any());
  }

  @Test
  void deleteGroup_withAndWithoutTenantId() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);

    rbacService.deleteGroup("group-1", "demo");
    verify(jdbcTemplate).update(eq("DELETE FROM iam_group WHERE (id = ? OR code = ?) AND tenant_id = ?"), any(), eq("group-1"), any());

    rbacService.deleteGroup("group-2", null);
    verify(jdbcTemplate).update(eq("DELETE FROM iam_group WHERE id = ? OR code = ?"), any(), eq("group-2"));
  }

  @Test
  void addAndRemoveGroupMember_andGetUserGroupNames() {
    var rbacService = new JdbcAdminRbacService(jdbcTemplate);

    rbacService.addGroupMember("group-1", "alice", "demo");
    verify(jdbcTemplate).update(contains("INSERT INTO iam_user_group_membership"), any(), any(), any(), any(), any());

    rbacService.removeGroupMember("group-1", "alice");
    verify(jdbcTemplate).update(eq("DELETE FROM iam_user_group_membership WHERE group_id = ? AND user_id = ?"), any(Object.class), any(Object.class));

    when(jdbcTemplate.query(contains("FROM iam_group g"), any(RowMapper.class), any()))
        .thenReturn(List.of("Engineering", "Platform"));

    List<String> groups = rbacService.getUserGroupNames("alice");
    assertThat(groups).containsExactly("Engineering", "Platform");
  }

  @Test
  void listUsers_andGetUser_aggregatesCorrectly() throws SQLException {
    var userService = new JdbcAdminUserService(jdbcTemplate, passwordEncoder);

    // Mock query 1: users
    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs1 = mock(ResultSet.class);
      when(rs1.getString("id")).thenReturn("u1");
      when(rs1.getString("email")).thenReturn("alice@example.com");
      when(rs1.getString("status")).thenReturn("ACTIVE");
      when(rs1.getString("tenant")).thenReturn("demo");
      when(rs1.getString("full_name")).thenReturn("Alice Smith");
      when(rs1.getString("department")).thenReturn("Engineering");

      ResultSet rs2 = mock(ResultSet.class);
      when(rs2.getString("id")).thenReturn("u2");
      when(rs2.getString("email")).thenReturn("bob@example.com");
      when(rs2.getString("status")).thenReturn("SUSPENDED");
      when(rs2.getString("tenant")).thenReturn(null);
      when(rs2.getString("full_name")).thenReturn(null);
      when(rs2.getString("iam_full_name")).thenReturn("Bob Iam");
      when(rs2.getString("department")).thenReturn("HR");

      handler.processRow(rs1);
      handler.processRow(rs2);
      return null;
    }).when(jdbcTemplate).query(contains("FROM iam_user u"), any(RowCallbackHandler.class));

    // Mock query 2: roles
    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("user_id")).thenReturn("u1", "u1");
      when(rs.getString("role_code")).thenReturn("ROLE_ADMIN", "ROLE_ADMIN");
      handler.processRow(rs);
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("FROM iam_user_role_assignment ura"), any(RowCallbackHandler.class));

    // Mock query 3: groups
    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("user_id")).thenReturn("u1", "u2");
      when(rs.getString("group_name")).thenReturn("Engineering", null);
      when(rs.getString("group_code")).thenReturn("ENG", "HR_GRP");
      handler.processRow(rs);
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("FROM iam_user_group_membership gm"), any(RowCallbackHandler.class));

    List<AdminUserService.AdminUserSummary> users = userService.listUsers();
    assertThat(users).hasSize(2);

    AdminUserService.AdminUserSummary alice = users.get(0);
    assertThat(alice.fullName()).isEqualTo("Alice Smith");
    assertThat(alice.enabled()).isTrue();
    assertThat(alice.roles()).containsExactly("ROLE_ADMIN");
    assertThat(alice.groups()).containsExactly("Engineering");

    AdminUserService.AdminUserSummary bob = users.get(1);
    assertThat(bob.fullName()).isEqualTo("Bob Iam");
    assertThat(bob.enabled()).isFalse();
    assertThat(bob.tenantId()).isEqualTo("demo");
    assertThat(bob.groups()).containsExactly("HR_GRP");

    assertThat(userService.getUser("alice@example.com")).isPresent();
    assertThat(userService.getUser("unknown")).isEmpty();
    assertThat(userService.getUser("")).isEmpty();
    assertThat(userService.getUser(null)).isEmpty();
  }

  @Test
  void createUser_withBlankEmail_andNoRole() {
    var userService = new JdbcAdminUserService(jdbcTemplate, passwordEncoder);
    when(passwordEncoder.encode("secret")).thenReturn("hashed");

    var cmd = new AdminUserService.CreateUserCommand(
        "carol", "secret", "Carol Danvers", "", "IT", "demo", null
    );
    userService.createUser(cmd);

    verify(jdbcTemplate).update(
        contains("INSERT INTO iam_user "),
        any(), eq("carol@example.com"), eq("hashed"), eq("Carol Danvers"), any(), any()
    );
  }

  @Test
  void updateUser_whenProfilesNotExisting_insertsProfile() {
    var userService = new JdbcAdminUserService(jdbcTemplate, passwordEncoder);

    // Return 0 for profile update, triggering insert fallback
    when(jdbcTemplate.update(contains("UPDATE user_profiles"), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0);

    var cmd = new AdminUserService.UpdateUserCommand(
        "david", "David", null, "Finance", "demo", false, List.of("ROLE_USER"), List.of("FinanceGroup")
    );
    userService.updateUser(cmd);

    verify(jdbcTemplate).update(
        contains("INSERT INTO user_profiles "),
        eq("david"), eq("David"), eq("david"), eq("Finance"), eq("demo"), any()
    );
  }

  @Test
  void setUserEnabled_updatesStatus() {
    var userService = new JdbcAdminUserService(jdbcTemplate, passwordEncoder);

    userService.setUserEnabled("alice", true);
    verify(jdbcTemplate).update(
        eq("UPDATE iam_user SET status = ? WHERE id = ? OR LOWER(email) = LOWER(?)"),
        eq("ACTIVE"), any(), eq("alice")
    );

    userService.setUserEnabled("bob", false);
    verify(jdbcTemplate).update(
        eq("UPDATE iam_user SET status = ? WHERE id = ? OR LOWER(email) = LOWER(?)"),
        eq("SUSPENDED"), any(), eq("bob")
    );
  }

  @Test
  void createClient_withPkceTrueAndEmptySecrets() {
    var cmd = new AdminClientService.CreateClientCommand(
        "spa-client", "", "Single Page App", "demo",
        "authorization_code", "openid", "http://localhost/cb", true
    );

    adminClientService.createClient(cmd);

    verify(registeredClientRepository).save(any(RegisteredClient.class));
  }

  @Test
  void testRbacAndUserRecordsValidation() {
    // PermissionSummary
    assertThatThrownBy(() -> new AdminRbacService.PermissionSummary(null, "code", "name", "desc", "CAT", false, "demo"))
        .isInstanceOf(IllegalArgumentException.class);
    var p2 = new AdminRbacService.PermissionSummary("id1", "Perm Name", "Perm Desc");
    assertThat(p2.code()).isEqualTo("Perm Name");
    assertThat(p2.category()).isEqualTo("GENERAL");

    var p3 = new AdminRbacService.PermissionSummary("id2", "", "", "desc", "", false, null);
    assertThat(p3.category()).isEqualTo("GENERAL");

    // RoleSummary
    assertThatThrownBy(() -> new AdminRbacService.RoleSummary(null, "ADMIN", "desc", "demo", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AdminRbacService.RoleSummary("r1", "", "desc", "demo", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    var r1 = new AdminRbacService.RoleSummary("r1", "ADMIN", "desc", "", null);
    assertThat(r1.tenantId()).isEqualTo("demo");
    assertThat(r1.permissions()).isEmpty();

    // GroupSummary
    assertThatThrownBy(() -> new AdminRbacService.GroupSummary("", "Eng", "desc", "demo", List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AdminRbacService.GroupSummary("g1", "", "desc", "demo", List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
    var g1 = new AdminRbacService.GroupSummary("g1", "Eng", "desc", null, null, 2);
    assertThat(g1.tenantId()).isEqualTo("demo");
    assertThat(g1.roles()).isEmpty();

    // Commands
    assertThatThrownBy(() -> new AdminRbacService.CreateRoleCommand("", "desc", "demo", null))
        .isInstanceOf(IllegalArgumentException.class);
    var crc = new AdminRbacService.CreateRoleCommand("Role", "desc", null, null);
    assertThat(crc.tenantId()).isEqualTo("demo");
    assertThat(crc.permissionIds()).isEmpty();

    assertThatThrownBy(() -> new AdminRbacService.CreateGroupCommand("", "desc", "demo", null))
        .isInstanceOf(IllegalArgumentException.class);
    var cgc = new AdminRbacService.CreateGroupCommand("Group", "desc", null, null);
    assertThat(cgc.tenantId()).isEqualTo("demo");
    assertThat(cgc.roleIds()).isEmpty();

    assertThatThrownBy(() -> new AdminRbacService.UpdateRoleCommand("", "desc", null))
        .isInstanceOf(IllegalArgumentException.class);
    var urc = new AdminRbacService.UpdateRoleCommand("r1", "desc", null);
    assertThat(urc.permissionIds()).isEmpty();

    assertThatThrownBy(() -> new AdminRbacService.UpdateGroupCommand("", "desc", null))
        .isInstanceOf(IllegalArgumentException.class);
    var ugc = new AdminRbacService.UpdateGroupCommand("g1", "desc", null);
    assertThat(ugc.roleIds()).isEmpty();

    var rd = new AdminRbacService.RoleDetail("r1", "Admin", "desc", null, null);
    assertThat(rd.tenantId()).isEqualTo("demo");
    assertThat(rd.permissionIds()).isEmpty();

    var gd = new AdminRbacService.GroupDetail("g1", "Eng", "desc", null, null);
    assertThat(gd.tenantId()).isEqualTo("demo");
    assertThat(gd.roleIds()).isEmpty();

    assertThatThrownBy(() -> new AdminRbacService.CreatePermissionCommand("", "Name", "desc", "CAT", "demo"))
        .isInstanceOf(IllegalArgumentException.class);
    var cpc = new AdminRbacService.CreatePermissionCommand("code", "", null, null, null);
    assertThat(cpc.name()).isEqualTo("code");
    assertThat(cpc.category()).isEqualTo("GENERAL");
    assertThat(cpc.tenantId()).isEqualTo("demo");

    assertThatThrownBy(() -> new AdminRbacService.UpdatePermissionCommand("", "Name", "desc", "CAT"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AdminRbacService.UpdatePermissionCommand("p1", "", "desc", "CAT"))
        .isInstanceOf(IllegalArgumentException.class);
    var upc = new AdminRbacService.UpdatePermissionCommand("p1", "Name", "desc", null);
    assertThat(upc.category()).isEqualTo("GENERAL");

    // AdminUserService records
    var ucmd = new AdminUserService.CreateUserCommand("alice", "pass", "Alice", null, null, null, null);
    assertThat(ucmd.department()).isEqualTo("general");
    assertThat(ucmd.tenantId()).isEqualTo("demo");
    assertThat(ucmd.role()).isEqualTo("ROLE_USER");

    var uucmd = new AdminUserService.UpdateUserCommand("alice", "Alice", "a@b.com", "IT", "demo", true, null, null);
    assertThat(uucmd.roles()).isEmpty();
    assertThat(uucmd.groups()).isEmpty();
  }
}

