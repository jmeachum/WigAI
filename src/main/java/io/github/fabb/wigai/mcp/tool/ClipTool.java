package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.common.validation.TrackTargetingContract;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.features.ClipLaunchResult;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for launching clips by track selector and clip index using unified error handling architecture.
 * Implements the launch_clip MCP command as specified in the API reference.
 */
public class ClipTool {

    private static final String TOOL_NAME = "launch_clip";

    /**
     * Creates the MCP tool specification for clip launching.
     *
     * @param clipSceneController The controller for clip/scene operations
     * @param logger The structured logger for operation logging
     * @return MCP tool specification
     */
    public static McpServerFeatures.SyncToolSpecification launchClipSpecification(ClipSceneController clipSceneController, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "track_name": {
                  "type": "string",
                  "description": "Track name selector (exact match after trim + case-insensitive normalization)"
                },
                "clip_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based index of the clip slot to launch"
                },
                "track_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Track index selector (authoritative when both selectors are present)"
                },
                "request_id": {
                  "type": "string",
                  "description": "Optional correlation ID for request tracing (idempotency deduplication handled separately)"
                }
              },
              "required": ["clip_index"],
              "anyOf": [
                { "required": ["track_index"] },
                { "required": ["track_name"] }
              ]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Launch a specific clip in Bitwig by providing track_index or track_name with clip_index")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                TOOL_NAME,
                req.arguments(),
                logger,
                () -> {
                    // Parse and validate arguments
                    LaunchClipArguments args = parseArguments(req.arguments());

                    // Perform clip launch operation
                    ClipLaunchResult result = args.trackIndex() != null && args.trackName() == null
                        ? clipSceneController.launchClipWithSelectors(args.trackIndex(), null, args.clipIndex())
                        : args.trackIndex() == null
                        ? clipSceneController.launchClip(args.trackName(), args.clipIndex())
                        : clipSceneController.launchClip(args.trackName(), args.clipIndex(), args.trackIndex());

                    if (result.isSuccess()) {
                        Map<String, Object> data = new java.util.LinkedHashMap<>();
                        data.put("action", "clip_launched");
                        String resolvedTrackName = result.getTrackName() != null ? result.getTrackName() : args.trackName();
                        Integer resolvedTrackIndex = result.getTrackIndex() != null ? result.getTrackIndex() : args.trackIndex();
                        data.put("track_name", resolvedTrackName);
                        data.put("track_index", resolvedTrackIndex);
                        data.put("clip_index", args.clipIndex());
                        data.put("message", result.getMessage());
                        return data;
                    } else if (result.isAmbiguous()) {
                        throw new BitwigApiException(
                            ErrorCode.INVALID_PARAMETER,
                            TOOL_NAME,
                            result.getMessage(),
                            Map.of(
                                "reason", "ambiguous_track_name",
                                "track_name", args.trackName(),
                                "clip_index", args.clipIndex(),
                                "confirmation_parameter", result.getConfirmationParameter(),
                                "candidates", result.getCandidates()
                            )
                        );
                    } else {
                        ErrorCode errorCode = ErrorCode.fromString(result.getErrorCode());
                        throw new BitwigApiException(errorCode, TOOL_NAME, result.getMessage());
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Parses the MCP tool arguments into a structured format.
     *
     * @param arguments Raw arguments map from MCP request
     * @return Parsed and validated LaunchClipArguments
     * @throws IllegalArgumentException if arguments are invalid
     */
    private static LaunchClipArguments parseArguments(Map<String, Object> arguments) {
        int clipIndex = ParameterValidator.validateRequiredIndexInteger(arguments, "clip_index", TOOL_NAME);
        clipIndex = ParameterValidator.validateClipIndex(clipIndex, TOOL_NAME);

        TrackTargetingContract.TrackTargetSelectors selectors =
            TrackTargetingContract.parse(arguments, TOOL_NAME, false);
        Integer trackIndex = selectors.trackIndex();
        String trackName = selectors.trackName();
        if (trackIndex == null && trackName == null) {
            throw new BitwigApiException(
                ErrorCode.MISSING_REQUIRED_PARAMETER,
                TOOL_NAME,
                "At least one of track_index or track_name must be provided",
                Map.of("required_one_of", java.util.List.of("track_index", "track_name"))
            );
        }

        return new LaunchClipArguments(trackName, clipIndex, trackIndex);
    }

    /**
     * Data record for validated launch clip arguments.
     *
     * @param trackName The name of the track (exact match after trim + case-insensitive normalization)
     * @param clipIndex The zero-based clip slot index
     * @param trackIndex Optional explicit track index confirmation
     */
    public record LaunchClipArguments(
        @JsonProperty("track_name") String trackName,
        @JsonProperty("clip_index") int clipIndex,
        @JsonProperty("track_index") Integer trackIndex
    ) {}
}
