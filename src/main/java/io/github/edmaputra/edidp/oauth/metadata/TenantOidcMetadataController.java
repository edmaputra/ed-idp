package io.github.edmaputra.edidp.oauth.metadata;

import io.github.edmaputra.edidp.tenancy.TenantIssuerService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing per-tenant OpenID Connect Discovery metadata documents.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@RestController
public class TenantOidcMetadataController {

  private final String baseIssuerUri;
  private final TenantIssuerService tenantIssuerService;

  public TenantOidcMetadataController(
      @Value("${app.issuer-uri}") String baseIssuerUri,
      TenantIssuerService tenantIssuerService) {
    this.baseIssuerUri = baseIssuerUri;
    this.tenantIssuerService = tenantIssuerService;
  }

  @GetMapping(value = "/t/{tenant}/.well-known/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> metadata(@PathVariable("tenant") String tenant) {
    String tenantIssuer = tenantIssuerService.resolveTenantIssuer(baseIssuerUri, tenant);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("issuer", tenantIssuer);
    response.put("authorization_endpoint", tenantIssuer + "/oauth2/authorize");
    response.put("token_endpoint", tenantIssuer + "/oauth2/token");
    response.put("jwks_uri", tenantIssuer + "/oauth2/jwks");
    response.put("userinfo_endpoint", tenantIssuer + "/userinfo");
    response.put("introspection_endpoint", tenantIssuer + "/oauth2/introspect");
    response.put("revocation_endpoint", tenantIssuer + "/oauth2/revoke");
    response.put("end_session_endpoint", tenantIssuer + "/connect/logout");
    return ResponseEntity.ok(response);
  }
}