package io.github.edmaputra.edidp.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/**
 * Unit tests for {@link ClientBootstrapService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
class ClientBootstrapServiceTests {

  @Mock
  private RegisteredClientRepository registeredClientRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  private ClientBootstrapService useCase;

  @BeforeEach
  void setUp() {
    useCase = new ClientBootstrapService(
        registeredClientRepository,
        passwordEncoder,
        TokenSettings.builder().build());
  }

  @Test
  void ensureDefaultClientsSeedsBothClientsWhenTheyDoNotExist() {
    when(registeredClientRepository.findByClientId("demo-client")).thenReturn(null);
    when(registeredClientRepository.findByClientId("pkce-public-client")).thenReturn(null);
    when(registeredClientRepository.findByClientId("ed-idp-account-portal")).thenReturn(null);
    when(passwordEncoder.encode("demo-secret")).thenReturn("encoded-demo-secret");

    useCase.ensureDefaultClients();

    ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
    verify(registeredClientRepository, times(3)).save(captor.capture());

    List<RegisteredClient> savedClients = captor.getAllValues();
    assertThat(savedClients).hasSize(3);
    assertThat(savedClients.get(0).getClientId()).isEqualTo("demo-client");
    assertThat(savedClients.get(0).getClientSecret()).isEqualTo("encoded-demo-secret");
    assertThat(savedClients.get(1).getClientId()).isEqualTo("pkce-public-client");
    assertThat(savedClients.get(2).getClientId()).isEqualTo("ed-idp-account-portal");
  }

  @Test
  void ensureDefaultClientsRegistersConfiguredCustomProductionUrls() {
    ClientBootstrapService customService = new ClientBootstrapService(
        registeredClientRepository,
        passwordEncoder,
        TokenSettings.builder().build(),
        "https://idp.example.com/",
        "https://account.example.com/");

    when(registeredClientRepository.findByClientId("demo-client")).thenReturn(null);
    when(registeredClientRepository.findByClientId("pkce-public-client")).thenReturn(null);
    when(registeredClientRepository.findByClientId("ed-idp-account-portal")).thenReturn(null);
    when(passwordEncoder.encode("demo-secret")).thenReturn("encoded-demo-secret");

    customService.ensureDefaultClients();

    ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
    verify(registeredClientRepository, times(3)).save(captor.capture());

    List<RegisteredClient> savedClients = captor.getAllValues();
    RegisteredClient demoClient = savedClients.get(0);
    assertThat(demoClient.getRedirectUris())
        .contains("https://idp.example.com/login/oauth2/code/demo-client", "http://127.0.0.1:9000/login/oauth2/code/demo-client");
    assertThat(demoClient.getPostLogoutRedirectUris())
        .contains("https://idp.example.com/logged-out", "http://127.0.0.1:9000/logged-out");

    RegisteredClient pkceClient = savedClients.get(1);
    assertThat(pkceClient.getRedirectUris())
        .contains("https://idp.example.com/login/oauth2/code/pkce-public-client");
    assertThat(pkceClient.getPostLogoutRedirectUris())
        .contains("https://idp.example.com/logged-out");

    RegisteredClient portalClient = savedClients.get(2);
    assertThat(portalClient.getRedirectUris())
        .contains("https://account.example.com/login/oauth2/code/ed-idp-account-portal", "http://127.0.0.1:9001/login/oauth2/code/ed-idp-account-portal");
    assertThat(portalClient.getPostLogoutRedirectUris())
        .contains("https://account.example.com/logged-out", "http://127.0.0.1:9001/logged-out");
  }

  @Test
  void ensureDefaultClientsSkipsAlreadyProvisionedClients() {
    RegisteredClient existingClient = RegisteredClient.withId("existing-id")
        .clientId("demo-client")
        .clientSecret("encoded-demo-secret")
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .build();

    when(registeredClientRepository.findByClientId("demo-client")).thenReturn(existingClient);
    when(registeredClientRepository.findByClientId("pkce-public-client")).thenReturn(existingClient);
    when(registeredClientRepository.findByClientId("ed-idp-account-portal")).thenReturn(existingClient);

    useCase.ensureDefaultClients();

    verifyNoInteractions(passwordEncoder);
    verify(registeredClientRepository).findByClientId("demo-client");
    verify(registeredClientRepository).findByClientId("pkce-public-client");
    verify(registeredClientRepository).findByClientId("ed-idp-account-portal");
  }
}
