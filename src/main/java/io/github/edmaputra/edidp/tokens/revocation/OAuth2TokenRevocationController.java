package io.github.edmaputra.edidp.tokens.revocation;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for RFC 7009 OAuth 2.0 Token Revocation.
 * <p>
 * Endpoint: POST /oauth2/revoke
 * Authentication: HTTP Basic Auth (client_id:client_secret)
 * Request Parameters: token (required), token_type_hint (optional)
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
@RequestMapping({"/oauth2/revoke", "/t/{tenant}/oauth2/revoke"})
@Slf4j
@RequiredArgsConstructor
public class OAuth2TokenRevocationController {

  private final RevokeTokenService revokeTokenService;

  @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<?> revoke(
      @PathVariable(value = "tenant", required = false) String tenant,
      @RequestParam(value = "token", required = false) String token,
      @RequestParam(value = "token_type_hint", required = false) String tokenTypeHint,
      HttpServletRequest request) {

    RevokeTokenResult result = revokeTokenService.revoke(
        new RevokeTokenCommand(token, tokenTypeHint, request.getHeader("Authorization")));

    if (result.status() == RevokeTokenResult.Status.OK) {
      return ResponseEntity.ok().build();
    }

    if (result.status() == RevokeTokenResult.Status.BAD_REQUEST) {
      return new ResponseEntity<>(result.body(), HttpStatus.BAD_REQUEST);
    }

    if (result.status() == RevokeTokenResult.Status.UNAUTHORIZED) {
      return new ResponseEntity<>(result.body(), HttpStatus.UNAUTHORIZED);
    }

    return new ResponseEntity<>(result.body(), HttpStatus.FORBIDDEN);
  }
}
