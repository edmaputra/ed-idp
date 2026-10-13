package io.github.edmaputra.edidp.ui.service;

import java.util.List;

/**
 * Service defining administrative view and write operations for OAuth2 registered clients.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public interface AdminClientService {

  /**
   * Detail summary of a registered client for admin views.
   *
   * @param id internal database ID
   * @param clientId client identifier
   * @param clientName client display name
   * @param clientAuthenticationMethods authentication methods
   * @param authorizationGrantTypes authorization grant types
   * @param scopes configured scopes
   * @param redirectUris registered redirect URIs
   * @param tenantId tenant owning this client
   */
  record AdminClientSummary(
      String id,
      String clientId,
      String clientName,
      String clientAuthenticationMethods,
      String authorizationGrantTypes,
      String scopes,
      String redirectUris,
      String tenantId
  ) {
    public AdminClientSummary {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Client internal ID must not be blank");
      }
      if (clientId == null || clientId.isBlank()) {
        throw new IllegalArgumentException("Client ID must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
    }
  }

  /**
   * Command to create/register a new OAuth2 client.
   *
   * @param clientId unique client identifier
   * @param clientSecret client secret (or null/blank for public clients)
   * @param clientName human-readable client name
   * @param tenantId tenant identifier
   * @param grantTypes comma-separated authorization grant types (e.g. authorization_code,client_credentials)
   * @param scopes comma-separated scopes (e.g. openid,profile,read)
   * @param redirectUris comma-separated redirect URIs
   * @param requirePkce whether PKCE is mandatory
   */
  record CreateClientCommand(
      String clientId,
      String clientSecret,
      String clientName,
      String tenantId,
      String grantTypes,
      String scopes,
      String redirectUris,
      boolean requirePkce
  ) {
    public CreateClientCommand {
      if (clientId == null || clientId.isBlank()) {
        throw new IllegalArgumentException("Client ID must not be blank");
      }
      if (tenantId == null || tenantId.isBlank()) {
        tenantId = "demo";
      }
      if (clientName == null || clientName.isBlank()) {
        clientName = clientId;
      }
      if (grantTypes == null || grantTypes.isBlank()) {
        grantTypes = "authorization_code";
      }
      if (scopes == null || scopes.isBlank()) {
        scopes = "openid,profile";
      }
      if (redirectUris == null) {
        redirectUris = "";
      }
    }
  }

  /**
   * Lists all registered OAuth2 clients across tenants.
   *
   * @return list of client summaries
   */
  List<AdminClientSummary> listClients();

  /**
   * Counts the total number of registered OAuth2 clients.
   *
   * @return client count
   */
  long countClients();

  /**
   * Registers a new OAuth2 client in the repository.
   *
   * @param command client creation command
   */
  void createClient(CreateClientCommand command);

  /**
   * Deletes a registered client by client ID and tenant ID.
   *
   * @param clientId client identifier
   * @param tenantId tenant identifier
   */
  void deleteClient(String clientId, String tenantId);
}
