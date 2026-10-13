package io.github.edmaputra.edidp.shared;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller serving the post-logout destination page.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
public class LoggedOutController {

  @GetMapping("/logged-out")
  public String loggedOut() {
    return "You have been signed out.";
  }
}
