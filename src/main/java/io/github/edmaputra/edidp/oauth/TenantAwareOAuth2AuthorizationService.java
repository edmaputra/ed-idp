package io.github.edmaputra.edidp.oauth;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Tenant-scoped JDBC authorization service scoping OAuth2 authorizations per tenant.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class TenantAwareOAuth2AuthorizationService extends JdbcOAuth2AuthorizationService {

  private static final String DEFAULT_TENANT = "demo";

  private final JdbcTemplate jdbcTemplate;

  public TenantAwareOAuth2AuthorizationService(
      JdbcTemplate jdbcTemplate,
      RegisteredClientRepository registeredClientRepository) {
    super(jdbcTemplate, registeredClientRepository);
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void save(OAuth2Authorization authorization) {
    super.save(authorization);
    jdbcTemplate.update(
        "update oauth2_authorization set tenant_id = ? where id = ?",
        resolveTenantId(),
        authorization.getId());
  }

  @Override
  public OAuth2Authorization findById(String id) {
    try {
      if (!belongsToTenant(id)) {
        return null;
      }
      return super.findById(id);
    } catch (DataRetrievalFailureException ex) {
      return null;
    }
  }

  @Override
  public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
    OAuth2Authorization authorization;
    try {
      authorization = super.findByToken(token, tokenType);
    } catch (DataRetrievalFailureException ex) {
      return null;
    }
    if (authorization == null || !belongsToTenant(authorization.getId())) {
      return null;
    }
    return authorization;
  }

  private boolean belongsToTenant(String authorizationId) {
    Integer count = jdbcTemplate.queryForObject(
        "select count(*) from oauth2_authorization where id = ? and tenant_id = ?",
        Integer.class,
        authorizationId,
        resolveTenantId());
    return count != null && count > 0;
  }

  private String resolveTenantId() {
    return TenantContext.getCurrentTenantOrDefault(DEFAULT_TENANT);
  }
}