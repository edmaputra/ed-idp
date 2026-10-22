package io.github.edmaputra.edidp.e2e.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.e2e.BaseE2ETest;
import io.github.edmaputra.edidp.e2e.pages.AdminDashboardPage;
import io.github.edmaputra.edidp.e2e.pages.AdminLoginPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for Admin Console authentication workflows.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@DisplayName("Admin Console Authentication E2E")
class AdminConsoleLoginE2EIT extends BaseE2ETest {

  @Test
  @DisplayName("Should display login view with username and password fields")
  void shouldDisplayAdminLoginView() {
    AdminLoginPage loginPage = new AdminLoginPage(baseUrl()).navigate();
    assertThat(loginPage.isDisplayed()).isTrue();
  }

  @Test
  @DisplayName("Should reject invalid administrative credentials and render error alert")
  void shouldRejectInvalidCredentials() {
    AdminLoginPage loginPage = new AdminLoginPage(baseUrl()).navigate();
    loginPage.fillUsername("admin@example.com");
    loginPage.fillPassword("wrong-password");
    loginPage.submit();

    assertThat(loginPage.getErrorMessage()).contains("Invalid username or password");
  }

  @Test
  @DisplayName("Should authenticate administrator, display dashboard, and support sign-out")
  void shouldAuthenticateAdministratorAndSignOut() {
    AdminLoginPage loginPage = new AdminLoginPage(baseUrl()).navigate();
    AdminDashboardPage dashboardPage = loginPage.loginAsAdmin("admin@example.com", "admin-password");

    assertThat(dashboardPage.isDisplayed()).isTrue();
    assertThat(dashboardPage.getHeaderTitle()).contains("Overview Dashboard");
    assertThat(dashboardPage.getPageContent()).contains("View all clients");

    AdminLoginPage loggedOutPage = dashboardPage.signOut();
    assertThat(loggedOutPage.getLogoutMessage()).contains("signed out");
  }
}
