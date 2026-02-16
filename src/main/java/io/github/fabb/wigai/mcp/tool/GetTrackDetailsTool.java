package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.validation.TrackTargetingContract;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.util.function.BiFunction;
import java.util.Map;

/**
 * MCP tool for retrieving detailed information about a specific track by index, name,
 * or the currently selected track.
 */
public class GetTrackDetailsTool {

    private static final String TOOL_NAME = "get_track_details";

    /**
     * Creates a "get_track_details" tool specification using the unified error handling system.
     *
     * @param bitwigApiFacade The BitwigApiFacade for track operations
     * @param logger          The structured logger for logging operations
     * @return A SyncToolSpecification for the "get_track_details" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "0-based index of the track"
                },
                "track_name": {
                  "type": "string",
                  "description": "Name of the track (exact match after trim + case-insensitive normalization)"
                },
                "get_selected": {
                  "type": "boolean",
                  "description": "If true, retrieves details for the currently selected track. Defaults to true when no parameter is provided."
                }
              },
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Retrieve detailed information for a specific track by index, name, or the currently selected track.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                TOOL_NAME,
                req.arguments(),
                logger,
                GetTrackDetailsTool::validateParameters,
                (validated) -> resolveTrackDetails(validated, bitwigApiFacade)
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private record ValidatedParams(Integer trackIndex, String trackName, boolean useSelectedTrackFallback) {}

    /**
     * Validates track-targeting selectors using the shared contract.
     */
    static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        TrackTargetingContract.TrackTargetSelectors selectors =
            TrackTargetingContract.parse(arguments, operation, true);
        return new ValidatedParams(
            selectors.trackIndex(),
            selectors.trackName(),
            selectors.useSelectedTrackFallback()
        );
    }

    private static Map<String, Object> resolveTrackDetails(
        ValidatedParams validated,
        BitwigApiFacade bitwigApiFacade
    ) throws BitwigApiException {
        boolean selectedOnlyRequest =
            validated.useSelectedTrackFallback()
                && validated.trackIndex() == null
                && validated.trackName() == null;

        if (selectedOnlyRequest) {
            Map<String, Object> selectedTrackDetails = bitwigApiFacade.getSelectedTrackDetails();
            if (selectedTrackDetails == null) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    TOOL_NAME,
                    "No track is currently selected. Provide track_index or track_name."
                );
            }
            return selectedTrackDetails;
        }

        int resolvedTrackIndex = bitwigApiFacade.resolveTrackIndex(
            validated.trackIndex(),
            validated.trackName(),
            validated.useSelectedTrackFallback(),
            TOOL_NAME
        );
        return bitwigApiFacade.getTrackDetailsByIndex(resolvedTrackIndex);
    }
}
