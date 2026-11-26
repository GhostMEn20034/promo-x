package com.promox.core.infrastructure.logging;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Adds the fields that describe an HTTP request to a log line, so the request's log lines share them.
 */
public final class HttpLogFields {

    private HttpLogFields() {}

    /**
     * Adds the method and path, without the query string, which may hold personal data.
     * Once the request has been mapped to a controller, also adds the controller method and the path variables,
     * such as {@code storefrontId}, under their own names.
     */
    public static LoggingEventBuilder addRequest(LoggingEventBuilder event, HttpServletRequest request) {
        event.addKeyValue(LogKeys.HTTP_METHOD, request.getMethod())
                .addKeyValue(LogKeys.PATH, request.getRequestURI());

        if (request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler) {
            event.addKeyValue(LogKeys.HANDLER, describe(handler));
        }

        if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> variables) {
            variables.forEach((name, value) -> event.addKeyValue(String.valueOf(name), value));
        }

        return event;
    }

    /** The controller method as {@code PromotionController#evaluate}. */
    public static String describe(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
    }
}
