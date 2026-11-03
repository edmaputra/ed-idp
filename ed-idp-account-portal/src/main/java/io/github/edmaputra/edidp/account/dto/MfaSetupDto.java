package io.github.edmaputra.edidp.account.dto;

import java.util.List;

/**
 * Data transfer object containing MFA enrollment details including TOTP secret, QR code URI, and recovery codes.
 *
 * @param secret the Base32 TOTP secret key
 * @param qrCodeUri the {@code otpauth://totp/...} URI
 * @param backupCodes the list of emergency single-use backup codes
 * @author edmaputra
 * @since 0.0.1
 */
public record MfaSetupDto(
    String secret,
    String qrCodeUri,
    List<String> backupCodes
) {
  public MfaSetupDto {
    secret = (secret != null) ? secret : "";
    qrCodeUri = (qrCodeUri != null) ? qrCodeUri : "";
    backupCodes = (backupCodes != null) ? List.copyOf(backupCodes) : List.of();
  }
}
