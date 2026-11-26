package com.promox.core.infrastructure.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Runs one run of a scheduled job with the job's name and a new run ID on the MDC, so its log lines can be
 * told apart from other runs'. A run also gets its own trace ID from the scheduled task observation.
 */
public final class ScheduledJobLogging {

    private ScheduledJobLogging() {}

    /**
     * Runs the job and logs its failure, with the MDC still set, to the job's logger.
     *
     * <p>The exception isn't rethrown: for a repeating {@code @Scheduled} job, Spring would only log it again
     * without the MDC, then run the job at its next time as usual.
     */
    public static void run(Logger log, String jobName, Runnable job) {
        MDC.put(LogKeys.JOB_NAME, jobName);
        MDC.put(LogKeys.JOB_RUN_ID, UUID.randomUUID().toString());
        try {
            job.run();
        } catch (RuntimeException ex) {
            log.error("Scheduled job failed", ex);
        } finally {
            MDC.remove(LogKeys.JOB_NAME);
            MDC.remove(LogKeys.JOB_RUN_ID);
        }
    }
}
