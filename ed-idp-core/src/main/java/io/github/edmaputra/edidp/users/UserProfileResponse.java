package io.github.edmaputra.edidp.users;

import java.util.List;

/**
 * Response payload representing the current user's self-service profile and identity.
 *
 * @param username the username or identifier
 * @param email the user's primary email address
 * @param fullName the user's display name
 * @param department the user's department
 * @param tenantId the resolved tenant identifier
 * @param roles list of assigned roles
 * @param groups list of assigned groups
 * @author edmaputra
 * @since 0.0.1
 */
public record UserProfileResponse(
    String username,
    String email,
    String fullName,
    String department,
    String tenantId,
    List<String> roles,
    List<String> groups
) {
  public UserProfileResponse {
    username = username != null ? username : "";
    email = email != null ? email : "";
    fullName = fullName != null ? fullName : "";
    department = department != null ? department : "";
    tenantId = tenantId != null ? tenantId : "demo";
    roles = roles != null ? List.copyOf(roles) : List.of();
    groups = groups != null ? List.copyOf(groups) : List.of();
  }
}
