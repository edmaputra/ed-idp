package io.github.edmaputra.edidp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SecurityAuditEvent} validation, default values, and sensitive data sanitization.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class SecurityAuditEventTests {

  @Test
  void constructorEnforcesNonNullActionAndStatus() {
    assertThatThrownBy(() -> new SecurityAuditEvent(
        null, Instant.now(), "demo", null, SecurityAuditStatus.SUCCESS, "user", null, "127.0.0.1", "agent", Map.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("action");

    assertThatThrownBy(() -> new SecurityAuditEvent(
        null, Instant.now(), "demo", SecurityAuditAction.LOGIN_SUCCESS, null, "user", null, "127.0.0.1", "agent", Map.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("status");
  }

  @Test
  void builderAppliesSensibleDefaults() {
    SecurityAuditEvent event = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.LOGIN_SUCCESS)
        .principal("john.doe")
        .build();

    assertThat(event.eventId()).isNotBlank();
    assertThat(event.timestamp()).isNotNull();
    assertThat(event.tenantId()).isNotBlank();
    assertThat(event.status()).isEqualTo(SecurityAuditStatus.SUCCESS);
    assertThat(event.principal()).isEqualTo("john.doe");
    assertThat(event.clientIdentifier()).isNull();
    assertThat(event.ipAddress()).isNotBlank();
    assertThat(event.userAgent()).isNotBlank();
    assertThat(event.details()).isEmpty();
  }

  @Test
  void sensitiveFieldsInDetailsAreSanitized() {
    SecurityAuditEvent event = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.LOGIN_FAILURE)
        .status(SecurityAuditStatus.FAILURE)
        .principal("attacker")
        .detail("password", "super-secret-password")
        .detail("client_secret", "confidential-secret")
        .detail("rawToken", "eyJhbGciOi...")
        .detail("credential", "auth-token")
        .detail("publicInfo", "visible-value")
        .build();

    assertThat(event.details().get("password")).isEqualTo("[REDACTED]");
    assertThat(event.details().get("client_secret")).isEqualTo("[REDACTED]");
    assertThat(event.details().get("rawToken")).isEqualTo("[REDACTED]");
    assertThat(event.details().get("credential")).isEqualTo("[REDACTED]");
    assertThat(event.details().get("publicInfo")).isEqualTo("visible-value");
  }
}
