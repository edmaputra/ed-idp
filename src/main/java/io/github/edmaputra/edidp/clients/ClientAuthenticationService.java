package io.github.edmaputra.edidp.clients;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientAuthenticationService {

  private final RegisteredClientRepository registeredClientRepository;
  private final PasswordEncoder passwordEncoder;

  public ClientAuthenticationResult authenticateBasic(String authorizationHeader) {
    String[] clientCredentials = extractClientCredentials(authorizationHeader);
    if (clientCredentials == null) {
      return ClientAuthenticationResult.failed(null);
    }

    String clientId = clientCredentials[0];
    String clientSecret = clientCredentials[1];

    RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
    if (registeredClient == null || !isClientSecretValid(registeredClient, clientSecret)) {
      return ClientAuthenticationResult.failed(clientId);
    }

    return ClientAuthenticationResult.success(
        clientId,
        registeredClient.getId(),
        registeredClient.getScopes());
  }

  private String[] extractClientCredentials(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Basic ")) {
      return null;
    }

    try {
      String credentials = new String(
          Base64.getDecoder().decode(authHeader.substring(6)),
          StandardCharsets.UTF_8);
      int colonIndex = credentials.indexOf(':');
      if (colonIndex == -1) {
        return null;
      }

      String clientId = credentials.substring(0, colonIndex);
      String clientSecret = credentials.substring(colonIndex + 1);
      return new String[] {clientId, clientSecret};
    } catch (Exception exception) {
      log.debug("Invalid Basic Auth header format", exception);
      return null;
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
