package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.features.DeviceController;
import io.github.fabb.wigai.features.TransportController;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
        assertSchemaHasRequestId(DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, structuredLogger));
        assertSchemaHasRequestId(DeviceParamTool.setMultipleDeviceParametersSpecification(deviceController, structuredLogger));
    }

    private void assertSchemaHasRequestId(McpServerFeatures.SyncToolSpecification specification) throws Exception {
        String schemaString = specification.tool().inputSchema().toString();
        assertNotNull(schemaString, "Schema must exist for: " + specification.tool().name());
        assertTrue(schemaString.contains("request_id"), "Expected request_id to be present in schema for: " + specification.tool().name());
    }
}
