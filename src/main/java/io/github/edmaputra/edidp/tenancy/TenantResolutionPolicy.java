package io.github.edmaputra.edidp.tenancy;

import java.util.Set;

public record TenantResolutionPolicy(
    boolean headerEnabled,
    boolean pathEnabled,
    boolean requireExplicitTenant,
    boolean enforceTrustedProxyForHeader,
    Set<String> trustedHeaderSources) {
}
