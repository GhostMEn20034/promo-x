package com.promox.core.infrastructure.logging;

/**
 * Names of the fields this application adds to its JSON log lines, so every log line uses the same names.
 *
 * <p>MDC keys are on every line written while they are set; key-value pairs
 * ({@code log.atWarn().addKeyValue(...)}) are on that one line only. Both become top-level fields of the line.
 * The trace and span IDs ({@code traceId}, {@code spanId}) are set by Micrometer Tracing.
 */
public final class LogKeys {
    // MDC
    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String JOB_NAME = "jobName";
    public static final String JOB_RUN_ID = "jobRunId";

    // Key-value pairs of HTTP log lines
    public static final String HTTP_METHOD = "httpMethod";
    public static final String PATH = "path";
    public static final String STATUS = "status";
    public static final String HANDLER = "handler";
    public static final String DURATION_MS = "durationMs";
    public static final String REASON = "reason";
    public static final String ERROR_TYPE = "errorType";

    private LogKeys() {}
}
