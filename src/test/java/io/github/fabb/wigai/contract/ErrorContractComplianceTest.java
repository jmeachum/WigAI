package io.github.fabb.wigai.contract;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.features.DeviceController;
import io.github.fabb.wigai.mcp.tool.*;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


/**
 * Contract Compliance Tests for MCP Error Handling.
 *
 * <p>These tests enforce the error contract defined in {@code docs/project-context.md}.
 * They validate that error responses match the canonical error taxonomy, preventing
 * drift between documentation, implementation, and test assertions.
 *
 * <h2>Contract Source of Truth</h2>
 * <p>The canonical error code definitions are in {@code docs/project-context.md}:
 * <ul>
 *   <li>{@code MISSING_REQUIRED_PARAMETER} — parameter not provided in request</li>
 *   <li>{@code EMPTY_PARAMETER} — parameter provided but empty (empty string, empty array)</li>
 *   <li>{@code INVALID_PARAMETER} — parameter has wrong type or malformed structure</li>
 *   <li>{@code INVALID_PARAMETER_INDEX} — index outside valid bounds (e.g., parameter_index 0-7)</li>
 *   <li>{@code INVALID_RANGE} — numeric value outside allowed range (e.g., value 0.0-1.0)</li>
 *   <li>{@code BITWIG_API_ERROR} — Bitwig API call failed (external system error)</li>
 *   <li>{@code INTERNAL_ERROR} — unexpected internal failure (code bug, not API issue)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>Add new error scenarios to the appropriate {@code *ErrorScenarios()} method.
 * The parameterized tests will automatically verify contract compliance.
 *
 * <h2>When Tests Fail</h2>
 * <p>A failing test indicates contract violation. Before "fixing" the test:
 * <ol>
 *   <li>Check if the contract (project-context.md) needs updating</li>
 *   <li>If contract is correct, fix the implementation</li>
 *   <li>Never update tests to match incorrect implementation behavior</li>
 * </ol>
 *
 * @see io.github.fabb.wigai.common.error.ErrorCode
 * @see <a href="docs/project-context.md">Project Context - Error Code Semantics</a>
 */
@Tag("contract")
@DisplayName("Error Contract Compliance")
// TODO (TEA Review): Split this test class into smaller focused files (<300 lines). See test-review-1-3-standardize-baseline-tool-response-envelopes-align-with-status-tool-api-reference.md
class ErrorContractComplianceTest {

    // ========================================================================
    // CONTRACT DEFINITIONS
    // ========================================================================

    /**
     * Declarative error scenario definition.
     * Each scenario maps a condition to the expected error code per contract.
     */
    record ErrorScenario(
        String tool,
        String condition,
        ErrorCode expectedCode,
        String contractReference
    ) {
        /**
         * Creates a scenario with reference to project-context.md
         */
        static ErrorScenario of(String tool, String condition, ErrorCode code, String semantics) {
            return new ErrorScenario(tool, condition, code,
                "docs/project-context.md: " + semantics);
        }
    }

    // ========================================================================
    // VALIDATION ERROR SCENARIOS (Per Contract)
    // ========================================================================

    /**
     * Contract: MISSING_REQUIRED_PARAMETER — parameter not provided in request
     */
    static Stream<Arguments> missingRequiredParameterScenarios() {
        return Stream.of(
            Arguments.of(ErrorScenario.of(
                "launch_clip",
                "clip_index not provided",
                ErrorCode.MISSING_REQUIRED_PARAMETER,
                "parameter not provided in request"
            )),
            Arguments.of(ErrorScenario.of(
                "get_clips_in_scene",
                "scene_index not provided",
                ErrorCode.MISSING_REQUIRED_PARAMETER,
                "parameter not provided in request"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameter",
                "parameter_index not provided",
                ErrorCode.MISSING_REQUIRED_PARAMETER,
                "parameter not provided in request"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameter",
                "value not provided",
                ErrorCode.MISSING_REQUIRED_PARAMETER,
                "parameter not provided in request"
            )),
            Arguments.of(ErrorScenario.of(
                "session_launchSceneByName",
                "scene_name not provided",
                ErrorCode.MISSING_REQUIRED_PARAMETER,
                "parameter not provided in request"
            ))
        );
    }

