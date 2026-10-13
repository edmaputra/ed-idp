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

  @Test
  void pathTenantRewritesRequestUriAndUrlProperly() throws Exception {
    TenantContextFilter filter = new TenantContextFilter(
        true,
        true,
        false,
        false,
        "X-Tenant-ID",
        "127.0.0.1,::1,0:0:0:0:0:0:0:1");

    AtomicReference<jakarta.servlet.http.HttpServletRequest> capturedRequest = new AtomicReference<>();
    FilterChain chain = (req, res) -> capturedRequest.set((jakarta.servlet.http.HttpServletRequest) req);

    // 1. Non-standard port (8080)
    MockHttpServletRequest request1 = new MockHttpServletRequest("POST", "/t/demo/oauth2/introspect");
    request1.setServerName("idp.example.com");
    request1.setServerPort(8080);
    request1.setScheme("http");
    filter.doFilter(request1, new MockHttpServletResponse(), chain);

    assertThat(capturedRequest.get().getRequestURI()).isEqualTo("/oauth2/introspect");
    assertThat(capturedRequest.get().getServletPath()).isEqualTo("/oauth2/introspect");
    assertThat(capturedRequest.get().getRequestURL().toString())
        .isEqualTo("http://idp.example.com:8080/oauth2/introspect");

    // 2. Standard HTTP port (80)
    MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/t/demo/oauth2/introspect");
    request2.setServerName("idp.example.com");
    request2.setServerPort(80);
    request2.setScheme("http");
    filter.doFilter(request2, new MockHttpServletResponse(), chain);

    assertThat(capturedRequest.get().getRequestURL().toString())
        .isEqualTo("http://idp.example.com/oauth2/introspect");

    // 3. Standard HTTPS port (443)
    MockHttpServletRequest request3 = new MockHttpServletRequest("POST", "/t/demo/oauth2/introspect");
    request3.setServerName("idp.example.com");
    request3.setServerPort(443);
    request3.setScheme("https");
    filter.doFilter(request3, new MockHttpServletResponse(), chain);

    assertThat(capturedRequest.get().getRequestURL().toString())
        .isEqualTo("https://idp.example.com/oauth2/introspect");
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
