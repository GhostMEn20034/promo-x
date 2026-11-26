package com.promox.core.infrastructure.security;

import com.promox.core.infrastructure.logging.UserIdLoggingFilter;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class DefaultSecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) {
        // Log the rejected requests; set on both, for a missing token and for an invalid one.
        var authenticationEntryPoint = new LoggingAuthenticationEntryPoint();
        var accessDeniedHandler = new LoggingAccessDeniedHandler();

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                    .requestMatchers(EndpointRequest.to("health")).permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .oauth2ResourceServer(o -> o
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter)))
            // Once the token is authenticated, so every later log line of the request has the user ID.
            .addFilterAfter(new UserIdLoggingFilter(), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtAuthoritiesConverter());
        return converter;
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        var c = new CorsConfiguration();
        c.setAllowedOrigins(properties.allowedOrigins());
        c.setAllowedMethods(properties.allowedMethods());
        c.setAllowedHeaders(properties.allowedHeaders());
        c.setAllowCredentials(properties.allowCredentials());
        c.setMaxAge(properties.maxAge());

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);

        return source;
    }
}
