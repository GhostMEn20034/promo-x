package com.promox.core.infrastructure.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * Logs, at DEBUG, each request a controller receives and how it completed, under the controller's own logger.
 * Off by default; turned on for one controller by setting its logger to DEBUG, such as
 * {@code logging.level.com.promox.core.promotion.internal.web.PromotionController=DEBUG}.
 *
 * <p>Only the method, the path and its variables, the status and the duration are logged: never the body or the
 * query string, which may hold personal data.
 */
public class ControllerDebugLoggingInterceptor implements HandlerInterceptor {
    private static final String START_NANOS_ATTRIBUTE = ControllerDebugLoggingInterceptor.class.getName() + ".START_NANOS";

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        if (handler instanceof HandlerMethod handlerMethod) {
            Logger log = LoggerFactory.getLogger(handlerMethod.getBeanType());
            if (log.isDebugEnabled()) {
                request.setAttribute(START_NANOS_ATTRIBUTE, System.nanoTime());
                HttpLogFields.addRequest(log.atDebug(), request).log("Request received");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            @Nullable Exception ex
    ) {
        // Without the start time, DEBUG was off when the request was received.
        if (handler instanceof HandlerMethod handlerMethod
                && request.getAttribute(START_NANOS_ATTRIBUTE) instanceof Long startNanos) {
            var event = HttpLogFields.addRequest(LoggerFactory.getLogger(handlerMethod.getBeanType()).atDebug(), request)
                    .addKeyValue(LogKeys.STATUS, response.getStatus())
                    .addKeyValue(LogKeys.DURATION_MS, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            // Only an exception no @ExceptionHandler resolved; its stack trace is in the server error log line.
            if (ex != null) {
                event.addKeyValue(LogKeys.ERROR_TYPE, ex.getClass().getName());
            }
            event.log("Request completed");
        }
    }
}
