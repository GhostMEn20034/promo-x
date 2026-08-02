package com.promox.core.infrastructure.web.exception;

public enum ErrorSource {
    REQUEST_BODY,
    QUERY_PARAMETER,
    PATH_VARIABLE,
    HEADER,
    COOKIE,
    DOMAIN,
    UNKNOWN
}
