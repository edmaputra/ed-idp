package io.github.edmaputra.edidp.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(TenantContextFilter.class);

  private final String headerName;
  private final ResolveTenantService resolveTenantService;

  public TenantContextFilter(
      @Value("${tenant.resolution.header-enabled:true}") boolean headerEnabled,
      @Value("${tenant.resolution.path-enabled:true}") boolean pathEnabled,
      @Value("${tenant.resolution.require-explicit-tenant:false}") boolean requireExplicitTenant,
      @Value("${tenant.resolution.enforce-trusted-proxy-for-header:false}") boolean enforceTrustedProxyForHeader,
      @Value("${tenant.resolution.header-name:X-Tenant-ID}") String headerName,
      @Value("${tenant.resolution.header-trusted-sources:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
      String headerTrustedSources) {
    this.headerName = headerName;
    Set<String> trustedHeaderSources = Arrays.stream(headerTrustedSources.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .collect(Collectors.toSet());
    TenantResolutionPolicy policy = new TenantResolutionPolicy(
        headerEnabled,
        pathEnabled,
        requireExplicitTenant,
        enforceTrustedProxyForHeader,
        trustedHeaderSources);
    this.resolveTenantService = new ResolveTenantService(policy);
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain) throws ServletException, IOException {
    String requestUri = request.getRequestURI();
    TenantResolutionResult resolution = resolveTenantService.resolve(
        requestUri,
        request.getHeader(headerName),
        request.getRemoteAddr());

    if (resolution.invalidRequest()) {
      LOG.debug("Tenant resolution failed for request URI '{}' with strict mode enabled", requestUri);
      writeInvalidRequest(response, "Unable to resolve tenant from request");
      return;
    }

    if (resolution.tenantId().isPresent()) {
      String tenantId = resolution.tenantId().get();
      if (resolution.tenantSource() == TenantResolutionResult.TenantSource.HEADER) {
        LOG.debug("Tenant resolved from header '{}' as '{}'", headerName, tenantId);
      } else if (resolution.tenantSource() == TenantResolutionResult.TenantSource.PATH) {
        LOG.debug("Tenant resolved from path as '{}'", tenantId);
      }
    }

    HttpServletRequest requestToUse = resolution.rewrittenPath().isPresent()
        ? new MachineEndpointRewriteRequest(request, resolution.rewrittenPath().get())
        : request;

    if (resolution.tenantId().isPresent()) {
      try {
        TenantContext.callWithTenant(resolution.tenantId().get(), () -> {
          filterChain.doFilter(requestToUse, response);
          return null;
        });
      } catch (ServletException | IOException | RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new ServletException(e);
      }
    } else {
      filterChain.doFilter(requestToUse, response);
    }
  }

  private void writeInvalidRequest(HttpServletResponse response, String description) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter()
        .write("{\"error\":\"invalid_request\",\"error_description\":\"" + description + "\"}");
  }

  private static final class MachineEndpointRewriteRequest extends HttpServletRequestWrapper {

    private final String rewrittenPath;

    private MachineEndpointRewriteRequest(HttpServletRequest request, String rewrittenPath) {
      super(request);
      this.rewrittenPath = rewrittenPath;
    }

    @Override
    public String getRequestURI() {
      return rewrittenPath;
    }

    @Override
    public String getServletPath() {
      return rewrittenPath;
    }

    @Override
    public StringBuffer getRequestURL() {
      StringBuffer url = new StringBuffer();
      url.append(getScheme())
          .append("://")
          .append(getServerName());
      if (!(getScheme().equals("http") && getServerPort() == 80)
          && !(getScheme().equals("https") && getServerPort() == 443)) {
        url.append(':').append(getServerPort());
      }
      url.append(rewrittenPath);
      return url;
    }
  }
}