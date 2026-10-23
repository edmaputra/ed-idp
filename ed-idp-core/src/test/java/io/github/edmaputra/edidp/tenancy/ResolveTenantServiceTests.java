package io.github.edmaputra.edidp.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.tenancy.ResolveTenantService;
import io.github.edmaputra.edidp.tenancy.TenantResolutionPolicy;
import io.github.edmaputra.edidp.tenancy.TenantResolutionResult;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResolveTenantServiceTests {

  @Test
  void headerTenantOverridesPathTenant() {
    ResolveTenantService useCase = new ResolveTenantService(defaultPolicy(false));

    TenantResolutionResult result = useCase.resolve(
        "/t/demo/oauth2/introspect",
        "tenant-b",
        "127.0.0.1");

    assertThat(result.tenantId()).contains("tenant-b");
    assertThat(result.tenantSource()).isEqualTo(TenantResolutionResult.TenantSource.HEADER);
    assertThat(result.rewrittenPath()).contains("/oauth2/introspect");
    assertThat(result.invalidRequest()).isFalse();
  }

  @Test
  void strictModeWithoutTenantMarksInvalidRequest() {
    ResolveTenantService useCase = new ResolveTenantService(defaultPolicy(true));

    TenantResolutionResult result = useCase.resolve("/oauth2/introspect", null, "127.0.0.1");

    assertThat(result.tenantId()).isEmpty();
    assertThat(result.invalidRequest()).isTrue();
  }

  @Test
  void untrustedHeaderFallsBackToPathWhenEnforced() {
    ResolveTenantService useCase = new ResolveTenantService(
        new TenantResolutionPolicy(true, true, false, true, Set.of("127.0.0.1")));

    TenantResolutionResult result = useCase.resolve(
        "/t/demo/oauth2/introspect",
        "tenant-b",
        "10.10.10.10");

    assertThat(result.tenantId()).contains("demo");
    assertThat(result.tenantSource()).isEqualTo(TenantResolutionResult.TenantSource.PATH);
    assertThat(result.invalidRequest()).isFalse();
  }

  @Test
  void invalidHeaderFallsBackToPath() {
    ResolveTenantService useCase = new ResolveTenantService(defaultPolicy(false));

    TenantResolutionResult result = useCase.resolve(
        "/t/demo/oauth2/introspect",
        "tenant bad",
        "127.0.0.1");

    assertThat(result.tenantId()).contains("demo");
    assertThat(result.tenantSource()).isEqualTo(TenantResolutionResult.TenantSource.PATH);
  }

  @Test
  void machineEndpointPathIsRewritten() {
    ResolveTenantService useCase = new ResolveTenantService(defaultPolicy(false));

    TenantResolutionResult result = useCase.resolve(
        "/t/demo/oauth2/revoke",
        null,
        "127.0.0.1");

    assertThat(result.tenantId()).contains("demo");
    assertThat(result.rewrittenPath()).contains("/oauth2/revoke");
  }

  @Test
  void userSelfServiceEndpointPathIsRewritten() {
    ResolveTenantService useCase = new ResolveTenantService(defaultPolicy(false));

    TenantResolutionResult resultMe = useCase.resolve(
        "/t/acme/api/v1/users/me",
        null,
        "127.0.0.1");
    assertThat(resultMe.tenantId()).contains("acme");
    assertThat(resultMe.rewrittenPath()).contains("/api/v1/users/me");

    TenantResolutionResult resultPassword = useCase.resolve(
        "/t/acme/api/v1/users/me/password",
        null,
        "127.0.0.1");
    assertThat(resultPassword.tenantId()).contains("acme");
    assertThat(resultPassword.rewrittenPath()).contains("/api/v1/users/me/password");
  }

  private TenantResolutionPolicy defaultPolicy(boolean requireExplicitTenant) {
    return new TenantResolutionPolicy(true, true, requireExplicitTenant, false, Set.of("127.0.0.1"));
  }
}
