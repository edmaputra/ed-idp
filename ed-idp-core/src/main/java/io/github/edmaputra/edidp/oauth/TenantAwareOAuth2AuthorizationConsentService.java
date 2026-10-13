package io.github.edmaputra.edidp.oauth;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Tenant-scoped JDBC authorization consent service ensuring consent state is isolated per tenant.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class TenantAwareOAuth2AuthorizationConsentService extends JdbcOAuth2AuthorizationConsentService {

  private static final String DEFAULT_TENANT = "demo";

  private final JdbcTemplate jdbcTemplate;

  public TenantAwareOAuth2AuthorizationConsentService(
      JdbcTemplate jdbcTemplate,
      RegisteredClientRepository registeredClientRepository) {
    super(jdbcTemplate, registeredClientRepository);
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void save(OAuth2AuthorizationConsent authorizationConsent) {
    super.save(authorizationConsent);
    jdbcTemplate.update(
        "update oauth2_authorization_consent set tenant_id = ? where registered_client_id = ? and principal_name = ?",
        resolveTenantId(),
        authorizationConsent.getRegisteredClientId(),
        authorizationConsent.getPrincipalName());
  }

  @Override
  public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
    OAuth2AuthorizationConsent consent = super.findById(registeredClientId, principalName);
    if (consent == null) {
      return null;
    }

    Integer count = jdbcTemplate.queryForObject(
        "select count(*) from oauth2_authorization_consent where registered_client_id = ? and principal_name = ? and tenant_id = ?",
        Integer.class,
        registeredClientId,
        principalName,
        resolveTenantId());

    if (count == null || count == 0) {
      return null;
    }
    return consent;
  }

  private String resolveTenantId() {
    return TenantContext.getCurrentTenantOrDefault(DEFAULT_TENANT);
  }
}