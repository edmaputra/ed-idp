package io.github.edmaputra.edidp.account.client;

import io.github.edmaputra.edidp.account.dto.ChangePasswordDto;
import io.github.edmaputra.edidp.account.dto.UpdateProfileDto;
import io.github.edmaputra.edidp.account.dto.UserProfileDto;

/**
 * Client interface for interacting with the ed-idp Resource Server self-service REST API.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface AccountPortalApiClient {

  /**
   * Retrieves the current user's profile and assigned roles/groups.
   *
   * @param accessToken the active Bearer token
   * @param tenantId the tenant realm
   * @return the user profile response DTO
   */
  UserProfileDto getProfile(String accessToken, String tenantId);

  /**
   * Updates the current user's profile metadata.
   *
   * @param accessToken the active Bearer token
   * @param tenantId the tenant realm
   * @param request the profile update payload
   * @return the updated user profile response DTO
   */
  UserProfileDto updateProfile(String accessToken, String tenantId, UpdateProfileDto request);

  /**
   * Updates the current user's password in-session.
   *
   * @param accessToken the active Bearer token
   * @param tenantId the tenant realm
   * @param request the password change payload
   */
  void changePassword(String accessToken, String tenantId, ChangePasswordDto request);
}
