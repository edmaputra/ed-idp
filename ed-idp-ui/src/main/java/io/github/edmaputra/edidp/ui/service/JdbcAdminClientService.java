package io.github.edmaputra.edidp.ui.service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC and repository-backed implementation of {@link AdminClientService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class JdbcAdminClientService implements AdminClientService {

  private final JdbcTemplate jdbcTemplate;
  private final RegisteredClientRepository registeredClientRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenSettings tokenSettings;

  public JdbcAdminClientService(
      JdbcTemplate jdbcTemplate,
      RegisteredClientRepository registeredClientRepository,
      PasswordEncoder passwordEncoder,
      TokenSettings tokenSettings) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.registeredClientRepository = Objects.requireNonNull(registeredClientRepository, "registeredClientRepository must not be null");
    this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    this.tokenSettings = Objects.requireNonNull(tokenSettings, "tokenSettings must not be null");
  }

  @Override
  public List<AdminClientSummary> listClients() {
    String sql = """
        SELECT id, client_id, client_name, client_authentication_methods,
               authorization_grant_types, scopes, redirect_uris, COALESCE(tenant_id, 'demo') AS tenant_id
        FROM oauth2_registered_client
        ORDER BY client_id ASC
        """;

    return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminClientSummary(
        rs.getString("id"),
        rs.getString("client_id"),
        rs.getString("client_name") != null ? rs.getString("client_name") : rs.getString("client_id"),
        rs.getString("client_authentication_methods"),
        rs.getString("authorization_grant_types"),
        rs.getString("scopes"),
        rs.getString("redirect_uris"),
        rs.getString("tenant_id")
    ));
  }

  @Override
  public long countClients() {
    Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM oauth2_registered_client", Long.class);
    return count != null ? count : 0L;
  }

  @Override
  @Transactional
  public void createClient(CreateClientCommand command) {
    String internalId = UUID.randomUUID().toString();
    RegisteredClient.Builder builder = RegisteredClient.withId(internalId)
        .clientId(command.clientId())
        .clientName(command.clientName())
        .tokenSettings(tokenSettings);

    if (command.clientSecret() != null && !command.clientSecret().isBlank()) {
      builder.clientSecret(passwordEncoder.encode(command.clientSecret()))
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
          .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    } else {
      builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
    }

    // Grant types
    for (String gt : command.grantTypes().split(",")) {
      String trimmed = gt.trim();
      if (!trimmed.isEmpty()) {
        builder.authorizationGrantType(new AuthorizationGrantType(trimmed));
      }
    }

    // Scopes
    for (String s : command.scopes().split(",")) {
      String trimmed = s.trim();
      if (!trimmed.isEmpty()) {
        builder.scope(trimmed);
      }
    }

    // Redirect URIs
    for (String uri : command.redirectUris().split(",")) {
      String trimmed = uri.trim();
      if (!trimmed.isEmpty()) {
        builder.redirectUri(trimmed);
      }
    }

    builder.clientSettings(ClientSettings.builder()
        .requireProofKey(command.requirePkce())
        .build());

    // Save client via repository
    registeredClientRepository.save(builder.build());

    // Explicitly update tenant_id in database
    jdbcTemplate.update(
        "UPDATE oauth2_registered_client SET tenant_id = ? WHERE id = ?",
        command.tenantId(),
        internalId
    );
  }

  @Override
  @Transactional
  public void deleteClient(String clientId, String tenantId) {
    jdbcTemplate.update(
        "DELETE FROM oauth2_registered_client WHERE client_id = ? AND tenant_id = ?",
        clientId,
        tenantId
    );
  }
}
