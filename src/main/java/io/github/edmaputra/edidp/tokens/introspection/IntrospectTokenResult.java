package io.github.edmaputra.edidp.tokens.introspection;

import java.util.Map;

public record IntrospectTokenResult(Status status, Map<String, Object> body) {

  public enum Status {
    OK,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN
  }
}
