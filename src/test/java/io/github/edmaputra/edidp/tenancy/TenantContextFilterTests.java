package io.github.edmaputra.edidp.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import io.github.edmaputra.edidp.tenancy.TenantContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantContextFilterTests {

  @Test
  void headerTenantOverridesPathTenant() throws Exception {
    TenantContextFilter filter = new TenantContextFilter(
        true,
        true,
        false,
        false,
        "X-Tenant-ID",
        "127.0.0.1,::1,0:0:0:0:0:0:0:1");

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/t/demo/oauth2/introspect");
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Tenant-ID", "tenant-b");
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<String> resolvedTenantInChain = new AtomicReference<>();
    FilterChain chain = new CapturingFilterChain(resolvedTenantInChain);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(resolvedTenantInChain.get()).isEqualTo("tenant-b");
    assertThat(TenantContext.getCurrentTenant()).isEmpty();
  }

  @Test
  void untrustedHeaderFallsBackToPathWhenEnforced() throws Exception {
    TenantContextFilter filter = new TenantContextFilter(
        true,
        true,
        false,
        true,
        "X-Tenant-ID",
        "127.0.0.1,::1,0:0:0:0:0:0:0:1");

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/t/demo/oauth2/introspect");
    request.setRemoteAddr("10.10.10.10");
    request.addHeader("X-Tenant-ID", "tenant-b");
    MockHttpServletResponse response = new MockHttpServletResponse();

    AtomicReference<String> resolvedTenantInChain = new AtomicReference<>();
    FilterChain chain = new CapturingFilterChain(resolvedTenantInChain);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(resolvedTenantInChain.get()).isEqualTo("demo");
  }

  @Test
  void strictModeReturnsInvalidRequestWhenTenantIsMissing() throws Exception {
    TenantContextFilter filter = new TenantContextFilter(
        true,
        true,
        true,
        false,
        "X-Tenant-ID",
        "127.0.0.1,::1,0:0:0:0:0:0:0:1");

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/introspect");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new NoOpFilterChain());

    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(response.getContentAsString()).contains("\"error\":\"invalid_request\"");
  }

  @Test
  void strictModeRejectsWhenOnlyUntrustedHeaderIsPresent() throws Exception {
    TenantContextFilter filter = new TenantContextFilter(
        true,
        true,
        true,
        true,
        "X-Tenant-ID",
        "127.0.0.1,::1,0:0:0:0:0:0:0:1");

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/introspect");
    request.setRemoteAddr("10.10.10.10");
    request.addHeader("X-Tenant-ID", "tenant-b");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new NoOpFilterChain());

    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(response.getContentAsString()).contains("\"error\":\"invalid_request\"");
  }

  private static final class CapturingFilterChain implements FilterChain {

    private final AtomicReference<String> resolvedTenant;

    private CapturingFilterChain(AtomicReference<String> resolvedTenant) {
      this.resolvedTenant = resolvedTenant;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {
      this.resolvedTenant.set(TenantContext.getCurrentTenant().orElse(null));
    }
  }

  private static final class NoOpFilterChain implements FilterChain {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {
      // no-op
    }
  }
}
