package io.github.edmaputra.edidp.account.exception;

/**
 * Exception thrown when an error response is returned from the IdP Resource Server API.
 *
 * @author edmaputra
 * @since 0.0.1
 */
public class AccountPortalApiException extends RuntimeException {

  private final int statusCode;

  public AccountPortalApiException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public AccountPortalApiException(int statusCode, String message, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
