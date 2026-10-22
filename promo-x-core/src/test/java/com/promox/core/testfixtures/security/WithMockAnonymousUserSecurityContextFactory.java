package com.promox.core.testfixtures.security;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;


public class WithMockAnonymousUserSecurityContextFactory implements WithSecurityContextFactory<WithMockAnonymousUser> {
    // Same principal and authority that AnonymousAuthenticationFilter uses by default
    private static final String KEY = "anonymous-key";
    private static final String PRINCIPAL = "anonymousUser";
    private static final String AUTHORITY = "ROLE_ANONYMOUS";

    @Override
    public @NonNull SecurityContext createSecurityContext(WithMockAnonymousUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AnonymousAuthenticationToken(
            KEY,
            PRINCIPAL,
            AuthorityUtils.createAuthorityList(AUTHORITY)
        ));
        return context;
    }
}
