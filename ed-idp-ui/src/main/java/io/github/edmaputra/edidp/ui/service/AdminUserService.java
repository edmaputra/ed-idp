package io.github.edmaputra.edidp.ui.service;

import io.github.edmaputra.iam.application.model.MfaSetupResponse;
import io.github.edmaputra.iam.application.model.MfaStatusResponse;
import java.util.List;

/**
 * Service defining IAM administrative operations for users and profiles.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface AdminUserService {

  /**
   * Summary descriptor of a user for the IAM admin interface.
   *
   * @param username username identifier
   * @param enabled whether account is enabled
   * @param tenantId tenant owning this user
   * @param fullName display full name
   * @param email email address
   * @param roles assigned authorities or roles
   * @param groups assigned user groups
   * @param department assigned department
   * @param mfaEnabled whether multi-factor authentication is active
   */
  record AdminUserSummary(
      String username,
      boolean enabled,
      String tenantId,
      String fullName,
      String email,
      List<String> roles,
      List<String> groups,
      String department,
      boolean mfaEnabled
  ) {
    public AdminUserSummary {
      if (username == null || username.isBlank()) {
        throw new IllegalArgumentException("Username must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (fullName == null || fullName.isBlank()) {
        fullName = username;
      }
      if (roles == null) {
        roles = List.of();
      }
      if (groups == null) {
        groups = List.of();
      }
    }

    public AdminUserSummary(
        String username,
        boolean enabled,
        String tenantId,
        String fullName,
        String email,
        List<String> roles,
        List<String> groups,
        String department) {
      this(username, enabled, tenantId, fullName, email, roles, groups, department, false);
    }

    public AdminUserSummary(
        String username,
        boolean enabled,
        String tenantId,
        String fullName,
        String email,
        List<String> roles,
        String department) {
      this(username, enabled, tenantId, fullName, email, roles, List.of(), department, false);
    }
  }

  /**
   * Command to create a new user.
   *
   * @param username username
   * @param password raw password
   * @param fullName full name
   * @param email email address
   * @param department department
   * @param tenantId tenant ID
   * @param role assigned role (e.g. ROLE_USER or ROLE_ADMIN)
   */
  record CreateUserCommand(
      String username,
      String password,
      String fullName,
      String email,
      String department,
      String tenantId,
      String role
  ) {
    public CreateUserCommand {
      if (username == null || username.isBlank()) {
        throw new IllegalArgumentException("Username must not be blank");
      }
      if (password == null || password.isBlank()) {
        throw new IllegalArgumentException("Password must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (fullName == null || fullName.isBlank()) {
        fullName = username;
      }
      if (email == null || email.isBlank()) {
        email = username + "@example.com";
      }
      if (department == null || department.isBlank()) {
        department = "general";
      }
      if (role == null || role.isBlank()) {
        role = "ROLE_USER";
      }
    }
  }

  /**
   * Command to update user profile and assign/unassign roles and groups.
   *
   * @param username username of user to update
   * @param fullName display full name
   * @param email email address
   * @param department department
   * @param tenantId tenant ID
   * @param enabled account active status
   * @param roles list of roles to assign
   * @param groups list of groups to assign
   */
  record UpdateUserCommand(
      String username,
      String fullName,
      String email,
      String department,
      String tenantId,
      boolean enabled,
      List<String> roles,
      List<String> groups
  ) {
    public UpdateUserCommand {
      if (username == null || username.isBlank()) {
        throw new IllegalArgumentException("Username must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (roles == null) {
        roles = List.of();
      }
      if (groups == null) {
        groups = List.of();
      }
    }
  }

  /**
   * Lists all users in the system across tenants or for a specific tenant.
   *
   * @return list of user summaries
   */
  List<AdminUserSummary> listUsers();

  /**
   * Gets a user summary by username.
   *
   * @param username target username
   * @return optional user summary
   */
  java.util.Optional<AdminUserSummary> getUser(String username);

  /**
   * Counts total registered users.
   *
   * @return user count
   */
  long countUsers();

  /**
   * Creates a user along with their authorities and profile.
   *
   * @param command user creation command
   */
  void createUser(CreateUserCommand command);

  /**
   * Updates a user profile, active state, and role/group assignments.
   *
   * @param command user update command
   */
  void updateUser(UpdateUserCommand command);

  /**
   * Toggles the enabled status of a user (activate or suspend).
   *
   * @param username target username
   * @param enabled new enabled state
   */
  void setUserEnabled(String username, boolean enabled);

  /**
   * Retrieves MFA status for a user.
   *
   * @param username target username
   * @return MFA status response
   */
  MfaStatusResponse getMfaStatus(String username);

  /**
   * Initiates MFA setup for a user, generating shared secret, QR code URI, and backup codes.
   *
   * @param username target username
   * @return MFA setup response
   */
  MfaSetupResponse initiateMfaSetup(String username);

  /**
   * Activates MFA for a user after verifying the initial TOTP code.
   *
   * @param username target username
   * @param code 6-digit TOTP code
   */
  void activateMfa(String username, String code);

  /**
   * Disables MFA for a user.
   *
   * @param username target username
   * @param codeOrPassword verification code or password
   */
  void disableMfa(String username, String codeOrPassword);
}
