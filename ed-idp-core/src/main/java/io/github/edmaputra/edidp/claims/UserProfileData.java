package io.github.edmaputra.edidp.claims;

/**
 * Immutable value object holding core user profile data for claim generation.
 *
 * @param username      the unique username, must not be null or blank
 * @param fullName      the user's full name
 * @param email         the user's email address
 * @param emailVerified whether the email is verified
 * @param locale        the user's preferred locale
 * @param zoneinfo      the user's time zone info
 * @param department    the department or organizational unit
 * @param tenant        the tenant identifier
 * @param updatedAt     the epoch second timestamp of the last update
 * @author edmaputra
 * @since 0.0.1
 */
public record UserProfileData(
    String username,
    String fullName,
    String email,
    boolean emailVerified,
    String locale,
    String zoneinfo,
    String department,
    String tenant,
    long updatedAt) {

  public UserProfileData {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Username must not be null or blank");
    }
  }
}
