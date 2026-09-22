package io.github.edmaputra.edidp.consent;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.springframework.web.util.UriComponentsBuilder;

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
      @RequestParam(value = "requested_scopes", required = false) String requestedScopes,
      @RequestParam(value = "scope", required = false) String scope,
      @RequestParam("redirect_uri") String redirectUri,
      @RequestParam(value = "state", required = false) String state,
      Authentication authentication,
      Model model) {

    String principalName = authentication.getName();
    String rawScopes = requestedScopes != null && !requestedScopes.isBlank()
        ? requestedScopes
        : (scope != null ? scope : "");
    Set<String> requestedScopesSet = Arrays.stream(rawScopes.split("\\s+"))
        .filter(s -> !s.isBlank())
        .collect(Collectors.toSet());

    RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
    if (registeredClient == null) {
      log.warn("Consent request for unknown client: {}", clientId);
      model.addAttribute("error", "Unknown client");
      return "consent-error";
    }

    String registeredClientId = registeredClient.getId();

    if (!requestedScopesSet.isEmpty()) {
      CheckConsentCommand command = new CheckConsentCommand(principalName, registeredClientId, requestedScopesSet);
      ConsentDecisionResult consentResult = authorizationConsentService.checkConsent(command);

      if (!consentResult.consentRequired()) {
        log.info(
            "User {} has previously authorized client {} for scopes",
            principalName,
            clientId);
        UriComponentsBuilder redirectBuilder = UriComponentsBuilder.fromPath("/oauth2/authorize")
            .queryParam("client_id", clientId)
            .queryParam("response_type", "code")
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", String.join(" ", requestedScopesSet));
        if (state != null && !state.isBlank()) {
          redirectBuilder.queryParam("state", state);
        }
        return "redirect:" + redirectBuilder.encode().build().toUriString();
      }
    }

    model.addAttribute("clientId", clientId);
    model.addAttribute("clientName", registeredClient.getClientName() != null
        ? registeredClient.getClientName()
        : clientId);
    model.addAttribute("requestedScopes", requestedScopesSet);
    model.addAttribute("redirectUri", redirectUri);
    model.addAttribute("state", state != null ? state : "");
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
      @RequestParam(value = "requested_scopes", required = false) String requestedScopes,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(name = "scope", required = false) String[] approvedScopes,
      @RequestParam(name = "action", required = false, defaultValue = "approve") String action,
      Authentication authentication) {

    if ("deny".equalsIgnoreCase(action)) {
      log.info("User {} denied consent for client {}", authentication.getName(), clientId);
      UriComponentsBuilder denyBuilder = UriComponentsBuilder.fromUriString(redirectUri)
          .queryParam("error", "access_denied")
          .queryParam("error_description", "The user denied the request");
      if (state != null && !state.isBlank()) {
        denyBuilder.queryParam("state", state);
      }
      return "redirect:" + denyBuilder.build().toUriString();
    }

    String principalName = authentication.getName();
    RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
    if (registeredClient == null) {
      log.warn("Consent approval for unknown client: {}", clientId);
      return "consent-error";
    }

    String registeredClientId = registeredClient.getId();

    Set<String> approvedSet = approvedScopes != null && approvedScopes.length > 0
        ? Arrays.stream(approvedScopes).filter(s -> !s.isBlank()).collect(Collectors.toSet())
        : Set.of();

    if (approvedSet.isEmpty()) {
      log.info("User {} approved no scopes for client {}", principalName, clientId);
      UriComponentsBuilder denyBuilder = UriComponentsBuilder.fromUriString(redirectUri)
          .queryParam("error", "access_denied")
          .queryParam("error_description", "The user did not approve any scopes");
      if (state != null && !state.isBlank()) {
        denyBuilder.queryParam("state", state);
      }
      return "redirect:" + denyBuilder.build().toUriString();
    }

    CheckConsentCommand command = new CheckConsentCommand(principalName, registeredClientId, approvedSet);
    authorizationConsentService.approveConsent(command);

    log.info(
        "User {} approved client {} for scopes: {}",
        principalName,
        clientId,
        approvedSet);

    UriComponentsBuilder redirectBuilder = UriComponentsBuilder.fromPath("/oauth2/authorize")
        .queryParam("client_id", clientId)
        .queryParam("response_type", "code")
        .queryParam("redirect_uri", redirectUri)
        .queryParam("scope", String.join(" ", approvedSet))
        .queryParam("consent_approved", "true");
    if (state != null && !state.isBlank()) {
      redirectBuilder.queryParam("state", state);
    }

    return "redirect:" + redirectBuilder.encode().build().toUriString();
  }
}
