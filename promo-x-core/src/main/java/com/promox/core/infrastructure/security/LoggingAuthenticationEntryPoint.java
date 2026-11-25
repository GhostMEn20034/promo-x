package com.promox.core.infrastructure.security;

import com.promox.core.infrastructure.logging.HttpLogFields;
import com.promox.core.infrastructure.logging.LogKeys;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Logs a request rejected with 401, for a missing, invalid or expired token, then answers it as the resource
 * server does by default.
 */
public class LoggingAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(LoggingAuthenticationEntryPoint.class);

    private final AuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        // The message says what was wrong, such as "Jwt expired at ...", and never contains the token.
        HttpLogFields.addRequest(log.atWarn(), request)
                .addKeyValue(LogKeys.STATUS, HttpServletResponse.SC_UNAUTHORIZED)
                .addKeyValue(LogKeys.ERROR_TYPE, exception.getClass().getSimpleName())
                .addKeyValue(LogKeys.REASON, exception.getMessage())
                .log("Request rejected: authentication failed");

        delegate.commence(request, response, exception);
    }
}
