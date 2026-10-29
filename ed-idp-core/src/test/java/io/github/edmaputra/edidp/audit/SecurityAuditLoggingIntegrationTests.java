package io.github.edmaputra.edidp.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.integration.AuthServerIntegrationTests;
import io.github.edmaputra.edidp.tokens.introspection.IntrospectTokenCommand;
import io.github.edmaputra.edidp.tokens.introspection.IntrospectTokenService;
import io.github.edmaputra.edidp.tokens.revocation.RevokeTokenCommand;
import io.github.edmaputra.edidp.tokens.revocation.RevokeTokenService;
import io.github.edmaputra.edidp.users.ChangePasswordRequest;
import io.github.edmaputra.edidp.users.UpdateProfileRequest;
import io.github.edmaputra.edidp.users.UserSelfService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * End-to-end integration tests verifying structured security audit logging across OAuth 2.1,
 * token lifecycle, and user identity operations.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityAuditLoggingIntegrationTests extends AuthServerIntegrationTests {

  @Autowired
  private AuditEventRepository auditEventRepository;

  @Autowired
  private RevokeTokenService revokeTokenService;

  @Autowired
  private IntrospectTokenService introspectTokenService;

  @Autowired
  private UserSelfService userSelfService;

  @BeforeEach
  void cleanRepository() {
    auditEventRepository.clear();
  }

  @Test
  void tokenIssuanceEmitsTokenIssuedAuditEvent() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", "read");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response = restTemplate
        .withBasicAuth("demo-client", "demo-secret")
        .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<SecurityAuditEvent> events = auditEventRepository.findRecent(10);
    assertThat(events).anySatisfy(event -> {
      assertThat(event.action()).isEqualTo(SecurityAuditAction.TOKEN_ISSUED);
      assertThat(event.status()).isEqualTo(SecurityAuditStatus.SUCCESS);
      assertThat(event.clientIdentifier()).isEqualTo("demo-client");
      assertThat(event.details().get("grant_type")).isEqualTo("client_credentials");
    });
  }

  @Test
  void invalidClientCredentialsEmitTokenFailedAuditEvent() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

    ResponseEntity<String> response = restTemplate
        .withBasicAuth("demo-client", "invalid-secret")
        .postForEntity("/oauth2/token", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    List<SecurityAuditEvent> events = auditEventRepository.findRecent(10);
    assertThat(events).anySatisfy(event -> {
      assertThat(event.action()).isEqualTo(SecurityAuditAction.TOKEN_FAILED);
      assertThat(event.status()).isEqualTo(SecurityAuditStatus.FAILURE);
    });
  }

  @Test
  void tokenRevocationEmitsTokenRevokedAuditEvent() {
    String basicAuth = "Basic " + Base64.getEncoder().encodeToString("demo-client:demo-secret".getBytes(StandardCharsets.UTF_8));
    revokeTokenService.revoke(new RevokeTokenCommand("sample-revocation-token", "access_token", basicAuth));

    List<SecurityAuditEvent> events = auditEventRepository.findRecent(10);
    assertThat(events).anySatisfy(event -> {
      assertThat(event.action()).isEqualTo(SecurityAuditAction.TOKEN_REVOKED);
      assertThat(event.clientIdentifier()).isEqualTo("demo-client");
    });
  }

  @Test
  void tokenIntrospectionEmitsTokenIntrospectedAuditEvent() {
    String basicAuth = "Basic " + Base64.getEncoder().encodeToString("demo-client:demo-secret".getBytes(StandardCharsets.UTF_8));
    introspectTokenService.introspect(new IntrospectTokenCommand("sample-opaque-token", basicAuth));

    List<SecurityAuditEvent> events = auditEventRepository.findRecent(10);
    assertThat(events).anySatisfy(event -> {
      assertThat(event.action()).isEqualTo(SecurityAuditAction.TOKEN_INTROSPECTED);
      assertThat(event.clientIdentifier()).isEqualTo("demo-client");
    });
  }

  @Test
  void userProfileUpdateEmitsUserProfileUpdatedAuditEvent() {
    UpdateProfileRequest updateRequest = new UpdateProfileRequest("Audited Alice", "Security Audit Dept");
    userSelfService.updateMyProfile("demo-user", "demo", updateRequest);

    List<SecurityAuditEvent> events = auditEventRepository.findRecent(10);
    assertThat(events).anySatisfy(event -> {
      assertThat(event.action()).isEqualTo(SecurityAuditAction.USER_PROFILE_UPDATED);
      assertThat(event.principal()).isEqualTo("demo-user");
      assertThat(event.details().get("fullName")).isEqualTo("Audited Alice");
    });
  }

  @Test
  void userPasswordFailureEmitsUserPasswordChangedFailureAuditEvent() {
    ChangePasswordRequest badRequest = new ChangePasswordRequest("wrong-password", "ValidPass#12345", "ValidPass#12345");
    try {
      userSelfService.changeMyPassword("demo-user", "demo", badRequest);
    } catch (IllegalArgumentException ignored) {
    }

    List<SecurityAuditEvent> events = auditEventRepository.findRecent(10);
    assertThat(events).anySatisfy(event -> {
      assertThat(event.action()).isEqualTo(SecurityAuditAction.USER_PASSWORD_CHANGED);
      assertThat(event.status()).isEqualTo(SecurityAuditStatus.FAILURE);
      assertThat(event.principal()).isEqualTo("demo-user");
    });
  }
}
