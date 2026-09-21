package io.github.edmaputra.edidp.oauth.metadata;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TenantJwksController {

  private final JWKSource<SecurityContext> jwkSource;

  public TenantJwksController(JWKSource<SecurityContext> jwkSource) {
    this.jwkSource = jwkSource;
  }

  @GetMapping(value = "/t/{tenant}/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> jwks() throws Exception {
    List<JWK> keys = jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
    Map<String, Object> body = new HashMap<>();
    body.put("keys", keys.stream().map(JWK::toPublicJWK).map(JWK::toJSONObject).collect(Collectors.toList()));
    return ResponseEntity.ok(body);
  }
}