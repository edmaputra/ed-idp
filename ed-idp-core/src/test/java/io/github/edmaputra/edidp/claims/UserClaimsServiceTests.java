package io.github.edmaputra.edidp.claims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.edmaputra.edidp.claims.ClaimType;
import io.github.edmaputra.edidp.claims.UserAttributeData;
import io.github.edmaputra.edidp.claims.UserClaimsDataProvider;
import io.github.edmaputra.edidp.claims.UserClaimsService;
import io.github.edmaputra.edidp.claims.UserProfileData;
import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserClaimsServiceTests {

  @Mock
  private UserClaimsDataProvider dataProvider;

  @Test
  void returnsDefaultProfileWhenProfileIsMissing() {
    TenantContext.runWithTenant("demo", () -> {
      when(dataProvider.findUserProfile("demo", "demo-user")).thenReturn(Optional.empty());

      UserClaimsService useCase = new UserClaimsService(dataProvider);
      UserProfileData profile = useCase.getOrDefaultProfile("demo-user");

      assertThat(profile.username()).isEqualTo("demo-user");
      assertThat(profile.tenant()).isEqualTo("demo");
      assertThat(profile.email()).isEqualTo("demo-user@example.com");
    });
  }

  @Test
  void filtersReservedAndNonIncludedClaims() {
    TenantContext.runWithTenant("demo", () -> {
      when(dataProvider.findUserAttributes("demo", "demo-user")).thenReturn(List.of(
          new UserAttributeData("favorite_color", "blue"),
          new UserAttributeData("scope", "read"),
          new UserAttributeData("region", "apac")));
      when(dataProvider.findIncludedAttributeKeys("demo", Set.of("favorite_color", "scope", "region"), ClaimType.ACCESS_TOKEN))
          .thenReturn(Set.of("favorite_color"));

      UserClaimsService useCase = new UserClaimsService(dataProvider);
      Map<String, Object> claims = useCase.getClaims("demo-user", ClaimType.ACCESS_TOKEN);

      assertThat(claims)
          .containsEntry("favorite_color", "blue")
          .doesNotContainKey("scope")
          .doesNotContainKey("region");
    });
  }
}
