package io.github.edmaputra.edidp.tokens;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.integration.AuthServerIntegrationTests;
import io.github.edmaputra.edidp.tokens.introspection.OAuth2TokenIntrospectionController;
import io.github.edmaputra.edidp.tokens.introspection.TokenIntrospectionValidator;
import io.github.edmaputra.edidp.tokens.revocation.OAuth2TokenRevocationController;
import io.github.edmaputra.edidp.tokens.revocation.TokenRevoker;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Integration tests verifying {@link TokenIntrospectionValidator}, {@link TokenRevoker},
 * and corresponding controller HTTP response mappings.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenRevokerAndIntrospectionIntegrationTests extends AuthServerIntegrationTests {

  @Autowired
  private TokenIntrospectionValidator tokenIntrospectionValidator;

  @Autowired
  private TokenRevoker tokenRevoker;

  @Autowired
  private OAuth2TokenIntrospectionController introspectionController;

  @Autowired
  private OAuth2TokenRevocationController revocationController;

  @Autowired
  private OAuth2AuthorizationService authorizationService;

  @Autowired
  private RegisteredClientRepository registeredClientRepository;

  private String registeredClientId;

  @BeforeEach
  void setUp() {
    RegisteredClient client = registeredClientRepository.findByClientId("demo-client");
    assertThat(client).isNotNull();
    registeredClientId = client.getId();
  }

  private String getAccessToken(String clientId, String clientSecret) throws Exception {
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);

    org.springframework.util.MultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", "read write");

    org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, String>> request =
        new org.springframework.http.HttpEntity<>(form, headers);

    org.springframework.http.ResponseEntity<String> response = restTemplate
        .withBasicAuth(clientId, clientSecret)
        .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
    tools.jackson.databind.JsonNode body = objectMapper.readTree(response.getBody());
    return body.path("access_token").asText();
  }

  @Test
  void tokenIntrospectionValidator_withValidToken_returnsAllClaims() throws Exception {
    // Arrange
    String accessToken = getAccessToken("demo-client", "demo-secret");

    // Act
    Map<String, Object> claims = tokenIntrospectionValidator.introspect(accessToken);

    // Assert
    assertThat(claims.get("active")).isEqualTo(true);
    assertThat(claims.get("sub")).isEqualTo("demo-client");
    assertThat(claims.get("username")).isEqualTo("demo-client");
    assertThat(claims.get("exp")).isNotNull();
    assertThat(claims.get("iat")).isNotNull();
    assertThat(claims.get("jti")).isNotNull();
    assertThat(claims.get("iss")).isNotNull();
  }

  @Test
  void tokenIntrospectionValidator_withInvalidToken_returnsInactive() {
    // Act
    Map<String, Object> claims = tokenIntrospectionValidator.introspect("not-a-real-token");

    // Assert
    assertThat(claims.get("active")).isEqualTo(false);
  }

  @Test
  void tokenRevoker_revokesAccessTokenSuccessfully() throws Exception {
    // Arrange
    String accessToken = getAccessToken("demo-client", "demo-secret");
    OAuth2Authorization authBefore =
        authorizationService.findByToken(accessToken, OAuth2TokenType.ACCESS_TOKEN);
    assertThat(authBefore).isNotNull();
    assertThat(authBefore.getAccessToken().isActive()).isTrue();

    // Act
    tokenRevoker.revokeTokenForClient(accessToken, "access_token", registeredClientId);

    // Assert
    OAuth2Authorization authAfter =
        authorizationService.findByToken(accessToken, OAuth2TokenType.ACCESS_TOKEN);
    assertThat(authAfter).isNotNull();
    assertThat(authAfter.getAccessToken().isActive()).isFalse();

    // Introspection also confirms inactive
    Map<String, Object> claims = tokenIntrospectionValidator.introspect(accessToken);
    assertThat(claims.get("active")).isEqualTo(false);
  }

  @Test
  void tokenRevoker_withUnknownTokenOrDifferentClient_isIdempotent() {
    // Unknown token
    tokenRevoker.revokeTokenForClient("completely-unknown-token", "access_token", registeredClientId);

    // Different client
    tokenRevoker.revokeTokenForClient("some-token", null, "different-client-id");
  }

  @Test
  void introspectionController_statusMappings() {
    String basicAuth = "Basic " + Base64.getEncoder().encodeToString("demo-client:demo-secret".getBytes(StandardCharsets.UTF_8));

    // 1. BAD_REQUEST: missing token
    MockHttpServletRequest request1 = new MockHttpServletRequest();
    request1.addHeader("Authorization", basicAuth);
    ResponseEntity<Map<String, Object>> response1 =
        introspectionController.introspect(null, null, request1);
    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 2. UNAUTHORIZED: bad client credentials
    MockHttpServletRequest request2 = new MockHttpServletRequest();
    request2.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("bad:secret".getBytes(StandardCharsets.UTF_8)));
    ResponseEntity<Map<String, Object>> response2 =
        introspectionController.introspect(null, "some-token", request2);
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // 3. OK: valid basic auth
    MockHttpServletRequest request3 = new MockHttpServletRequest();
    request3.addHeader("Authorization", basicAuth);
    ResponseEntity<Map<String, Object>> response3 =
        introspectionController.introspect(null, "some-token", request3);
    assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void revocationController_statusMappings() {
    String basicAuth = "Basic " + Base64.getEncoder().encodeToString("demo-client:demo-secret".getBytes(StandardCharsets.UTF_8));

    // 1. BAD_REQUEST: missing token
    MockHttpServletRequest request1 = new MockHttpServletRequest();
    request1.addHeader("Authorization", basicAuth);
    ResponseEntity<?> response1 =
        revocationController.revoke(null, null, "access_token", request1);
    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // 2. UNAUTHORIZED: bad client credentials
    MockHttpServletRequest request2 = new MockHttpServletRequest();
    request2.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("bad:secret".getBytes(StandardCharsets.UTF_8)));
    ResponseEntity<?> response2 =
        revocationController.revoke(null, "some-token", "access_token", request2);
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // 3. OK: valid client
    MockHttpServletRequest request3 = new MockHttpServletRequest();
    request3.addHeader("Authorization", basicAuth);
    ResponseEntity<?> response3 =
        revocationController.revoke(null, "some-token", "access_token", request3);
    assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
