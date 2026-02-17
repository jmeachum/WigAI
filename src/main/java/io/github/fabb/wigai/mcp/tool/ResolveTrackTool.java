package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for resolving fuzzy track queries into deterministic candidate lists.
 */
public class ResolveTrackTool {

    private static final String TOOL_NAME = "resolve_track";

    /**
     * Creates a "resolve_track" tool specification.
     *
     * @param bitwigApiFacade The Bitwig API facade
     * @param logger The structured logger
     * @return The MCP sync tool specification
     */
    public static McpServerFeatures.SyncToolSpecification specification(
        BitwigApiFacade bitwigApiFacade,
        StructuredLogger logger
    ) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "Track query string used for deterministic candidate matching (exact, then prefix, then substring)"
                }
              },
              "required": ["query"],
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Resolve a track query into deterministic candidates for explicit user confirmation. Never performs mutating actions.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                TOOL_NAME,
                req.arguments(),
                logger,
                ResolveTrackTool::validateParameters,
                (validated) -> bitwigApiFacade.resolveTrack(validated.query(), TOOL_NAME)
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private record ValidatedParams(String query) {
    }

    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        String query = ParameterValidator.validateRequiredString(arguments, "query", operation);
        query = ParameterValidator.validateNotEmpty(query, "query", operation);
        return new ValidatedParams(query);
    }
}
