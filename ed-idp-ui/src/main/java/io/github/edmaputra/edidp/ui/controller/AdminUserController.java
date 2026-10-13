package io.github.edmaputra.edidp.ui.controller;

import io.github.edmaputra.edidp.ui.UiProperties;
import io.github.edmaputra.edidp.ui.service.AdminRbacService;
import io.github.edmaputra.edidp.ui.service.AdminUserService;
import io.github.edmaputra.edidp.ui.service.AdminUserService.CreateUserCommand;
import io.github.edmaputra.edidp.ui.service.AdminUserService.UpdateUserCommand;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for IAM Users management with Thymeleaf views and HTMX interactions.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
@RequestMapping("${edidp.ui.base-path:/admin}/users")
public class AdminUserController {

  private final UiProperties uiProperties;
  private final AdminUserService adminUserService;
  private final AdminRbacService adminRbacService;

  public AdminUserController(
      UiProperties uiProperties,
      AdminUserService adminUserService,
      AdminRbacService adminRbacService) {
    this.uiProperties = Objects.requireNonNull(uiProperties, "uiProperties must not be null");
    this.adminUserService = Objects.requireNonNull(adminUserService, "adminUserService must not be null");
    this.adminRbacService = Objects.requireNonNull(adminRbacService, "adminRbacService must not be null");
  }

  @ModelAttribute("basePath")
  public String basePath() {
    return uiProperties.basePath();
  }

  @GetMapping
  public String listUsers(Model model) {
    model.addAttribute("users", adminUserService.listUsers());
    return "admin/users/list";
  }

  @GetMapping("/table")
  public String usersTableFragment(Model model) {
    model.addAttribute("users", adminUserService.listUsers());
    return "admin/users/list :: userTableBody";
  }

  @PostMapping
  public String createUser(
      @RequestParam String username,
      @RequestParam String password,
      @RequestParam(required = false, defaultValue = "") String fullName,
      @RequestParam(required = false, defaultValue = "") String email,
      @RequestParam(required = false, defaultValue = "general") String department,
      @RequestParam(required = false, defaultValue = "demo") String tenantId,
      @RequestParam(required = false, defaultValue = "ROLE_USER") String role,
      Model model) {

    adminUserService.createUser(new CreateUserCommand(
        username, password, fullName, email, department, tenantId, role
    ));
    model.addAttribute("users", adminUserService.listUsers());
    return "admin/users/list :: userTableBody";
  }

  @PostMapping("/{username}/toggle-status")
  public String toggleStatus(
      @PathVariable String username,
      @RequestParam boolean enabled,
      Model model) {

    adminUserService.setUserEnabled(username, !enabled);
    model.addAttribute("users", adminUserService.listUsers());
    return "admin/users/list :: userTableBody";
  }

  @GetMapping("/{username}/edit")
  public String editUserModal(@PathVariable String username, Model model) {
    var userOpt = adminUserService.getUser(username);
    if (userOpt.isEmpty()) {
      return "redirect:" + uiProperties.basePath() + "/users";
    }
    model.addAttribute("user", userOpt.get());
    model.addAttribute("availableRoles", adminRbacService.listRoles());
    model.addAttribute("availableGroups", adminRbacService.listGroups());
    return "admin/users/edit-modal :: editUserModal";
  }

  @PostMapping("/{username}")
  public String updateUser(
      @PathVariable String username,
      @RequestParam(required = false, defaultValue = "") String fullName,
      @RequestParam(required = false, defaultValue = "") String email,
      @RequestParam(required = false, defaultValue = "general") String department,
      @RequestParam(required = false, defaultValue = "demo") String tenantId,
      @RequestParam(required = false, defaultValue = "false") boolean enabled,
      @RequestParam(required = false) List<String> roles,
      @RequestParam(required = false) List<String> groups,
      Model model) {

    adminUserService.updateUser(new UpdateUserCommand(
        username,
        fullName,
        email,
        department,
        tenantId,
        enabled,
        roles != null ? roles : List.of(),
        groups != null ? groups : List.of()
    ));

    model.addAttribute("users", adminUserService.listUsers());
    return "admin/users/list :: userTableBody";
  }
}
