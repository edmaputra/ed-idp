package io.github.edmaputra.edidp.users;

/**
 * Request payload for in-session self-service password update.
 *
 * @param currentPassword the user's existing raw password
 * @param newPassword the new raw password meeting policy
 * @param confirmPassword confirmation matching newPassword
 * @author edmaputra
 * @since 0.0.1
 */
public record ChangePasswordRequest(
    String currentPassword,
    String newPassword,
    String confirmPassword
) {
  public ChangePasswordRequest {
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
