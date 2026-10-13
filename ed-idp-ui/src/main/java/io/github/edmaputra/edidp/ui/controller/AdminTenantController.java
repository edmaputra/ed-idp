package io.github.edmaputra.edidp.ui.controller;

import io.github.edmaputra.edidp.ui.UiProperties;
import io.github.edmaputra.edidp.ui.service.AdminTenantService;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for viewing tenants via HTMX and Thymeleaf.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
@RequestMapping("${edidp.ui.base-path:/admin}/tenants")
public class AdminTenantController {

  private final UiProperties uiProperties;
  private final AdminTenantService adminTenantService;

  public AdminTenantController(UiProperties uiProperties, AdminTenantService adminTenantService) {
    this.uiProperties = Objects.requireNonNull(uiProperties, "uiProperties must not be null");
    this.adminTenantService = Objects.requireNonNull(adminTenantService, "adminTenantService must not be null");
  }

  @ModelAttribute("basePath")
  public String basePath() {
    return uiProperties.basePath();
  }

  @GetMapping
  public String listTenants(Model model) {
    model.addAttribute("tenants", adminTenantService.listTenants());
    return "admin/tenants/list";
  }

  @GetMapping("/table")
  public String tenantsTableFragment(Model model) {
    model.addAttribute("tenants", adminTenantService.listTenants());
    return "admin/tenants/list :: tenantTableBody";
  }
}
