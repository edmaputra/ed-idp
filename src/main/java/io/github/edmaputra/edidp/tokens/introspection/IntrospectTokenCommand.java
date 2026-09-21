package io.github.edmaputra.edidp.tokens.introspection;

public record IntrospectTokenCommand(String token, String authorizationHeader) {
}
