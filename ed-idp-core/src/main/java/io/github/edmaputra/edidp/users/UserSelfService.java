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

  /**
   * Retrieves the current Multi-Factor Authentication status for the authenticated user.
   *
   * @param subject the user identifier
   * @param tenantId the resolved tenant identifier
   * @return the user MFA status response
   */
  UserMfaStatusResponse getMyMfaStatus(String subject, String tenantId);

  /**
   * Initiates TOTP Multi-Factor Authentication enrollment, generating a shared secret,
   * QR code URI, and single-use backup recovery codes.
   *
   * @param subject the user identifier
   * @param tenantId the resolved tenant identifier
   * @return the MFA setup response
   */
  MfaEnrollmentSetupResponse initiateMyMfaSetup(String subject, String tenantId);

  /**
   * Activates Multi-Factor Authentication after verifying a valid 6-digit TOTP code.
   *
   * @param subject the user identifier
   * @param tenantId the resolved tenant identifier
   * @param code the 6-digit verification code
   */
  void activateMyMfa(String subject, String tenantId, String code);

  /**
   * Disables Multi-Factor Authentication on the user's account after verifying a code or password.
   *
   * @param subject the user identifier
   * @param tenantId the resolved tenant identifier
   * @param code the verification code or password
   */
  void disableMyMfa(String subject, String tenantId, String code);
}
