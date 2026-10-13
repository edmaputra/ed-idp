package io.github.edmaputra.edidp.users;

import io.github.edmaputra.iam.domain.security.DefaultPasswordValidator;
import io.github.edmaputra.iam.domain.security.PasswordPolicy;
import io.github.edmaputra.iam.domain.security.PasswordValidator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring configuration for seeding demo users, profiles, attributes, and canonical IAM RBAC records.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
public class UsersConfig {

  private static final String DEMO_TENANT = "demo";

  @Bean
  @Primary
  public PasswordValidator passwordValidator() {
    PasswordPolicy policy = new PasswordPolicy(
        8,
        128,
        0,
        1,
        0,
        0,
        null,
        null,
        false);
    return new DefaultPasswordValidator(policy);
  }

  @Bean
  @Order(2)
  @ConditionalOnProperty(name = "edidp.seeding.enabled", havingValue = "true", matchIfMissing = true)
  CommandLineRunner demoIamUserSeeder(
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      PasswordValidator passwordValidator) {
    return args -> {
      Timestamp now = Timestamp.from(Instant.now());

      // 1. Seed demo-user (email: demo-user@example.com)
      Long demoCount = jdbcTemplate.queryForObject(
          "SELECT count(*) FROM iam_user WHERE LOWER(email) = LOWER('demo-user@example.com')", Long.class);
      if (demoCount == null || demoCount == 0) {
        String rawDemoPassword = "demo-password";
        passwordValidator.validatePassword(rawDemoPassword, "demo-user");
        UUID demoUserId = UUID.randomUUID();
        String encodedPassword = passwordEncoder.encode(rawDemoPassword);
        jdbcTemplate.update("""
            INSERT INTO iam_user (id, email, password_hash, full_name, status, is_platform_superadmin, created_at, updated_at)
            VALUES (?, 'demo-user@example.com', ?, 'Demo User', 'ACTIVE', false, ?, ?)
            """,
            demoUserId, encodedPassword, now, now
        );
        jdbcTemplate.update("""
            INSERT INTO iam_user_identity (id, user_id, provider_type, external_subject_id, created_at)
            VALUES (?, ?, 'LOCAL', 'demo-user@example.com', ?)
            """,
            UUID.randomUUID(), demoUserId, now
        );
      }

      // 2. Seed admin (email: admin@example.com)
      Long adminCount = jdbcTemplate.queryForObject(
          "SELECT count(*) FROM iam_user WHERE LOWER(email) = LOWER('admin@example.com')", Long.class);
      if (adminCount == null || adminCount == 0) {
        String rawAdminPassword = "admin-password";
        passwordValidator.validatePassword(rawAdminPassword, "admin");
        UUID adminUserId = UUID.randomUUID();
        String encodedPassword = passwordEncoder.encode(rawAdminPassword);
        jdbcTemplate.update("""
            INSERT INTO iam_user (id, email, password_hash, full_name, status, is_platform_superadmin, created_at, updated_at)
            VALUES (?, 'admin@example.com', ?, 'Administrator', 'ACTIVE', true, ?, ?)
            """,
            adminUserId, encodedPassword, now, now
        );
        jdbcTemplate.update("""
            INSERT INTO iam_user_identity (id, user_id, provider_type, external_subject_id, created_at)
            VALUES (?, ?, 'LOCAL', 'admin@example.com', ?)
            """,
            UUID.randomUUID(), adminUserId, now
        );
      }
    };
  }

  @Bean
  @Order(3)
  @ConditionalOnProperty(name = "edidp.seeding.enabled", havingValue = "true", matchIfMissing = true)
  CommandLineRunner demoUserProfileSeeder(UserProfileRepository userProfileRepository) {
    return args -> {
      if (userProfileRepository.findByTenantAndUsername(DEMO_TENANT, "demo-user").isEmpty()) {
        userProfileRepository.save(new UserProfile(
            "demo-user",
            "Demo User",
            "demo-user@example.com",
            true,
            "en-US",
            "Asia/Jakarta",
            "engineering",
            DEMO_TENANT,
            Instant.now().getEpochSecond()));
      }

      if (userProfileRepository.findByTenantAndUsername(DEMO_TENANT, "demo-user@example.com").isPresent()) {
        return;
      }

      UserProfile userProfile = new UserProfile(
          "demo-user@example.com",
          "Demo User",
          "demo-user@example.com",
          true,
          "en-US",
          "Asia/Jakarta",
          "engineering",
          DEMO_TENANT,
          Instant.now().getEpochSecond());

      userProfileRepository.save(userProfile);
    };
  }

