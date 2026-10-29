package io.github.edmaputra.edidp.audit;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
    Map<String, Object> details
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

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for constructing {@link SecurityAuditEvent} instances with sensible defaults.
   */
  public static final class Builder {
    private String eventId;
    private Instant timestamp;
    private String tenantId;
    private SecurityAuditAction action;
    private SecurityAuditStatus status = SecurityAuditStatus.SUCCESS;
    private String principal;
    private String clientIdentifier;
    private String ipAddress;
    private String userAgent;
    private final Map<String, Object> details = new LinkedHashMap<>();

    public Builder eventId(String eventId) {
      this.eventId = eventId;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder action(SecurityAuditAction action) {
      this.action = action;
      return this;
    }

    public Builder status(SecurityAuditStatus status) {
      this.status = status;
      return this;
    }

    public Builder principal(String principal) {
      this.principal = principal;
      return this;
    }

    public Builder clientIdentifier(String clientIdentifier) {
      this.clientIdentifier = clientIdentifier;
      return this;
    }

    public Builder ipAddress(String ipAddress) {
      this.ipAddress = ipAddress;
      return this;
    }

    public Builder userAgent(String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    public Builder detail(String key, Object value) {
      if (key != null && value != null) {
        this.details.put(key, value);
      }
      return this;
    }

    public Builder details(Map<String, ?> map) {
      if (map != null) {
        this.details.putAll(map);
      }
      return this;
    }

    public SecurityAuditEvent build() {
      return new SecurityAuditEvent(
          eventId,
          timestamp,
          tenantId,
          action,
          status,
          principal,
          clientIdentifier,
          ipAddress,
          userAgent,
          details
      );
    }
  }
}
