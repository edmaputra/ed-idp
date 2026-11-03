package io.github.edmaputra.edidp.audit;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

/**
 * Audit event port adapter that serializes security events to SIEM-ready structured JSON logs,
 * enriches MDC diagnostics, stores records in an {@link AuditEventRepository}, and publishes
 * events to the Spring application context.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class StructuredLoggingAuditEventAdapter implements AuditEventPort {

  private static final Logger AUDIT_LOG = LoggerFactory.getLogger("io.github.edmaputra.edidp.audit");

  private final ObjectMapper objectMapper;
  private final AuditEventRepository auditEventRepository;
  private final ApplicationEventPublisher eventPublisher;

  public StructuredLoggingAuditEventAdapter(
      ObjectMapper objectMapper,
      AuditEventRepository auditEventRepository,
      ApplicationEventPublisher eventPublisher) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.auditEventRepository = Objects.requireNonNull(auditEventRepository, "auditEventRepository must not be null");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
  }

  @Override
  public void record(SecurityAuditEvent event) {
    if (event == null) {
      return;
    }

    // 1. Store in repository
    auditEventRepository.save(event);

    // 2. Structured SIEM Logging with MDC context
    try {
      MDC.put("audit.event_id", event.eventId());
      MDC.put("audit.tenant_id", event.tenantId());
      MDC.put("audit.action", event.action().name());
      MDC.put("audit.status", event.status().name());
      MDC.put("audit.principal", event.principal());
      if (event.clientIdentifier() != null) {
        MDC.put("audit.client_id", event.clientIdentifier());
      }
      MDC.put("audit.ip_address", event.ipAddress());

      String jsonPayload;
      try {
        jsonPayload = objectMapper.writeValueAsString(event);
      } catch (Exception e) {
        jsonPayload = "{\"error\":\"serialization_failure\",\"eventId\":\"" + event.eventId() + "\"}";
      }

      if (event.status() == SecurityAuditStatus.SUCCESS) {
        AUDIT_LOG.info("SECURITY_AUDIT: {}", jsonPayload);
      } else {
        AUDIT_LOG.warn("SECURITY_AUDIT: {}", jsonPayload);
      }
    } finally {
      MDC.remove("audit.event_id");
      MDC.remove("audit.tenant_id");
      MDC.remove("audit.action");
      MDC.remove("audit.status");
      MDC.remove("audit.principal");
      MDC.remove("audit.client_id");
      MDC.remove("audit.ip_address");
    }

    // 3. Publish to Spring Application Context
    eventPublisher.publishEvent(event);
  }
}