  @Bean
  @Order(4)
  @ConditionalOnProperty(name = "edidp.seeding.enabled", havingValue = "true", matchIfMissing = true)
  CommandLineRunner demoUserProfileAttributeSeeder(
      UserProfileRepository userProfileRepository,
      UserProfileAttributeRepository userProfileAttributeRepository) {
    return args -> {
      UserProfile userProfile =
          userProfileRepository.findByTenantAndUsername(DEMO_TENANT, "demo-user").orElse(null);
      if (userProfile == null) {
        return;
      }

      createAttributeIfMissing(
          userProfile,
          userProfileAttributeRepository,
          "favorite_color",
          "blue");
      createAttributeIfMissing(
          userProfile,
          userProfileAttributeRepository,
          "employee_level",
          "senior");
      createAttributeIfMissing(
          userProfile,
          userProfileAttributeRepository,
          "region",
          "apac");
    };
  }

  @Bean
  @Order(5)
  @ConditionalOnProperty(name = "edidp.seeding.enabled", havingValue = "true", matchIfMissing = true)
  CommandLineRunner demoIamRbacSeeder(JdbcTemplate jdbcTemplate) {
    return args -> {
      // 1. Seed canonical system permissions in iam_permission
      record PermSeed(String code, String name, String desc, String cat) {}
      List<PermSeed> permissions = List.of(
          new PermSeed("clients:read", "View Registered Clients", "Grants view access to client credentials and settings", "CLIENTS"),
          new PermSeed("clients:write", "Manage Registered Clients", "Grants ability to create, update, or revoke clients", "CLIENTS"),
          new PermSeed("users:read", "View User Directory", "Allows searching and viewing users and profile attributes", "USERS"),
          new PermSeed("users:write", "Manage Users & Profiles", "Allows creating, updating, and disabling users", "USERS"),
          new PermSeed("roles:manage", "Manage RBAC Roles & Permissions", "Grants full control over role definitions and assignments", "RBAC"),
          new PermSeed("groups:manage", "Manage User Groups", "Allows creating groups and modifying memberships", "RBAC"),
          new PermSeed("tokens:revoke", "Revoke OAuth Tokens", "RFC 7009 Token Revocation endpoint access", "TOKENS"),
          new PermSeed("tokens:introspect", "Introspect OAuth Tokens", "RFC 7662 Token Introspection endpoint access", "TOKENS"),
          new PermSeed("permissions:manage", "Manage Custom Permissions", "Allows managing tenant custom catalog permissions", "RBAC")
      );

      Timestamp now = Timestamp.from(Instant.now());
      for (PermSeed p : permissions) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM iam_permission WHERE code = ?", Long.class, p.code());
        if (count == null || count == 0) {
          jdbcTemplate.update("""
              INSERT INTO iam_permission (id, tenant_id, code, name, description, category, is_system_permission, created_at, updated_at)
              VALUES (?, null, ?, ?, ?, ?, true, ?, ?)
              """,
              UUID.randomUUID(), p.code(), p.name(), p.desc(), p.cat(), now, now
          );
        }
      }

      // 2. Seed canonical roles in iam_role
      Long roleCount = jdbcTemplate.queryForObject("SELECT count(*) FROM iam_role", Long.class);
      if (roleCount == null || roleCount == 0) {
        UUID adminRoleId = UUID.randomUUID();
        UUID userRoleId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO iam_role (id, tenant_id, code, name, description, is_system_role, created_at, updated_at)
            VALUES (?, null, 'ROLE_ADMIN', 'Administrator', 'Full system and administrative privileges', true, ?, ?)
            """,
            adminRoleId, now, now
        );

        jdbcTemplate.update("""
            INSERT INTO iam_role (id, tenant_id, code, name, description, is_system_role, created_at, updated_at)
            VALUES (?, null, 'ROLE_USER', 'Standard User', 'Standard user permissions', true, ?, ?)
            """,
            userRoleId, now, now
        );

        // Seed role permissions (direct permission codes)
        for (PermSeed p : permissions) {
          jdbcTemplate.update("INSERT INTO iam_role_permission (role_id, permission) VALUES (?, ?)", adminRoleId, p.code());
        }
        jdbcTemplate.update("INSERT INTO iam_role_permission (role_id, permission) VALUES (?, 'users:read')", userRoleId);
      }

      // 3. Seed canonical groups in iam_group
      Long groupCount = jdbcTemplate.queryForObject("SELECT count(*) FROM iam_group", Long.class);
      if (groupCount == null || groupCount == 0) {
        UUID engGroupId = UUID.randomUUID();
        UUID demoTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        jdbcTemplate.update("""
            INSERT INTO iam_group (id, tenant_id, code, name, description, created_at, updated_at)
            VALUES (?, ?, 'ENG', 'Engineering', 'Engineering and product development team', ?, ?)
            """,
            engGroupId, demoTenantId, now, now
        );
      }
    };
  }

  private static void createAttributeIfMissing(
      UserProfile userProfile,
      UserProfileAttributeRepository userProfileAttributeRepository,
      String key,
      String value) {
    if (userProfileAttributeRepository.existsByTenantIdAndUserProfileUsernameAndAttributeKey(
        userProfile.getTenant(), userProfile.getUsername(), key)) {
      return;
    }

    userProfileAttributeRepository.save(
        new UserProfileAttribute(
            userProfile,
            key,
            value));
  }
}
