package com.promox.core.infrastructure.web.exception;

import tools.jackson.core.JacksonException;

import java.util.stream.Collectors;

public class JacksonExceptionUtils {
    private JacksonExceptionUtils() {}

    /**
     * Converts a Jackson exception path into dot notation.
     *
     * Examples:
     * - name
     * - address.street
     * - products[0].price
     */
    public static String toJsonPath(JacksonException exception) {
        return exception.getPath().stream()
                .map(reference -> {
                    if (reference.getPropertyName() != null) {
                        return reference.getPropertyName();
                    }

                    if (reference.getIndex() >= 0) {
                        return "[" + reference.getIndex() + "]";
                    }

                    return "?";
                })
                .collect(Collectors.joining("."));
    }
}
