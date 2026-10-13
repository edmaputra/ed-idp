package io.github.edmaputra.edidp.oauth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Custom {@link UserDetailsService} querying {@code iam_user}, {@code iam_user_identity},
 * {@code iam_user_role_assignment}, and {@code iam_role_permission} tables.
 * <p>
 * Supports email-as-username login format.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class IamUserDetailsService implements UserDetailsService {

  private final JdbcTemplate jdbcTemplate;

  public IamUserDetailsService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
    if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
      throw new UsernameNotFoundException("Email must not be empty");
    }

    String userSql = """
        SELECT u.id, u.email, u.password_hash, u.status, u.is_platform_superadmin
        FROM iam_user u
        WHERE LOWER(u.email) = LOWER(?)
        LIMIT 1
        """;

    List<UserDetails> results = jdbcTemplate.query(userSql, (rs, rowNum) -> {
      String id = rs.getString("id");
      String email = rs.getString("email");
      String password = rs.getString("password_hash");
      String status = rs.getString("status");
      boolean superadmin = rs.getBoolean("is_platform_superadmin");

      boolean enabled = "ACTIVE".equalsIgnoreCase(status);

      Set<GrantedAuthority> authorities = new HashSet<>();
      if (superadmin) {
        authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_SUPERADMIN"));
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
      }

      // Load roles assigned directly or through groups
      String roleSql = """
          SELECT r.code
          FROM iam_user_role_assignment ura
          JOIN iam_role r ON ura.role_id = r.id
          WHERE ura.user_id = ?
          UNION
          SELECT r.code
          FROM iam_user_group_membership gm
          JOIN iam_group_role_assignment gra ON gm.group_id = gra.group_id
          JOIN iam_role r ON gra.role_id = r.id
          WHERE gm.user_id = ?
          """;
      jdbcTemplate.query(roleSql, roleRs -> {
        String roleCode = roleRs.getString("code");
        if (roleCode != null && !roleCode.isBlank()) {
          String roleAuthority = roleCode.startsWith("ROLE_") ? roleCode : "ROLE_" + roleCode.toUpperCase();
          authorities.add(new SimpleGrantedAuthority(roleAuthority));
        }
      }, id, id);

      // Default ROLE_USER if authorities empty
      if (authorities.isEmpty()) {
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
      }

      return User.builder()
          .username(email)
          .password(password != null ? password : "")
          .disabled(!enabled)
          .authorities(authorities)
          .build();
    }, usernameOrEmail);

    if (results.isEmpty()) {
      throw new UsernameNotFoundException("User not found with email: " + usernameOrEmail);
    }

    return results.get(0);
  }
}
