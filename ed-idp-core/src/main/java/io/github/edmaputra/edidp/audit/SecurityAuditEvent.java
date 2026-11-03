package io.github.edmaputra.edidp.audit;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.Builder;
import lombok.Singular;

/**
 * Immutable security audit record for SIEM event logging, compliance, and intrusion monitoring.
 *
 * @param eventId unique event identifier (UUID)
 * @param timestamp timestamp when the event occurred
 * @param tenantId multi-tenant domain identifier
 * @param action security action performed
 * @param status success or failure outcome
 * @param principal acting username, client identity, or anonymous
 * @param clientIdentifier registered OAuth2 client identifier if applicable
 * @param ipAddress originating client IP address
 * @param userAgent HTTP client User-Agent string
 * @param details contextual metadata sanitized of sensitive secrets
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Builder(toBuilder = true)
public record SecurityAuditEvent(
    String eventId,
    Instant timestamp,
    String tenantId,
    SecurityAuditAction action,
    SecurityAuditStatus status,
    String principal,
    String clientIdentifier,
    String ipAddress,
    String userAgent,
    @Singular("detail") Map<String, Object> details
) {

  public SecurityAuditEvent {
    eventId = (eventId != null && !eventId.isBlank()) ? eventId : UUID.randomUUID().toString();
    timestamp = (timestamp != null) ? timestamp : Instant.now();
    tenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : TenantContext.getCurrentTenantOrDefault("demo");
    action = Objects.requireNonNull(action, "action must not be null");
    status = Objects.requireNonNull(status, "status must not be null");
    principal = (principal != null && !principal.isBlank()) ? principal : "anonymous";
    clientIdentifier = (clientIdentifier != null && !clientIdentifier.isBlank()) ? clientIdentifier : null;
    ipAddress = (ipAddress != null && !ipAddress.isBlank()) ? ipAddress : AuditContextUtils.resolveClientIp();
    userAgent = (userAgent != null && !userAgent.isBlank()) ? userAgent : AuditContextUtils.resolveUserAgent();
    details = sanitizeDetails(details);
  }

  public static class SecurityAuditEventBuilder {
    private SecurityAuditStatus status = SecurityAuditStatus.SUCCESS;
  }

  private static Map<String, Object> sanitizeDetails(Map<String, Object> raw) {
    if (raw == null || raw.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> sanitized = new LinkedHashMap<>();
    raw.forEach((k, v) -> {
      if (k == null) {
        return;
      }
      String lowerKey = k.toLowerCase();
      if (lowerKey.contains("password") || lowerKey.contains("secret") || lowerKey.contains("credential")
          || lowerKey.equals("token") || lowerKey.equals("rawtoken")) {
        sanitized.put(k, "[REDACTED]");
      } else {
        sanitized.put(k, v);
      }
    });
    return Collections.unmodifiableMap(sanitized);
  }
}
