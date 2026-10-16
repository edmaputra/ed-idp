package io.github.edmaputra.edidp.ui.controller;

import io.github.edmaputra.edidp.ui.UiProperties;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller providing root and default login URL redirection to Admin Console UI.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Controller
@ConditionalOnProperty(name = "edidp.ui.enabled", havingValue = "true")
public class AdminRootController {

  private final UiProperties uiProperties;

  public AdminRootController(UiProperties uiProperties) {
    this.uiProperties = Objects.requireNonNull(uiProperties, "uiProperties must not be null");
  }

  @GetMapping("/")
  public String root() {
    return "redirect:" + uiProperties.basePath() + "/dashboard";
  }

  @GetMapping("/login")
  public String loginRedirect() {
    return "redirect:" + uiProperties.basePath() + "/login";
  }
}
