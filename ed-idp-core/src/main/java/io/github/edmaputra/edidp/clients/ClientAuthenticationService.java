package io.github.edmaputra.edidp.clients;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;

/**
 * Service for authenticating client credentials supplied via HTTP Basic Auth headers.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClientAuthenticationService {

  private final RegisteredClientRepository registeredClientRepository;
  private final PasswordEncoder passwordEncoder;

  /**
   * Internal record representing parsed HTTP Basic client credentials.
   *
   * @param clientId     client identifier
   * @param clientSecret client secret
   * @author edmaputra
   * @since 0.0.1
   */
  public record ClientCredentials(String clientId, String clientSecret) {
    public ClientCredentials {
      if (clientId == null || clientId.isBlank()) {
        throw new IllegalArgumentException("clientId cannot be null or blank");
      }
    }
  }

  public ClientAuthenticationResult authenticateBasic(String authorizationHeader) {
    Optional<ClientCredentials> clientCredentials = extractClientCredentials(authorizationHeader);
    if (clientCredentials.isEmpty()) {
      return ClientAuthenticationResult.failed(null);
    }

    ClientCredentials credentials = clientCredentials.get();
    String clientId = credentials.clientId();
    String clientSecret = credentials.clientSecret();

    RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
    if (registeredClient == null || !isClientSecretValid(registeredClient, clientSecret)) {
      return ClientAuthenticationResult.failed(clientId);
    }

    return ClientAuthenticationResult.success(
        clientId,
        registeredClient.getId(),
        registeredClient.getScopes());
  }

  private Optional<ClientCredentials> extractClientCredentials(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Basic ")) {
      return Optional.empty();
    }

    try {
      String credentials = new String(
          Base64.getDecoder().decode(authHeader.substring(6)),
          StandardCharsets.UTF_8);
      int colonIndex = credentials.indexOf(':');
      if (colonIndex == -1) {
        return Optional.empty();
      }

      String clientId = credentials.substring(0, colonIndex);
      String clientSecret = credentials.substring(colonIndex + 1);
      return Optional.of(new ClientCredentials(clientId, clientSecret));
    } catch (Exception exception) {
      log.debug("Invalid Basic Auth header format", exception);
      return Optional.empty();
    }
  }

  private boolean isClientSecretValid(RegisteredClient registeredClient, String providedSecret) {
    String registeredSecret = registeredClient.getClientSecret();
    if (registeredSecret == null) {
      return providedSecret == null || providedSecret.isEmpty();
    }

    return passwordEncoder.matches(providedSecret, registeredSecret);
  }
}
