package com.promox.core.testfixtures.security;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Runs the test as an unauthenticated caller, the way Spring Security represents a request
 * without a bearer token. On a method it overrides a class-level {@link WithMockJwt}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockAnonymousUserSecurityContextFactory.class)
public @interface WithMockAnonymousUser {
}
