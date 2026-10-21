package io.github.edmaputra.edidp.e2e.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.e2e.BaseE2ETest;
import io.github.edmaputra.edidp.e2e.pages.AdminClientsPage;
import io.github.edmaputra.edidp.e2e.pages.AdminClientsPage.ClientRegistration;
import io.github.edmaputra.edidp.e2e.pages.AdminDashboardPage;
import io.github.edmaputra.edidp.e2e.pages.AdminLoginPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end browser tests for OAuth 2.1 client administration in the Admin Console,
 * validating HTMX dynamic DOM insertion, client registration, and deletion lifecycles.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@DisplayName("Admin Console Client Management E2E")
class AdminClientsE2EIT extends BaseE2ETest {

  private AdminClientsPage clientsPage;

  @BeforeEach
  void setUp() {
    AdminLoginPage loginPage = new AdminLoginPage(baseUrl());
    AdminDashboardPage dashboardPage = loginPage.navigate().loginAsAdmin("admin@example.com", "admin-password");
    this.clientsPage = dashboardPage.goToClients();
  }

  @Test
  @DisplayName("Should dynamically register and delete an OAuth 2.1 client via HTMX")
  void shouldRegisterAndDeleteClientViaHtmx() {
    String testClientId = "e2e-payment-service";

    ClientRegistration client = new ClientRegistration(
        testClientId,
        "payment-secret-123",
        "Payment Processing Service",
        "demo",
        "client_credentials,authorization_code",
        "read,write",
        "http://127.0.0.1:8080/callback"
    );

    // 1. Register new client via HTMX form
    clientsPage.registerClient(client);

    // 2. Validate toast message and presence in live table
    clientsPage.waitForToastContaining("OAuth2 client registered successfully");
    assertThat(clientsPage.isClientInTable(testClientId)).isTrue();

    // 3. Delete client and verify row is removed
    clientsPage.deleteClient(testClientId);
    assertThat(clientsPage.isClientInTable(testClientId)).isFalse();
  }
}
