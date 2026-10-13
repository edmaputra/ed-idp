package io.github.edmaputra.edidp.tokens;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for token lifetime, rotation policies, and allowed scopes.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "app.token")
public class TokenPolicyProperties {

  private Duration accessTokenTimeToLive = Duration.ofMinutes(5);

  private Duration refreshTokenTimeToLive = Duration.ofDays(7);

  private boolean reuseRefreshTokens = false;

  private Set<String> clientCredentialsAllowedScopes = new LinkedHashSet<>(
      Set.of("read", "write", "introspection", "revocation"));

  public Duration getAccessTokenTimeToLive() {
    return accessTokenTimeToLive;
  }

  public void setAccessTokenTimeToLive(Duration accessTokenTimeToLive) {
    this.accessTokenTimeToLive = accessTokenTimeToLive;
  }

  public Duration getRefreshTokenTimeToLive() {
    return refreshTokenTimeToLive;
  }

  public void setRefreshTokenTimeToLive(Duration refreshTokenTimeToLive) {
    this.refreshTokenTimeToLive = refreshTokenTimeToLive;
  }

  public boolean isReuseRefreshTokens() {
    return reuseRefreshTokens;
  }

  public void setReuseRefreshTokens(boolean reuseRefreshTokens) {
    this.reuseRefreshTokens = reuseRefreshTokens;
  }

  public Set<String> getClientCredentialsAllowedScopes() {
    return clientCredentialsAllowedScopes;
  }

  public void setClientCredentialsAllowedScopes(Set<String> clientCredentialsAllowedScopes) {
    if (clientCredentialsAllowedScopes == null) {
      this.clientCredentialsAllowedScopes = new LinkedHashSet<>();
      return;
    }

    this.clientCredentialsAllowedScopes = clientCredentialsAllowedScopes.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.trim().toLowerCase(Locale.ROOT))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
