package io.github.edmaputra.edidp.tokens.revocation;

import java.util.Map;

/**
 * Result model representing the outcome of an RFC 7009 token revocation evaluation.
 *
 * @param status the HTTP status mapping category
 * @param body   the revocation response claims or error payload
 * @author edmaputra
 * @since 0.0.1
 */
public record RevokeTokenResult(Status status, Map<String, Object> body) {

  public RevokeTokenResult {
    body = body == null ? Map.of() : Map.copyOf(body);
  }

  public enum Status {
    OK,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN
  }
}
