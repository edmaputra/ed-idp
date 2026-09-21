package io.github.edmaputra.edidp.tenancy;

import java.util.Set;

/**
 * Policy definition for resolving tenant identities across request headers and path prefixes.
 *
 * @param headerEnabled                whether header-based tenant resolution is enabled
 * @param pathEnabled                  whether path-based tenant resolution is enabled
 * @param requireExplicitTenant        whether strict mode is active requiring explicit tenant identifiers
 * @param enforceTrustedProxyForHeader whether proxy source verification is enforced for headers
 * @param trustedHeaderSources         set of trusted IP sources permitted to supply tenant headers
 * @author edmaputra
 * @since 0.0.1
 */
public record TenantResolutionPolicy(
    boolean headerEnabled,
    boolean pathEnabled,
    boolean requireExplicitTenant,
    boolean enforceTrustedProxyForHeader,
    Set<String> trustedHeaderSources) {

  public TenantResolutionPolicy {
    trustedHeaderSources = trustedHeaderSources == null ? Set.of() : Set.copyOf(trustedHeaderSources);
  }
}
