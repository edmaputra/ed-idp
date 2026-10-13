package io.github.edmaputra.edidp.ui.service;

import java.util.List;

/**
 * Service providing administrative insights into tenants.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface AdminTenantService {

  /**
   * Summary descriptor of a tenant.
   *
   * @param tenantId tenant identifier
   * @param clientCount number of OAuth2 clients in this tenant
   */
  record AdminTenantSummary(
      String tenantId,
      long clientCount
  ) {
    public AdminTenantSummary {
      if (tenantId == null || tenantId.isBlank()) {
        throw new IllegalArgumentException("Tenant ID must not be blank");
      }
    }
  }

  /**
   * Retrieves all unique tenants found in the identity provider.
   *
   * @return list of tenant summaries
   */
  List<AdminTenantSummary> listTenants();

  /**
   * Counts the total number of distinct tenants.
   *
   * @return tenant count
   */
  long countTenants();
}
