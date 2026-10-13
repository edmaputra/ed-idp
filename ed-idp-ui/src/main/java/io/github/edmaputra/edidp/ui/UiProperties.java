package io.github.edmaputra.edidp.ui;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the admin console UI add-on.
 *
 * @param enabled Whether the admin console UI is enabled. Defaults to false.
 * @param basePath Base path for admin console endpoints. Defaults to /admin.
 * @author edmaputra
 * @since 0.0.1
 */
@ConfigurationProperties(prefix = "edidp.ui")
public record UiProperties(
    boolean enabled,
    String basePath
) {

  public UiProperties {
    if (basePath == null || basePath.isBlank()) {
      basePath = "/admin";
    }
  }

  public UiProperties() {
    this(false, "/admin");
  }
}
