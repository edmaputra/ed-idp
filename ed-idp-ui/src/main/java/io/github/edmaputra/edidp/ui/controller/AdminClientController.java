package io.github.edmaputra.edidp.ui.controller;

import io.github.edmaputra.edidp.ui.UiProperties;
import io.github.edmaputra.edidp.ui.service.AdminClientService;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for managing and viewing OAuth 2.1 registered clients via HTMX and Thymeleaf.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
@RequestMapping("${edidp.ui.base-path:/admin}/clients")
public class AdminClientController {

  private final UiProperties uiProperties;
  private final AdminClientService adminClientService;

  public AdminClientController(UiProperties uiProperties, AdminClientService adminClientService) {
    this.uiProperties = Objects.requireNonNull(uiProperties, "uiProperties must not be null");
    this.adminClientService = Objects.requireNonNull(adminClientService, "adminClientService must not be null");
  }

  @ModelAttribute("basePath")
  public String basePath() {
    return uiProperties.basePath();
  }

  @GetMapping
  public String listClients(Model model) {
    model.addAttribute("clients", adminClientService.listClients());
    return "admin/clients/list";
  }

  @GetMapping("/table")
  public String clientsTableFragment(Model model) {
    model.addAttribute("clients", adminClientService.listClients());
    return "admin/clients/list :: clientTableBody";
  }

  @org.springframework.web.bind.annotation.PostMapping
  public String createClient(
      @org.springframework.web.bind.annotation.RequestParam String clientId,
      @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "") String clientSecret,
      @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "") String clientName,
      @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "demo") String tenantId,
      @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "authorization_code") String grantTypes,
      @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "openid,profile") String scopes,
      @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "") String redirectUris,
      @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "false") boolean requirePkce,
      Model model) {

    adminClientService.createClient(new AdminClientService.CreateClientCommand(
        clientId, clientSecret, clientName, tenantId, grantTypes, scopes, redirectUris, requirePkce
    ));
    model.addAttribute("clients", adminClientService.listClients());
    return "admin/clients/list :: clientTableBody";
  }

  @org.springframework.web.bind.annotation.PostMapping("/{clientId}/delete")
  public String deleteClient(
      @org.springframework.web.bind.annotation.PathVariable String clientId,
      @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "demo") String tenantId,
      Model model) {

    adminClientService.deleteClient(clientId, tenantId);
    model.addAttribute("clients", adminClientService.listClients());
    return "admin/clients/list :: clientTableBody";
  }
}
