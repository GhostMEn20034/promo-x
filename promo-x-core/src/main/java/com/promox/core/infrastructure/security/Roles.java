package com.promox.core.infrastructure.security;

/**
 * Names of the roles issued by the identity provider in the {@code roles} claim.
 * <p>
 * Values are compile-time constants, so they can be concatenated into
 * {@code @PreAuthorize} expressions, e.g. {@code "hasRole('" + Roles.ADMIN + "')"}.
 * {@link JwtAuthoritiesConverter} adds the {@code ROLE_} prefix, so the names are used as is.
 */
public final class Roles {
    public static final String ADMIN = "admin";
    public static final String EMPLOYEE = "employee";
    public static final String AUDITOR = "auditor";
    public static final String PROMOTION_MANAGER = "promotion-manager";
    public static final String INTEGRATION_CLIENT = "integration-client";

    private Roles() {}
}
