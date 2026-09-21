package io.github.edmaputra.edidp.oauth;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

public class TenantAwareRegisteredClientRepository extends JdbcRegisteredClientRepository {

  private static final String DEFAULT_TENANT = "demo";

  private final JdbcTemplate jdbcTemplate;

  public TenantAwareRegisteredClientRepository(JdbcTemplate jdbcTemplate) {
    super(jdbcTemplate);
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void save(RegisteredClient registeredClient) {
    super.save(registeredClient);
    jdbcTemplate.update(
        "update oauth2_registered_client set tenant_id = ? where id = ?",
        resolveTenantId(),
        registeredClient.getId());
  }

  @Override
  public RegisteredClient findById(String id) {
    if (!belongsToTenant(id)) {
      return null;
    }
    return super.findById(id);
  }

  @Override
  public RegisteredClient findByClientId(String clientId) {
    List<String> ids = jdbcTemplate.queryForList(
        "select id from oauth2_registered_client where client_id = ? and tenant_id = ?",
        String.class,
        clientId,
        resolveTenantId());
    if (ids.isEmpty()) {
      return null;
    }
    return super.findById(ids.get(0));
  }

  private boolean belongsToTenant(String registeredClientId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(*) from oauth2_registered_client where id = ? and tenant_id = ?",
        Integer.class,
        registeredClientId,
        resolveTenantId());
    return count != null && count > 0;
  }

  private String resolveTenantId() {
    return TenantContext.getCurrentTenantOrDefault(DEFAULT_TENANT);
  }
}