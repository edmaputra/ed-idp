package io.github.edmaputra.edidp.ui;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * Auto-configuration for the ed-idp Admin Console UI add-on.
 * Activated only when {@code edidp.ui.enabled=true}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@AutoConfiguration
@ConditionalOnProperty(name = "edidp.ui.enabled", havingValue = "true")
@EnableConfigurationProperties(UiProperties.class)
@ComponentScan(basePackageClasses = UiAutoConfiguration.class)
public class UiAutoConfiguration {
}
