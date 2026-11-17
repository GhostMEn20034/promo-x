package com.promox.core.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves every promo-x-core REST controller under {@value #API_PREFIX}, so a controller maps {@code /storefront}
 * and is called at {@code /api/storefront}.
 *
 * <p>Only the application's own controllers get the prefix. Actuator ({@code /actuator/health}) and the API docs
 * ({@code /swagger-ui}, {@code /v3/api-docs}) aren't controllers in this package and keep their paths.
 */
@Configuration
public class ApiPathConfig implements WebMvcConfigurer {
    public static final String API_PREFIX = "/api";

    private static final String APPLICATION_PACKAGE = "com.promox.core";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                API_PREFIX,
                HandlerTypePredicate.forAnnotation(RestController.class)
                        .and(HandlerTypePredicate.forBasePackage(APPLICATION_PACKAGE))
        );
    }
}
