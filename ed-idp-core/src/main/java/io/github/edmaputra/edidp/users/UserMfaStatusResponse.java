package io.github.edmaputra.edidp.users;

import java.time.Instant;

/**
 * Response payload describing the current Multi-Factor Authentication status for a user.
 *
 * @param enabled whether MFA is currently active and enforced for the user
 * @param enrolledAt the instant MFA was enrolled and activated, or null if inactive
 * @author edmaputra
 * @since 0.0.1
 */
public record UserMfaStatusResponse(
    boolean enabled,
    Instant enrolledAt
) {
}
