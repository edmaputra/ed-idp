package io.github.edmaputra.enhauthserv.tokens.introspection;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * REST Controller for RFC 7662 OAuth 2.0 Token Introspection.
 *
 * Endpoint: POST /oauth2/introspect
 * Authentication: HTTP Basic Auth (client_id:client_secret)
 * Request Parameters: token (required)
 * Response: JSON with RFC 7662 fields (active, scope, client_id, etc.)
 */
@RestController
@RequestMapping({"/oauth2/introspect", "/t/{tenant}/oauth2/introspect"})
@Slf4j
@RequiredArgsConstructor
public class OAuth2TokenIntrospectionController {

    private final IntrospectTokenService introspectTokenService;

    /**
     * RFC 7662 Token Introspection endpoint.
     *
     * @param token the token to introspect (required)
     * @param request the HTTP request (for extracting client credentials and IP address)
     * @return RFC 7662 introspection response
     */
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> introspect(
        @PathVariable(required = false) String tenant,
            @RequestParam(value = "token", required = false) String token,
            HttpServletRequest request) {

        log.debug("Received token introspection request");
        IntrospectTokenResult result = introspectTokenService.introspect(
            new IntrospectTokenCommand(token, request.getHeader("Authorization")));

        if (result.status() == IntrospectTokenResult.Status.OK) {
            boolean isActive = (Boolean) result.body().getOrDefault("active", false);
            log.info("Token introspection completed - Active: {}", isActive);
            return ResponseEntity.ok(result.body());
        }

        if (result.status() == IntrospectTokenResult.Status.BAD_REQUEST) {
            log.warn("Token introspection request without token parameter");
            return new ResponseEntity<>(result.body(), HttpStatus.BAD_REQUEST);
        }

        if (result.status() == IntrospectTokenResult.Status.UNAUTHORIZED) {
            log.warn("Token introspection request without valid Basic Auth credentials");
            return new ResponseEntity<>(result.body(), HttpStatus.UNAUTHORIZED);
        }

        return new ResponseEntity<>(result.body(), HttpStatus.FORBIDDEN);
    }
}
