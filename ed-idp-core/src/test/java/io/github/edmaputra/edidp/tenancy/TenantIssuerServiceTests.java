package io.github.edmaputra.edidp.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TenantIssuerService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class TenantIssuerServiceTests {

  private TenantIssuerService service;

  @BeforeEach
  void setUp() {
    service = new TenantIssuerService();
  }

  @Test
  void resolveTenantIssuer_withValidTenantAndBaseIssuer_returnsTenantIssuer() {
    // Act
    String issuer = service.resolveTenantIssuer("https://idp.example.com", "tenant-1");

    // Assert
    assertThat(issuer).isEqualTo("https://idp.example.com/t/tenant-1");
  }

  @Test
  void resolveTenantIssuer_withTrailingSlashInBaseIssuer_trimsSlash() {
    // Act
    String issuer = service.resolveTenantIssuer("https://idp.example.com/", "tenant-1");

    // Assert
    assertThat(issuer).isEqualTo("https://idp.example.com/t/tenant-1");
  }

  @Test
  void resolveTenantIssuer_withNullOrBlankTenant_returnsBaseIssuer() {
    // Act & Assert
    assertThat(service.resolveTenantIssuer("https://idp.example.com", null))
        .isEqualTo("https://idp.example.com");
    assertThat(service.resolveTenantIssuer("https://idp.example.com/", ""))
        .isEqualTo("https://idp.example.com");
    assertThat(service.resolveTenantIssuer("https://idp.example.com/", "   "))
        .isEqualTo("https://idp.example.com");
  }

  @Test
  void resolveTenantIssuer_withNullOrBlankBaseIssuer_returnsEmptyStringOrTenantPath() {
    // Act & Assert
    assertThat(service.resolveTenantIssuer(null, null)).isEqualTo("");
    assertThat(service.resolveTenantIssuer("", null)).isEqualTo("");
    assertThat(service.resolveTenantIssuer(null, "tenant-1")).isEqualTo("/t/tenant-1");
  }

  @Test
  void resolveTenantIssuer_withInvalidTenantCharacters_throwsIllegalArgumentException() {
    // Assert
    assertThatThrownBy(() -> service.resolveTenantIssuer("https://idp.example.com", "tenant/1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid tenant id");

    assertThatThrownBy(() -> service.resolveTenantIssuer("https://idp.example.com", "tenant@org"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid tenant id");

    assertThatThrownBy(() -> service.resolveTenantIssuer("https://idp.example.com", "tenant 1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid tenant id");
  }
}
