package io.github.edmaputra.edidp.ui.security;

import io.github.edmaputra.edidp.ui.UiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Dedicated Security Filter Chain for the Admin Console UI.
 * Intercepts requests under the admin base path (e.g. /admin/**) and static assets (/ui-assets/**),
 * providing form-based authentication and CSRF protection.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
@ConditionalOnProperty(name = "edidp.ui.enabled", havingValue = "true")
public class UiSecurityConfig {

  private final UiProperties uiProperties;

  public UiSecurityConfig(UiProperties uiProperties) {
    this.uiProperties = uiProperties;
  }

  @Bean
  @Order(3) // Runs before defaultSecurityFilterChain (@Order(4)) in ed-idp-core
  SecurityFilterChain adminUiSecurityFilterChain(HttpSecurity http) throws Exception {
    String basePath = uiProperties.basePath();
    String pattern = basePath.endsWith("/") ? basePath + "**" : basePath + "/**";

    http
        .securityMatcher(new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(pattern),
            PathPatternRequestMatcher.withDefaults().matcher("/ui-assets/**")
        ))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/ui-assets/**").permitAll()
            .requestMatchers(basePath + "/login").permitAll()
            .requestMatchers(pattern).hasAnyRole("ADMIN", "USER")
        )
        .formLogin(form -> form
            .loginPage(basePath + "/login")
            .defaultSuccessUrl(basePath + "/dashboard", true)
            .permitAll()
        )
        .logout(logout -> logout
            .logoutUrl(basePath + "/logout")
            .logoutSuccessUrl(basePath + "/login?logout")
            .permitAll()
        );

    return http.build();
  }
}
