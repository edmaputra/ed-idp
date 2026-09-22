package io.github.edmaputra.edidp.oauth;

import io.github.edmaputra.edidp.tenancy.TenantContextFilter;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Core security configuration establishing multi-tier filter chains, OAuth 2.1 protocol endpoints,
 * and tenant-aware repository bindings.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
      TenantContextFilter tenantContextFilter) {
    FilterRegistrationBean<TenantContextFilter> registrationBean =
        new FilterRegistrationBean<>(tenantContextFilter);
    registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registrationBean.addUrlPatterns("/*");
    return registrationBean;
  }

  @Bean
  @Order(1)
  SecurityFilterChain tenantMachineEndpointsFilterChain(HttpSecurity http) throws Exception {
    http
      .securityMatcher(new OrRequestMatcher(
          PathPatternRequestMatcher.withDefaults().matcher("/t/{tenant}/oauth2/introspect"),
          PathPatternRequestMatcher.withDefaults().matcher("/t/{tenant}/oauth2/revoke")))
      .csrf(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests((authorize) -> authorize.anyRequest().permitAll());

    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain authorizationServerSecurityFilterChain(
      HttpSecurity http,
      Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper)
      throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

    http
        .securityMatcher(
            new OrRequestMatcher(
                authorizationServerConfigurer.getEndpointsMatcher(),
                PathPatternRequestMatcher.withDefaults().matcher("/oauth2/authorize-consent")))
        .with(authorizationServerConfigurer, (authorizationServer) ->
            authorizationServer
            .oidc((oidc) ->
              oidc.userInfoEndpoint((userInfoEndpoint) ->
                userInfoEndpoint.userInfoMapper(userInfoMapper)))
        )
        .authorizeHttpRequests((authorize) ->
            authorize.anyRequest().authenticated()
        )
        .exceptionHandling((exceptions) -> exceptions
            .defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
            )
        );

    return http.build();
  }

  @Bean
  @Order(3)
  SecurityFilterChain introspectionFilterChain(HttpSecurity http) throws Exception {
    http
      .securityMatcher(
          "/oauth2/introspect",
          "/oauth2/revoke")
      .csrf(AbstractHttpConfigurer::disable)
      .authorizeHttpRequests((authorize) -> authorize
          .anyRequest().permitAll()
      );

    return http.build();
  }

  @Bean
  @Order(4)
  SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests((authorize) -> authorize
            .requestMatchers("/logged-out", "/t/*/.well-known/openid-configuration", "/t/*/oauth2/jwks")
            .permitAll()
            .anyRequest().authenticated())
        .formLogin(Customizer.withDefaults());

    return http.build();
  }

  @Bean
  RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
    return new TenantAwareRegisteredClientRepository(jdbcTemplate);
  }

  @Bean
  OAuth2AuthorizationService authorizationService(
      JdbcTemplate jdbcTemplate,
      RegisteredClientRepository registeredClientRepository) {
    return new TenantAwareOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
  }

  @Bean
  OAuth2AuthorizationConsentService oauth2AuthorizationConsentService(
      JdbcTemplate jdbcTemplate,
      RegisteredClientRepository registeredClientRepository) {
    return new TenantAwareOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
  }

  @Bean
  JdbcUserDetailsManager userDetailsService(DataSource dataSource) {
    return new JdbcUserDetailsManager(dataSource);
  }

  @Bean
  SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  @Bean
  HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  JWKSource<SecurityContext> jwkSource() {
    RSAKey rsaKey = generateRsaKey();
    JWKSet jwkSet = new JWKSet(rsaKey);
    return new ImmutableJWKSet<>(jwkSet);
  }

  @Bean
  AuthorizationServerSettings authorizationServerSettings(
      @Value("${app.issuer-uri}") String issuerUri) {
    return AuthorizationServerSettings.builder()
        .issuer(issuerUri)
        .build();
  }

  private static RSAKey generateRsaKey() {
    KeyPair keyPair = generateRsaKeyPair();
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

    return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(UUID.randomUUID().toString())
        .build();
  }

  private static KeyPair generateRsaKeyPair() {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      return keyPairGenerator.generateKeyPair();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("RSA algorithm is not available", exception);
    }
  }
}