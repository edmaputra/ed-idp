package io.github.edmaputra.edidp.tokens.revocation;

import java.util.Map;

public record RevokeTokenResult(Status status, Map<String, Object> body) {

  public enum Status {
    OK,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN
  }
}
