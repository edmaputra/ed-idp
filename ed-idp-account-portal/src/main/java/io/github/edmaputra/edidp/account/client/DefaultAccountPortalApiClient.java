package io.github.edmaputra.edidp.account.client;

import io.github.edmaputra.edidp.account.dto.ChangePasswordDto;
import io.github.edmaputra.edidp.account.dto.UpdateProfileDto;
import io.github.edmaputra.edidp.account.dto.UserProfileDto;
import io.github.edmaputra.edidp.account.exception.AccountPortalApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Default implementation of {@link AccountPortalApiClient} communicating with ed-idp Resource Server.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Slf4j
@Service
public class DefaultAccountPortalApiClient implements AccountPortalApiClient {

  private final RestClient restClient;

  public DefaultAccountPortalApiClient(
      RestClient.Builder restClientBuilder,
      @Value("${edidp.auth-server-url:http://localhost:9000}") String authServerUrl) {
    this.restClient = restClientBuilder
        .baseUrl(authServerUrl)
        .build();
  }

  @Override
  public UserProfileDto getProfile(String accessToken, String tenantId) {
    String resolvedPath = resolvePath(tenantId, "");
    log.debug("Fetching user profile from [{}] for tenant [{}]", resolvedPath, tenantId);

    return restClient.get()
        .uri(resolvedPath)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header("X-Tenant-ID", resolveTenantHeader(tenantId))
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
        .body(UserProfileDto.class);
  }

  @Override
  public UserProfileDto updateProfile(String accessToken, String tenantId, UpdateProfileDto request) {
    String resolvedPath = resolvePath(tenantId, "");
    log.debug("Updating user profile at [{}] for tenant [{}]", resolvedPath, tenantId);

    return restClient.put()
        .uri(resolvedPath)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header("X-Tenant-ID", resolveTenantHeader(tenantId))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
        .body(UserProfileDto.class);
  }

  @Override
  public void changePassword(String accessToken, String tenantId, ChangePasswordDto request) {
    String resolvedPath = resolvePath(tenantId, "/password");
    log.debug("Changing user password at [{}] for tenant [{}]", resolvedPath, tenantId);

    restClient.post()
        .uri(resolvedPath)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header("X-Tenant-ID", resolveTenantHeader(tenantId))
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .onStatus(HttpStatusCode::isError, this::handleErrorResponse)
        .toBodilessEntity();
  }

  private String resolvePath(String tenantId, String subPath) {
    String basePath = "/api/v1/users/me";
    if (tenantId != null && !tenantId.isBlank() && !"demo".equalsIgnoreCase(tenantId.trim())) {
      basePath = "/t/" + tenantId.trim() + "/api/v1/users/me";
    }
    return basePath + subPath;
  }

  private String resolveTenantHeader(String tenantId) {
    return (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "demo";
  }

  private void handleErrorResponse(org.springframework.http.HttpRequest request, ClientHttpResponse response) throws IOException {
    int statusCode = response.getStatusCode().value();
    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    log.warn("API request failed with status [{}]: {}", statusCode, body);
    throw new AccountPortalApiException(statusCode, body.isBlank() ? "HTTP error " + statusCode : body);
  }
}
