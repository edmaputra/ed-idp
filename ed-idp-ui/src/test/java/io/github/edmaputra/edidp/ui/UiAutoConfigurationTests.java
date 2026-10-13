package io.github.edmaputra.edidp.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.edmaputra.edidp.ui.controller.AdminDashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests verifying conditional auto-configuration for the Admin Console UI add-on.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class UiAutoConfigurationTests {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(UiAutoConfiguration.class));

  @Test
  void uiDisabledByDefault_beansNotLoaded() {
    contextRunner.run(context -> {
      assertThat(context).doesNotHaveBean(UiAutoConfiguration.class);
      assertThat(context).doesNotHaveBean(AdminDashboardController.class);
    });
  }

  @Test
  void uiExplicitlyDisabled_beansNotLoaded() {
    contextRunner
        .withPropertyValues("edidp.ui.enabled=false")
        .run(context -> {
          assertThat(context).doesNotHaveBean(UiAutoConfiguration.class);
          assertThat(context).doesNotHaveBean(AdminDashboardController.class);
        });
  }
}
