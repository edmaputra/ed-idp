package io.github.edmaputra.edidp.account.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.edmaputra.edidp.account.dto.ChangePasswordDto;
import io.github.edmaputra.edidp.account.dto.UpdateProfileDto;
import io.github.edmaputra.edidp.account.dto.UserProfileDto;
import io.github.edmaputra.edidp.account.exception.AccountPortalApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests for {@link DefaultAccountPortalApiClient} using {@link MockRestServiceServer}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class DefaultAccountPortalApiClientTests {

  private MockRestServiceServer mockServer;
  private DefaultAccountPortalApiClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    client = new DefaultAccountPortalApiClient(builder, "http://localhost:9000");
  }

  @Test
  void getProfile_demoTenant_success() {
    String jsonResponse = """
        {
          "username": "alice",
          "email": "alice@example.com",
          "fullName": "Alice Smith",
          "department": "Engineering",
          "tenantId": "demo",
          "roles": ["ROLE_USER"],
          "groups": ["DEVELOPERS"]
        }
        """;

    mockServer.expect(requestTo("http://localhost:9000/api/v1/users/me"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andExpect(header("X-Tenant-ID", "demo"))
        .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    UserProfileDto profile = client.getProfile("test-token", "demo");

    assertThat(profile.username()).isEqualTo("alice");
    assertThat(profile.email()).isEqualTo("alice@example.com");
    assertThat(profile.fullName()).isEqualTo("Alice Smith");
    assertThat(profile.department()).isEqualTo("Engineering");
    assertThat(profile.tenantId()).isEqualTo("demo");
    assertThat(profile.roles()).containsExactly("ROLE_USER");
    assertThat(profile.groups()).containsExactly("DEVELOPERS");

    mockServer.verify();
  }

  @Test
  void getProfile_customTenant_routesToTenantPath() {
    String jsonResponse = """
        {
          "username": "bob",
          "email": "bob@acme.com",
          "fullName": "Bob Acme",
          "department": "Sales",
          "tenantId": "acme-corp",
          "roles": [],
          "groups": []
        }
        """;

    mockServer.expect(requestTo("http://localhost:9000/t/acme-corp/api/v1/users/me"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andExpect(header("X-Tenant-ID", "acme-corp"))
        .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    UserProfileDto profile = client.getProfile("test-token", "acme-corp");

    assertThat(profile.username()).isEqualTo("bob");
    assertThat(profile.tenantId()).isEqualTo("acme-corp");

    mockServer.verify();
  }

  @Test
  void updateProfile_success() {
    String jsonResponse = """
        {
          "username": "alice",
          "email": "alice@example.com",
          "fullName": "Alice Updated",
          "department": "Platform",
          "tenantId": "demo",
          "roles": ["ROLE_USER"],
          "groups": []
        }
        """;

    mockServer.expect(requestTo("http://localhost:9000/api/v1/users/me"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

    UserProfileDto result = client.updateProfile("test-token", "demo",
        new UpdateProfileDto("Alice Updated", "Platform"));

    assertThat(result.fullName()).isEqualTo("Alice Updated");
    assertThat(result.department()).isEqualTo("Platform");

    mockServer.verify();
  }

  @Test
  void changePassword_success() {
    mockServer.expect(requestTo("http://localhost:9000/api/v1/users/me/password"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andRespond(withNoContent());

    client.changePassword("test-token", "demo",
        new ChangePasswordDto("oldPass123", "newPass456!", "newPass456!"));

    mockServer.verify();
  }

  @Test
  void changePassword_badRequest_throwsApiException() {
    mockServer.expect(requestTo("http://localhost:9000/api/v1/users/me/password"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withBadRequest().body("Current password is incorrect"));

    assertThatThrownBy(() -> client.changePassword("test-token", "demo",
        new ChangePasswordDto("wrongPass", "newPass456!", "newPass456!")))
        .isInstanceOf(AccountPortalApiException.class)
        .hasMessageContaining("Current password is incorrect")
        .extracting("statusCode").isEqualTo(400);

    mockServer.verify();
  }
}
