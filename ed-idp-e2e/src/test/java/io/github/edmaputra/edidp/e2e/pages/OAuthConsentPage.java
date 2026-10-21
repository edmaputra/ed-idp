package io.github.edmaputra.edidp.e2e.pages;

import static com.codeborne.selenide.Condition.checked;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.open;

import com.codeborne.selenide.SelenideElement;
import java.util.Objects;

/**
 * Page Object representing the interactive OAuth 2.1 User Consent screen ({@code /oauth2/authorize-consent}).
 * Encapsulates client metadata verification, scope inspection/toggling, and approve/deny actions.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class OAuthConsentPage {

  private final String baseUrl;

  public OAuthConsentPage(String baseUrl) {
    this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
  }

  public OAuthConsentPage navigate(String clientId, String redirectUri, String scopes, String state) {
    String url = String.format("%s/oauth2/authorize-consent?client_id=%s&redirect_uri=%s&requested_scopes=%s&state=%s",
        baseUrl, clientId, redirectUri, scopes, state);
    open(url);
    return this;
  }

  public boolean isDisplayed() {
    return $("h1").shouldHave(text("Authorization Consent")).is(visible);
  }

  public String getClientName() {
    return $(".client-info span").shouldBe(visible).text().trim();
  }

  public String getPrincipalName() {
    return $$(".client-info span").get(1).shouldBe(visible).text().trim();
  }

  public boolean isScopeChecked(String scope) {
    return $("#scope_" + scope).is(checked);
  }

  public OAuthConsentPage setScope(String scope, boolean shouldBeChecked) {
    SelenideElement checkbox = $("#scope_" + scope);
    if (checkbox.is(checked) != shouldBeChecked) {
      checkbox.click();
    }
    return this;
  }

  public OAuthConsentPage deselectScope(String scope) {
    return setScope(scope, false);
  }

  public void approve() {
    $("button.btn-approve").click();
  }

  public void deny() {
    $("button.btn-deny").click();
  }
}
