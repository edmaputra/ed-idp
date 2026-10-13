package io.github.edmaputra.edidp.ui.service;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * JDBC-based implementation of {@link AdminTenantService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class JdbcAdminTenantService implements AdminTenantService {

  private final JdbcTemplate jdbcTemplate;

  public JdbcAdminTenantService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  @Override
  public List<AdminTenantSummary> listTenants() {
    String sql = """
        SELECT COALESCE(tenant_id, 'demo') AS tenant_id, COUNT(*) AS client_count
        FROM oauth2_registered_client
        GROUP BY COALESCE(tenant_id, 'demo')
        ORDER BY tenant_id ASC
        """;

    return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminTenantSummary(
        rs.getString("tenant_id"),
        rs.getLong("client_count")
    ));
  }

  @Override
  public long countTenants() {
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(DISTINCT COALESCE(tenant_id, 'demo')) FROM oauth2_registered_client",
        Long.class
    );
    return count != null ? count : 0L;
  }
}
