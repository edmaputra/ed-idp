package io.github.edmaputra.edidp.account.dto;

/**
 * DTO carrying updated user profile attributes for the Resource Server.
 *
 * @param fullName updated full display name
 * @param department updated department or team
 * @author edmaputra
 * @since 0.0.1
 */
public record UpdateProfileDto(
    String fullName,
    String department
) {
  public UpdateProfileDto {
    fullName = fullName != null ? fullName.trim() : "";
    department = department != null ? department.trim() : "";
  }
}
