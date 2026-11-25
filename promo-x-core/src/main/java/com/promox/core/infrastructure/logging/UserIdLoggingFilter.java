package com.promox.core.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sets the ID of the authenticated user (the token's {@code sub} claim) on the MDC.
 *
 * <p>Added to the Spring Security filter chain after the bearer token is authenticated, so it isn't a bean: as a
 * bean, it would also be registered as a servlet filter that runs before authentication.
 * The ID stays on the MDC until {@link RequestLoggingFilter} removes it at the end of the request, so the
 * server error logged there has it too.
 */
public class UserIdLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.getToken().getSubject() != null) {
            MDC.put(LogKeys.USER_ID, token.getToken().getSubject());
        }
        chain.doFilter(request, response);
    }
}
