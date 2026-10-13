package io.github.edmaputra.edidp.ui.controller;

import io.github.edmaputra.edidp.ui.UiProperties;
import io.github.edmaputra.edidp.ui.service.AdminClientService;
import io.github.edmaputra.edidp.ui.service.AdminTenantService;
import io.github.edmaputra.edidp.ui.service.AdminUserService;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Main Controller for Admin Console UI navigation, dashboard, and login.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
@RequestMapping("${edidp.ui.base-path:/admin}")
public class AdminDashboardController {

  private final UiProperties uiProperties;
  private final AdminClientService adminClientService;
  private final AdminTenantService adminTenantService;
  private final AdminUserService adminUserService;
  private final io.github.edmaputra.edidp.ui.service.AdminRbacService adminRbacService;

  public AdminDashboardController(
      UiProperties uiProperties,
      AdminClientService adminClientService,
      AdminTenantService adminTenantService,
      AdminUserService adminUserService,
      io.github.edmaputra.edidp.ui.service.AdminRbacService adminRbacService) {
    this.uiProperties = Objects.requireNonNull(uiProperties, "uiProperties must not be null");
    this.adminClientService = Objects.requireNonNull(adminClientService, "adminClientService must not be null");
    this.adminTenantService = Objects.requireNonNull(adminTenantService, "adminTenantService must not be null");
    this.adminUserService = Objects.requireNonNull(adminUserService, "adminUserService must not be null");
    this.adminRbacService = Objects.requireNonNull(adminRbacService, "adminRbacService must not be null");
  }

  @ModelAttribute("basePath")
  public String basePath() {
    return uiProperties.basePath();
  }

  @GetMapping("/login")
  public String login() {
    return "admin/login";
  }

  @GetMapping({"", "/", "/dashboard"})
  public String dashboard(Model model) {
    model.addAttribute("clientCount", adminClientService.countClients());
    model.addAttribute("tenantCount", adminTenantService.countTenants());
    model.addAttribute("userCount", adminUserService.countUsers());
    model.addAttribute("roleCount", adminRbacService.countRoles());
    model.addAttribute("groupCount", adminRbacService.countGroups());
    return "admin/dashboard";
  }
}
