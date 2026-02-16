package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.validation.TrackTargetingContract;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for listing all devices on a specific track with detailed information.
 * Supports track identification by index, name, or selected track.
 */
public class ListDevicesOnTrackTool {

    /**
     * Creates a "list_devices_on_track" tool specification using the unified error handling system.
     *
     * @param bitwigApiFacade The BitwigApiFacade for track and device operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "list_devices_on_track" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "0-based index of the track",
                  "minimum": 0
                },
                "track_name": {
                  "type": "string",
                  "description": "Name of the track (exact match after trim + case-insensitive normalization)"
                },
                "get_selected": {
                  "type": "boolean",
                  "description": "If true, lists devices for the currently selected track. Defaults to true when no parameter is provided"
                }
              },
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("list_devices_on_track")
            .description("List all devices on a specific track with detailed information including name, type, and key states (bypassed, selected). Track can be identified by index, name, or as the currently selected track.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "list_devices_on_track",
                req.arguments(),
                logger,
                ListDevicesOnTrackTool::validateParameters,
                (validatedParams) -> bitwigApiFacade.getDevicesOnTrack(
                    validatedParams.trackIndex(),
                    validatedParams.trackName(),
                    validatedParams.useSelectedTrackFallback()
                )
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Validates the parameters for the list_devices_on_track tool.
     * Uses the shared track-targeting selector contract.
     *
     * @param arguments The raw arguments map
     * @param operation The operation name for error context
     * @return Validated parameters
     */
    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        TrackTargetingContract.TrackTargetSelectors selectors =
            TrackTargetingContract.parse(arguments, operation, true);
        return new ValidatedParams(
            selectors.trackIndex(),
            selectors.trackName(),
            selectors.useSelectedTrackFallback()
        );
    }

    /**
     * Record to hold validated parameters for the list_devices_on_track tool.
     */
    private record ValidatedParams(Integer trackIndex, String trackName, boolean useSelectedTrackFallback) {}
}
