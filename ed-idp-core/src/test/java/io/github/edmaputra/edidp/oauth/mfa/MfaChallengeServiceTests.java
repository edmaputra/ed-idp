package io.github.edmaputra.edidp.oauth.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.edmaputra.iam.domain.auth.mfa.TotpGenerator;
import io.github.edmaputra.iam.domain.model.User;
import io.github.edmaputra.iam.domain.model.UserId;
import io.github.edmaputra.iam.domain.model.UserMfa;
import io.github.edmaputra.iam.domain.repository.UserMfaRepository;
import io.github.edmaputra.iam.domain.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Unit tests verifying MfaChallengeService requirements and TOTP/backup code verification.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class MfaChallengeServiceTests {

  private UserRepository userRepository;
  private UserMfaRepository userMfaRepository;
  private JdbcTemplate jdbcTemplate;
  private MfaChallengeService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    userMfaRepository = mock(UserMfaRepository.class);
    jdbcTemplate = mock(JdbcTemplate.class);
    service = new MfaChallengeService(userRepository, userMfaRepository, jdbcTemplate);
  }

  @Test
  void isMfaRequiredReturnsFalseForNullOrBlank() {
    assertThat(service.isMfaRequired(null)).isFalse();
    assertThat(service.isMfaRequired("")).isFalse();
    assertThat(service.isMfaRequired("   ")).isFalse();
  }

  @Test
  void isMfaRequiredReturnsFalseWhenUserNotFound() {
    when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("unknown@example.com")))
        .thenReturn(List.of());

    assertThat(service.isMfaRequired("unknown@example.com")).isFalse();
  }

  @Test
  void isMfaRequiredReturnsFalseWhenMfaNotActive() {
    User user = User.create("alice@example.com", "hash", "Alice", false);
    when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
    when(userMfaRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

    assertThat(service.isMfaRequired("alice@example.com")).isFalse();
  }

  @Test
  void isMfaRequiredReturnsTrueWhenMfaActive() {
    User user = User.create("bob@example.com", "hash", "Bob", false);
    UserMfa mfa = UserMfa.create(user.getId(), TotpGenerator.generateSecret(), List.of());
    mfa.activate();

    when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));
    when(userMfaRepository.findByUserId(user.getId())).thenReturn(Optional.of(mfa));

    assertThat(service.isMfaRequired("bob@example.com")).isTrue();
  }

  @Test
  void isMfaRequiredResolvesUserViaJdbcFallback() {
    User user = User.create("carol@example.com", "hash", "Carol", false);
    UserMfa mfa = UserMfa.create(user.getId(), TotpGenerator.generateSecret(), List.of());
    mfa.activate();

    when(userRepository.findByEmail("CAROL@example.com")).thenReturn(Optional.empty());
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("CAROL@example.com")))
        .thenReturn(List.of("carol@example.com"));
    when(userRepository.findByEmail("carol@example.com")).thenReturn(Optional.of(user));
    when(userMfaRepository.findByUserId(user.getId())).thenReturn(Optional.of(mfa));

    assertThat(service.isMfaRequired("CAROL@example.com")).isTrue();
  }

  @Test
  void verifyCodeRejectsBlankOrNullInputs() {
    assertThat(service.verifyCode(null, "123456")).isFalse();
    assertThat(service.verifyCode("user@example.com", null)).isFalse();
    assertThat(service.verifyCode("", "123456")).isFalse();
    assertThat(service.verifyCode("user@example.com", "  ")).isFalse();
  }

  @Test
  void verifyCodeReturnsFalseWhenUserNotFound() {
    when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("notfound@example.com")))
        .thenReturn(List.of());

    assertThat(service.verifyCode("notfound@example.com", "123456")).isFalse();
  }

  @Test
  void verifyCodeReturnsFalseWhenMfaNotEnrolledOrDisabled() {
    User user = User.create("dave@example.com", "hash", "Dave", false);
    when(userRepository.findByEmail("dave@example.com")).thenReturn(Optional.of(user));
    when(userMfaRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

    assertThat(service.verifyCode("dave@example.com", "123456")).isFalse();
  }

  @Test
  void verifyCodeSucceedsWithValidTotpCode() {
    User user = User.create("eve@example.com", "hash", "Eve", false);
    String secret = TotpGenerator.generateSecret();
    UserMfa mfa = UserMfa.create(user.getId(), secret, List.of());
    mfa.activate();

    when(userRepository.findByEmail("eve@example.com")).thenReturn(Optional.of(user));
    when(userMfaRepository.findByUserId(user.getId())).thenReturn(Optional.of(mfa));

    String validTotp = TotpGenerator.generateCurrentTotp(secret);
    assertThat(service.verifyCode("eve@example.com", validTotp)).isTrue();
    verify(userMfaRepository).save(mfa);
    assertThat(mfa.getLastVerifiedStep()).isNotNull();
  }

  @Test
  void verifyCodePreventsTotpReplayAttack() {
    User user = User.create("replay@example.com", "hash", "Replay", false);
    String secret = TotpGenerator.generateSecret();
    UserMfa mfa = UserMfa.create(user.getId(), secret, List.of());
    mfa.activate();

    when(userRepository.findByEmail("replay@example.com")).thenReturn(Optional.of(user));
    when(userMfaRepository.findByUserId(user.getId())).thenReturn(Optional.of(mfa));

    String validTotp = TotpGenerator.generateCurrentTotp(secret);
    // First attempt succeeds
    assertThat(service.verifyCode("replay@example.com", validTotp)).isTrue();
    assertThat(mfa.getLastVerifiedStep()).isNotNull();

    // Replay attempt fails immediately
    assertThat(service.verifyCode("replay@example.com", validTotp)).isFalse();
  }

  @Test
  void verifyCodeSucceedsWithValidBackupCodeAndConsumesIt() {
    User user = User.create("frank@example.com", "hash", "Frank", false);
    String rawBackupCode = "A1B2C3D4";
    String hashedBackupCode = TotpGenerator.hashBackupCode(rawBackupCode);

    List<String> backupCodes = new ArrayList<>(List.of(hashedBackupCode));
    UserMfa mfa = UserMfa.create(user.getId(), TotpGenerator.generateSecret(), backupCodes);
    mfa.activate();

    when(userRepository.findByEmail("frank@example.com")).thenReturn(Optional.of(user));
    when(userMfaRepository.findByUserId(user.getId())).thenReturn(Optional.of(mfa));

    assertThat(service.verifyCode("frank@example.com", rawBackupCode)).isTrue();
    verify(userMfaRepository).save(mfa);
    assertThat(mfa.getBackupCodes()).doesNotContain(hashedBackupCode);
  }

  @Test
  void verifyCodeRejectsInvalidCode() {
    User user = User.create("grace@example.com", "hash", "Grace", false);
    String secret = TotpGenerator.generateSecret();
    UserMfa mfa = UserMfa.create(user.getId(), secret, List.of());
    mfa.activate();

    when(userRepository.findByEmail("grace@example.com")).thenReturn(Optional.of(user));
    when(userMfaRepository.findByUserId(user.getId())).thenReturn(Optional.of(mfa));

    assertThat(service.verifyCode("grace@example.com", "000000")).isFalse();
    verify(userMfaRepository, never()).save(any());
  }
}
