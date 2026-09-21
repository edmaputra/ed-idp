package io.github.edmaputra.edidp.users;

import java.time.Instant;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;

/**
 * Spring configuration for seeding demo users, profiles, and attributes.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
public class UsersConfig {

  private static final String DEMO_TENANT = "demo";

  @Bean
  @Order(2)
  CommandLineRunner demoUserSeeder(
      JdbcUserDetailsManager userDetailsManager,
      PasswordEncoder passwordEncoder) {
    return args -> {
      if (userDetailsManager.userExists("demo-user")) {
        return;
      }

      UserDetails user = User.withUsername("demo-user")
          .password(passwordEncoder.encode("demo-password"))
          .roles("USER")
          .build();

      userDetailsManager.createUser(user);
    };
  }

  @Bean
  @Order(3)
  CommandLineRunner demoUserProfileSeeder(UserProfileRepository userProfileRepository) {
    return args -> {
      if (userProfileRepository.findByTenantAndUsername(DEMO_TENANT, "demo-user").isPresent()) {
        return;
      }

      UserProfile userProfile = new UserProfile(
          "demo-user",
          "Demo User",
          "demo-user@example.com",
          true,
          "en-US",
          "Asia/Jakarta",
          "engineering",
          DEMO_TENANT,
          Instant.now().getEpochSecond());

      userProfileRepository.save(userProfile);
    };
  }

  @Bean
  @Order(4)
  CommandLineRunner demoUserProfileAttributeSeeder(
      UserProfileRepository userProfileRepository,
      UserProfileAttributeRepository userProfileAttributeRepository) {
    return args -> {
      UserProfile userProfile =
          userProfileRepository.findByTenantAndUsername(DEMO_TENANT, "demo-user").orElse(null);
      if (userProfile == null) {
        return;
      }

      createAttributeIfMissing(
          userProfile,
          userProfileAttributeRepository,
          "favorite_color",
          "blue");
      createAttributeIfMissing(
          userProfile,
          userProfileAttributeRepository,
          "employee_level",
          "senior");
      createAttributeIfMissing(
          userProfile,
          userProfileAttributeRepository,
          "region",
          "apac");
    };
  }

  private static void createAttributeIfMissing(
      UserProfile userProfile,
      UserProfileAttributeRepository userProfileAttributeRepository,
      String key,
      String value) {
    if (userProfileAttributeRepository.existsByTenantIdAndUserProfileUsernameAndAttributeKey(
        userProfile.getTenant(), userProfile.getUsername(), key)) {
      return;
    }

    userProfileAttributeRepository.save(
        new UserProfileAttribute(
            userProfile,
            key,
            value));
  }
}
