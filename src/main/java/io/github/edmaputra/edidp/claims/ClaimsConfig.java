package io.github.edmaputra.edidp.claims;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;

/**
 * Spring configuration providing claims mapping and seed rules for dynamic claims assembly.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
public class ClaimsConfig {

  private static final String DEMO_TENANT = "demo";

  @Bean
  @Order(5)
  @SuppressWarnings("unused")
  CommandLineRunner demoClaimInclusionRuleSeeder(
      ClaimInclusionRuleRepository claimInclusionRuleRepository) {
    return ignored -> {
      createRuleTargetIfMissing(
          claimInclusionRuleRepository,
          "favorite_color",
          ClaimTarget.USERINFO);
      createRuleTargetIfMissing(
          claimInclusionRuleRepository,
          "favorite_color",
          ClaimTarget.ACCESS_TOKEN);

      createRuleTargetIfMissing(
          claimInclusionRuleRepository,
          "employee_level",
          ClaimTarget.ID_TOKEN);
      createRuleTargetIfMissing(
          claimInclusionRuleRepository,
          "employee_level",
          ClaimTarget.ACCESS_TOKEN);

      createRuleTargetIfMissing(
          claimInclusionRuleRepository,
          "region",
          ClaimTarget.USERINFO);
      createRuleTargetIfMissing(
          claimInclusionRuleRepository,
          "region",
          ClaimTarget.ID_TOKEN);
    };
  }

  @Bean
  Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper(
      UserClaimsService userClaimsService) {
    return (context) -> {
      var authorization = context.getAuthorization();
      if (authorization == null) {
        return new OidcUserInfo(Map.of());
      }

      String username = authorization.getPrincipalName();
      UserProfileData userProfile = userClaimsService.getOrDefaultProfile(username);

      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("sub", username);
      claims.put("preferred_username", username);
      claims.put("name", userProfile.fullName());
      claims.put("email", userProfile.email());
      claims.put("email_verified", userProfile.emailVerified());
      claims.put("locale", userProfile.locale());
      claims.put("zoneinfo", userProfile.zoneinfo());
      claims.put("updated_at", userProfile.updatedAt());
      claims.put("department", userProfile.department());
      claims.put("tenant", userProfile.tenant());
      claims.putAll(userClaimsService.getClaims(username, ClaimType.USERINFO));

      return new OidcUserInfo(claims);
    };
  }

  private static void createRuleTargetIfMissing(
      ClaimInclusionRuleRepository claimInclusionRuleRepository,
      String attributeKey,
      ClaimTarget target) {
    ClaimInclusionRule rule = claimInclusionRuleRepository
        .findByTenantIdAndAttributeKey(DEMO_TENANT, attributeKey)
        .orElseGet(() -> claimInclusionRuleRepository.save(new ClaimInclusionRule(DEMO_TENANT, attributeKey)));

    if (rule.includesTarget(target)) {
      return;
    }

    rule.addTarget(target);
    claimInclusionRuleRepository.save(rule);
  }
}
