package io.github.edmaputra.edidp.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InMemoryAuditEventRepository}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class InMemoryAuditEventRepositoryTests {

  private InMemoryAuditEventRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InMemoryAuditEventRepository(5);
  }

  @Test
  void storesAndRetrievesRecentEvents() {
    SecurityAuditEvent e1 = SecurityAuditEvent.builder().action(SecurityAuditAction.LOGIN_SUCCESS).principal("u1").build();
    SecurityAuditEvent e2 = SecurityAuditEvent.builder().action(SecurityAuditAction.LOGIN_FAILURE).principal("u2").build();

    repository.save(e1);
    repository.save(e2);

    List<SecurityAuditEvent> recent = repository.findRecent(10);
    assertThat(recent).hasSize(2);
    assertThat(recent.get(0).principal()).isEqualTo("u2");
    assertThat(recent.get(1).principal()).isEqualTo("u1");
  }

  @Test
  void isolatesEventsByTenant() {
    SecurityAuditEvent t1 = SecurityAuditEvent.builder().tenantId("tenant-alpha").action(SecurityAuditAction.LOGIN_SUCCESS).principal("alpha-user").build();
    SecurityAuditEvent t2 = SecurityAuditEvent.builder().tenantId("tenant-beta").action(SecurityAuditAction.LOGIN_SUCCESS).principal("beta-user").build();

    repository.save(t1);
    repository.save(t2);

    List<SecurityAuditEvent> alphaEvents = repository.findByTenant("tenant-alpha", 10);
    assertThat(alphaEvents).hasSize(1);
    assertThat(alphaEvents.get(0).principal()).isEqualTo("alpha-user");

    List<SecurityAuditEvent> betaEvents = repository.findByTenant("tenant-beta", 10);
    assertThat(betaEvents).hasSize(1);
    assertThat(betaEvents.get(0).principal()).isEqualTo("beta-user");
  }

  @Test
  void enforcesCapacityLimit() {
    for (int i = 0; i < 10; i++) {
      repository.save(SecurityAuditEvent.builder()
          .action(SecurityAuditAction.LOGIN_SUCCESS)
          .principal("user-" + i)
          .build());
    }

    List<SecurityAuditEvent> recent = repository.findRecent(100);
    assertThat(recent).hasSize(5);
    assertThat(recent.get(0).principal()).isEqualTo("user-9");
  }

  @Test
  void clearRemovesAllEvents() {
    repository.save(SecurityAuditEvent.builder().action(SecurityAuditAction.LOGIN_SUCCESS).principal("u1").build());
    assertThat(repository.findRecent(10)).hasSize(1);

    repository.clear();
    assertThat(repository.findRecent(10)).isEmpty();
  }
}
