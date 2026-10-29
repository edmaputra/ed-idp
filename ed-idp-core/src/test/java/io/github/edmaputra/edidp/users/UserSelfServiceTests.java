package io.github.edmaputra.edidp.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.edmaputra.iam.domain.security.PasswordValidator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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
 * Unit tests for {@link DefaultUserSelfService} and user self-service DTOs.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
class UserSelfServiceTests {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private PasswordValidator passwordValidator;

  private DefaultUserSelfService userSelfService;

  @BeforeEach
  void setUp() {
    userSelfService = new DefaultUserSelfService(jdbcTemplate, passwordEncoder, passwordValidator);
  }

  @Test
  void userProfileResponse_handlesNullSafety() {
    UserProfileResponse response = new UserProfileResponse(null, null, null, null, null, null, null);
    assertThat(response.username()).isEmpty();
    assertThat(response.email()).isEmpty();
    assertThat(response.fullName()).isEmpty();
    assertThat(response.department()).isEmpty();
    assertThat(response.tenantId()).isEqualTo("demo");
    assertThat(response.roles()).isEmpty();
    assertThat(response.groups()).isEmpty();
  }

  @Test
  void updateProfileRequest_trimsInputs() {
    UpdateProfileRequest req = new UpdateProfileRequest(" Alice Smith ", " Platform Team ");
    assertThat(req.fullName()).isEqualTo("Alice Smith");
    assertThat(req.department()).isEqualTo("Platform Team");

    UpdateProfileRequest nullReq = new UpdateProfileRequest(null, null);
    assertThat(nullReq.fullName()).isEmpty();
    assertThat(nullReq.department()).isEmpty();
  }

