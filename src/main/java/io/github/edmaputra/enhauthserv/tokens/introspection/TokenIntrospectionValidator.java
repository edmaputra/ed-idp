package io.github.edmaputra.enhauthserv.tokens.introspection;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for validating tokens and formatting RFC 7662 token introspection responses.
 *
 * This service:
 * - Validates JWT token signatures and expiration
 * - Extracts token claims (scope, client_id, subject, etc.)
 * - Formats responses according to RFC 7662 specification
 * - Handles both valid and invalid tokens gracefully
 */
@Service
@RequiredArgsConstructor
public class TokenIntrospectionValidator {

    private final JwtDecoder jwtDecoder;
    private final OAuth2AuthorizationService authorizationService;

    /**
     * Introspects a token and returns an RFC 7662 compliant response.
     *
     * @param token the token to introspect (typically an access token)
     * @return map containing RFC 7662 response fields
     */
    public Map<String, Object> introspect(String token) {
        Map<String, Object> response = new HashMap<>();

        try {
            OAuth2Authorization authorization =
                authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
            if (authorization == null || authorization.getAccessToken() == null
                || !authorization.getAccessToken().isActive()) {
                response.put("active", false);
                return response;
            }

            Jwt jwt = jwtDecoder.decode(token);
            
            // Token is valid - extract claims and build response
            response.put("active", true);
            response.put("token_type", jwt.getClaimAsString("token_type"));
            
            // Extract scope
            String scope = jwt.getClaimAsString("scope");
            if (scope != null) {
                response.put("scope", scope);
            }

            // Extract client_id (typically stored as 'client_id' claim)
            String clientId = jwt.getClaimAsString("client_id");
            if (clientId != null) {
                response.put("client_id", clientId);
            }

            // Extract subject (user identifier)
            String subject = jwt.getClaimAsString("sub");
            if (subject != null) {
                response.put("sub", subject);
                // Also include 'username' for compatibility with some clients
                response.put("username", subject);
            }

            // Extract expiration time
            if (jwt.getExpiresAt() != null) {
                response.put("exp", jwt.getExpiresAt().getEpochSecond());
            }

            // Extract issued at time
            if (jwt.getIssuedAt() != null) {
                response.put("iat", jwt.getIssuedAt().getEpochSecond());
            }

            // Extract JTI (JWT ID for tracking)
            String jti = jwt.getClaimAsString("jti");
            if (jti != null) {
                response.put("jti", jti);
            }

            // Extract issuer
            String issuer = jwt.getClaimAsString("iss");
            if (issuer != null) {
                response.put("iss", issuer);
            }

            // Extract authorized party (for OIDC compatibility)
            String azp = jwt.getClaimAsString("azp");
            if (azp != null) {
                response.put("azp", azp);
            }

        } catch (JwtException e) {
            // Token is invalid, expired, or malformed
            response.put("active", false);
        }

        return response;
    }
}
