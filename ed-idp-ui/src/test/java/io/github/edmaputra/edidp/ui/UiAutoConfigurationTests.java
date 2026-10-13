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

  @Test
  void uiProperties_defaultAndCustomValues() {
    UiProperties defaultProps = new UiProperties();
    assertThat(defaultProps.enabled()).isFalse();
    assertThat(defaultProps.basePath()).isEqualTo("/admin");

    UiProperties blankProps = new UiProperties(true, "   ");
    assertThat(blankProps.basePath()).isEqualTo("/admin");

    UiProperties nullProps = new UiProperties(true, null);
    assertThat(nullProps.basePath()).isEqualTo("/admin");

    UiProperties customProps = new UiProperties(true, "/custom-admin");
    assertThat(customProps.basePath()).isEqualTo("/custom-admin");
  }

  @Test
  void uiSecurityConfig_createsSecurityFilterChain_withTrailingSlashAndWithout() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class,
            org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration.class,
            io.github.edmaputra.edidp.ui.security.UiSecurityConfig.class
        ))
        .withUserConfiguration(TestSecurityConfig.class)
        .withBean(UiProperties.class, () -> new UiProperties(true, "/admin"))
        .withPropertyValues("edidp.ui.enabled=true")
        .run(context -> {
          assertThat(context).hasBean("adminUiSecurityFilterChain");
        });

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration.class,
            org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration.class,
            io.github.edmaputra.edidp.ui.security.UiSecurityConfig.class
        ))
        .withUserConfiguration(TestSecurityConfig.class)
        .withBean(UiProperties.class, () -> new UiProperties(true, "/admin/"))
        .withPropertyValues("edidp.ui.enabled=true")
        .run(context -> {
          assertThat(context).hasBean("adminUiSecurityFilterChain");
        });
  }

  @Test
  void uiApplication_canBeInstantiated() {
    UiApplication app = new UiApplication();
    assertThat(app).isNotNull();
  }

  @org.springframework.context.annotation.Configuration
  @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
  static class TestSecurityConfig {}
}
