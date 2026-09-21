package io.github.edmaputra.edidp.consent;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * MVC controller managing user consent UI rendering and decision processing.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
@RequestMapping("/oauth2")
@Slf4j
@RequiredArgsConstructor
public class OAuth2AuthorizationConsentController {

  private final AuthorizationConsentService authorizationConsentService;
  private final RegisteredClientRepository registeredClientRepository;

  @GetMapping("/authorize-consent")
  public String consentForm(
      @RequestParam("client_id") String clientId,
      @RequestParam("requested_scopes") String requestedScopes,
      @RequestParam("redirect_uri") String redirectUri,
      @RequestParam("state") String state,
      Authentication authentication,
      Model model) {

    String principalName = authentication.getName();

    Set<String> requestedScopesSet = Set.of(requestedScopes.split("\\s+"));

    CheckConsentCommand command = new CheckConsentCommand(principalName, clientId, requestedScopesSet);
    ConsentDecisionResult consentResult = authorizationConsentService.checkConsent(command);

    if (!consentResult.consentRequired()) {
      log.info(
          "User {} has previously authorized client {} for scopes",
          principalName,
          clientId);
      return "redirect:/oauth2/authorize?client_id=" + clientId
          + "&response_type=code"
          + "&redirect_uri=" + redirectUri
          + "&scope=" + requestedScopes.replace(" ", "+")
          + "&state=" + state;
    }

    RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
    if (registeredClient == null) {
      log.warn("Consent request for unknown client: {}", clientId);
      model.addAttribute("error", "Unknown client");
      return "consent-error";
    }

    model.addAttribute("clientId", clientId);
    model.addAttribute("clientName", registeredClient.getClientName() != null
        ? registeredClient.getClientName()
        : clientId);
    model.addAttribute("requestedScopes", requestedScopesSet);
    model.addAttribute("redirectUri", redirectUri);
    model.addAttribute("state", state);
    model.addAttribute("principalName", principalName);

    log.info(
        "Displaying consent form for user {} to authorize client {} for scopes: {}",
        principalName,
        clientId,
        requestedScopesSet);

    return "authorize-consent";
  }

  @PostMapping("/authorize-consent")
  public String approveConsent(
      @RequestParam("client_id") String clientId,
      @RequestParam("redirect_uri") String redirectUri,
      @RequestParam("requested_scopes") String requestedScopes,
      @RequestParam("state") String state,
      @RequestParam(name = "scope", required = false) String[] approvedScopes,
      Authentication authentication) {

    String principalName = authentication.getName();
    Set<String> requestedScopesSet = Set.of(requestedScopes.split("\\s+"));

    CheckConsentCommand command = new CheckConsentCommand(principalName, clientId, requestedScopesSet);
    authorizationConsentService.approveConsent(command);

    log.info(
        "User {} approved client {} for scopes: {}",
        principalName,
        clientId,
        requestedScopesSet);

    return "redirect:/oauth2/authorize?client_id=" + clientId
        + "&response_type=code"
        + "&redirect_uri=" + redirectUri
        + "&scope=" + requestedScopes.replace(" ", "+")
        + "&state=" + state
        + "&consent_approved=true";
  }
}
