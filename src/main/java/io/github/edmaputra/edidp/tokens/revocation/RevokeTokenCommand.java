package io.github.edmaputra.edidp.tokens.revocation;

public record RevokeTokenCommand(
    String token,
    String tokenTypeHint,
    String authorizationHeader) {
}
