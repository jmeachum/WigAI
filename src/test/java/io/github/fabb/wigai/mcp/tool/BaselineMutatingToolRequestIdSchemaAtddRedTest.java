package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.features.DeviceController;
import io.github.fabb.wigai.features.TransportController;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("atdd_red")
class BaselineMutatingToolRequestIdSchemaAtddRedTest {

    @Mock
    private TransportController transportController;
    @Mock
    private ClipSceneController clipSceneController;
    @Mock
    private DeviceController deviceController;
    @Mock
    private StructuredLogger structuredLogger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("1.4-ATDD-003 [P1] Given baseline mutating tool schemas, then they accept optional request_id without breaking clients")
    void baselineMutatingToolSchemasAcceptOptionalRequestId() throws Exception {
        assertSchemaHasRequestId(TransportTool.transportStartSpecification(transportController, structuredLogger));
        assertSchemaHasRequestId(TransportTool.transportStopSpecification(transportController, structuredLogger));
        assertSchemaHasRequestId(ClipTool.launchClipSpecification(clipSceneController, structuredLogger));
        assertSchemaHasRequestId(SceneTool.launchSceneByIndexSpecification(clipSceneController, structuredLogger));
        assertSchemaHasRequestId(SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger));
        assertSchemaHasRequestId(DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, structuredLogger));
        assertSchemaHasRequestId(DeviceParamTool.setMultipleDeviceParametersSpecification(deviceController, structuredLogger));
    }

    private void assertSchemaHasRequestId(McpServerFeatures.SyncToolSpecification specification) {
        String toolName = specification.tool().name();
        McpSchema.JsonSchema schema = specification.tool().inputSchema();
        assertNotNull(schema, "Schema must exist for: " + toolName);
        assertNotNull(schema.properties(), "Schema properties must exist for: " + toolName);
        assertTrue(schema.properties().containsKey("request_id"),
            "request_id must be present in properties for: " + toolName);
        if (schema.required() != null) {
            assertFalse(schema.required().contains("request_id"),
                "request_id must remain optional (not in required) for: " + toolName);
        }
    }
}
