package com.promox.core.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ServerHttpObservationFilter;
import org.springframework.web.servlet.DispatcherServlet;

import java.io.IOException;
import java.util.UUID;

/**
 * Sets the request ID on the MDC for the whole request and logs the requests that fail with a server error (5xx).
 *
 * <p>The request ID is always generated here, never taken from the request, so a caller can't reuse one ID to
 * mix its requests' log lines with others'. It's sent back in the {@value #REQUEST_ID_HEADER} header, for a
 * caller to quote when reporting a problem.
 *
 * <p>Runs right inside the observation filter, so its log lines have the trace and span IDs, and outside Spring
 * Security, so the authentication errors are logged with the request ID. Client errors (4xx) aren't logged here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(LogKeys.REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);

            if (response.getStatus() >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
                // An exception resolved to a 5xx response, by an @ExceptionHandler or by Spring MVC itself.
                logServerError(request, response.getStatus(), (Throwable) request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE));
            }
        } catch (ServletException | IOException | RuntimeException ex) {
            // The DispatcherServlet wraps a controller's exception in a ServletException; log the controller's own.
            Throwable cause = ex instanceof ServletException servletException && servletException.getRootCause() != null
                    ? servletException.getRootCause()
                    : ex;
            logServerError(request, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, cause);
            if (response.isCommitted()) {
                throw ex;
            }
            // Answered here instead of rethrown, so the container doesn't log the exception a second time
            // without the MDC. The error page (/error) renders the same 500 response as before.
            ServerHttpObservationFilter.findObservationContext(request).ifPresent(context -> context.setError(cause));
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, cause);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            MDC.remove(LogKeys.REQUEST_ID);
            // Set by UserIdLoggingFilter, inside Spring Security, and kept until here so the line above has it.
            MDC.remove(LogKeys.USER_ID);
        }
    }

    private static void logServerError(HttpServletRequest request, int status, Throwable cause) {
        HttpLogFields.addRequest(log.atError(), request)
                .addKeyValue(LogKeys.STATUS, status)
                .setCause(cause)
                .log("Request failed with a server error");
    }
}
