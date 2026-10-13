package io.github.edmaputra.edidp.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Unit tests for {@link IamUserDetailsService}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class IamUserDetailsServiceTests {

  private JdbcTemplate jdbcTemplate;
  private IamUserDetailsService userDetailsService;

  @BeforeEach
  void setUp() {
    jdbcTemplate = mock(JdbcTemplate.class);
    userDetailsService = new IamUserDetailsService(jdbcTemplate);
  }

  @Test
  void loadUserByUsername_whenNullOrBlank_throwsUsernameNotFoundException() {
    assertThatThrownBy(() -> userDetailsService.loadUserByUsername(null))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("Email must not be empty");

    assertThatThrownBy(() -> userDetailsService.loadUserByUsername("   "))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("Email must not be empty");
  }

  @Test
  void loadUserByUsername_whenUserNotFound_throwsUsernameNotFoundException() {
    when(jdbcTemplate.query(any(String.class), any(RowMapper.class), eq("unknown@example.com")))
        .thenReturn(List.of());

    assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown@example.com"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("User not found with email");
  }

  @Test
  @SuppressWarnings("unchecked")
  void loadUserByUsername_whenSuperadmin_hasSuperadminAndAdminAndUserRoles() {
    doAnswer(invocation -> {
      RowMapper<UserDetails> rowMapper = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("id")).thenReturn("user-super-1");
      when(rs.getString("email")).thenReturn("admin@example.com");
      when(rs.getString("password_hash")).thenReturn("encoded-pwd");
      when(rs.getString("status")).thenReturn("ACTIVE");
      when(rs.getBoolean("is_platform_superadmin")).thenReturn(true);

      UserDetails user = rowMapper.mapRow(rs, 0);
      return List.of(user);
    }).when(jdbcTemplate).query(contains("FROM iam_user u"), any(RowMapper.class), eq("admin@example.com"));

    UserDetails details = userDetailsService.loadUserByUsername("admin@example.com");

    assertThat(details.getUsername()).isEqualTo("admin@example.com");
    assertThat(details.getPassword()).isEqualTo("encoded-pwd");
    assertThat(details.isEnabled()).isTrue();
    assertThat(details.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .contains("ROLE_PLATFORM_SUPERADMIN", "ROLE_ADMIN", "ROLE_USER");
  }

  @Test
  @SuppressWarnings("unchecked")
  void loadUserByUsername_whenRegularUserWithDirectAndGroupRoles() {
    // Stub role query called inside mapRow
    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("code")).thenReturn("ROLE_OPERATOR", "finance", null, "   ");
      handler.processRow(rs);
      handler.processRow(rs);
      handler.processRow(rs);
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("FROM iam_user_role_assignment ura"), any(RowCallbackHandler.class), eq("user-regular-1"), eq("user-regular-1"));

    doAnswer(invocation -> {
      RowMapper<UserDetails> rowMapper = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("id")).thenReturn("user-regular-1");
      when(rs.getString("email")).thenReturn("regular@example.com");
      when(rs.getString("password_hash")).thenReturn("hash-123");
      when(rs.getString("status")).thenReturn("ACTIVE");
      when(rs.getBoolean("is_platform_superadmin")).thenReturn(false);

      UserDetails user = rowMapper.mapRow(rs, 0);
      return List.of(user);
    }).when(jdbcTemplate).query(contains("FROM iam_user u"), any(RowMapper.class), eq("regular@example.com"));

    UserDetails details = userDetailsService.loadUserByUsername("regular@example.com");

    assertThat(details.getUsername()).isEqualTo("regular@example.com");
    assertThat(details.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .contains("ROLE_OPERATOR", "ROLE_FINANCE");
  }

  @Test
  @SuppressWarnings("unchecked")
  void loadUserByUsername_whenSuspendedUserAndEmptyRoles_disabledAndDefaultsToRoleUser() {
    // Stub role query with no roles
    doAnswer(invocation -> null).when(jdbcTemplate)
        .query(contains("FROM iam_user_role_assignment ura"), any(RowCallbackHandler.class), eq("user-inactive-1"), eq("user-inactive-1"));

    doAnswer(invocation -> {
      RowMapper<UserDetails> rowMapper = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("id")).thenReturn("user-inactive-1");
      when(rs.getString("email")).thenReturn("suspended@example.com");
      when(rs.getString("password_hash")).thenReturn(null);
      when(rs.getString("status")).thenReturn("SUSPENDED");
      when(rs.getBoolean("is_platform_superadmin")).thenReturn(false);

      UserDetails user = rowMapper.mapRow(rs, 0);
      return List.of(user);
    }).when(jdbcTemplate).query(contains("FROM iam_user u"), any(RowMapper.class), eq("suspended@example.com"));

    UserDetails details = userDetailsService.loadUserByUsername("suspended@example.com");

    assertThat(details.getUsername()).isEqualTo("suspended@example.com");
    assertThat(details.getPassword()).isEmpty();
    assertThat(details.isEnabled()).isFalse();
    assertThat(details.getAuthorities().stream().map(GrantedAuthority::getAuthority))
        .containsExactly("ROLE_USER");
  }
}
