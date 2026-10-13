package io.github.edmaputra.edidp.consent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Integration tests verifying tenant isolation in {@link io.github.edmaputra.edidp.oauth.TenantAwareOAuth2AuthorizationConsentService}
 * and consent management in {@link ConsentStore}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantAwareConsentIntegrationTests {

  @Autowired
  private ConsentStore consentStore;

  @Autowired
  private OAuth2AuthorizationConsentService authorizationConsentService;

  @Autowired
  private RegisteredClientRepository registeredClientRepository;

  private String defaultClientId;

  @BeforeEach
  void setUp() {
    RegisteredClient client = registeredClientRepository.findByClientId("demo-client");
    assertThat(client).isNotNull();
    defaultClientId = client.getId();
  }

  @Test
  void saveConsent_andRetrieve_withinDefaultTenant_isSuccessful() {
    String principal = "alice-demo-user";

    // Act
    consentStore.saveConsent(principal, defaultClientId, Set.of("read", "write"));

    // Assert
    Set<String> scopes = consentStore.getAuthorizedScopes(principal, defaultClientId);
    assertThat(scopes).containsExactlyInAnyOrder("SCOPE_read", "SCOPE_write");

    boolean missingRead = consentStore.isMissingConsent(principal, defaultClientId, Set.of("SCOPE_read"));
    assertThat(missingRead).isFalse();

    boolean missingAdmin = consentStore.isMissingConsent(principal, defaultClientId, Set.of("SCOPE_read", "SCOPE_admin"));
    assertThat(missingAdmin).isTrue();

    OAuth2AuthorizationConsent consent = authorizationConsentService.findById(defaultClientId, principal);
    assertThat(consent).isNotNull();
    assertThat(consent.getPrincipalName()).isEqualTo(principal);
  }

  @Test
  void tenantScopedClient_saveAndRetrieveConsent_isSuccessful() throws Exception {
    String tenant = "tenant-custom";
    String principal = "bob-custom-user";

    RegisteredClient clientInTenant = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("custom-client-" + UUID.randomUUID())
        .clientSecret("{noop}secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("http://example.com/callback")
        .scope("openid")
        .scope("profile")
        .build();

    TenantContext.callWithTenant(tenant, () -> {
      registeredClientRepository.save(clientInTenant);
      consentStore.saveConsent(principal, clientInTenant.getId(), Set.of("openid", "profile"));

      Set<String> scopes = consentStore.getAuthorizedScopes(principal, clientInTenant.getId());
      assertThat(scopes).containsExactlyInAnyOrder("SCOPE_openid", "SCOPE_profile");

      OAuth2AuthorizationConsent consent =
          authorizationConsentService.findById(clientInTenant.getId(), principal);
      assertThat(consent).isNotNull();
      assertThat(consent.getPrincipalName()).isEqualTo(principal);
      return null;
    });
  }

  @Test
  void findById_whenConsentDoesNotExist_returnsNull() {
    OAuth2AuthorizationConsent nonExistent =
        authorizationConsentService.findById(defaultClientId, "non-existent-user");
    assertThat(nonExistent).isNull();

    Set<String> emptyScopes = consentStore.getAuthorizedScopes("non-existent-user", defaultClientId);
    assertThat(emptyScopes).isEmpty();
  }
}
