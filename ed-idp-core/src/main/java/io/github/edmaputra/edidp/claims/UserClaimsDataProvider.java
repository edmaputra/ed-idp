package io.github.edmaputra.edidp.claims;

import io.github.edmaputra.edidp.users.UserProfile;
import io.github.edmaputra.edidp.users.UserProfileAttributeRepository;
import io.github.edmaputra.edidp.users.UserProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Data provider adapter accessing user profile and attribute repositories for claim construction.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Component
@RequiredArgsConstructor
public class UserClaimsDataProvider {

  private final UserProfileRepository userProfileRepository;
  private final UserProfileAttributeRepository userProfileAttributeRepository;
  private final ClaimInclusionRuleRepository claimInclusionRuleRepository;

  public Optional<UserProfileData> findUserProfile(String tenantId, String username) {
    return userProfileRepository.findByTenantAndUsername(tenantId, username)
        .map(this::toUserProfileData);
  }

  public List<UserAttributeData> findUserAttributes(String tenantId, String username) {
    return userProfileAttributeRepository.findByTenantIdAndUserProfileUsername(tenantId, username)
        .stream()
        .map((attribute) -> new UserAttributeData(
            attribute.getAttributeKey(),
            attribute.getAttributeValue()))
        .toList();
  }

  public Set<String> findIncludedAttributeKeys(
      String tenantId,
      Set<String> attributeKeys,
      ClaimType claimType) {
    ClaimTarget target = toClaimTarget(claimType);
    return claimInclusionRuleRepository.findByTenantIdAndAttributeKeyIn(tenantId, attributeKeys)
        .stream()
        .filter((rule) -> rule.includesTarget(target))
        .map(ClaimInclusionRule::getAttributeKey)
        .collect(Collectors.toSet());
  }

  private UserProfileData toUserProfileData(UserProfile profile) {
    return new UserProfileData(
        profile.getUsername(),
        profile.getFullName(),
        profile.getEmail(),
        profile.isEmailVerified(),
        profile.getLocale(),
        profile.getZoneinfo(),
        profile.getDepartment(),
        profile.getTenant(),
        profile.getUpdatedAt());
  }

  private ClaimTarget toClaimTarget(ClaimType claimType) {
    return switch (claimType) {
      case USERINFO -> ClaimTarget.USERINFO;
      case ID_TOKEN -> ClaimTarget.ID_TOKEN;
      case ACCESS_TOKEN -> ClaimTarget.ACCESS_TOKEN;
    };
  }
}
