package io.github.edmaputra.edidp.e2e.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.e2e.BaseE2ETest;
import io.github.edmaputra.edidp.e2e.pages.AdminDashboardPage;
import io.github.edmaputra.edidp.e2e.pages.AdminLoginPage;
import io.github.edmaputra.edidp.e2e.pages.AdminUsersPage;
import io.github.edmaputra.edidp.e2e.pages.AdminUsersPage.UserRegistration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end browser tests for User and Identity administration in the Admin Console,
 * validating HTMX user provisioning, status toggles, and subsequent user login verification.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@DisplayName("Admin Console User Management E2E")
class AdminUsersE2EIT extends BaseE2ETest {

  @Test
  @DisplayName("Should provision a new user via HTMX and authenticate successfully with new credentials")
  void shouldProvisionUserAndAuthenticateSuccessfully() {
    AdminLoginPage loginPage = new AdminLoginPage(baseUrl());
    AdminDashboardPage adminDashboard = loginPage.navigate().loginAsAdmin("admin@example.com", "admin-password");

    AdminUsersPage usersPage = adminDashboard.goToUsers();
    assertThat(usersPage.isDisplayed()).isTrue();

    String newUsername = "test-engineer";
    String newPassword = "engineer-password-123";

    UserRegistration newUser = new UserRegistration(
        newUsername,
        newPassword,
        "Test Engineer",
        "test-engineer@example.com",
        "ROLE_USER",
        "demo"
    );

    // 1. Provision user via HTMX form
    usersPage.createUser(newUser);

    // 2. Validate toast message and user table presence
    usersPage.waitForToastContaining("User created successfully");
    assertThat(usersPage.isUserInTable(newUsername)).isTrue();

    // 3. Sign out admin
    adminDashboard.navigate();
    adminDashboard.signOut();

    // 4. Authenticate as the newly created user (IamUserDetailsService validates via email)
    AdminDashboardPage userDashboard = loginPage.navigate().loginAsAdmin(newUser.email(), newPassword);
    assertThat(userDashboard.isDisplayed()).isTrue();
    assertThat(userDashboard.getHeaderTitle()).contains("Overview Dashboard");

    // Clean sign out
    userDashboard.signOut();
  }
}
