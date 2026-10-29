package io.github.edmaputra.edidp.clients;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;

/**
 * Bootstraps initial demo OAuth 2.1 client registrations into the repository.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class ClientBootstrapService {

  private static final String DEMO_CLIENT_ID = "demo-client";
  private static final String DEMO_CLIENT_SECRET = "demo-secret";
  private static final String DEMO_CLIENT_REDIRECT_URI =
      "http://127.0.0.1:9000/login/oauth2/code/demo-client";
  private static final String DEMO_CLIENT_POST_LOGOUT_REDIRECT_URI =
      "http://127.0.0.1:9000/logged-out";

  private static final String PKCE_PUBLIC_CLIENT_ID = "pkce-public-client";
  private static final String PKCE_PUBLIC_CLIENT_REDIRECT_URI =
      "http://127.0.0.1:9000/login/oauth2/code/pkce-public-client";
  private static final String PKCE_PUBLIC_CLIENT_POST_LOGOUT_REDIRECT_URI =
      "http://127.0.0.1:9000/logged-out";

  private static final String ACCOUNT_PORTAL_CLIENT_ID = "ed-idp-account-portal";
  private static final String ACCOUNT_PORTAL_REDIRECT_URI =
      "http://127.0.0.1:9000/login/oauth2/code/ed-idp-account-portal";
  private static final String ACCOUNT_PORTAL_REDIRECT_URI_EDIDP =
      "http://127.0.0.1:9000/login/oauth2/code/edidp";
  private static final String ACCOUNT_PORTAL_POST_LOGOUT_REDIRECT_URI =
      "http://127.0.0.1:9000/logged-out";

  private final RegisteredClientRepository registeredClientRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenSettings tokenSettings;
  private final String baseIssuerUri;
  private final String accountPortalBaseUrl;

  @Autowired
  public ClientBootstrapService(
      RegisteredClientRepository registeredClientRepository,
      PasswordEncoder passwordEncoder,
      TokenSettings tokenSettings,
      @Value("${app.issuer-uri:http://localhost:9000}") String baseIssuerUri,
      @Value("${app.client.account-portal.base-url:http://localhost:9001}") String accountPortalBaseUrl) {
    this.registeredClientRepository = registeredClientRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenSettings = tokenSettings;
    this.baseIssuerUri = (baseIssuerUri != null && !baseIssuerUri.isBlank()) ? baseIssuerUri.trim() : "http://localhost:9000";
    this.accountPortalBaseUrl = (accountPortalBaseUrl != null && !accountPortalBaseUrl.isBlank()) ? accountPortalBaseUrl.trim() : "http://localhost:9001";
  }

  public ClientBootstrapService(
      RegisteredClientRepository registeredClientRepository,
      PasswordEncoder passwordEncoder,
      TokenSettings tokenSettings) {
    this(registeredClientRepository, passwordEncoder, tokenSettings, "http://localhost:9000", "http://localhost:9001");
  }

  public void ensureDefaultClients() {
    ensureDemoClient();
    ensurePkcePublicClient();
    ensureAccountPortalClient();
  }

  private void ensureDemoClient() {
    if (registeredClientRepository.findByClientId(DEMO_CLIENT_ID) != null) {
      return;
    }

    registeredClientRepository.save(createDemoClient());
  }

  private void ensurePkcePublicClient() {
    if (registeredClientRepository.findByClientId(PKCE_PUBLIC_CLIENT_ID) != null) {
      return;
    }

    registeredClientRepository.save(createPkcePublicClient());
  }

  private void ensureAccountPortalClient() {
    if (registeredClientRepository.findByClientId(ACCOUNT_PORTAL_CLIENT_ID) != null) {
      return;
    }

    registeredClientRepository.save(createAccountPortalClient());
  }

  private RegisteredClient createDemoClient() {
    String configuredRedirect = trimTrailingSlash(baseIssuerUri) + "/login/oauth2/code/demo-client";
    String configuredPostLogout = trimTrailingSlash(baseIssuerUri) + "/logged-out";

    return RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId(DEMO_CLIENT_ID)
        .clientName("Demo Client")
        .clientSecret(passwordEncoder.encode(DEMO_CLIENT_SECRET))
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri(DEMO_CLIENT_REDIRECT_URI)
        .redirectUri("http://localhost:9000/login/oauth2/code/demo-client")
        .redirectUri(configuredRedirect)
        .postLogoutRedirectUri(DEMO_CLIENT_POST_LOGOUT_REDIRECT_URI)
        .postLogoutRedirectUri("http://localhost:9000/logged-out")
        .postLogoutRedirectUri(configuredPostLogout)
        .scope("openid")
        .scope("profile")
        .scope("email")
        .scope("read")
        .scope("write")
        .scope("introspection")
        .scope("revocation")
        .scope("profile:write")
        .scope("password:write")
        .scope("account:manage")
        .clientSettings(ClientSettings.builder().requireProofKey(false).build())
        .tokenSettings(tokenSettings)
        .build();
  }

  private RegisteredClient createPkcePublicClient() {
    String configuredRedirect = trimTrailingSlash(baseIssuerUri) + "/login/oauth2/code/pkce-public-client";
    String configuredPostLogout = trimTrailingSlash(baseIssuerUri) + "/logged-out";

    return RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId(PKCE_PUBLIC_CLIENT_ID)
        .clientName("PKCE Public Client")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri(PKCE_PUBLIC_CLIENT_REDIRECT_URI)
        .redirectUri("http://localhost:9000/login/oauth2/code/pkce-public-client")
        .redirectUri(configuredRedirect)
        .postLogoutRedirectUri(PKCE_PUBLIC_CLIENT_POST_LOGOUT_REDIRECT_URI)
        .postLogoutRedirectUri("http://localhost:9000/logged-out")
        .postLogoutRedirectUri(configuredPostLogout)
        .scope("openid")
        .scope("profile")
        .scope("email")
        .scope("read")
        .scope("profile:write")
        .scope("password:write")
        .scope("account:manage")
        .clientSettings(ClientSettings.builder().requireProofKey(true).build())
        .tokenSettings(tokenSettings)
        .build();
  }

  private RegisteredClient createAccountPortalClient() {
    String configuredRedirect = trimTrailingSlash(accountPortalBaseUrl) + "/login/oauth2/code/ed-idp-account-portal";
    String configuredRedirectEdidp = trimTrailingSlash(accountPortalBaseUrl) + "/login/oauth2/code/edidp";
    String configuredPostLogout = trimTrailingSlash(accountPortalBaseUrl) + "/logged-out";

    return RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId(ACCOUNT_PORTAL_CLIENT_ID)
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("http://127.0.0.1:9001/login/oauth2/code/ed-idp-account-portal")
        .redirectUri("http://localhost:9001/login/oauth2/code/ed-idp-account-portal")
        .redirectUri("http://127.0.0.1:9001/login/oauth2/code/edidp")
        .redirectUri("http://localhost:9001/login/oauth2/code/edidp")
        .redirectUri(ACCOUNT_PORTAL_REDIRECT_URI)
        .redirectUri(ACCOUNT_PORTAL_REDIRECT_URI_EDIDP)
        .redirectUri("http://localhost:9000/login/oauth2/code/ed-idp-account-portal")
        .redirectUri("http://localhost:9000/login/oauth2/code/edidp")
        .redirectUri(configuredRedirect)
        .redirectUri(configuredRedirectEdidp)
        .postLogoutRedirectUri("http://127.0.0.1:9001/logged-out")
        .postLogoutRedirectUri("http://localhost:9001/logged-out")
        .postLogoutRedirectUri(ACCOUNT_PORTAL_POST_LOGOUT_REDIRECT_URI)
        .postLogoutRedirectUri("http://localhost:9000/logged-out")
        .postLogoutRedirectUri(configuredPostLogout)
        .scope("openid")
        .scope("profile")
        .scope("email")
        .scope("profile:write")
        .scope("password:write")
        .scope("account:manage")
        .clientSettings(ClientSettings.builder().requireProofKey(true).build())
        .tokenSettings(tokenSettings)
        .build();
  }

  private static String trimTrailingSlash(String uri) {
    if (uri == null) {
      return "";
    }
    String trimmed = uri.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
