package io.github.edmaputra.edidp.account;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Basic application smoke test ensuring Spring context initializes properly.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(properties = {
    "spring.security.oauth2.client.registration.edidp.client-id=ed-idp-account-portal",
    "spring.security.oauth2.client.registration.edidp.client-secret=dummy",
    "spring.security.oauth2.client.registration.edidp.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.edidp.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
    "spring.security.oauth2.client.provider.edidp.authorization-uri=http://localhost:9000/oauth2/authorize",
    "spring.security.oauth2.client.provider.edidp.token-uri=http://localhost:9000/oauth2/token",
    "spring.security.oauth2.client.provider.edidp.jwk-set-uri=http://localhost:9000/oauth2/jwks",
    "spring.security.oauth2.client.provider.edidp.user-name-attribute=sub"
})
class AccountPortalApplicationTests {

  @Test
  void contextLoads() {
  }
}
