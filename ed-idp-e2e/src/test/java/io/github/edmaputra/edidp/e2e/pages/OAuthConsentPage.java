package io.github.edmaputra.edidp.e2e.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.Objects;

/**
 * Page Object representing the interactive OAuth 2.1 User Consent screen ({@code /oauth2/authorize-consent}).
 * Encapsulates client metadata verification, scope inspection/toggling, and approve/deny actions.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class OAuthConsentPage {

  private final Page page;
  private final String baseUrl;

  public OAuthConsentPage(Page page, String baseUrl) {
    this.page = Objects.requireNonNull(page, "page must not be null");
    this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
  }

  /**
   * Navigates directly to the consent screen with the provided query parameters.
   *
   * @param clientId client identifier
   * @param redirectUri registered callback URI
   * @param scopes space-delimited requested scopes
   * @param state authorization state
   * @return this page object
   */
  public OAuthConsentPage navigate(String clientId, String redirectUri, String scopes, String state) {
    String url = String.format("%s/oauth2/authorize-consent?client_id=%s&redirect_uri=%s&requested_scopes=%s&state=%s",
        baseUrl, clientId, redirectUri, scopes, state);
    page.navigate(url);
    return this;
  }

  /**
   * Checks whether the consent container header is visible.
   *
   * @return {@code true} if visible
   */
  public boolean isDisplayed() {
    return page.locator("h1:has-text('Authorization Consent')").isVisible();
  }

  /**
   * Retrieves the client name displayed in the client information card.
   *
   * @return displayed client name
   */
  public String getClientName() {
    return page.locator(".client-info span").first().innerText().trim();
  }

  /**
   * Retrieves the principal (user) name displayed in the consent screen.
   *
   * @return displayed user principal
   */
  public String getPrincipalName() {
    return page.locator(".client-info span").nth(1).innerText().trim();
  }

  /**
   * Checks whether a specific scope checkbox is present and checked.
   *
   * @param scope scope identifier (e.g. "openid", "profile")
   * @return {@code true} if checked
   */
  public boolean isScopeChecked(String scope) {
    return page.locator("#scope_" + scope).isChecked();
  }

  /**
   * Sets the checked state of a specific scope checkbox.
   *
   * @param scope scope identifier
   * @param checked target state
   * @return this page object
   */
  public OAuthConsentPage setScope(String scope, boolean checked) {
    Locator checkbox = page.locator("#scope_" + scope);
    checkbox.setChecked(checked);
    return this;
  }

  /**
   * Clicks the Approve button to authorize the requested (or checked) scopes.
   */
  public void approve() {
    page.locator("button.btn-approve").click();
  }

  /**
   * Clicks the Deny button to reject authorization.
   */
  public void deny() {
    page.locator("button.btn-deny").click();
  }
}
