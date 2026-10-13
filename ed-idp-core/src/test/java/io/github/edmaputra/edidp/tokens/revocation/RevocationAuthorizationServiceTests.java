package io.github.edmaputra.edidp.tokens.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RevocationAuthorizationService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class RevocationAuthorizationServiceTests {

  private RevocationAuthorizationService service;

  @BeforeEach
  void setUp() {
    service = new RevocationAuthorizationService();
  }

  @Test
  void canRevoke_withRevocationScope_returnsTrue() {
    // Act & Assert
    assertThat(service.canRevoke(Set.of("revocation"))).isTrue();
    assertThat(service.canRevoke(Set.of("read", "revocation", "write"))).isTrue();
  }

  @Test
  void canRevoke_withoutRevocationScope_returnsFalse() {
    // Act & Assert
    assertThat(service.canRevoke(Set.of("read", "write"))).isFalse();
    assertThat(service.canRevoke(Set.of("openid"))).isFalse();
  }

  @Test
  void canRevoke_withNullOrEmptyScopes_returnsFalse() {
    // Act & Assert
    assertThat(service.canRevoke(null)).isFalse();
    assertThat(service.canRevoke(Set.of())).isFalse();
  }
}
