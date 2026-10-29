package io.github.edmaputra.edidp.e2e.pages;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selectors.byText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.open;

import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.WebDriverRunner;
import java.util.Objects;

/**
 * Page Object representing Users and IAM identity management in the Admin Console (/admin/users).
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AdminUsersPage {

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

  public AdminUsersPage(String baseUrl) {
    this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
  }

  public AdminUsersPage navigate() {
    open(baseUrl + "/admin/users");
    return this;
  }

  public boolean isDisplayed() {
    return WebDriverRunner.url().contains("/admin/users") && $("#users-table").is(visible);
  }

  public AdminUsersPage createUser(UserRegistration user) {
    $("input[name='username']").setValue(user.username());
    $("input[name='password']").setValue(user.password());
    $("input[name='fullName']").setValue(user.fullName());
    $("input[name='email']").setValue(user.email());
    $("select[name='role']").selectOptionByValue(user.role());
    $("input[name='tenantId']").setValue(user.tenantId());

    $(byText("Create User")).click();
    return this;
  }

  public boolean isUserInTable(String username) {
    return $$("#users-tbody tr").findBy(text(username)).exists();
  }

  public AdminUsersPage toggleUserStatus(String username) {
    SelenideElement row = $$("#users-tbody tr").findBy(text(username));
    row.find("button.btn-outline-warning, button.btn-outline-success")
        .scrollIntoView("{behavior: 'instant', block: 'center'}")
        .click();
    return this;
  }

  public AdminUsersPage clickRefresh() {
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
