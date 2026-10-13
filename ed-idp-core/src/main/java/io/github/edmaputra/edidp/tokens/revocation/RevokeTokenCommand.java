package io.github.edmaputra.edidp.tokens.revocation;

/**
 * Command encapsulating the parameters required to execute an RFC 7009 token revocation request.
 *
 * @param token               the token to revoke
 * @param tokenTypeHint       optional hint about the type of token (access_token or refresh_token)
 * @param authorizationHeader the HTTP Authorization header containing client credentials
 * @author edmaputra
 * @since 0.0.1
 */
public record RevokeTokenCommand(
    String token,
    String tokenTypeHint,
    String authorizationHeader) {
}
