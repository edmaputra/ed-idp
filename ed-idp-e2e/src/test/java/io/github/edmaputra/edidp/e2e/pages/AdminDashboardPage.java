package io.github.edmaputra.edidp.e2e.pages;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.title;
import static com.codeborne.selenide.WebDriverConditions.urlStartingWith;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.WebDriverRunner;
import java.util.Objects;

/**
 * Page Object representing the administrative overview dashboard (/admin/dashboard).
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AdminDashboardPage {

  private final String baseUrl;

  public AdminDashboardPage(String baseUrl) {
    this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
  }

  public AdminDashboardPage navigate() {
    open(baseUrl + "/admin/dashboard");
    return this;
  }

  public boolean isDisplayed() {
    return WebDriverRunner.url().contains("/admin/dashboard") && title().contains("Dashboard");
  }

  public String getHeaderTitle() {
    return $("header h1, header h2, .app-topbar h1").shouldBe(visible).text().trim();
  }

  public String getPageContent() {
    return $("body").text();
  }

  public AdminClientsPage goToClients() {
    $(byText("OAuth2 Clients")).click();
    Selenide.webdriver().shouldHave(urlStartingWith(baseUrl + "/admin/clients"));
    return new AdminClientsPage(baseUrl);
  }

  public AdminUsersPage goToUsers() {
    $(byText("Users & IAM")).click();
    Selenide.webdriver().shouldHave(urlStartingWith(baseUrl + "/admin/users"));
    return new AdminUsersPage(baseUrl);
  }

  public AdminLoginPage signOut() {
    $(byText("Sign Out")).click();
    Selenide.webdriver().shouldHave(urlStartingWith(baseUrl + "/admin/login?logout"));
    return new AdminLoginPage(baseUrl);
  }
}
