package io.github.edmaputra.edidp.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility functions for resolving contextual HTTP metadata such as IP and User-Agent.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public final class AuditContextUtils {

  private static final String UNKNOWN = "UNKNOWN";

  private AuditContextUtils() {
  }

  /**
   * Resolves the originating client IP address, honoring {@code X-Forwarded-For} when present.
   *
   * @return resolved IP address string, or {@code "UNKNOWN"} if unobtainable.
   */
  public static String resolveClientIp() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return UNKNOWN;
    }
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      int commaIndex = forwardedFor.indexOf(',');
      return (commaIndex != -1) ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
    }
    String remoteAddr = request.getRemoteAddr();
    return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : UNKNOWN;
  }

  /**
   * Resolves the HTTP {@code User-Agent} header string.
   *
   * @return resolved User-Agent or {@code "UNKNOWN"} if unobtainable.
   */
  public static String resolveUserAgent() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return UNKNOWN;
    }
    String userAgent = request.getHeader("User-Agent");
    return (userAgent != null && !userAgent.isBlank()) ? userAgent : UNKNOWN;
  }

  private static HttpServletRequest currentRequest() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servletAttributes) {
      return servletAttributes.getRequest();
    }
    return null;
  }
}
