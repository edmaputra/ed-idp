package io.github.edmaputra.edidp.users;

/**
 * Service contract for end-user self-service profile and credential maintenance operations.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface UserSelfService {

  /**
   * Retrieves profile details for the authenticated subject within the target tenant.
   *
   * @param subject the user identifier (username or email)
   * @param tenantId the resolved tenant identifier
   * @return the user profile response
   */
  UserProfileResponse getMyProfile(String subject, String tenantId);

  /**
   * Updates basic profile attributes (full name, department) scoped by tenant.
   *
   * @param subject the user identifier
   * @param tenantId the resolved tenant identifier
   * @param request the profile update request
   * @return the updated user profile response
   */
  UserProfileResponse updateMyProfile(String subject, String tenantId, UpdateProfileRequest request);

  /**
   * Changes the authenticated user's password following policy verification and existing credential checks.
   *
   * @param subject the user identifier
   * @param tenantId the resolved tenant identifier
   * @param request the password change request
   */
  void changeMyPassword(String subject, String tenantId, ChangePasswordRequest request);
}
