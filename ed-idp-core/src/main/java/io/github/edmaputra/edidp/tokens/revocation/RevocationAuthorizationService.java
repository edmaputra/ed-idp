package io.github.edmaputra.edidp.tokens.revocation;

import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Service evaluating whether a client possesses authority to revoke tokens.
 *
 * @author edmaputra
 * @since 0.0.1
 */
@Service
public class RevocationAuthorizationService {

  private static final String REVOCATION_SCOPE = "revocation";

  public boolean canRevoke(Set<String> clientScopes) {
    if (clientScopes == null || clientScopes.isEmpty()) {
      return false;
    }
    return clientScopes.contains(REVOCATION_SCOPE);
  }
}
