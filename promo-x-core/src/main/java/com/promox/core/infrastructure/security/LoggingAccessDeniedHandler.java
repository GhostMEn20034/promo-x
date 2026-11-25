package com.promox.core.infrastructure.security;

import com.promox.core.infrastructure.logging.HttpLogFields;
import com.promox.core.infrastructure.logging.LogKeys;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Logs a request rejected with 403, from an authenticated user without the role it needs, then answers it as the
 * resource server does by default. The user ID is already on the MDC.
 */
public class LoggingAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(LoggingAccessDeniedHandler.class);

    private final AccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        HttpLogFields.addRequest(log.atWarn(), request)
                .addKeyValue(LogKeys.STATUS, HttpServletResponse.SC_FORBIDDEN)
                .addKeyValue(LogKeys.ERROR_TYPE, exception.getClass().getSimpleName())
                .addKeyValue(LogKeys.REASON, exception.getMessage())
                .log("Request rejected: access denied");

        delegate.handle(request, response, exception);
    }
}
