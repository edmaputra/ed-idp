package io.github.edmaputra.edidp.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object representing Users and IAM identity management in the Admin Console (/admin/users).
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AdminUsersPage {

  private final Page page;
  private final String baseUrl;

  /**
   * Data record representing new user creation attributes.
   */
  public record UserRegistration(
      String username,
      String password,
      String fullName,
      String email,
      String role,
      String tenantId) {}

  public AdminUsersPage(Page page, String baseUrl) {
    this.page = page;
    this.baseUrl = baseUrl;
  }

  public AdminUsersPage navigate() {
    page.navigate(baseUrl + "/admin/users");
    return this;
  }

  public boolean isDisplayed() {
    return page.url().contains("/admin/users") && page.locator("#users-table").isVisible();
  }

  public AdminUsersPage createUser(UserRegistration user) {
    page.locator("input[name='username']").fill(user.username());
    page.locator("input[name='password']").fill(user.password());
    page.locator("input[name='fullName']").fill(user.fullName());
    page.locator("input[name='email']").fill(user.email());
    page.locator("select[name='role']").selectOption(user.role());
    page.locator("input[name='tenantId']").fill(user.tenantId());

    page.locator("button:has-text('Create User')").click();
    return this;
  }

  public boolean isUserInTable(String username) {
    return page.locator("#users-tbody tr td", new Page.LocatorOptions().setHasText(username)).count() > 0;
  }

  public AdminUsersPage toggleUserStatus(String username) {
    Locator row = page.locator("#users-tbody tr", new Page.LocatorOptions()
        .setHas(page.locator("td", new Page.LocatorOptions().setHasText(username))));
    row.locator("button:has-text('Suspend'), button:has-text('Activate')").first().click();
    return this;
  }

  public AdminUsersPage clickRefresh() {
    page.locator("button:has-text('Refresh Table')").click();
    return this;
  }

  public void waitForToastContaining(String text) {
    page.locator("#toast-container .toast", new Page.LocatorOptions().setHasText(text)).first().waitFor();
  }

  public String getToastMessage() {
    Locator toast = page.locator("#toast-container .toast").last();
    toast.waitFor();
    return toast.innerText().trim();
  }
}
