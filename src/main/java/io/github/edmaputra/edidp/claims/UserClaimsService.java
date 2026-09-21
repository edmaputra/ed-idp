package io.github.edmaputra.edidp.claims;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserClaimsService {

  private static final String DEFAULT_TENANT = "demo";

  private static final Set<String> RESERVED_CLAIMS = Set.of(
      "sub", "iss", "aud", "exp", "iat", "nbf", "jti", "scope", "client_id", "azp", "token_type",
      "auth_time", "nonce", "at_hash", "c_hash", "sid", "amr", "acr");

  private final UserClaimsDataProvider dataProvider;

  public UserProfileData getOrDefaultProfile(String username) {
    String tenantId = resolveTenantId();
    return dataProvider.findUserProfile(tenantId, username)
        .orElseGet(() -> defaultProfile(username, tenantId));
  }

  public Map<String, Object> getClaims(String username, ClaimType claimType) {
    String tenantId = resolveTenantId();
    List<UserAttributeData> attributes = dataProvider.findUserAttributes(tenantId, username);
    if (attributes.isEmpty()) {
      return Map.of();
    }

    Set<String> attributeKeys = attributes.stream()
        .map(UserAttributeData::key)
        .filter((key) -> key != null && !key.isBlank())
        .collect(Collectors.toSet());

    Set<String> includedKeys = dataProvider.findIncludedAttributeKeys(
        tenantId,
        attributeKeys,
        claimType);

    Map<String, Object> claims = new LinkedHashMap<>();
    for (UserAttributeData attribute : attributes) {
      String key = attribute.key();
      if (key == null || key.isBlank() || RESERVED_CLAIMS.contains(key)) {
        continue;
      }

      if (!includedKeys.contains(key)) {
        continue;
      }

      claims.put(key, attribute.value());
    }

    return claims;
  }

  private static UserProfileData defaultProfile(String username, String tenantId) {
    return new UserProfileData(
        username,
        username,
        username + "@example.com",
        false,
        "en-US",
        "UTC",
        "unknown",
        tenantId,
        Instant.now().getEpochSecond());
  }

  private String resolveTenantId() {
    return TenantContext.getCurrentTenantOrDefault(DEFAULT_TENANT);
  }
}
