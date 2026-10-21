package io.github.edmaputra.edidp.e2e.pages;

import com.microsoft.playwright.Page;

/**
 * Page Object representing the administrative overview dashboard (/admin/dashboard).
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AdminDashboardPage {

  private final Page page;
  private final String baseUrl;

  public AdminDashboardPage(Page page, String baseUrl) {
    this.page = page;
    this.baseUrl = baseUrl;
  }

  public AdminDashboardPage navigate() {
    page.navigate(baseUrl + "/admin/dashboard");
    return this;
  }

  public boolean isDisplayed() {
    return page.url().contains("/admin/dashboard") && page.title().contains("Dashboard");
  }

  public String getHeaderTitle() {
    return page.locator("header h2").innerText().trim();
  }

  public String getPageContent() {
    return page.content();
  }

  public AdminClientsPage goToClients() {
    page.locator("a.nav-link:has-text('OAuth2 Clients')").click();
    page.waitForURL("**/admin/clients");
    return new AdminClientsPage(page, baseUrl);
  }

  public AdminUsersPage goToUsers() {
    page.locator("a.nav-link:has-text('Users & IAM')").click();
    page.waitForURL("**/admin/users");
    return new AdminUsersPage(page, baseUrl);
  }

  public AdminLoginPage signOut() {
    page.locator("button:has-text('Sign Out')").click();
    page.waitForURL("**/admin/login?logout");
    return new AdminLoginPage(page, baseUrl);
  }
}
