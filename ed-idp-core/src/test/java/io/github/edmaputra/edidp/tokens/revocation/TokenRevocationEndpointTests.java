package io.github.edmaputra.edidp.tokens.revocation;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import io.github.edmaputra.edidp.tenancy.TenantContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.github.edmaputra.edidp.integration.AuthServerIntegrationTests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenRevocationEndpointTests extends AuthServerIntegrationTests {

  @Autowired
  private RegisteredClientRepository registeredClientRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  void revokeValidAccessTokenReturnsOkAndMakesTokenInactive() throws Exception {
    String accessToken = getAccessToken("demo-client", "demo-secret");

    ResponseEntity<String> revocationResponse = revokeToken(accessToken, "access_token");
    assertThat(revocationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> introspectionResponse = introspect(accessToken);
    assertThat(introspectionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode introspectionBody = objectMapper.readTree(introspectionResponse.getBody());
    assertThat(introspectionBody.path("active").asBoolean()).isFalse();
  }

  @Test
  void revokeUnknownTokenStillReturnsOk() {
    ResponseEntity<String> revocationResponse = revokeToken("unknown-token-value", "access_token");
    assertThat(revocationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void revokeWithoutTokenParameterReturnsInvalidRequest() throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("demo-client", "demo-secret")
            .postForEntity("/oauth2/revoke", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("error").asText()).isEqualTo("invalid_request");
  }

  @Test
  void revokeWithInvalidClientSecretReturnsInvalidClient() throws Exception {
    String accessToken = getAccessToken("demo-client", "demo-secret");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", accessToken);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth("demo-client", "wrong-secret")
            .postForEntity("/oauth2/revoke", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.path("error").asText()).isEqualTo("invalid_client");
  }

  @Test
  void tenantPathRevocationFromDifferentTenantDoesNotRevokeDemoToken() throws Exception {
    ensureTenantClient("tenant-b", "tenant-b-revoke-client", "tenant-b-secret", Set.of("revocation", "read"));
    String demoAccessToken = getAccessToken("demo-client", "demo-secret");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth("tenant-b-revoke-client", "tenant-b-secret");

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", demoAccessToken);
    form.add("token_type_hint", "access_token");

    ResponseEntity<String> revokeResponse = restTemplate.postForEntity(
        "/t/{tenant}/oauth2/revoke",
        new HttpEntity<>(form, headers),
        String.class,
        "tenant-b");

    assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> introspectionResponse = introspect(demoAccessToken);
    assertThat(introspectionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode introspectionBody = objectMapper.readTree(introspectionResponse.getBody());
    assertThat(introspectionBody.path("active").asBoolean()).isTrue();
  }

  @Test
  void tenantPathRevocationWithMatchingTenantClientDoesNotRedirectToLogin() throws Exception {
    String demoAccessToken = getAccessToken("demo-client", "demo-secret");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth("demo-client", "demo-secret");

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", demoAccessToken);
    form.add("token_type_hint", "access_token");

    ResponseEntity<String> revokeResponse = restTemplate.postForEntity(
        "/t/{tenant}/oauth2/revoke",
        new HttpEntity<>(form, headers),
        String.class,
        "demo");

    assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void headerTenantRevocationWithMatchingTenantClientDoesNotRedirectToLogin() throws Exception {
    String demoAccessToken = getAccessToken("demo-client", "demo-secret");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth("demo-client", "demo-secret");
    headers.set("X-Tenant-ID", "demo");

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", demoAccessToken);
    form.add("token_type_hint", "access_token");

    ResponseEntity<String> revokeResponse = restTemplate.postForEntity(
        "/oauth2/revoke",
        new HttpEntity<>(form, headers),
        String.class);

    assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void headerTenantRevocationFromDifferentTenantDoesNotRevokeDemoToken() throws Exception {
    ensureTenantClient("tenant-b", "tenant-b-revoke-client", "tenant-b-secret", Set.of("revocation", "read"));
    String demoAccessToken = getAccessToken("demo-client", "demo-secret");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth("tenant-b-revoke-client", "tenant-b-secret");
    headers.set("X-Tenant-ID", "tenant-b");

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", demoAccessToken);
    form.add("token_type_hint", "access_token");

    ResponseEntity<String> revokeResponse = restTemplate.postForEntity(
        "/oauth2/revoke",
        new HttpEntity<>(form, headers),
        String.class);

    assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> introspectionResponse = introspect(demoAccessToken);
    assertThat(introspectionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode introspectionBody = objectMapper.readTree(introspectionResponse.getBody());
    assertThat(introspectionBody.path("active").asBoolean()).isTrue();
  }

  @Test
  void headerTenantRevocationOverridesPathTenantWhenBothArePresent() throws Exception {
    ensureTenantClient("tenant-b", "tenant-b-revoke-client", "tenant-b-secret", Set.of("revocation", "read"));
    String demoAccessToken = getAccessToken("demo-client", "demo-secret");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.setBasicAuth("tenant-b-revoke-client", "tenant-b-secret");
    headers.set("X-Tenant-ID", "tenant-b");

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", demoAccessToken);
    form.add("token_type_hint", "access_token");

    ResponseEntity<String> revokeResponse = restTemplate.postForEntity(
        "/t/{tenant}/oauth2/revoke",
        new HttpEntity<>(form, headers),
        String.class,
        "demo");

    assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    HttpHeaders introspectHeaders = new HttpHeaders();
    introspectHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    MultiValueMap<String, String> introspectForm = new LinkedMultiValueMap<>();
    introspectForm.add("token", demoAccessToken);

    ResponseEntity<String> introspectionResponse = restTemplate
        .withBasicAuth("demo-client", "demo-secret")
        .postForEntity("/oauth2/introspect", new HttpEntity<>(introspectForm, introspectHeaders), String.class);

    assertThat(introspectionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode introspectionBody = objectMapper.readTree(introspectionResponse.getBody());
    assertThat(introspectionBody.path("active").asBoolean()).isTrue();
  }

  private ResponseEntity<String> revokeToken(String token, String tokenTypeHint) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", token);
    if (tokenTypeHint != null && !tokenTypeHint.isBlank()) {
      form.add("token_type_hint", tokenTypeHint);
    }

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    return restTemplate
        .withBasicAuth("demo-client", "demo-secret")
        .postForEntity("/oauth2/revoke", request, String.class);
  }

  private ResponseEntity<String> introspect(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("token", token);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    return restTemplate
        .withBasicAuth("demo-client", "demo-secret")
        .postForEntity("/oauth2/introspect", request, String.class);
  }

  private String getAccessToken(String clientId, String clientSecret) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", "read write");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response =
        restTemplate
            .withBasicAuth(clientId, clientSecret)
            .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = objectMapper.readTree(response.getBody());
    return body.path("access_token").asText();
  }

  private void ensureTenantClient(
      String tenant,
      String clientId,
      String clientSecret,
      Set<String> scopes) {
    TenantContext.runWithTenant(tenant, () -> {
      if (registeredClientRepository.findByClientId(clientId) != null) {
        return;
      }

      RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
          .clientId(clientId)
          .clientSecret(passwordEncoder.encode(clientSecret))
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
          .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
          .clientSettings(ClientSettings.builder().build())
          .tokenSettings(TokenSettings.builder().build());

      for (String scope : scopes) {
        builder.scope(scope);
      }

      registeredClientRepository.save(builder.build());
    });
  }
}
