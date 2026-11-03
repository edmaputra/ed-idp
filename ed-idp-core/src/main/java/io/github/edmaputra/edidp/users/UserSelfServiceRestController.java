package io.github.edmaputra.edidp.users;

import io.github.edmaputra.edidp.tenancy.TenantContext;
import io.github.edmaputra.iam.domain.exception.AuthenticationException;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller providing self-service user profile, credential, and Multi-Factor Authentication maintenance.
 * Protected as an OAuth 2.1 Resource Server endpoint via Bearer JWT tokens.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserSelfServiceRestController {

  private final UserSelfService userSelfService;

  public UserSelfServiceRestController(UserSelfService userSelfService) {
    this.userSelfService = Objects.requireNonNull(userSelfService, "userSelfService must not be null");
  }

  @GetMapping
  public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
    String subject = resolveSubject(jwt);
    String tenantId = TenantContext.getCurrentTenantOrDefault("demo");
    return ResponseEntity.ok(userSelfService.getMyProfile(subject, tenantId));
  }

  @PutMapping
  public ResponseEntity<UserProfileResponse> updateMyProfile(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody UpdateProfileRequest request) {
    String subject = resolveSubject(jwt);
    String tenantId = TenantContext.getCurrentTenantOrDefault("demo");
    return ResponseEntity.ok(userSelfService.updateMyProfile(subject, tenantId, request));
  }

  @PostMapping("/password")
  public ResponseEntity<Void> changeMyPassword(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody ChangePasswordRequest request) {
    String subject = resolveSubject(jwt);
    String tenantId = TenantContext.getCurrentTenantOrDefault("demo");
    userSelfService.changeMyPassword(subject, tenantId, request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/mfa")
  public ResponseEntity<UserMfaStatusResponse> getMyMfaStatus(@AuthenticationPrincipal Jwt jwt) {
    String subject = resolveSubject(jwt);
    String tenantId = TenantContext.getCurrentTenantOrDefault("demo");
    return ResponseEntity.ok(userSelfService.getMyMfaStatus(subject, tenantId));
  }

  @PostMapping("/mfa/setup")
  public ResponseEntity<MfaEnrollmentSetupResponse> initiateMyMfaSetup(@AuthenticationPrincipal Jwt jwt) {
    String subject = resolveSubject(jwt);
    String tenantId = TenantContext.getCurrentTenantOrDefault("demo");
    return ResponseEntity.ok(userSelfService.initiateMyMfaSetup(subject, tenantId));
  }

  @PostMapping("/mfa/activate")
  public ResponseEntity<Void> activateMyMfa(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody MfaVerificationRequest request) {
    String subject = resolveSubject(jwt);
    String tenantId = TenantContext.getCurrentTenantOrDefault("demo");
    userSelfService.activateMyMfa(subject, tenantId, request.code());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/mfa/disable")
  public ResponseEntity<Void> disableMyMfa(
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody MfaVerificationRequest request) {
    String subject = resolveSubject(jwt);
    String tenantId = TenantContext.getCurrentTenantOrDefault("demo");
    userSelfService.disableMyMfa(subject, tenantId, request.code());
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler({IllegalArgumentException.class, AuthenticationException.class})
  public ResponseEntity<Map<String, String>> handleClientException(RuntimeException ex) {
    return ResponseEntity.badRequest().body(Map.of(
        "error", "bad_request",
        "error_description", ex.getMessage() != null ? ex.getMessage() : "Invalid request"
    ));
  }

  private String resolveSubject(Jwt jwt) {
    if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
      return jwt.getSubject();
    }
    throw new IllegalArgumentException("JWT subject claim is missing");
  }
}
