package io.github.edmaputra.edidp.e2e.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.e2e.BaseE2ETest;
import io.github.edmaputra.edidp.e2e.pages.AdminDashboardPage;
import io.github.edmaputra.edidp.e2e.pages.AdminLoginPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end browser tests for Admin Console authentication lifecycle,
 * covering login page rendering, bad credential handling, successful authentication,
 * and session sign out using Page Object Model.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@DisplayName("Admin Console Authentication E2E")
class AdminConsoleLoginE2EIT extends BaseE2ETest {

  private AdminLoginPage loginPage;

  @BeforeEach
  void setUp() {
    this.loginPage = new AdminLoginPage(page(), baseUrl());
  }

  @Test
  @DisplayName("Should display admin login form with required fields")
  void shouldDisplayLoginForm() {
    loginPage.navigate();

    assertThat(loginPage.isDisplayed()).isTrue();
  }

  @Test
  @DisplayName("Should display error message on invalid credentials")
  void shouldDisplayErrorMessageOnInvalidCredentials() {
    loginPage.navigate()
        .fillUsername("unknown-user@example.com")
        .fillPassword("wrong-password")
        .submit();

    assertThat(loginPage.getErrorMessage()).contains("Invalid username or password.");
  }

  @Test
  @DisplayName("Should successfully authenticate administrator, display dashboard and sign out")
  void shouldAuthenticateAdministratorAndSignOut() {
    loginPage.navigate();

    AdminDashboardPage dashboardPage = loginPage.loginAsAdmin("admin@example.com", "admin-password");

    assertThat(dashboardPage.isDisplayed()).isTrue();
    assertThat(dashboardPage.getHeaderTitle()).contains("Overview Dashboard");
    assertThat(dashboardPage.getPageContent()).contains("View all clients");

    AdminLoginPage loggedOutPage = dashboardPage.signOut();
    assertThat(loggedOutPage.getLogoutMessage()).contains("signed out");
  }
}
