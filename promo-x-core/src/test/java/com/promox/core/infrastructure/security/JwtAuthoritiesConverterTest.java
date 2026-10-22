package com.promox.core.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthoritiesConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Access token payload as issued by the promo-x-dev Keycloak realm. */
    private static final String KEYCLOAK_TOKEN_CLAIMS = """
        {
          "exp": 1792965143,
          "iat": 1792964843,
          "jti": "onrtna:133a0593-b629-c5e3-b5c5-c2af53421838",
          "iss": "http://localhost:8080/realms/promo-x-dev",
          "aud": ["promo-x-api", "account"],
          "sub": "2f258f0d-f82a-4734-a602-dffb5d60bbe1",
          "typ": "Bearer",
          "azp": "promo-x-web",
          "sid": "RlPWhMqSlAw2DPgSFYqfNxnq",
          "acr": "1",
          "allowed-origins": ["http://localhost:4200"],
          "realm_access": {
            "roles": ["offline_access", "admin", "uma_authorization", "employee", "default-roles-promo-x-dev"]
          },
          "resource_access": {
            "promo-x-api": {
              "roles": ["promotion-manager", "auditor", "integration-client"]
            },
            "account": {
              "roles": ["manage-account", "manage-account-links", "view-profile"]
            }
          },
          "scope": "openid email profile",
          "email_verified": true,
          "roles": ["admin", "employee"],
          "name": "Test User",
          "preferred_username": "testusr44",
          "given_name": "Test",
          "family_name": "User",
          "email": "test@gmail.com"
        }
        """;

    private final JwtAuthoritiesConverter converter = new JwtAuthoritiesConverter();

    @Nested
    @DisplayName("Top-level roles claim")
    class RolesClaimTests {

        @Test
        @DisplayName("Should map every role of the top-level roles claim to a ROLE_ prefixed authority")
        void convert_keycloakToken_mapsTopLevelRolesWithPrefix() {
            Collection<GrantedAuthority> authorities = converter.convert(keycloakToken(claims -> {}));

            assertThat(authorityNames(authorities)).containsExactlyInAnyOrder("ROLE_admin", "ROLE_employee");
        }

        @Test
        @DisplayName("Should produce authorities matching the Roles constants used in @PreAuthorize")
        void convert_rolesFromConstants_matchHasRoleChecks() {
            Jwt jwt = keycloakToken(claims -> claims.put("roles", List.of(
                Roles.ADMIN, Roles.EMPLOYEE, Roles.AUDITOR, Roles.PROMOTION_MANAGER, Roles.INTEGRATION_CLIENT
            )));

            assertThat(authorityNames(Objects.requireNonNull(converter.convert(jwt)))).containsExactlyInAnyOrder(
                "ROLE_" + Roles.ADMIN,
                "ROLE_" + Roles.EMPLOYEE,
                "ROLE_" + Roles.AUDITOR,
                "ROLE_" + Roles.PROMOTION_MANAGER,
                "ROLE_" + Roles.INTEGRATION_CLIENT
            );
        }

        @Test
        @DisplayName("Should return no authorities when the token has no roles claim")
        void convert_rolesClaimMissing_returnsEmpty() {
            Jwt jwt = keycloakToken(claims -> claims.remove("roles"));

            assertThat(converter.convert(jwt)).isEmpty();
        }

        @Test
        @DisplayName("Should return no authorities when the roles claim is an empty list")
        void convert_rolesClaimEmpty_returnsEmpty() {
            Jwt jwt = keycloakToken(claims -> claims.put("roles", List.of()));

            assertThat(converter.convert(jwt)).isEmpty();
        }

        @Test
        @DisplayName("Should map a roles claim sent as a single string to one authority")
        void convert_rolesClaimIsSingleString_mapsOneAuthority() {
            Jwt jwt = keycloakToken(claims -> claims.put("roles", "admin"));

            assertThat(authorityNames(Objects.requireNonNull(converter.convert(jwt)))).containsExactly("ROLE_admin");
        }

        @Test
        @DisplayName("Should skip null entries in the roles claim")
        void convert_rolesClaimHasNullEntry_skipsIt() {
            Jwt jwt = keycloakToken(claims -> claims.put("roles", Arrays.asList("admin", null)));

            assertThat(authorityNames(Objects.requireNonNull(converter.convert(jwt)))).containsExactly("ROLE_admin");
        }

        @Test
        @DisplayName("Should keep role names as they are, so a differently cased role does not satisfy hasRole")
        void convert_roleNameCase_isPreserved() {
            Jwt jwt = keycloakToken(claims -> claims.put("roles", List.of("Admin")));

            assertThat(authorityNames(Objects.requireNonNull(converter.convert(jwt))))
                .containsExactly("ROLE_Admin")
                .doesNotContain("ROLE_" + Roles.ADMIN);
        }
    }

    @Nested
    @DisplayName("Claims that are not mapped")
    class IgnoredClaimTests {

        @Test
        @DisplayName("Should not map realm roles from realm_access")
        void convert_realmAccessRoles_areIgnored() {
            Jwt jwt = keycloakToken(claims -> claims.remove("roles"));

            assertThat(converter.convert(jwt)).isEmpty();
        }

        @Test
        @DisplayName("Should not map client roles from resource_access, even for the promo-x-api client")
        void convert_resourceAccessRoles_areIgnored() {
            Collection<String> names = authorityNames(Objects.requireNonNull(converter.convert(keycloakToken(claims -> {
            }))));

            assertThat(names).doesNotContain(
                "ROLE_promotion-manager", "ROLE_auditor", "ROLE_integration-client",
                "ROLE_manage-account", "ROLE_manage-account-links", "ROLE_view-profile",
                "ROLE_offline_access", "ROLE_uma_authorization", "ROLE_default-roles-promo-x-dev"
            );
        }

        @Test
        @DisplayName("Should not map scopes, so no SCOPE_ authorities are produced")
        void convert_scopeClaim_isIgnored() {
            Collection<String> names = authorityNames(Objects.requireNonNull(converter.convert(keycloakToken(claims -> {
            }))));

            assertThat(names).noneMatch(name -> name.startsWith("SCOPE_"));
        }
    }

    @Nested
    @DisplayName("Wiring in DefaultSecurityConfig")
    class SecurityConfigWiringTests {

        @Test
        @DisplayName("Should build a JwtAuthenticationToken with the role authorities and the subject as name")
        void jwtAuthenticationConverter_keycloakToken_usesRolesAndSubject() {
            Jwt jwt = keycloakToken(claims -> {});

            AbstractAuthenticationToken authentication =
                new DefaultSecurityConfig().jwtAuthenticationConverter().convert(jwt);

            assertThat(authentication).isInstanceOf(JwtAuthenticationToken.class);
            assertThat(authentication.getName()).isEqualTo("2f258f0d-f82a-4734-a602-dffb5d60bbe1");
            // Spring Security 7 also records how the user authenticated as a factor authority
            assertThat(authorityNames(authentication.getAuthorities()))
                .containsExactlyInAnyOrder("ROLE_admin", "ROLE_employee", FactorGrantedAuthority.BEARER_AUTHORITY);
        }
    }

    /**
     * Builds a {@link Jwt} from {@link #KEYCLOAK_TOKEN_CLAIMS}. {@code iat} and {@code exp} are turned
     * into {@link Instant}s, as the JWT decoder does, before the customizer can change the claims.
     */
    @SuppressWarnings("unchecked")
    private static Jwt keycloakToken(Consumer<Map<String, Object>> customizer) {
        Map<String, Object> claims = new LinkedHashMap<>(OBJECT_MAPPER.readValue(KEYCLOAK_TOKEN_CLAIMS, Map.class));
        claims.put("iat", Instant.ofEpochSecond(((Number) claims.get("iat")).longValue()));
        claims.put("exp", Instant.ofEpochSecond(((Number) claims.get("exp")).longValue()));
        customizer.accept(claims);

        return Jwt.withTokenValue("keycloak-access-token")
            .header("alg", "RS256")
            .claims(c -> c.putAll(claims))
            .build();
    }

    private static List<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
