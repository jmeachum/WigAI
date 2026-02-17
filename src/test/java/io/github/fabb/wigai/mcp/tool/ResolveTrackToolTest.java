package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResolveTrackToolTest {

    @Mock
    private BitwigApiFacade bitwigApiFacade;
    @Mock
    private StructuredLogger structuredLogger;
    @Mock
    private Logger baseLogger;
    @Mock
    private StructuredLogger.TimedOperation timedOperation;
    @Mock
    private McpSyncServerExchange exchange;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-rt-1");
        when(structuredLogger.startTimedOperation(any(), any(), any())).thenReturn(timedOperation);
    }

    @Test
    void testSpecificationCreation() {
        McpServerFeatures.SyncToolSpecification spec = ResolveTrackTool.specification(bitwigApiFacade, structuredLogger);

        assertNotNull(spec);
        assertEquals("resolve_track", spec.tool().name());
        assertNotNull(spec.tool().description());
        assertNotNull(spec.tool().inputSchema());
        assertNotNull(spec.callHandler());
    }

    @Test
    void testSuccessfulCallReturnsOrderedCandidates() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ambiguous", true);
        payload.put(
            "candidates",
            List.of(
                new LinkedHashMap<>(Map.of(
                    "track_index", 2,
                    "track_name", "Drums",
                    "match_type", "exact"
                )),
                new LinkedHashMap<>(Map.of(
                    "track_index", 5,
                    "track_name", "Drum Bus",
                    "match_type", "prefix"
                ))
            )
        );
        when(bitwigApiFacade.resolveTrack(any(), eq("resolve_track"))).thenReturn(payload);

        McpServerFeatures.SyncToolSpecification spec = ResolveTrackTool.specification(bitwigApiFacade, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("resolve_track")
            .arguments(Map.of("query", "  drum  "))
            .build();

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        assertNotNull(result);
        assertFalse(result.isError());

        JsonNode data = McpResponseTestUtils.validateObjectResponse(result);
        assertTrue(data.get("ambiguous").asBoolean());
        assertEquals(2, data.get("candidates").size());
        assertEquals(2, data.get("candidates").get(0).get("track_index").asInt());
        assertEquals("Drums", data.get("candidates").get(0).get("track_name").asText());
        assertEquals("exact", data.get("candidates").get(0).get("match_type").asText());

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(bitwigApiFacade).resolveTrack(queryCaptor.capture(), eq("resolve_track"));
        assertEquals("drum", queryCaptor.getValue());
    }

    @Test
    void testMissingQueryReturnsMissingRequiredParameter() throws Exception {
        McpServerFeatures.SyncToolSpecification spec = ResolveTrackTool.specification(bitwigApiFacade, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("resolve_track")
            .arguments(Map.of())
            .build();

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("MISSING_REQUIRED_PARAMETER", error.get("code").asText());
        assertEquals("resolve_track", error.get("operation").asText());
    }

    @Test
    void testBlankQueryReturnsEmptyParameter() throws Exception {
        McpServerFeatures.SyncToolSpecification spec = ResolveTrackTool.specification(bitwigApiFacade, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("resolve_track")
            .arguments(Map.of("query", "   "))
            .build();

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("EMPTY_PARAMETER", error.get("code").asText());
        assertEquals("resolve_track", error.get("operation").asText());
    }

    @Test
    void testNonStringQueryReturnsInvalidParameterType() throws Exception {
        McpServerFeatures.SyncToolSpecification spec = ResolveTrackTool.specification(bitwigApiFacade, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("resolve_track")
            .arguments(Map.of("query", 123))
            .build();

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_PARAMETER_TYPE", error.get("code").asText());
        assertEquals("resolve_track", error.get("operation").asText());
    }

    @Test
    void testTrackNotFoundReturnsCanonicalErrorEnvelope() throws Exception {
        when(bitwigApiFacade.resolveTrack(eq("missing"), eq("resolve_track")))
            .thenThrow(new BitwigApiException(
                ErrorCode.TRACK_NOT_FOUND,
                "resolve_track",
                "No tracks matched query 'missing'. Use list_tracks to inspect available tracks."
            ));

        McpServerFeatures.SyncToolSpecification spec = ResolveTrackTool.specification(bitwigApiFacade, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("resolve_track")
            .arguments(Map.of("query", "missing"))
            .build();

        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        JsonNode response = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text());
        assertEquals("error", response.get("status").asText());
        assertEquals("TRACK_NOT_FOUND", response.get("error").get("code").asText());
        assertEquals("resolve_track", response.get("error").get("operation").asText());
    }
}
