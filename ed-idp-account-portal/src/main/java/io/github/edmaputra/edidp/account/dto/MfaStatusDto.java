package io.github.edmaputra.edidp.account.dto;

import java.time.Instant;

/**
 * Data transfer object representing the Multi-Factor Authentication enrollment status of a user.
 *
 * @param enabled whether MFA is currently enabled
 * @param enrolledAt the instant MFA was activated, or null
 * @author edmaputra
 * @since 0.0.1
 */
public record MfaStatusDto(
    boolean enabled,
    Instant enrolledAt
) {
}
