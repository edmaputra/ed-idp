package io.github.edmaputra.edidp.users;

/**
 * Request payload containing a verification code or credential to activate or disable Multi-Factor Authentication.
 *
 * @param code the 6-digit TOTP verification code or single-use recovery code / password
 * @author edmaputra
 * @since 0.0.1
 */
public record MfaVerificationRequest(
    String code
) {
  public MfaVerificationRequest {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Verification code must not be blank.");
    }
    code = code.trim();
  }
}
