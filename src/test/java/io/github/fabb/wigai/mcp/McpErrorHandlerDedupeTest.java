package io.github.fabb.wigai.mcp;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.retry.RetryPolicy;
import io.github.fabb.wigai.mcp.idempotency.IdempotencyCache;
import io.github.fabb.wigai.mcp.tool.*;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Idempotency dedupe behavior on the shared tool execution path.
 *
 * <p>Split out of the original 1718-line McpErrorHandlerTest along the same seams
 * as the production extraction.
 */
class McpErrorHandlerDedupeTest {

    @BeforeEach
    void resetIdempotencyCache() {
        // Fresh cache per test to prevent cross-test interference
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));
    }
    private StructuredLogger createMockLogger() {
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-dedupe");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);
        return mockLogger;
    }

    @Test
    void testDedupe_SameToolSameRequestId_ReturnsFirstResultWithoutReExecution() {
        // AC 1: same (tool_name, request_id) returns cached result without re-executing
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "dedupe-test-1");

        // First call — executes normally
        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "transport_started");
            }
        );

        // Second call — same tool + request_id, should NOT re-execute
        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Task should execute only once; second call is dedupe hit");
        assertSame(first, second, "Dedupe hit should return exact same result object");
        assertFalse(first.isError());
        String json = ((McpSchema.TextContent) first.content().get(0)).text();
        assertTrue(json.contains("\"status\":\"success\""));
    }

    @Test
    void testDedupe_SameRequestIdDifferentTools_NoCrossContamination() {
        // AC 1: (tool_name, request_id) key prevents cross-tool collision
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "shared-req-id");

        // Call transport_start
        McpSchema.CallToolResult startResult = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        // Call transport_stop with same request_id — different tool, should execute
        McpSchema.CallToolResult stopResult = McpErrorHandler.executeWithErrorHandling(
            "transport_stop", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "stopped");
            }
        );

        assertEquals(2, executions.get(), "Different tools with same request_id should both execute");
        assertNotSame(startResult, stopResult);
    }

    @Test
    void testDedupe_NoRequestId_NeverDedupes() {
        // AC 3: no request_id means no dedupe (backward compatible)
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> argsNoReqId = new HashMap<>();
        argsNoReqId.put("parameter_index", 0);

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", argsNoReqId, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", argsNoReqId, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(), "Without request_id, every call should execute");
    }

    @Test
    void testDedupe_NullArguments_NeverDedupes() {
        // AC 3: null arguments means no dedupe
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", null, mockLogger, RetryPolicy.DEFAULT,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", null, mockLogger, RetryPolicy.DEFAULT,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(), "Null arguments should never dedupe");
    }

    @Test
    void testDedupe_InvalidRequestId_NeverDedupes() {
        // AC 3: invalid request_id (non-string, empty) means no dedupe
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> argsInvalid = new HashMap<>();
        argsInvalid.put("request_id", 12345); // non-string

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", argsInvalid, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", argsInvalid, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(), "Non-string request_id should never dedupe");
    }

    @Test
    void testDedupe_CachedErrorResult_ReturnsSameError() {
        // AC 1: cached result includes errors — first failure is returned on dedupe hit
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "error-dedupe-test");

        // First call fails
        McpSchema.CallToolResult firstError = McpErrorHandler.executeWithErrorHandling(
            "launch_clip", args, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                throw new BitwigApiException(ErrorCode.CLIP_NOT_FOUND, "launch_clip", "Clip not found");
            }
        );

        // Second call with same key — should return cached error, not re-execute
        McpSchema.CallToolResult secondError = McpErrorHandler.executeWithErrorHandling(
            "launch_clip", args, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Error result should also be cached for dedupe");
        assertSame(firstError, secondError);
        assertTrue(firstError.isError());
        String json = ((McpSchema.TextContent) firstError.content().get(0)).text();
        assertTrue(json.contains("\"code\":\"CLIP_NOT_FOUND\""));
    }

    @Test
    void testDedupe_TtlExpiry_AllowsReExecution() {
        // AC 2: after TTL expiry, re-execution occurs
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "ttl-test");

        // First call
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "first");
            }
        );

        // Advance clock past TTL
        clock.set(1000L + 60_000L);

        // Second call — TTL expired, should re-execute
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "second");
            }
        );

        assertEquals(2, executions.get(), "After TTL expiry, request should re-execute");
    }

    @Test
    void testDedupe_ReadOnlyPath_NeverDedupes() {
        // AC 3: 3-arg overload (read-only) should never dedupe
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        // Even though we use same operation name, 3-arg path has no arguments
        McpErrorHandler.executeWithErrorHandling(
            "status", mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("transport", "playing");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "status", mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("transport", "playing");
            }
        );

        assertEquals(2, executions.get(), "Read-only path should never dedupe");
    }

    @Test
    void testDedupe_NonMutatingOperationWithRequestId_DoesNotDedupesOnSharedPath() {
        // Review follow-up: shared execution path must enforce mutating-only dedupe.
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);
        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "status-req-1");

        McpErrorHandler.executeWithErrorHandling(
            "status", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("transport", "playing");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "status", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("transport", "playing");
            }
        );

        assertEquals(2, executions.get(),
            "Non-mutating operations must not dedupe even when request_id is provided");
    }

    @Test
    void testExecuteWithValidation_MutatingOperationWithRequestId_Dedupes() {
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger validations = new AtomicInteger(0);
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "validation-dedupe");
        args.put("parameter_index", 7);

        McpSchema.CallToolResult first = McpErrorHandler.executeWithValidation(
            "set_selected_device_parameter",
            args,
            mockLogger,
            (arguments, operation) -> {
                validations.incrementAndGet();
                return ((Number) arguments.get("parameter_index")).intValue();
            },
            validatedIndex -> {
                executions.incrementAndGet();
                return Map.of("action", "parameter_set", "parameter_index", validatedIndex);
            }
        );

        McpSchema.CallToolResult second = McpErrorHandler.executeWithValidation(
            "set_selected_device_parameter",
            args,
            mockLogger,
            (arguments, operation) -> {
                validations.incrementAndGet();
                return ((Number) arguments.get("parameter_index")).intValue();
            },
            validatedIndex -> {
                executions.incrementAndGet();
                return Map.of("action", "parameter_set", "parameter_index", validatedIndex);
            }
        );

        assertEquals(1, validations.get(), "Validation should run once when dedupe returns cached result");
        assertEquals(1, executions.get(), "Mutating executeWithValidation path should dedupe by request_id");
        assertSame(first, second, "Dedupe hit should return cached first result");
    }

    @Test
    void testExecuteWithValidation_NonMutatingOperationWithRequestId_DoesNotDedupe() {
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger validations = new AtomicInteger(0);
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "validation-non-mutating");
        args.put("track_index", 1);

        McpErrorHandler.executeWithValidation(
            "list_tracks",
            args,
            mockLogger,
            (arguments, operation) -> {
                validations.incrementAndGet();
                return ((Number) arguments.get("track_index")).intValue();
            },
            validated -> {
                executions.incrementAndGet();
                return Map.of("track_index", validated);
            }
        );

        McpErrorHandler.executeWithValidation(
            "list_tracks",
            args,
            mockLogger,
            (arguments, operation) -> {
                validations.incrementAndGet();
                return ((Number) arguments.get("track_index")).intValue();
            },
            validated -> {
                executions.incrementAndGet();
                return Map.of("track_index", validated);
            }
        );

        assertEquals(2, validations.get(), "Non-mutating executeWithValidation should validate each call");
        assertEquals(2, executions.get(), "Non-mutating executeWithValidation should not dedupe");
    }

    @Test
    void testDedupe_RetryThenDedupe_FirstSuccessfulResultCached() {
        // Coherence: retry succeeds on 2nd attempt, then dedupe returns that success
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "retry-then-dedupe");

        // First call — retries once, then succeeds
        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                int attempt = executions.incrementAndGet();
                if (attempt == 1) {
                    throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "transport_start", "transient");
                }
                return Map.of("action", "transport_started");
            }
        );

        // Second call — dedupe hit, no execution
        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(2, executions.get(), "First call retried once (2 attempts); second call deduped (0 attempts)");
        assertSame(first, second, "Dedupe should return the first successful result");
        assertFalse(first.isError());
    }

    @Test
    void testDedupe_EnvelopeFormatPreserved() {
        // Regression: dedupe hit must return identical envelope format
        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "envelope-test");

        // Success case
        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> Map.of("action", "transport_started", "message", "OK")
        );

        McpSchema.CallToolResult deduped = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> { throw new RuntimeException("should not reach"); }
        );

        String firstJson = ((McpSchema.TextContent) first.content().get(0)).text();
        String dedupedJson = ((McpSchema.TextContent) deduped.content().get(0)).text();

        assertEquals(firstJson, dedupedJson, "Dedupe hit must return identical JSON envelope");
        assertTrue(firstJson.contains("\"status\":\"success\""));
        assertTrue(firstJson.contains("\"data\""));
        assertFalse(firstJson.contains("\"dedupe\""), "No dedupe metadata should leak into response envelope");
    }

    @Test
    void testDedupe_OperationNamePreserved() {
        // Regression: error.operation must still equal MCP tool name after dedupe
        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "op-name-test");

        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args, mockLogger, RetryPolicy.NONE,
            () -> {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "internal_op", "No device");
            }
        );

        McpSchema.CallToolResult deduped = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args, mockLogger, RetryPolicy.NONE,
            () -> { throw new RuntimeException("should not reach"); }
        );

        String json = ((McpSchema.TextContent) first.content().get(0)).text();
        assertTrue(json.contains("\"operation\":\"set_selected_device_parameter\""),
            "error.operation should equal MCP tool name, got: " + json);
        assertSame(first, deduped);
    }

    @Test
    void testDedupe_ControlCharRequestId_SkipsDedupe() {
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "test\ninjection");

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(),
            "Control-char request_id must skip dedupe — both calls execute");
    }

    @Test
    void testDedupe_OversizedRequestId_SkipsDedupe() {
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        String oversized = "x".repeat(RequestContextExtractor.MAX_RAW_REQUEST_ID_LENGTH + 1);
        Map<String, Object> args = new HashMap<>();
        args.put("request_id", oversized);

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started_again");
            }
        );

        assertEquals(2, executions.get(), "Oversized request_id must skip dedupe — both calls execute");
    }

    @Test
    void testDedupe_LongRequestIds_DifferAfterTruncationPoint_NoCacheCollision() {
        // Two request_ids that share the first 256 chars but differ after must NOT collide
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        String prefix = "a".repeat(256);
        String id1 = prefix + "-suffix-ONE";
        String id2 = prefix + "-suffix-TWO";

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", id1);
        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", id2);

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args1, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "first");
            }
        );

        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args2, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "second");
            }
        );

        assertEquals(2, executions.get(),
                "Different long request_ids must not collide even if they share the first 256 chars");
    }

    @Test
    void testDedupe_HitLogIncludesOutcome_Success() {
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "log-outcome-success");

        // First call — success
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> Map.of("action", "started")
        );

        // Second call — dedupe hit, should log outcome=success
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> { throw new RuntimeException("should not reach"); }
        );

        verify(mockLogger).info(any(), eq("transport_start"),
                contains("outcome=success"));
    }

    @Test
    void testDedupe_HitLogIncludesOutcome_Error() {
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "log-outcome-error");

        // First call — error
        McpErrorHandler.executeWithErrorHandling(
            "launch_clip", args, mockLogger, RetryPolicy.NONE,
            () -> { throw new BitwigApiException(ErrorCode.CLIP_NOT_FOUND, "launch_clip", "Not found"); }
        );

        // Second call — dedupe hit, should log outcome=error
        McpErrorHandler.executeWithErrorHandling(
            "launch_clip", args, mockLogger, RetryPolicy.NONE,
            () -> { throw new RuntimeException("should not reach"); }
        );

        verify(mockLogger).info(any(), eq("launch_clip"),
                contains("outcome=error"));
    }

    @Test
    void testDedupe_MissDoesNotLogDedupeHit() {
        AtomicLong clock = new AtomicLong(1000L);
        McpErrorHandler.setIdempotencyCache(new IdempotencyCache(60_000L, 1000, clock::get));

        StructuredLogger mockLogger = createMockLogger();

        Map<String, Object> args = new HashMap<>();
        args.put("request_id", "no-hit-log");

        // First call — cache miss, should NOT log "Dedupe hit"
        McpErrorHandler.executeWithErrorHandling(
            "transport_start", args, mockLogger,
            () -> Map.of("action", "started")
        );

        verify(mockLogger, never()).info(any(), any(), contains("Dedupe hit"));
    }

    @Test
    void testDedupe_SamePayload_ReturnsCachedResult() {
        // Same request_id + same payload = normal dedupe hit
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "payload-same");
        args1.put("parameter_index", 3);
        args1.put("value", 0.5);

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "payload-same");
        args2.put("parameter_index", 3);
        args2.put("value", 0.5);

        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args1, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "parameter_set");
            }
        );

        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args2, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Identical payload must dedupe normally");
        assertSame(first, second);
    }

    @Test
    void testDedupe_DifferentPayload_RejectsWithError() {
        // Same request_id but different payload = conflict error, not stale cache hit
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "payload-conflict");
        args1.put("parameter_index", 3);
        args1.put("value", 0.5);

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "payload-conflict");
        args2.put("parameter_index", 7);
        args2.put("value", 0.9);

        McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args1, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "parameter_set");
            }
        );

        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args2, mockLogger, RetryPolicy.NONE,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Mismatched payload must not re-execute");
        assertTrue(second.isError(), "Mismatched payload replay must return an error");
        String json = ((McpSchema.TextContent) second.content().get(0)).text();
        assertTrue(json.contains("\"code\":\"INVALID_PARAMETER\""),
            "Error code should be INVALID_PARAMETER for payload mismatch, got: " + json);
        assertTrue(json.contains("request_id"),
            "Error message should mention request_id, got: " + json);
    }

    @Test
    void testDedupe_RequestIdOnlyPayload_DedupesSameEmptyPayload() {
        // request_id is the only argument — both calls have identical (empty) payload fingerprint
        StructuredLogger mockLogger = createMockLogger();
        AtomicInteger executions = new AtomicInteger(0);

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "empty-payload-1");

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "empty-payload-1");

        McpSchema.CallToolResult first = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args1, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "started");
            }
        );

        McpSchema.CallToolResult second = McpErrorHandler.executeWithErrorHandling(
            "transport_start", args2, mockLogger,
            () -> {
                executions.incrementAndGet();
                return Map.of("action", "should_not_reach");
            }
        );

        assertEquals(1, executions.get(), "Same empty payload should dedupe");
        assertSame(first, second);
    }

    @Test
    void testDedupe_PayloadMismatch_RoutedThroughTimedOperationTelemetry() {
        StructuredLogger mockLogger = mock(StructuredLogger.class);
        when(mockLogger.generateOperationId()).thenReturn("op-telemetry");
        StructuredLogger.TimedOperation mockTimedOp = mock(StructuredLogger.TimedOperation.class);
        when(mockLogger.startTimedOperation(any(), any(), any())).thenReturn(mockTimedOp);

        Map<String, Object> args1 = new HashMap<>();
        args1.put("request_id", "telemetry-mismatch");
        args1.put("parameter_index", 3);

        McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args1, mockLogger, RetryPolicy.NONE,
            () -> Map.of("action", "set")
        );

        Map<String, Object> args2 = new HashMap<>();
        args2.put("request_id", "telemetry-mismatch");
        args2.put("parameter_index", 7);

        McpErrorHandler.executeWithErrorHandling(
            "set_selected_device_parameter", args2, mockLogger, RetryPolicy.NONE,
            () -> Map.of("action", "should_not_reach")
        );

        // Verify timed operation failure was recorded for the mismatch path
        verify(mockTimedOp).failure(eq(ErrorCode.INVALID_PARAMETER),
                contains("different payload"));
    }
}
