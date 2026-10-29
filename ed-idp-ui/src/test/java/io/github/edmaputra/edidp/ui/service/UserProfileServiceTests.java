package io.github.edmaputra.edidp.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.edmaputra.edidp.ui.service.UserProfileService.ChangePasswordCommand;
import io.github.edmaputra.edidp.ui.service.UserProfileService.UpdateProfileCommand;
import io.github.edmaputra.edidp.ui.service.UserProfileService.UserProfileDto;
import io.github.edmaputra.iam.domain.security.PasswordValidator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link JdbcUserProfileService} and associated commands/DTOs.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTests {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private PasswordValidator passwordValidator;

  private JdbcUserProfileService profileService;

  @BeforeEach
  void setUp() {
    profileService = new JdbcUserProfileService(jdbcTemplate, passwordEncoder, passwordValidator);
  }

  @Test
  void userProfileDto_handlesNullSafety() {
    UserProfileDto dto = new UserProfileDto(null, null, null, null, null, null, null);
    assertThat(dto.username()).isEmpty();
    assertThat(dto.email()).isEmpty();
    assertThat(dto.fullName()).isEmpty();
    assertThat(dto.department()).isEmpty();
    assertThat(dto.tenantId()).isEqualTo("demo");
    assertThat(dto.roles()).isEmpty();
    assertThat(dto.groups()).isEmpty();
  }

  @Test
  void updateProfileCommand_validatesInvariants() {
    assertThatThrownBy(() -> new UpdateProfileCommand(null, "John", "Eng"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Username must not be blank");

    assertThatThrownBy(() -> new UpdateProfileCommand("  ", "John", "Eng"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Username must not be blank");

    UpdateProfileCommand cmd = new UpdateProfileCommand("john", " John Doe ", " Eng ");
    assertThat(cmd.fullName()).isEqualTo("John Doe");
    assertThat(cmd.department()).isEqualTo("Eng");
  }

  @Test
  void changePasswordCommand_validatesInvariants() {
    assertThatThrownBy(() -> new ChangePasswordCommand(null, "old", "new12345", "new12345"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Username must not be blank");

    assertThatThrownBy(() -> new ChangePasswordCommand("john", null, "new12345", "new12345"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Current password must not be blank");

    assertThatThrownBy(() -> new ChangePasswordCommand("john", "old", null, "new12345"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("New password must not be blank");

    assertThatThrownBy(() -> new ChangePasswordCommand("john", "old", "new12345", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Confirm password must not be blank");

    assertThatThrownBy(() -> new ChangePasswordCommand("john", "old", "new12345", "different"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("do not match");

    assertThatThrownBy(() -> new ChangePasswordCommand("john", "same-pass", "same-pass", "same-pass"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be the same as current password");
  }

  @Test
  void getProfile_returnsEmpty_whenUsernameIsBlankOrNotFound() {
    assertThat(profileService.getProfile(null)).isEmpty();
    assertThat(profileService.getProfile("  ")).isEmpty();

    when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
        .thenReturn(List.of());

    assertThat(profileService.getProfile("nonexistent@example.com")).isEmpty();
  }

  @Test
  void getProfile_returnsDtoWithRolesAndGroups_whenUserExists() throws SQLException {
    UUID userId = UUID.randomUUID();

    // Mock query for user profile row
    doAnswer(invocation -> {
      RowMapper<UserProfileDto> mapper = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("id")).thenReturn(userId.toString());
      when(rs.getString("email")).thenReturn("alice@example.com");
      when(rs.getString("tenant")).thenReturn("demo");
      when(rs.getString("profile_full_name")).thenReturn("Alice Wonderland");
      when(rs.getString("iam_full_name")).thenReturn("Alice Wonderland");
      when(rs.getString("department")).thenReturn("Engineering");

      UserProfileDto dto = mapper.mapRow(rs, 1);
      return List.of(dto);
    }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(), any(), any());

    // Mock query for roles
    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("code")).thenReturn("ROLE_USER");
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("iam_user_role_assignment"), any(RowCallbackHandler.class), eq(userId), eq(userId));

    // Mock query for groups
    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("name")).thenReturn("Developers");
      when(rs.getString("code")).thenReturn("DEV");
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("iam_user_group_membership"), any(RowCallbackHandler.class), eq(userId));

    Optional<UserProfileDto> result = profileService.getProfile("alice@example.com");

    assertThat(result).isPresent();
    UserProfileDto profile = result.get();
    assertThat(profile.username()).isEqualTo("alice@example.com");
    assertThat(profile.email()).isEqualTo("alice@example.com");
    assertThat(profile.fullName()).isEqualTo("Alice Wonderland");
    assertThat(profile.department()).isEqualTo("Engineering");
    assertThat(profile.roles()).containsExactly("ROLE_USER");
    assertThat(profile.groups()).containsExactly("Developers");
  }

  @Test
  void updateProfile_updatesIamUserAndExistingProfile() {
    org.mockito.Mockito.lenient()
        .when(jdbcTemplate.update(contains("UPDATE user_profiles"), any(), any(), any(), any(), any()))
        .thenReturn(1);

    UpdateProfileCommand cmd = new UpdateProfileCommand("alice@example.com", "Alice Smith", "QA");
    profileService.updateProfile(cmd);

    verify(jdbcTemplate).update(contains("UPDATE iam_user"), eq("Alice Smith"), any(), any(), eq("alice@example.com"));
    verify(jdbcTemplate).update(contains("UPDATE user_profiles"), eq("Alice Smith"), eq("QA"), any(), eq("alice@example.com"), eq("alice@example.com"));
  }

  @Test
  void updateProfile_insertsIntoUserProfiles_whenProfileRowDoesNotExist() {
    org.mockito.Mockito.lenient()
        .when(jdbcTemplate.update(contains("UPDATE user_profiles"), any(), any(), any(), any(), any()))
        .thenReturn(0);

    UpdateProfileCommand cmd = new UpdateProfileCommand("alice@example.com", "Alice Smith", "QA");
    profileService.updateProfile(cmd);

    verify(jdbcTemplate).update(
        contains("INSERT INTO user_profiles"),
        eq("alice@example.com"), eq("Alice Smith"), eq("alice@example.com"), eq("QA"), any()
    );
  }

  @Test
  void changePassword_throwsException_whenPolicyViolated() {
    doThrow(new RuntimeException("Password must have at least 8 characters"))
        .when(passwordValidator).validatePassword("short", "alice");

    ChangePasswordCommand cmd = new ChangePasswordCommand("alice", "oldpass", "short", "short");

    assertThatThrownBy(() -> profileService.changePassword(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Password must have at least 8 characters");
  }

  @Test
  void changePassword_throwsException_whenUserNotFound() {
    when(jdbcTemplate.query(contains("SELECT password_hash FROM iam_user"), any(RowMapper.class), any(), any()))
        .thenReturn(List.of());

    ChangePasswordCommand cmd = new ChangePasswordCommand("alice", "oldpass", "newpass123", "newpass123");

    assertThatThrownBy(() -> profileService.changePassword(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User not found");
  }

  @Test
  void changePassword_throwsException_whenHashBlank() {
    when(jdbcTemplate.query(contains("SELECT password_hash FROM iam_user"), any(RowMapper.class), any(), any()))
        .thenReturn(List.of(""));

    ChangePasswordCommand cmd = new ChangePasswordCommand("alice", "oldpass", "newpass123", "newpass123");

    assertThatThrownBy(() -> profileService.changePassword(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User has no current password set");
  }

  @Test
  void changePassword_throwsException_whenCurrentPasswordIncorrect() {
    when(jdbcTemplate.query(contains("SELECT password_hash FROM iam_user"), any(RowMapper.class), any(), any()))
        .thenReturn(List.of("existing-hash"));
    when(passwordEncoder.matches("wrong-pass", "existing-hash")).thenReturn(false);

    ChangePasswordCommand cmd = new ChangePasswordCommand("alice", "wrong-pass", "newpass123", "newpass123");

    assertThatThrownBy(() -> profileService.changePassword(cmd))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Current password is incorrect");
  }

  @Test
  void changePassword_successfullyUpdatesPassword() {
    when(jdbcTemplate.query(contains("SELECT password_hash FROM iam_user"), any(RowMapper.class), any(), any()))
        .thenReturn(List.of("existing-hash"));
    when(passwordEncoder.matches("correct-pass", "existing-hash")).thenReturn(true);
    when(passwordEncoder.encode("newpass123")).thenReturn("encoded-new-hash");

    ChangePasswordCommand cmd = new ChangePasswordCommand("alice", "correct-pass", "newpass123", "newpass123");
    profileService.changePassword(cmd);

    verify(passwordValidator).validatePassword("newpass123", "alice");
    verify(passwordEncoder).encode("newpass123");
    verify(jdbcTemplate).update(
        contains("UPDATE iam_user SET password_hash"),
        eq("encoded-new-hash"), any(), any(), eq("alice")
    );
  }
}
