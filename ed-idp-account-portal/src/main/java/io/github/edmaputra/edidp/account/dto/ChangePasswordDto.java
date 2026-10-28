package io.github.edmaputra.edidp.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO carrying password change request for the Resource Server.
 *
 * @param currentPassword user's current password
 * @param newPassword new password to set
 * @param confirmPassword confirmation password matching newPassword
 * @author edmaputra
 * @since 0.0.1
 */
public record ChangePasswordDto(
    @NotBlank(message = "Current password must not be blank")
    String currentPassword,

    @NotBlank(message = "New password must not be blank")
    String newPassword,

    @NotBlank(message = "Confirm password must not be blank")
    String confirmPassword
) {
  public ChangePasswordDto {
    currentPassword = currentPassword != null ? currentPassword : "";
    newPassword = newPassword != null ? newPassword : "";
    confirmPassword = confirmPassword != null ? confirmPassword : "";
  }
}
