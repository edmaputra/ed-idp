package io.github.edmaputra.edidp.claims;

public record UserProfileData(
    String username,
    String fullName,
    String email,
    boolean emailVerified,
    String locale,
    String zoneinfo,
    String department,
    String tenant,
    long updatedAt) {}
