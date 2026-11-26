package com.promox.core.infrastructure.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ScheduledJobLoggingTest {
    private static final Logger log = LoggerFactory.getLogger(ScheduledJobLoggingTest.class);

    @Test
    @DisplayName("Should set the job name and a new run ID on the MDC while the job runs, and remove them after")
    void run_ShouldSetTheJobsMdc() {
        List<String> runIds = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            ScheduledJobLogging.run(log, "test-job", () -> {
                assertThat(MDC.get(LogKeys.JOB_NAME)).isEqualTo("test-job");
                runIds.add(MDC.get(LogKeys.JOB_RUN_ID));
            });
        }

        assertThat(runIds).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(MDC.get(LogKeys.JOB_NAME)).isNull();
        assertThat(MDC.get(LogKeys.JOB_RUN_ID)).isNull();
    }

    @Test
    @DisplayName("Should log a failed run instead of rethrowing it, and still remove the MDC")
    void run_ShouldNotRethrowAFailure() {
        assertThatCode(() -> ScheduledJobLogging.run(log, "test-job", () -> {
            throw new IllegalStateException("boom");
        })).doesNotThrowAnyException();

        assertThat(MDC.get(LogKeys.JOB_NAME)).isNull();
        assertThat(MDC.get(LogKeys.JOB_RUN_ID)).isNull();
    }
}
