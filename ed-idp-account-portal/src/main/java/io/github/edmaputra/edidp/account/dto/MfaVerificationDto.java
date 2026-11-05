package io.github.edmaputra.edidp.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data transfer object carrying a verification code or credential to activate or disable MFA.
 *
 * @param code 6-digit TOTP code, recovery code, or password
 * @author edmaputra
 * @since 0.0.1
 */
public record MfaVerificationDto(
    @NotBlank(message = "Verification code is required.")
    String code
) {
  public MfaVerificationDto {
    code = (code != null) ? code.trim() : "";
  }
}
