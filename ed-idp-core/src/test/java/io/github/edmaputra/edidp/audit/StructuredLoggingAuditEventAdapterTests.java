package io.github.edmaputra.edidp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link StructuredLoggingAuditEventAdapter}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class StructuredLoggingAuditEventAdapterTests {

  private ObjectMapper objectMapper;
  private AuditEventRepository repository;
  private ApplicationEventPublisher eventPublisher;
  private StructuredLoggingAuditEventAdapter adapter;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    repository = new InMemoryAuditEventRepository();
    eventPublisher = mock(ApplicationEventPublisher.class);
    adapter = new StructuredLoggingAuditEventAdapter(objectMapper, repository, eventPublisher);
  }

  @Test
  void recordStoresInRepositoryAndPublishesApplicationEvent() {
    SecurityAuditEvent event = SecurityAuditEvent.builder()
        .action(SecurityAuditAction.TOKEN_ISSUED)
        .principal("demo-client")
        .tenantId("demo")
        .detail("grant_type", "client_credentials")
        .build();

    adapter.record(event);

    assertThat(repository.findRecent(10)).containsExactly(event);
    verify(eventPublisher).publishEvent(event);
  }

  @Test
  void recordHandlesNullGracefully() {
    adapter.record(null);
    assertThat(repository.findRecent(10)).isEmpty();
  }
}