    /**
     * Contract: EMPTY_PARAMETER — parameter provided but empty
     */
    static Stream<Arguments> emptyParameterScenarios() {
        return Stream.of(
            Arguments.of(ErrorScenario.of(
                "launch_clip",
                "track_name is empty string",
                ErrorCode.EMPTY_PARAMETER,
                "parameter provided but empty (empty string, empty array)"
            )),
            Arguments.of(ErrorScenario.of(
                "launch_clip",
                "track_name is whitespace only",
                ErrorCode.EMPTY_PARAMETER,
                "parameter provided but empty (empty string, empty array)"
            )),
            Arguments.of(ErrorScenario.of(
                "session_launchSceneByName",
                "scene_name is empty string",
                ErrorCode.EMPTY_PARAMETER,
                "parameter provided but empty (empty string, empty array)"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameters",
                "parameters is empty array",
                ErrorCode.EMPTY_PARAMETER,
                "parameter provided but empty (empty string, empty array)"
            ))
        );
    }

    /**
     * Contract: INVALID_PARAMETER — wrong type or malformed structure
     */
    static Stream<Arguments> invalidParameterScenarios() {
        return Stream.of(
            Arguments.of(ErrorScenario.of(
                "list_tracks",
                "type has invalid value",
                ErrorCode.INVALID_PARAMETER,
                "parameter has wrong type or malformed structure"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameters",
                "parameters is not an array",
                ErrorCode.INVALID_PARAMETER,
                "parameter has wrong type or malformed structure"
            )),
            Arguments.of(ErrorScenario.of(
                "list_devices_on_track",
                "conflicting track_index with get_selected",
                ErrorCode.INVALID_PARAMETER,
                "parameter has wrong type or malformed structure"
            )),
            Arguments.of(ErrorScenario.of(
                "get_clips_in_scene",
                "scene_index is non-integer (1.5)",
                ErrorCode.INVALID_PARAMETER,
                "parameter has wrong type (non-integer scene_index)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_device_details",
                "track_index is non-integer (1.5)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index conversion failure for track_index selector"
            )),
            Arguments.of(ErrorScenario.of(
                "get_device_details",
                "device_index is non-integer (1.5)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index conversion failure for device_index selector"
            ))
        );
    }

