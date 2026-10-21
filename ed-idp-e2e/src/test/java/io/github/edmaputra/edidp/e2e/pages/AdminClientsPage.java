package io.github.edmaputra.edidp.e2e.pages;

import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Page Object representing OAuth 2.1 client management in the Admin Console (/admin/clients).
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AdminClientsPage {

  private final Page page;
  private final String baseUrl;

  /**
   * Data record representing new client registration parameters.
   */
  public record ClientRegistration(
      String clientId,
      String clientSecret,
      String clientName,
      String tenantId,
      String grantTypes,
      String scopes,
      String redirectUris) {}

  public AdminClientsPage(Page page, String baseUrl) {
    this.page = page;
    this.baseUrl = baseUrl;
  }

  public AdminClientsPage navigate() {
    page.navigate(baseUrl + "/admin/clients");
    return this;
  }

  public boolean isDisplayed() {
    return page.url().contains("/admin/clients") && page.locator("#clients-table").isVisible();
  }

  public AdminClientsPage registerClient(ClientRegistration client) {
    page.locator("input[name='clientId']").fill(client.clientId());
    page.locator("input[name='clientSecret']").fill(client.clientSecret());
    page.locator("input[name='clientName']").fill(client.clientName());
    page.locator("input[name='tenantId']").fill(client.tenantId());
    page.locator("input[name='grantTypes']").fill(client.grantTypes());
    page.locator("input[name='scopes']").fill(client.scopes());
    page.locator("input[name='redirectUris']").fill(client.redirectUris());

    page.locator("button:has-text('Register Client')").click();
    return this;
  }

  public boolean isClientInTable(String clientId) {
    return page.locator("#clients-tbody tr td", new Page.LocatorOptions().setHasText(clientId)).count() > 0;
  }

  public AdminClientsPage deleteClient(String clientId) {
    page.onceDialog(Dialog::accept);

    Locator row = page.locator("#clients-tbody tr", new Page.LocatorOptions()
        .setHas(page.locator("td", new Page.LocatorOptions().setHasText(clientId))));
    row.locator("button:has-text('Delete')").click();
    row.waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.DETACHED));
    return this;
  }

  public AdminClientsPage clickRefresh() {
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
