package io.github.edmaputra.edidp.users;

import java.util.List;

/**
 * Response payload containing configuration details to initiate Multi-Factor Authentication enrollment.
 *
 * @param secret the Base32-encoded TOTP shared secret key
 * @param qrCodeUri the standardized {@code otpauth://totp/...} URI for authenticator app configuration
 * @param backupCodes the list of emergency single-use backup recovery codes
 * @author edmaputra
 * @since 0.0.1
 */
public record MfaEnrollmentSetupResponse(
    String secret,
    String qrCodeUri,
    List<String> backupCodes
) {
  public MfaEnrollmentSetupResponse {
    secret = (secret != null) ? secret : "";
    qrCodeUri = (qrCodeUri != null) ? qrCodeUri : "";
    backupCodes = (backupCodes != null) ? List.copyOf(backupCodes) : List.of();
  }
}