    /**
     * Contract: INVALID_PARAMETER_INDEX — index outside valid bounds
     */
    static Stream<Arguments> invalidParameterIndexScenarios() {
        return Stream.of(
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameter",
                "parameter_index is 8 (outside 0-7)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (e.g., parameter_index 0-7)"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameter",
                "parameter_index is -1",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (e.g., parameter_index 0-7)"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameters",
                "parameter entry has index 10",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (e.g., parameter_index 0-7)"
            )),
            Arguments.of(ErrorScenario.of(
                "launch_clip",
                "clip_index is -1",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (clip_index negative)"
            )),
            Arguments.of(ErrorScenario.of(
                "session_launchSceneByIndex",
                "scene_index is -1",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (scene_index negative)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_clips_in_scene",
                "scene_index is -1",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (scene_index negative)"
            )),
            Arguments.of(ErrorScenario.of(
                "list_devices_on_track",
                "track_index is -1",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (track_index negative)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_track_details",
                "track_index is -1",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (track_index negative)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_clips_in_scene",
                "scene_index is 999 (exceeds scene count)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (scene_index exceeds scene count)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_track_details",
                "track_index is 999 (exceeds track count)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (track_index exceeds track count)"
            )),
            Arguments.of(ErrorScenario.of(
                "list_devices_on_track",
                "track_index is 999 (exceeds track count)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (track_index exceeds track count)"
            )),
            Arguments.of(ErrorScenario.of(
                "session_launchSceneByIndex",
                "scene_index is 999 (exceeds track clip counts)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (scene_index exceeds track clip counts)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_device_details",
                "track_index overflow (4294967296)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (track_index overflow)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_device_details",
                "device_index overflow (4294967296)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (device_index overflow)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_device_details",
                "track_index is -1",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (track_index negative)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_device_details",
                "device_index is -1",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (device_index negative)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_clips_in_scene",
                "scene_index overflow (4294967296)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (scene_index overflow)"
            )),
            Arguments.of(ErrorScenario.of(
                "get_track_details",
                "track_index overflow (4294967296)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (track_index overflow)"
            )),
            Arguments.of(ErrorScenario.of(
                "list_devices_on_track",
                "track_index overflow (4294967296)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (track_index overflow)"
            )),
            Arguments.of(ErrorScenario.of(
                "launch_clip",
                "clip_index overflow (4294967296)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (clip_index overflow)"
            )),
            Arguments.of(ErrorScenario.of(
                "session_launchSceneByIndex",
                "scene_index overflow (4294967296)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (scene_index overflow)"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameter",
                "parameter_index overflow (4294967296)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (parameter_index overflow)"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameters",
                "parameter_index overflow (4294967296)",
                ErrorCode.INVALID_PARAMETER_INDEX,
                "index outside valid bounds (parameter_index overflow)"
            ))
        );
    }

    /**
     * Contract: INVALID_RANGE — numeric value outside allowed range
     */
    static Stream<Arguments> invalidRangeScenarios() {
        return Stream.of(
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameter",
                "value is 1.5 (outside 0.0-1.0)",
                ErrorCode.INVALID_RANGE,
                "numeric value outside allowed range (e.g., value 0.0-1.0)"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameter",
                "value is -0.1",
                ErrorCode.INVALID_RANGE,
                "numeric value outside allowed range (e.g., value 0.0-1.0)"
            ))
        );
    }

    // ========================================================================
    // STATE ERROR SCENARIOS (Per Contract)
    // ========================================================================

    /**
     * Contract: DEVICE_NOT_SELECTED — no device is currently selected
     */
    static Stream<Arguments> deviceNotSelectedScenarios() {
        return Stream.of(
            Arguments.of(ErrorScenario.of(
                "get_selected_device_parameters",
                "no device selected in Bitwig",
                ErrorCode.DEVICE_NOT_SELECTED,
                "state error - no device currently selected"
            )),
            Arguments.of(ErrorScenario.of(
                "set_selected_device_parameter",
                "no device selected in Bitwig",
                ErrorCode.DEVICE_NOT_SELECTED,
                "state error - no device currently selected"
            ))
        );
    }

    /**
     * Contract: TRACK_NOT_FOUND — specified track was not found
     */
    static Stream<Arguments> trackNotFoundScenarios() {
        return Stream.of(
            Arguments.of(ErrorScenario.of(
                "launch_clip",
                "track_name does not exist",
                ErrorCode.TRACK_NOT_FOUND,
                "state error - specified track was not found"
            )),
            Arguments.of(ErrorScenario.of(
                "get_track_details",
                "no track selected",
                ErrorCode.TRACK_NOT_FOUND,
                "state error - specified track was not found"
            ))
        );
    }

    /**
     * Contract: SCENE_NOT_FOUND — specified scene was not found
     */
    static Stream<Arguments> sceneNotFoundScenarios() {
        return Stream.of(
            Arguments.of(ErrorScenario.of(
                "session_launchSceneByName",
                "scene_name does not exist",
                ErrorCode.SCENE_NOT_FOUND,
                "state error - specified scene was not found"
            )),
            Arguments.of(ErrorScenario.of(
                "session_launchSceneByIndex",
                "no tracks in session",
                ErrorCode.SCENE_NOT_FOUND,
                "state error - no tracks found in Bitwig session"
            ))
        );
    }

    // ========================================================================
    // SYSTEM ERROR SCENARIOS (Per Contract)
    // ========================================================================

    /**
     * Contract: BITWIG_API_ERROR — Bitwig API call failed (external system error)
     */
    static Stream<Arguments> bitwigApiErrorScenarios() {
        return Stream.of(
            Arguments.of(ErrorScenario.of(
                "get_selected_device_parameters",
                "Bitwig API throws exception",
                ErrorCode.BITWIG_API_ERROR,
                "Bitwig API call failed (external system error)"
            )),
            Arguments.of(ErrorScenario.of(
                "list_scenes",
                "Bitwig API throws exception",
                ErrorCode.BITWIG_API_ERROR,
                "Bitwig API call failed (external system error)"
            ))
        );
    }

    // ========================================================================
    // CONTRACT ENFORCEMENT TESTS
    // ========================================================================

    @Nested
    @DisplayName("MISSING_REQUIRED_PARAMETER Contract")
    class MissingRequiredParameterContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.fabb.wigai.contract.ErrorContractComplianceTest#missingRequiredParameterScenarios")
        @DisplayName("Missing parameter returns MISSING_REQUIRED_PARAMETER")
        void missingParameterReturnsCorrectCode(ErrorScenario scenario) throws Exception {
            McpSchema.CallToolResult result = invokeToolForScenario(scenario);

            assertErrorCode(result, scenario);
        }
    }

    @Nested
    @DisplayName("EMPTY_PARAMETER Contract")
    class EmptyParameterContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.fabb.wigai.contract.ErrorContractComplianceTest#emptyParameterScenarios")
        @DisplayName("Empty parameter returns EMPTY_PARAMETER")
        void emptyParameterReturnsCorrectCode(ErrorScenario scenario) throws Exception {
            McpSchema.CallToolResult result = invokeToolForScenario(scenario);

            assertErrorCode(result, scenario);
        }
    }

    @Nested
    @DisplayName("INVALID_PARAMETER Contract")
    class InvalidParameterContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.fabb.wigai.contract.ErrorContractComplianceTest#invalidParameterScenarios")
        @DisplayName("Invalid parameter type/structure returns INVALID_PARAMETER")
        void invalidParameterReturnsCorrectCode(ErrorScenario scenario) throws Exception {
            McpSchema.CallToolResult result = invokeToolForScenario(scenario);

            assertErrorCode(result, scenario);
        }
    }

    @Nested
    @DisplayName("INVALID_PARAMETER_INDEX Contract")
    class InvalidParameterIndexContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.fabb.wigai.contract.ErrorContractComplianceTest#invalidParameterIndexScenarios")
        @DisplayName("Out-of-bounds index returns INVALID_PARAMETER_INDEX")
        void invalidIndexReturnsCorrectCode(ErrorScenario scenario) throws Exception {
            McpSchema.CallToolResult result = invokeToolForScenario(scenario);

            assertErrorCode(result, scenario);
        }
    }

    @Nested
    @DisplayName("INVALID_RANGE Contract")
    class InvalidRangeContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.fabb.wigai.contract.ErrorContractComplianceTest#invalidRangeScenarios")
        @DisplayName("Out-of-range value returns INVALID_RANGE")
        void invalidRangeReturnsCorrectCode(ErrorScenario scenario) throws Exception {
            McpSchema.CallToolResult result = invokeToolForScenario(scenario);

            assertErrorCode(result, scenario);
        }
    }

    @Nested
    @DisplayName("DEVICE_NOT_SELECTED Contract")
    class DeviceNotSelectedContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.fabb.wigai.contract.ErrorContractComplianceTest#deviceNotSelectedScenarios")
        @DisplayName("No device selected returns DEVICE_NOT_SELECTED")
        void noDeviceSelectedReturnsCorrectCode(ErrorScenario scenario) throws Exception {
            McpSchema.CallToolResult result = invokeToolForScenario(scenario);

            assertErrorCode(result, scenario);
        }
    }

    @Nested
    @DisplayName("TRACK_NOT_FOUND Contract")
    class TrackNotFoundContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.fabb.wigai.contract.ErrorContractComplianceTest#trackNotFoundScenarios")
        @DisplayName("Track not found returns TRACK_NOT_FOUND")
        void trackNotFoundReturnsCorrectCode(ErrorScenario scenario) throws Exception {
            McpSchema.CallToolResult result = invokeToolForScenario(scenario);

            assertErrorCode(result, scenario);
        }
    }

    @Nested
    @DisplayName("SCENE_NOT_FOUND Contract")
    class SceneNotFoundContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.fabb.wigai.contract.ErrorContractComplianceTest#sceneNotFoundScenarios")
        @DisplayName("Scene not found returns SCENE_NOT_FOUND")
        void sceneNotFoundReturnsCorrectCode(ErrorScenario scenario) throws Exception {
            McpSchema.CallToolResult result = invokeToolForScenario(scenario);

            assertErrorCode(result, scenario);
        }
    }

    @Nested
    @DisplayName("BITWIG_API_ERROR Contract")
    class BitwigApiErrorContract {

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.fabb.wigai.contract.ErrorContractComplianceTest#bitwigApiErrorScenarios")
        @DisplayName("Bitwig API failure returns BITWIG_API_ERROR")
        void bitwigApiErrorReturnsCorrectCode(ErrorScenario scenario) throws Exception {
            McpSchema.CallToolResult result = invokeToolForScenario(scenario);

            assertErrorCode(result, scenario);
        }
    }

    // ========================================================================
    // ERROR.OPERATION CONTRACT
    // ========================================================================

    @Nested
    @DisplayName("error.operation Contract")
    class ErrorOperationContract {

        @Test
        @DisplayName("error.operation always equals MCP tool name, not internal method")
        void errorOperationMatchesToolName() throws Exception {
            // Simulate controller throwing with internal operation name
            DeviceController deviceController = mock(DeviceController.class);
            when(deviceController.getSelectedDeviceParameters()).thenThrow(
                new BitwigApiException(ErrorCode.BITWIG_API_ERROR,
                    "getSelectedDeviceParameters",  // Internal method name
                    "API error")
            );

            StructuredLogger logger = mockStructuredLogger();
            McpServerFeatures.SyncToolSpecification spec =
                DeviceParamTool.getSelectedDeviceParametersSpecification(deviceController, logger);

            McpSchema.CallToolResult result = spec.callHandler().apply(
                mock(McpSyncServerExchange.class),
                buildRequest("get_selected_device_parameters", Map.of())
            );

            JsonNode error = McpResponseTestUtils.validateErrorResponse(result);

            // Contract: error.operation MUST be MCP tool name
            assertEquals("get_selected_device_parameters", error.get("operation").asText(),
                "Contract violation: error.operation must equal MCP tool name, not internal method. " +
                "See: docs/project-context.md - 'error.operation — always equals the MCP tool name'");
        }
    }

    // ========================================================================
    // TEST INFRASTRUCTURE
    // ========================================================================

    /**
     * Invokes the appropriate tool with error-triggering arguments based on scenario.
     */
    private McpSchema.CallToolResult invokeToolForScenario(ErrorScenario scenario) throws Exception {
        StructuredLogger logger = mockStructuredLogger();

        return switch (scenario.tool()) {
            case "launch_clip" -> invokeLaunchClip(scenario, logger);
            case "get_clips_in_scene" -> invokeGetClipsInScene(scenario, logger);
            case "set_selected_device_parameter" -> invokeSetSelectedDeviceParameter(scenario, logger);
            case "set_selected_device_parameters" -> invokeSetSelectedDeviceParameters(scenario, logger);
            case "session_launchSceneByName" -> invokeLaunchSceneByName(scenario, logger);
            case "session_launchSceneByIndex" -> invokeLaunchSceneByIndex(scenario, logger);
            case "list_tracks" -> invokeListTracks(scenario, logger);
            case "list_devices_on_track" -> invokeListDevicesOnTrack(scenario, logger);
            case "get_selected_device_parameters" -> invokeGetSelectedDeviceParameters(scenario, logger);
            case "get_track_details" -> invokeGetTrackDetails(scenario, logger);
            case "list_scenes" -> invokeListScenes(scenario, logger);
            case "get_device_details" -> invokeGetDeviceDetails(scenario, logger);
            default -> throw new IllegalArgumentException("Unknown tool: " + scenario.tool());
        };
    }

    private McpSchema.CallToolResult invokeLaunchClip(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        ClipSceneController controller = mock(ClipSceneController.class);

        // Configure mock based on scenario
        if (scenario.condition().contains("track_name does not exist")) {
            when(controller.launchClip(anyString(), anyInt()))
                .thenReturn(ClipSceneController.ClipLaunchResult.error("TRACK_NOT_FOUND", "Track not found"));
        }

        Map<String, Object> args = switch (scenario.condition()) {
            case "clip_index not provided" -> Map.of("track_name", "Test");
            case "track_name is empty string" -> Map.of("track_name", "", "clip_index", 0);
            case "track_name is whitespace only" -> Map.of("track_name", "   ", "clip_index", 0);
            case "clip_index is -1" -> Map.of("track_name", "Test", "clip_index", -1);
            case "clip_index overflow (4294967296)" -> Map.of("track_name", "Test", "clip_index", 4294967296.0);
            case "track_name does not exist" -> Map.of("track_name", "NonExistent", "clip_index", 0);
            default -> Map.of();
        };

        McpServerFeatures.SyncToolSpecification spec = ClipTool.launchClipSpecification(controller, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("launch_clip", args));
    }

    private McpSchema.CallToolResult invokeGetClipsInScene(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        ClipSceneController controller = mock(ClipSceneController.class);

        // Configure mock for out-of-bounds scene_index (controller throws INVALID_PARAMETER_INDEX)
        if (scenario.condition().contains("exceeds scene count")) {
            when(controller.getClipsInScene(eq(999), isNull()))
                .thenThrow(new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER_INDEX,
                    "get_clips_in_scene",
                    "Scene index out of bounds: 999",
                    Map.of("scene_index", 999)
                ));
        }

        Map<String, Object> args = switch (scenario.condition()) {
            case "scene_index not provided" -> Map.of();
            case "scene_index is -1" -> Map.of("scene_index", -1);
            case "scene_index is 999 (exceeds scene count)" -> Map.of("scene_index", 999);
            case "scene_index overflow (4294967296)" -> Map.of("scene_index", 4294967296.0);
            case "scene_index is non-integer (1.5)" -> Map.of("scene_index", 1.5);
            default -> Map.of();
        };

        McpServerFeatures.SyncToolSpecification spec = GetClipsInSceneTool.getClipsInSceneSpecification(controller, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("get_clips_in_scene", args));
    }

    private McpSchema.CallToolResult invokeSetSelectedDeviceParameter(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        DeviceController controller = mock(DeviceController.class);

        // Configure mock for state errors (void method requires doThrow pattern)
        if (scenario.condition().contains("no device selected")) {
            doThrow(new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "set_selected_device_parameter", "No device selected"))
                .when(controller).setSelectedDeviceParameter(anyInt(), anyDouble());
        }

        Map<String, Object> args = switch (scenario.condition()) {
            case "parameter_index not provided" -> Map.of("value", 0.5);
            case "value not provided" -> Map.of("parameter_index", 0);
            case "parameter_index is 8 (outside 0-7)" -> Map.of("parameter_index", 8, "value", 0.5);
            case "parameter_index is -1" -> Map.of("parameter_index", -1, "value", 0.5);
            case "parameter_index overflow (4294967296)" -> Map.of("parameter_index", 4294967296.0, "value", 0.5);
            case "value is 1.5 (outside 0.0-1.0)" -> Map.of("parameter_index", 0, "value", 1.5);
            case "value is -0.1" -> Map.of("parameter_index", 0, "value", -0.1);
            case "no device selected in Bitwig" -> Map.of("parameter_index", 0, "value", 0.5);
            default -> Map.of();
        };

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setSelectedDeviceParameterSpecification(controller, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("set_selected_device_parameter", args));
    }

    private McpSchema.CallToolResult invokeSetSelectedDeviceParameters(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        DeviceController controller = mock(DeviceController.class);

        Map<String, Object> args = switch (scenario.condition()) {
            case "parameters is empty array" -> Map.of("parameters", List.of());
            case "parameters is not an array" -> Map.of("parameters", "not-an-array");
            case "parameter entry has index 10" -> Map.of("parameters", List.of(Map.of("parameter_index", 10, "value", 0.5)));
            case "parameter_index overflow (4294967296)" -> Map.of("parameters", List.of(Map.of("parameter_index", 4294967296.0, "value", 0.5)));
            default -> Map.of();
        };

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.setMultipleDeviceParametersSpecification(controller, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("set_selected_device_parameters", args));
    }

    private McpSchema.CallToolResult invokeLaunchSceneByName(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        ClipSceneController controller = mock(ClipSceneController.class);

        if (scenario.condition().contains("does not exist")) {
            when(controller.launchSceneByName(anyString()))
                .thenReturn(ClipSceneController.SceneLaunchResult.error("SCENE_NOT_FOUND", "Scene not found"));
        }

        Map<String, Object> args = switch (scenario.condition()) {
            case "scene_name not provided" -> Map.of();
            case "scene_name is empty string" -> Map.of("scene_name", "");
            case "scene_name does not exist" -> Map.of("scene_name", "NonExistent");
            default -> Map.of();
        };

        McpServerFeatures.SyncToolSpecification spec = SceneByNameTool.launchSceneByNameSpecification(controller, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("session_launchSceneByName", args));
    }

    private McpSchema.CallToolResult invokeLaunchSceneByIndex(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        ClipSceneController controller = mock(ClipSceneController.class);

        if (scenario.condition().contains("exceeds track clip counts")) {
            when(controller.launchSceneByIndex(eq(999)))
                .thenReturn(ClipSceneController.SceneLaunchResult.error("INVALID_PARAMETER_INDEX", "Scene index 999 is out of bounds for all tracks"));
        }
        if (scenario.condition().equals("no tracks in session")) {
            when(controller.launchSceneByIndex(eq(0)))
                .thenReturn(ClipSceneController.SceneLaunchResult.error("SCENE_NOT_FOUND", "No tracks found in Bitwig session"));
        }

        Map<String, Object> args = switch (scenario.condition()) {
            case "scene_index is -1" -> Map.of("scene_index", -1);
            case "scene_index is 999 (exceeds track clip counts)" -> Map.of("scene_index", 999);
            case "scene_index overflow (4294967296)" -> Map.of("scene_index", 4294967296.0);
            case "no tracks in session" -> Map.of("scene_index", 0);
            default -> Map.of();
        };

        McpServerFeatures.SyncToolSpecification spec = SceneTool.launchSceneByIndexSpecification(controller, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("session_launchSceneByIndex", args));
    }

    private McpSchema.CallToolResult invokeListTracks(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        io.github.fabb.wigai.bitwig.BitwigApiFacade facade = mock(io.github.fabb.wigai.bitwig.BitwigApiFacade.class);

        Map<String, Object> args = switch (scenario.condition()) {
            case "type has invalid value" -> Map.of("type", "invalid_type");
            default -> Map.of();
        };

        McpServerFeatures.SyncToolSpecification spec = ListTracksTool.specification(facade, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("list_tracks", args));
    }

    private McpSchema.CallToolResult invokeListDevicesOnTrack(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        io.github.fabb.wigai.bitwig.BitwigApiFacade facade = mock(io.github.fabb.wigai.bitwig.BitwigApiFacade.class);

        if (scenario.condition().contains("exceeds track count")) {
            when(facade.getDevicesOnTrack(eq(999), isNull(), eq(false))).thenThrow(
                new BitwigApiException(ErrorCode.INVALID_PARAMETER_INDEX,
                    "list_devices_on_track", "Track index out of bounds: 999",
                    Map.of("track_index", 999)));
        }

        Map<String, Object> args = switch (scenario.condition()) {
            case "conflicting track_index with get_selected" -> Map.of("track_index", 0, "get_selected", true);
            case "track_index is -1" -> Map.of("track_index", -1);
            case "track_index is 999 (exceeds track count)" -> Map.of("track_index", 999);
            case "track_index overflow (4294967296)" -> Map.of("track_index", 4294967296.0);
            default -> Map.of();
        };

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(facade, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("list_devices_on_track", args));
    }

    private McpSchema.CallToolResult invokeGetSelectedDeviceParameters(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        DeviceController controller = mock(DeviceController.class);

        if (scenario.condition().contains("no device selected")) {
            when(controller.getSelectedDeviceParameters()).thenThrow(
                new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "get_selected_device_parameters", "No device selected"));
        } else if (scenario.condition().contains("Bitwig API throws")) {
            when(controller.getSelectedDeviceParameters()).thenThrow(
                new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "get_selected_device_parameters", "API error"));
        }

        McpServerFeatures.SyncToolSpecification spec = DeviceParamTool.getSelectedDeviceParametersSpecification(controller, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("get_selected_device_parameters", Map.of()));
    }

    private McpSchema.CallToolResult invokeGetTrackDetails(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        io.github.fabb.wigai.bitwig.BitwigApiFacade facade = mock(io.github.fabb.wigai.bitwig.BitwigApiFacade.class);

        if (scenario.condition().contains("no track selected")) {
            when(facade.getSelectedTrackDetails()).thenReturn(null);
        }
        if (scenario.condition().contains("exceeds track count")) {
            when(facade.resolveTrackIndex(eq(999), isNull(), eq(false), eq("get_track_details"))).thenThrow(
                new BitwigApiException(ErrorCode.INVALID_PARAMETER_INDEX,
                    "get_track_details", "Track index out of bounds: 999",
                    Map.of("track_index", 999)));
        }

        Map<String, Object> args = switch (scenario.condition()) {
            case "track_index is -1" -> Map.of("track_index", -1);
            case "track_index is 999 (exceeds track count)" -> Map.of("track_index", 999);
            case "track_index overflow (4294967296)" -> Map.of("track_index", 4294967296.0);
            default -> Map.of();
        };

        McpServerFeatures.SyncToolSpecification spec = GetTrackDetailsTool.specification(facade, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("get_track_details", args));
    }

    private McpSchema.CallToolResult invokeListScenes(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        io.github.fabb.wigai.bitwig.BitwigApiFacade facade = mock(io.github.fabb.wigai.bitwig.BitwigApiFacade.class);

        if (scenario.condition().contains("Bitwig API throws")) {
            when(facade.getAllScenesInfo()).thenThrow(
                new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "list_scenes", "API error"));
        }

        McpServerFeatures.SyncToolSpecification spec = ListScenesTool.specification(facade, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("list_scenes", Map.of()));
    }

    private McpSchema.CallToolResult invokeGetDeviceDetails(ErrorScenario scenario, StructuredLogger logger) throws Exception {
        DeviceController controller = mock(DeviceController.class);

        // Overflow and negative scenarios are caught by tool-level validation (no mock needed).
        // Non-integer scenarios also caught at validation layer.
        java.util.HashMap<String, Object> args = new java.util.HashMap<>();
        switch (scenario.condition()) {
            case "track_index overflow (4294967296)" -> {
                args.put("track_index", 4294967296.0);
                args.put("device_index", 0);
            }
            case "device_index overflow (4294967296)" -> {
                args.put("track_index", 0);
                args.put("device_index", 4294967296.0);
            }
            case "track_index is -1" -> {
                args.put("track_index", -1);
                args.put("device_index", 0);
            }
            case "device_index is -1" -> {
                args.put("track_index", 0);
                args.put("device_index", -1);
            }
            case "track_index is non-integer (1.5)" -> {
                args.put("track_index", 1.5);
                args.put("device_index", 0);
            }
            case "device_index is non-integer (1.5)" -> {
                args.put("track_index", 0);
                args.put("device_index", 1.5);
            }
            default -> {}
        }

        McpServerFeatures.SyncToolSpecification spec = GetDeviceDetailsTool.getDeviceDetailsSpecification(controller, logger);
        return spec.callHandler().apply(mock(McpSyncServerExchange.class), buildRequest("get_device_details", args));
    }

    // ========================================================================
    // ASSERTION HELPERS
    // ========================================================================

    /**
     * Asserts error code matches contract expectation with detailed failure message.
     */
    private void assertErrorCode(McpSchema.CallToolResult result, ErrorScenario scenario) throws Exception {
        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);

        String actualCode = error.get("code").asText();
        String expectedCode = scenario.expectedCode().getCode();

        assertEquals(expectedCode, actualCode,
            String.format(
                "Contract violation for %s [%s]%n" +
                "Expected: %s%n" +
                "Actual: %s%n" +
                "Contract: %s%n" +
                "To fix: Update implementation to match contract OR update contract if semantics changed",
                scenario.tool(),
                scenario.condition(),
                expectedCode,
                actualCode,
                scenario.contractReference()
            )
        );

        // Also verify error.operation matches tool name
        assertEquals(scenario.tool(), error.get("operation").asText(),
            "error.operation must equal MCP tool name per contract");
    }

    private McpSchema.CallToolRequest buildRequest(String name, Map<String, Object> arguments) {
        return McpSchema.CallToolRequest.builder()
            .name(name)
            .arguments(arguments)
            .build();
    }

    private StructuredLogger mockStructuredLogger() {
        StructuredLogger logger = mock(StructuredLogger.class);
        io.github.fabb.wigai.common.Logger baseLogger = mock(io.github.fabb.wigai.common.Logger.class);
        StructuredLogger.TimedOperation timedOperation = mock(StructuredLogger.TimedOperation.class);
        when(logger.getBaseLogger()).thenReturn(baseLogger);
        when(logger.generateOperationId()).thenReturn("op-contract-test");
        when(logger.startTimedOperation(any(), any(), any())).thenReturn(timedOperation);
        return logger;
    }
}
