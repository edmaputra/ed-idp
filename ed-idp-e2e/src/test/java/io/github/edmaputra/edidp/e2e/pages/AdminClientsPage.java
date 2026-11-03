package io.github.edmaputra.edidp.e2e.pages;

import static com.codeborne.selenide.Condition.disappear;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.open;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.WebDriverRunner;
import java.util.Objects;

/**
 * Page Object representing OAuth 2.1 client management in the Admin Console (/admin/clients).
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AdminClientsPage {

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

  public AdminClientsPage(String baseUrl) {
    this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
  }

  public AdminClientsPage navigate() {
    open(baseUrl + "/admin/clients");
    return this;
  }

  public boolean isDisplayed() {
    return WebDriverRunner.url().contains("/admin/clients") && $("#clients-table").is(visible);
  }

  public AdminClientsPage registerClient(ClientRegistration client) {
    $("input[name='clientId']").setValue(client.clientId());
    $("input[name='clientSecret']").setValue(client.clientSecret());
    $("input[name='clientName']").setValue(client.clientName());
    $("input[name='tenantId']").setValue(client.tenantId());
    $("input[name='grantTypes']").setValue(client.grantTypes());
    $("input[name='scopes']").setValue(client.scopes());
    $("input[name='redirectUris']").setValue(client.redirectUris());

    $(byText("Register Client")).click();
    return this;
  }

  public boolean isClientInTable(String clientId) {
    return $$("#clients-tbody tr").findBy(text(clientId)).exists();
  }

  public AdminClientsPage deleteClient(String clientId) {
    SelenideElement row = $$("#clients-tbody tr").findBy(text(clientId));
    row.find(byText("Delete")).scrollIntoView("{behavior: 'instant', block: 'center'}").click();
    Selenide.confirm();
    row.should(disappear);
    return this;
  }

  public AdminClientsPage clickRefresh() {
    $(byText("Refresh Table")).click();
    return this;
  }

  public void waitForToastContaining(String text) {
    $("#toast-container .toast").shouldHave(text(text));
  }

  public String getToastMessage() {
    return $("#toast-container .toast").shouldBe(visible).text().trim();
  }
}
