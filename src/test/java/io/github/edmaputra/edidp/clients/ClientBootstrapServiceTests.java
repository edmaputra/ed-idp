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
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

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
    when(passwordEncoder.encode("demo-secret")).thenReturn("encoded-demo-secret");

    useCase.ensureDefaultClients();

    ArgumentCaptor<RegisteredClient> captor = ArgumentCaptor.forClass(RegisteredClient.class);
    verify(registeredClientRepository, times(2)).save(captor.capture());

    List<RegisteredClient> savedClients = captor.getAllValues();
    assertThat(savedClients).hasSize(2);
    assertThat(savedClients.get(0).getClientId()).isEqualTo("demo-client");
    assertThat(savedClients.get(0).getClientSecret()).isEqualTo("encoded-demo-secret");
    assertThat(savedClients.get(1).getClientId()).isEqualTo("pkce-public-client");
  }

  @Test
  void ensureDefaultClientsSkipsAlreadyProvisionedClients() {
    RegisteredClient existingClient = RegisteredClient.withId("existing-id")
        .clientId("demo-client")
        .clientSecret("encoded-demo-secret")
        .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)
        .build();

    when(registeredClientRepository.findByClientId("demo-client")).thenReturn(existingClient);
    when(registeredClientRepository.findByClientId("pkce-public-client")).thenReturn(existingClient);

    useCase.ensureDefaultClients();

    verifyNoInteractions(passwordEncoder);
    verify(registeredClientRepository).findByClientId("demo-client");
    verify(registeredClientRepository).findByClientId("pkce-public-client");
  }
}
