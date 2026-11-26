package com.promox.core.infrastructure.logging;

import com.promox.core.shared.IntegrationTestBase;
import com.promox.core.testfixtures.security.WithMockAnonymousUser;
import com.promox.core.testfixtures.security.WithMockJwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
@Import(RequestLoggingIntegrationTest.LoggingTestController.class)
class RequestLoggingIntegrationTest extends IntegrationTestBase {
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @RestController
    @RequestMapping("/logging-test/widgets/{widgetId}")
    static class LoggingTestController {

        @GetMapping
        String ok(@PathVariable String widgetId) {
            return widgetId;
        }

        @GetMapping("/fail")
        String fail(@PathVariable String widgetId) {
            throw new IllegalStateException("Widget store is down");
        }

        @GetMapping("/unavailable")
        String unavailable(@PathVariable String widgetId) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Try again later");
        }

        @GetMapping("/admin")
        @PreAuthorize("hasRole('admin')")
        String admin(@PathVariable String widgetId) {
            return widgetId;
        }
    }

    @Test
    @WithMockJwt(username = "user-42")
    @DisplayName("Should log an unhandled exception as a server error, with the request's context and stack trace")
    void unhandledException_ShouldBeLoggedWithContext(CapturedOutput output) throws Exception {
        String requestId = requestIdOf(mockMvc.perform(get("/api/logging-test/widgets/{widgetId}/fail", "7"))
                .andExpect(status().isInternalServerError()));

        JsonNode line = singleLine(output, requestId, "Request failed with a server error");
        assertThat(line.get("level").asString()).isEqualTo("ERROR");
        assertThat(line.get("logger_name").asString()).isEqualTo(RequestLoggingFilter.class.getName());
        assertThat(line.get("@timestamp").asString()).isNotBlank();
        assertThat(line.get("thread_name").asString()).isNotBlank();
        assertThat(line.get("app_name").asString()).isEqualTo("promo-x-core");
        assertThat(line.get("environment").asString()).isEqualTo("unknown");
        assertThat(line.get("userId").asString()).isEqualTo("user-42");
        assertThat(line.get("traceId").asString()).matches("[0-9a-f]{32}");
        assertThat(line.get("spanId").asString()).matches("[0-9a-f]{16}");
        assertThat(line.get("httpMethod").asString()).isEqualTo("GET");
        assertThat(line.get("path").asString()).isEqualTo("/api/logging-test/widgets/7/fail");
        assertThat(line.get("handler").asString()).isEqualTo("LoggingTestController#fail");
        assertThat(line.get("widgetId").asString()).isEqualTo("7");
        assertThat(line.get("status").asInt()).isEqualTo(500);
        assertThat(line.get("stack_trace").asString()).startsWith("java.lang.IllegalStateException: Widget store is down");
    }

    @Test
    @WithMockJwt
    @DisplayName("Should log an exception that Spring MVC resolved to a 5xx status, with its stack trace")
    void resolvedServerError_ShouldBeLogged(CapturedOutput output) throws Exception {
        String requestId = requestIdOf(mockMvc.perform(get("/api/logging-test/widgets/{widgetId}/unavailable", "7"))
                .andExpect(status().isServiceUnavailable()));

        JsonNode line = singleLine(output, requestId, "Request failed with a server error");
        assertThat(line.get("status").asInt()).isEqualTo(503);
        assertThat(line.get("stack_trace").asString()).contains("ResponseStatusException");
    }

    @Test
    @WithMockJwt(username = "user-42")
    @DisplayName("Should clear the request ID and the user ID from the MDC once the request ends")
    void request_ShouldClearItsMdc() throws Exception {
        mockMvc.perform(get("/api/logging-test/widgets/{widgetId}/fail", "7"))
                .andExpect(status().isInternalServerError());

        assertThat(MDC.get(LogKeys.REQUEST_ID)).isNull();
        assertThat(MDC.get(LogKeys.USER_ID)).isNull();
    }

    @Test
    @WithMockJwt
    @DisplayName("Should generate a new request ID for every request, ignoring the one a caller sends")
    void requestId_ShouldAlwaysBeGenerated() throws Exception {
        String first = requestIdOf(mockMvc.perform(get("/api/logging-test/widgets/{widgetId}", "7")
                .header("X-Request-Id", "same-id")));
        String second = requestIdOf(mockMvc.perform(get("/api/logging-test/widgets/{widgetId}", "7")
                .header("X-Request-Id", "same-id")));

        assertThat(first).matches(UUID_PATTERN).isNotEqualTo("same-id");
        assertThat(second).matches(UUID_PATTERN).isNotEqualTo(first);
    }

    @Test
    @WithMockAnonymousUser
    @DisplayName("Should log a request rejected with 401, without a user ID")
    void unauthenticatedRequest_ShouldBeLogged(CapturedOutput output) throws Exception {
        String requestId = requestIdOf(mockMvc.perform(get("/api/logging-test/widgets/{widgetId}", "7"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate")));

        JsonNode line = singleLine(output, requestId, "Request rejected: authentication failed");
        assertThat(line.get("level").asString()).isEqualTo("WARN");
        assertThat(line.get("status").asInt()).isEqualTo(401);
        assertThat(line.get("errorType").asString()).isEqualTo("InsufficientAuthenticationException");
        assertThat(line.get("reason").asString()).isNotBlank();
        assertThat(line.get("path").asString()).isEqualTo("/api/logging-test/widgets/7");
        assertThat(line.has("userId")).isFalse();
        assertThat(line.has("stack_trace")).isFalse();
    }

    @Test
    @WithMockJwt(username = "user-42")
    @DisplayName("Should log a request rejected with 403, with the user ID")
    void accessDenied_ShouldBeLoggedWithUser(CapturedOutput output) throws Exception {
        String requestId = requestIdOf(mockMvc.perform(get("/api/logging-test/widgets/{widgetId}/admin", "7"))
                .andExpect(status().isForbidden()));

        JsonNode line = singleLine(output, requestId, "Request rejected: access denied");
        assertThat(line.get("level").asString()).isEqualTo("WARN");
        assertThat(line.get("status").asInt()).isEqualTo(403);
        assertThat(line.get("userId").asString()).isEqualTo("user-42");
    }

    @Test
    @WithMockJwt
    @DisplayName("Should not log client errors, such as a resource that doesn't exist")
    void clientError_ShouldNotBeLogged(CapturedOutput output) throws Exception {
        String requestId = requestIdOf(mockMvc.perform(get("/api/storefront/{storefrontId}", 999_999))
                .andExpect(status().isNotFound()));

        assertThat(linesOf(output, requestId)).isEmpty();
    }

    @Test
    @WithMockJwt
    @DisplayName("Should log a controller's requests at DEBUG only while its logger is set to DEBUG")
    void controllerDebugLines_ShouldFollowTheControllersLogLevel(CapturedOutput output) throws Exception {
        String debugOffRequestId = requestIdOf(mockMvc.perform(get("/api/logging-test/widgets/{widgetId}", "7"))
                .andExpect(status().isOk()));
        assertThat(linesOf(output, debugOffRequestId)).isEmpty();

        LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());
        String loggerName = LoggingTestController.class.getName();
        loggingSystem.setLogLevel(loggerName, LogLevel.DEBUG);
        String requestId;
        try {
            requestId = requestIdOf(mockMvc.perform(get("/api/logging-test/widgets/{widgetId}", "7"))
                    .andExpect(status().isOk()));
        } finally {
            loggingSystem.setLogLevel(loggerName, null);
        }

        JsonNode received = singleLine(output, requestId, "Request received");
        assertThat(received.get("level").asString()).isEqualTo("DEBUG");
        assertThat(received.get("logger_name").asString()).isEqualTo(loggerName);
        assertThat(received.get("handler").asString()).isEqualTo("LoggingTestController#ok");
        assertThat(received.get("widgetId").asString()).isEqualTo("7");

        JsonNode completed = singleLine(output, requestId, "Request completed");
        assertThat(completed.get("status").asInt()).isEqualTo(200);
        assertThat(completed.get("durationMs").asLong()).isNotNegative();
    }

    /** The request ID the server generated, as sent back in the response header. */
    private static String requestIdOf(ResultActions result) {
        String requestId = result.andReturn().getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(requestId).matches(UUID_PATTERN);
        return requestId;
    }

    /** The JSON log lines written for the request with the given ID. */
    private static List<JsonNode> linesOf(CapturedOutput output, String requestId) {
        return Arrays.stream(output.getOut().split("\\R"))
                .filter(line -> line.startsWith("{"))
                .map(JSON::readTree)
                .filter(line -> line.has(LogKeys.REQUEST_ID) && line.get(LogKeys.REQUEST_ID).asString().equals(requestId))
                .toList();
    }

    private static JsonNode singleLine(CapturedOutput output, String requestId, String message) {
        List<JsonNode> lines = linesOf(output, requestId).stream()
                .filter(line -> line.get("message").asString().equals(message))
                .toList();
        assertThat(lines).hasSize(1);
        return lines.getFirst();
    }
}
