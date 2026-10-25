package com.promox.core.testfixtures.security;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class WithMockJwtSecurityContextFactory implements WithSecurityContextFactory<WithMockJwt> {
    @Override
    public @NonNull SecurityContext createSecurityContext(WithMockJwt annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        // Collect all granted authorities (Map the "SCOPE_" and "ROLE_")
        List<GrantedAuthority> authorities = new ArrayList<>();

        Arrays.stream(annotation.scopes())
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .forEach(authorities::add);

        Arrays.stream(annotation.roles())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);


        Jwt.Builder jwtBuilder = Jwt.withTokenValue("mocked-token-value")
                .header("alg", "none")
                .subject(annotation.username())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        if (annotation.scopes().length > 0) {
            jwtBuilder.claim("scope", String.join(" ", annotation.scopes()));
        }

        if (annotation.roles().length > 0) {
            jwtBuilder.claim("roles", List.of(annotation.roles()));
        }

        Jwt jwt = jwtBuilder.build();
        var authentication = new JwtAuthenticationToken(jwt, authorities, annotation.username());

        context.setAuthentication(authentication);
        return context;
    }
}
