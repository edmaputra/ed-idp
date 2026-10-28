package io.github.edmaputra.edidp.account.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the User Account Portal OAuth 2.1 client.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
@EnableWebSecurity
public class PortalSecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(PortalSecurityConfig.class);

  @Bean
  public PortalOidcLogoutSuccessHandler portalOidcLogoutSuccessHandler(
      @Value("${edidp.auth-server-url:http://localhost:9000}") String authServerUrl) {
    return new PortalOidcLogoutSuccessHandler(authServerUrl);
  }

  @Bean
  public SecurityFilterChain portalSecurityFilterChain(
      HttpSecurity http,
      PortalOidcLogoutSuccessHandler logoutSuccessHandler) throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/logged-out", "/error").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth2 -> oauth2
            .defaultSuccessUrl("/account", true)
            .failureHandler((request, response, exception) -> {
              log.error("OAuth2 Login Failed: {} - message: {}", exception.getClass().getName(), exception.getMessage(), exception);
              response.sendRedirect("/login?error");
            })
        )
        .logout(logout -> logout
            .logoutSuccessHandler(logoutSuccessHandler)
            .invalidateHttpSession(true)
            .clearAuthentication(true)
            .deleteCookies("ACCOUNT_PORTAL_SESSIONID", "JSESSIONID")
        );

    return http.build();
  }

  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuth2AuthorizedClientRepository authorizedClientRepository) {

    OAuth2AuthorizedClientProvider authorizedClientProvider =
        OAuth2AuthorizedClientProviderBuilder.builder()
            .authorizationCode()
            .refreshToken()
            .build();

    DefaultOAuth2AuthorizedClientManager authorizedClientManager =
        new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }
}
