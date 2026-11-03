package io.github.edmaputra.edidp.e2e.pages;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.title;
import static com.codeborne.selenide.WebDriverConditions.urlStartingWith;

import com.codeborne.selenide.Selenide;
import java.util.Objects;

/**
 * Page Object encapsulating interactions and assertions on the Admin Console login view (/admin/login).
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AdminLoginPage {

  private final String baseUrl;

  public AdminLoginPage(String baseUrl) {
    this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
  }

  public AdminLoginPage navigate() {
    open(baseUrl + "/admin/login");
    return this;
  }

  public boolean isDisplayed() {
    return title().contains("Admin Login") && $("#username").is(visible);
  }

  public AdminLoginPage fillUsername(String username) {
    $("#username").setValue(username);
    return this;
  }

  public AdminLoginPage fillPassword(String password) {
    $("#password").setValue(password);
    return this;
  }

  public void submit() {
    $("button[type='submit']").click();
  }

  public AdminDashboardPage loginAsAdmin(String username, String password) {
    fillUsername(username);
    fillPassword(password);
    submit();
    Selenide.webdriver().shouldHave(urlStartingWith(baseUrl + "/admin/dashboard"));
    return new AdminDashboardPage(baseUrl);
  }

  public String getErrorMessage() {
    Selenide.webdriver().shouldHave(urlStartingWith(baseUrl + "/admin/login?error"));
    return $(".alert-danger, .alert-error").shouldBe(visible).text().trim();
  }

  public String getLogoutMessage() {
    Selenide.webdriver().shouldHave(urlStartingWith(baseUrl + "/admin/login?logout"));
    return $(".alert-success, .alert-info").shouldBe(visible).text().trim();
  }
}
