package io.github.edmaputra.edidp.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object encapsulating interactions and assertions on the Admin Console login view (/admin/login).
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AdminLoginPage {

  private final Page page;
  private final String baseUrl;

  public AdminLoginPage(Page page, String baseUrl) {
    this.page = page;
    this.baseUrl = baseUrl;
  }

  public AdminLoginPage navigate() {
    page.navigate(baseUrl + "/admin/login");
    return this;
  }

  public boolean isDisplayed() {
    return page.title().contains("Admin Login") && page.locator("#username").isVisible();
  }

  public AdminLoginPage fillUsername(String username) {
    page.locator("#username").fill(username);
    return this;
  }

  public AdminLoginPage fillPassword(String password) {
    page.locator("#password").fill(password);
    return this;
  }

  public void submit() {
    page.locator("button[type='submit']").click();
  }

  public AdminDashboardPage loginAsAdmin(String username, String password) {
    fillUsername(username);
    fillPassword(password);
    submit();
    page.waitForURL("**/admin/dashboard");
    return new AdminDashboardPage(page, baseUrl);
  }

  public String getErrorMessage() {
    page.waitForURL("**/admin/login?error");
    Locator alert = page.locator(".alert-error");
    alert.waitFor();
    return alert.innerText().trim();
  }

  public String getLogoutMessage() {
    page.waitForURL("**/admin/login?logout");
    Locator alert = page.locator(".alert-error");
    alert.waitFor();
    return alert.innerText().trim();
  }
}
