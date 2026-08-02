package com.promox.core.infrastructure.web.exception;

import org.springframework.context.MessageSourceResolvable;

public final class ValidationErrorCodes {
    private ValidationErrorCodes() {}

    static String resolve(
            MessageSourceResolvable resolvable
    ) {
        String[] codes = resolvable.getCodes();

        if (codes == null || codes.length == 0) {
            return "UNKNOWN";
        }

        return toErrorCode(codes[codes.length - 1]);
    }

    private static String toErrorCode(String code) {
        return code
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toUpperCase();
    }
}
