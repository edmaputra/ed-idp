package io.github.edmaputra.edidp.ui;

import io.github.edmaputra.edidp.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runner application entrypoint for starting the Identity Provider with UI Add-on bundled.
 * Automatically sets {@code edidp.ui.enabled=true} by default.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@SpringBootApplication(scanBasePackageClasses = {Application.class, UiApplication.class})
public class UiApplication {

  public static void main(String[] args) {
    System.setProperty("edidp.ui.enabled", "true");
    SpringApplication.run(UiApplication.class, args);
  }
}
