package io.github.edmaputra.edidp.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring configuration activating structured security audit logging and framework event bridges.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Configuration
public class AuditConfig {

  @Bean
  @ConditionalOnMissingBean(AuthenticationEventPublisher.class)
  AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
  }

  @Bean
  @ConditionalOnMissingBean(AuditEventRepository.class)
  AuditEventRepository auditEventRepository() {
    return new InMemoryAuditEventRepository();
  }

  @Bean
  @ConditionalOnMissingBean(AuditEventPort.class)
  AuditEventPort auditEventPort(
      ObjectMapper objectMapper,
      AuditEventRepository auditEventRepository,
      ApplicationEventPublisher eventPublisher) {
    return new StructuredLoggingAuditEventAdapter(objectMapper, auditEventRepository, eventPublisher);
  }

  @Bean
  SecurityAuditEventListener securityAuditEventListener(AuditEventPort auditEventPort) {
    return new SecurityAuditEventListener(auditEventPort);
  }
}
