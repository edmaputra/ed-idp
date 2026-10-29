package io.github.edmaputra.edidp.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-safe, bounded in-memory implementation of {@link AuditEventRepository}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class InMemoryAuditEventRepository implements AuditEventRepository {

  private static final int DEFAULT_MAX_CAPACITY = 1000;

  private final int maxCapacity;
  private final ConcurrentLinkedDeque<SecurityAuditEvent> events = new ConcurrentLinkedDeque<>();

  public InMemoryAuditEventRepository() {
    this(DEFAULT_MAX_CAPACITY);
  }

  public InMemoryAuditEventRepository(int maxCapacity) {
    this.maxCapacity = Math.max(1, maxCapacity);
  }

  @Override
  public void save(SecurityAuditEvent event) {
    if (event == null) {
      return;
    }
    events.addFirst(event);
    while (events.size() > maxCapacity) {
      events.pollLast();
    }
  }

  @Override
  public List<SecurityAuditEvent> findRecent(int limit) {
    int cap = Math.max(0, limit);
    List<SecurityAuditEvent> result = new ArrayList<>();
    for (SecurityAuditEvent event : events) {
      if (result.size() >= cap) {
        break;
      }
      result.add(event);
    }
    return List.copyOf(result);
  }

  @Override
  public List<SecurityAuditEvent> findByTenant(String tenantId, int limit) {
    if (tenantId == null || tenantId.isBlank()) {
      return List.of();
    }
    int cap = Math.max(0, limit);
    List<SecurityAuditEvent> result = new ArrayList<>();
    for (SecurityAuditEvent event : events) {
      if (result.size() >= cap) {
        break;
      }
      if (tenantId.equalsIgnoreCase(event.tenantId())) {
        result.add(event);
      }
    }
    return List.copyOf(result);
  }

  @Override
  public void clear() {
    events.clear();
  }
}
