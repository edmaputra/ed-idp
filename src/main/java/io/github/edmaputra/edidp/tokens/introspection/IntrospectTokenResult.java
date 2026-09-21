package io.github.edmaputra.edidp.tokens.introspection;

import java.util.Map;

/**
 * Result model representing the outcome of an RFC 7662 token introspection evaluation.
 *
 * @param status the HTTP status mapping category
 * @param body   the introspection response claims or error payload
 * @author edmaputra
 * @since 0.0.1
 */
public record IntrospectTokenResult(Status status, Map<String, Object> body) {

  public IntrospectTokenResult {
    body = body == null ? Map.of() : Map.copyOf(body);
  }

  public enum Status {
    OK,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN
  }
}
