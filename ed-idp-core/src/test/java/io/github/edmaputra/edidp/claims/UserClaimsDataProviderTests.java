package io.github.edmaputra.edidp.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.edmaputra.edidp.users.UserProfile;
import io.github.edmaputra.edidp.users.UserProfileAttribute;
import io.github.edmaputra.edidp.users.UserProfileAttributeRepository;
import io.github.edmaputra.edidp.users.UserProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UserClaimsDataProvider}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class UserClaimsDataProviderTests {

  private UserProfileRepository userProfileRepository;
  private UserProfileAttributeRepository userProfileAttributeRepository;
  private ClaimInclusionRuleRepository claimInclusionRuleRepository;
  private UserClaimsDataProvider dataProvider;

  @BeforeEach
  void setUp() {
    userProfileRepository = mock(UserProfileRepository.class);
    userProfileAttributeRepository = mock(UserProfileAttributeRepository.class);
    claimInclusionRuleRepository = mock(ClaimInclusionRuleRepository.class);
    dataProvider = new UserClaimsDataProvider(
        userProfileRepository,
        userProfileAttributeRepository,
        claimInclusionRuleRepository
    );
  }

  @Test
  void findUserProfile_whenPresent_mapsAllFields() {
    UserProfile profile = new UserProfile(
        "alice",
        "Alice Smith",
        "alice@example.com",
        true,
        "en-US",
        "America/New_York",
        "Engineering",
        "demo",
        1700000000L
    );
    when(userProfileRepository.findByTenantAndUsername("demo", "alice"))
        .thenReturn(Optional.of(profile));

    Optional<UserProfileData> result = dataProvider.findUserProfile("demo", "alice");

    assertThat(result).isPresent();
    UserProfileData data = result.get();
    assertThat(data.username()).isEqualTo("alice");
    assertThat(data.fullName()).isEqualTo("Alice Smith");
    assertThat(data.email()).isEqualTo("alice@example.com");
    assertThat(data.emailVerified()).isTrue();
    assertThat(data.locale()).isEqualTo("en-US");
    assertThat(data.zoneinfo()).isEqualTo("America/New_York");
    assertThat(data.department()).isEqualTo("Engineering");
    assertThat(data.tenant()).isEqualTo("demo");
    assertThat(data.updatedAt()).isEqualTo(1700000000L);
  }

  @Test
  void findUserProfile_whenAbsent_returnsEmpty() {
    when(userProfileRepository.findByTenantAndUsername("demo", "unknown"))
        .thenReturn(Optional.empty());

    assertThat(dataProvider.findUserProfile("demo", "unknown")).isEmpty();
  }

  @Test
  void findUserAttributes_mapsAttributeList() {
    UserProfile profile = new UserProfile(
        "alice", "Alice", "a@b.com", true, "en", "UTC", "Dept", "demo", 100L
    );
    UserProfileAttribute attr1 = new UserProfileAttribute(profile, "custom_rank", "senior");
    UserProfileAttribute attr2 = new UserProfileAttribute(profile, "cost_center", "CC-42");

    when(userProfileAttributeRepository.findByTenantIdAndUserProfileUsername("demo", "alice"))
        .thenReturn(List.of(attr1, attr2));

    List<UserAttributeData> attrs = dataProvider.findUserAttributes("demo", "alice");

    assertThat(attrs).hasSize(2);
    assertThat(attrs.get(0).key()).isEqualTo("custom_rank");
    assertThat(attrs.get(0).value()).isEqualTo("senior");
    assertThat(attrs.get(1).key()).isEqualTo("cost_center");
    assertThat(attrs.get(1).value()).isEqualTo("CC-42");
  }

  @Test
  void findIncludedAttributeKeys_evaluatesAllClaimTypes() {
    ClaimInclusionRule ruleUserInfo = new ClaimInclusionRule("demo", "attr_userinfo");
    ruleUserInfo.addTarget(ClaimTarget.USERINFO);

    ClaimInclusionRule ruleIdToken = new ClaimInclusionRule("demo", "attr_id_token");
    ruleIdToken.addTarget(ClaimTarget.ID_TOKEN);

    ClaimInclusionRule ruleAccessToken = new ClaimInclusionRule("demo", "attr_access_token");
    ruleAccessToken.addTarget(ClaimTarget.ACCESS_TOKEN);

    Set<String> keys = Set.of("attr_userinfo", "attr_id_token", "attr_access_token");
    when(claimInclusionRuleRepository.findByTenantIdAndAttributeKeyIn("demo", keys))
        .thenReturn(List.of(ruleUserInfo, ruleIdToken, ruleAccessToken));

    Set<String> userinfoIncluded = dataProvider.findIncludedAttributeKeys("demo", keys, ClaimType.USERINFO);
    assertThat(userinfoIncluded).containsExactly("attr_userinfo");

    Set<String> idTokenIncluded = dataProvider.findIncludedAttributeKeys("demo", keys, ClaimType.ID_TOKEN);
    assertThat(idTokenIncluded).containsExactly("attr_id_token");

    Set<String> accessTokenIncluded = dataProvider.findIncludedAttributeKeys("demo", keys, ClaimType.ACCESS_TOKEN);
    assertThat(accessTokenIncluded).containsExactly("attr_access_token");
  }
}