  @Test
  void changePasswordRequest_validatesInvariants() {
    assertThatThrownBy(() -> new ChangePasswordRequest(null, "new12345", "new12345"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Current password must not be blank");

    assertThatThrownBy(() -> new ChangePasswordRequest("old", null, "new12345"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("New password must not be blank");

    assertThatThrownBy(() -> new ChangePasswordRequest("old", "new12345", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Confirm password must not be blank");

    assertThatThrownBy(() -> new ChangePasswordRequest("old", "new12345", "different"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("do not match");

    assertThatThrownBy(() -> new ChangePasswordRequest("same-pass", "same-pass", "same-pass"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be the same as current password");
  }

  @Test
  void getMyProfile_throwsException_whenSubjectBlank() {
    assertThatThrownBy(() -> userSelfService.getMyProfile(null, "demo"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Subject must not be blank");
  }

  @Test
  void getMyProfile_returnsProfileWithRolesAndGroups() throws SQLException {
    UUID userId = UUID.randomUUID();

    doAnswer(invocation -> {
      RowMapper<UserProfileResponse> mapper = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("id")).thenReturn(userId.toString());
      when(rs.getString("email")).thenReturn("bob@example.com");
      when(rs.getString("profile_full_name")).thenReturn("Bob Builder");
      when(rs.getString("iam_full_name")).thenReturn("Bob Builder");
      when(rs.getString("department")).thenReturn("Construction");

      UserProfileResponse response = mapper.mapRow(rs, 1);
      return List.of(response);
    }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any());

    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("code")).thenReturn("ROLE_USER");
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("iam_user_role_assignment"), any(RowCallbackHandler.class), eq(userId), eq(userId));

    doAnswer(invocation -> {
      RowCallbackHandler handler = invocation.getArgument(1);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("name")).thenReturn("Workers");
      when(rs.getString("code")).thenReturn("WRK");
      handler.processRow(rs);
      return null;
    }).when(jdbcTemplate).query(contains("iam_user_group_membership"), any(RowCallbackHandler.class), eq(userId));

    UserProfileResponse result = userSelfService.getMyProfile("bob@example.com", "acme");

    assertThat(result.username()).isEqualTo("bob@example.com");
    assertThat(result.email()).isEqualTo("bob@example.com");
    assertThat(result.fullName()).isEqualTo("Bob Builder");
    assertThat(result.department()).isEqualTo("Construction");
    assertThat(result.tenantId()).isEqualTo("acme");
    assertThat(result.roles()).containsExactly("ROLE_USER");
    assertThat(result.groups()).containsExactly("Workers");
  }

  @Test
  void updateMyProfile_updatesAndReturnsUpdatedProfile() {
    lenient().when(jdbcTemplate.update(contains("UPDATE user_profiles"), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);

    UpdateProfileRequest req = new UpdateProfileRequest("Bob The Builder", "Civil");
    UserProfileResponse response = userSelfService.updateMyProfile("bob@example.com", "acme", req);

    verify(jdbcTemplate).update(contains("UPDATE iam_user"), eq("Bob The Builder"), any(), any(), eq("bob@example.com"));
    verify(jdbcTemplate).update(contains("UPDATE user_profiles"), eq("Bob The Builder"), eq("Civil"), any(), eq("acme"), eq("bob@example.com"), eq("bob@example.com"));
    assertThat(response.tenantId()).isEqualTo("acme");
  }

  @Test
  void updateMyProfile_insertsProfile_whenNotPresentInProfiles() {
    lenient().when(jdbcTemplate.update(contains("UPDATE user_profiles"), any(), any(), any(), any(), any(), any()))
        .thenReturn(0);

    UpdateProfileRequest req = new UpdateProfileRequest("Bob The Builder", "Civil");
    UserProfileResponse response = userSelfService.updateMyProfile("bob@example.com", "acme", req);

    verify(jdbcTemplate).update(contains("INSERT INTO user_profiles"), eq("bob@example.com"), eq("Bob The Builder"), eq("bob@example.com"), eq("Civil"), eq("acme"), any());
    assertThat(response.tenantId()).isEqualTo("acme");
  }

  @Test
  void changeMyPassword_throwsException_whenPolicyViolated() {
    doThrow(new RuntimeException("Password must have at least 8 characters"))
        .when(passwordValidator).validatePassword("short", "bob");

    ChangePasswordRequest req = new ChangePasswordRequest("oldpass", "short", "short");

    assertThatThrownBy(() -> userSelfService.changeMyPassword("bob", "demo", req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Password must have at least 8 characters");
  }

  @Test
  void changeMyPassword_throwsException_whenUserNotFound() {
    when(jdbcTemplate.query(contains("SELECT password_hash FROM iam_user"), any(RowMapper.class), any(), any()))
        .thenReturn(List.of());

    ChangePasswordRequest req = new ChangePasswordRequest("oldpass", "newpass123", "newpass123");

    assertThatThrownBy(() -> userSelfService.changeMyPassword("bob", "demo", req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User not found");
  }

  @Test
  void changeMyPassword_throwsException_whenHashBlank() {
    when(jdbcTemplate.query(contains("SELECT password_hash FROM iam_user"), any(RowMapper.class), any(), any()))
        .thenReturn(List.of(""));

    ChangePasswordRequest req = new ChangePasswordRequest("oldpass", "newpass123", "newpass123");

    assertThatThrownBy(() -> userSelfService.changeMyPassword("bob", "demo", req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User has no current password set");
  }

  @Test
  void changeMyPassword_throwsException_whenCurrentPasswordIncorrect() {
    when(jdbcTemplate.query(contains("SELECT password_hash FROM iam_user"), any(RowMapper.class), any(), any()))
        .thenReturn(List.of("existing-hash"));
    when(passwordEncoder.matches("wrong-pass", "existing-hash")).thenReturn(false);

    ChangePasswordRequest req = new ChangePasswordRequest("wrong-pass", "newpass123", "newpass123");

    assertThatThrownBy(() -> userSelfService.changeMyPassword("bob", "demo", req))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Current password is incorrect");
  }

  @Test
  void changeMyPassword_successfullyUpdatesPassword() {
    when(jdbcTemplate.query(contains("SELECT password_hash FROM iam_user"), any(RowMapper.class), any(), any()))
        .thenReturn(List.of("existing-hash"));
    when(passwordEncoder.matches("correct-pass", "existing-hash")).thenReturn(true);
    when(passwordEncoder.encode("newpass123")).thenReturn("encoded-new-hash");

    ChangePasswordRequest req = new ChangePasswordRequest("correct-pass", "newpass123", "newpass123");
    userSelfService.changeMyPassword("bob", "demo", req);

    verify(passwordValidator).validatePassword("newpass123", "bob");
    verify(passwordEncoder).encode("newpass123");
    verify(jdbcTemplate).update(contains("UPDATE iam_user SET password_hash"), eq("encoded-new-hash"), any(), any(), eq("bob"));
  }
}
