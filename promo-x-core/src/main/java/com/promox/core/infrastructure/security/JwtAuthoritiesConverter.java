package com.promox.core.infrastructure.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


import org.jspecify.annotations.NonNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;


/**
 * Converts a {@link Jwt} into Spring Security authorities.
 * <p>
 * Reads the list of role names from the {@value #ROLES_CLAIM} claim and creates a
 * {@link SimpleGrantedAuthority} for each one, prefixing the role name with
 * {@value #ROLE_PREFIX} so that {@code hasRole(...)} checks work.
 * If the claim is missing, no authorities are returned.
 *
 * <h4>Example</h4>
 * Token claims:
 * <pre>{@code
 * {
 *   "roles": [
 *     "promotion-manager",
 *     "employee"
 *   ],
 *   // ... other claims
 * }
 * }</pre>
 * Resulting authorities:
 * <pre>
 * ROLE_promotion-manager
 * ROLE_employee
 * </pre>
 * These can then be checked with {@code hasRole('employee')} or
 * {@code hasAuthority('ROLE_employee')}.
 */
public class JwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Extracts the roles from the {@value #ROLES_CLAIM} claim and maps each one to a
     * {@link SimpleGrantedAuthority} with the {@value #ROLE_PREFIX} prefix.
     *
     * @param jwt the validated token
     * @return the authorities for the token's roles, or an empty list if the claim is absent
     */
    @Override
    public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) return List.of();

        return roles.stream()
            .map(r -> new SimpleGrantedAuthority(ROLE_PREFIX + r))
            .collect(Collectors.toList());
    }
}
