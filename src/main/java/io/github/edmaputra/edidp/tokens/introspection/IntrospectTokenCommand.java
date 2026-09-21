package io.github.edmaputra.edidp.tokens.introspection;

/**
 * Command encapsulating the parameters required to execute an RFC 7662 token introspection request.
 *
 * @param token               the token string to introspect
 * @param authorizationHeader the HTTP Authorization header containing client credentials
 * @author edmaputra
 * @since 0.0.1
 */
public record IntrospectTokenCommand(String token, String authorizationHeader) {
}
