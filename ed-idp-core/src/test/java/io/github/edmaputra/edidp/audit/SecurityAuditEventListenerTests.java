package io.github.edmaputra.edidp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;

/**
 * Unit tests for {@link SecurityAuditEventListener}.
 *
 * @author edmaputra
 * @since 0.0.1
 */
class SecurityAuditEventListenerTests {

  private InMemoryAuditEventRepository repository;
  private SecurityAuditEventListener listener;

  @BeforeEach
  void setUp() {
    repository = new InMemoryAuditEventRepository();
    AuditEventPort port = repository::save;
    listener = new SecurityAuditEventListener(port);
  }

  @Test
  void onAuthenticationSuccessRecordsLoginSuccessEvent() {
    Authentication auth = new UsernamePasswordAuthenticationToken("alice", "secret", List.of());
    AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(auth);

    listener.onAuthenticationSuccess(event);

    List<SecurityAuditEvent> events = repository.findRecent(10);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).action()).isEqualTo(SecurityAuditAction.LOGIN_SUCCESS);
    assertThat(events.get(0).principal()).isEqualTo("alice");
    assertThat(events.get(0).status()).isEqualTo(SecurityAuditStatus.SUCCESS);
  }

  @Test
  void onAuthenticationFailureRecordsLoginFailureEvent() {
    Authentication auth = new UsernamePasswordAuthenticationToken("bob", "wrong");
    AuthenticationFailureBadCredentialsEvent event =
        new AuthenticationFailureBadCredentialsEvent(auth, new BadCredentialsException("Bad credentials"));

    listener.onAuthenticationFailure(event);

    List<SecurityAuditEvent> events = repository.findRecent(10);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).action()).isEqualTo(SecurityAuditAction.LOGIN_FAILURE);
    assertThat(events.get(0).principal()).isEqualTo("bob");
    assertThat(events.get(0).status()).isEqualTo(SecurityAuditStatus.FAILURE);
    assertThat(events.get(0).details().get("error")).isEqualTo("Bad credentials");
  }

  @Test
  void onLogoutSuccessRecordsLogoutEvent() {
    Authentication auth = new UsernamePasswordAuthenticationToken("carol", null, List.of());
    LogoutSuccessEvent event = new LogoutSuccessEvent(auth);

    listener.onLogoutSuccess(event);

    List<SecurityAuditEvent> events = repository.findRecent(10);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).action()).isEqualTo(SecurityAuditAction.LOGOUT);
    assertThat(events.get(0).principal()).isEqualTo("carol");
    assertThat(events.get(0).status()).isEqualTo(SecurityAuditStatus.SUCCESS);
  }
}
