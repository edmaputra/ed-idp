package io.github.edmaputra.edidp.ui.service;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for end-user self-service profile and credential maintenance operations.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface UserProfileService {

  /**
   * Data transfer record representing a user's self-service profile view.
   *
   * @param username the username or identifier
   * @param email the user's primary email address
   * @param fullName the user's full display name
   * @param department the department or organizational unit
   * @param tenantId the tenant ID the user belongs to
   * @param roles list of assigned role codes
   * @param groups list of assigned group display names
   * @author edmaputra
   * @since 0.0.1
   */
  record UserProfileDto(
      String username,
      String email,
      String fullName,
      String department,
      String tenantId,
      List<String> roles,
      List<String> groups
  ) {
    public UserProfileDto {
      username = username != null ? username : "";
      email = email != null ? email : "";
      fullName = fullName != null ? fullName : "";
      department = department != null ? department : "";
      tenantId = tenantId != null ? tenantId : "demo";
      roles = roles != null ? List.copyOf(roles) : List.of();
      groups = groups != null ? List.copyOf(groups) : List.of();
    }
  }

  /**
   * Command to update user profile information.
   *
   * @param username the target username
   * @param fullName the updated full name
   * @param department the updated department
   * @author edmaputra
   * @since 0.0.1
   */
  record UpdateProfileCommand(
      String username,
      String fullName,
      String department
  ) {
    public UpdateProfileCommand {
      if (username == null || username.isBlank()) {
        throw new IllegalArgumentException("Username must not be blank");
      }
      fullName = fullName != null ? fullName.trim() : "";
      department = department != null ? department.trim() : "";
    }
  }

  /**
   * Command to change the authenticated user's password.
   *
   * @param username the target username
   * @param currentPassword the current raw password
   * @param newPassword the new raw password
   * @param confirmPassword confirmation of the new password
   * @author edmaputra
   * @since 0.0.1
   */
  record ChangePasswordCommand(
      String username,
      String currentPassword,
      String newPassword,
      String confirmPassword
  ) {
    public ChangePasswordCommand {
      if (username == null || username.isBlank()) {
        throw new IllegalArgumentException("Username must not be blank");
      }
      if (currentPassword == null || currentPassword.isBlank()) {
        throw new IllegalArgumentException("Current password must not be blank");
      }
      if (newPassword == null || newPassword.isBlank()) {
        throw new IllegalArgumentException("New password must not be blank");
      }
      if (confirmPassword == null || confirmPassword.isBlank()) {
        throw new IllegalArgumentException("Confirm password must not be blank");
      }
      if (!newPassword.equals(confirmPassword)) {
        throw new IllegalArgumentException("New password and confirm password do not match");
      }
      if (newPassword.equals(currentPassword)) {
        throw new IllegalArgumentException("New password must not be the same as current password");
      }
    }
  }

  /**
   * Retrieves profile details for the specified username or email.
   *
   * @param username username or email identifier
   * @return the profile DTO if found, or empty
   */
  Optional<UserProfileDto> getProfile(String username);

  /**
   * Updates basic profile attributes (full name, department).
   *
   * @param command the update profile command
   */
  void updateProfile(UpdateProfileCommand command);

  /**
   * Changes the user's password after verifying existing credentials and validating policy.
   *
   * @param command the change password command
   */
  void changePassword(ChangePasswordCommand command);
}
