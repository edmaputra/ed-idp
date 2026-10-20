package io.github.edmaputra.edidp.users;

/**
 * Request payload to update self-service profile attributes.
 *
 * @param fullName updated full display name
 * @param department updated department or team
 * @author edmaputra
 * @since 0.0.1
 */
public record UpdateProfileRequest(
    String fullName,
    String department
) {
  public UpdateProfileRequest {
    fullName = fullName != null ? fullName.trim() : "";
    department = department != null ? department.trim() : "";
  }
}
