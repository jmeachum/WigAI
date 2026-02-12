# API Contracts - main

## Scope

This contract documents the runtime MCP API exposed by WigAI's embedded Jetty server via the MCP endpoint.

- **Transport endpoint:** `http://localhost:61169/mcp` (host/port configurable, loopback-only enforced)
- **Protocol:** MCP Streamable HTTP transport
- **Tool registration source:** `src/main/java/io/github/fabb/wigai/mcp/McpServerManager.java`

## Response Envelope

All tool handlers are normalized through `McpErrorHandler`.

### Success

```json
{
  "status": "success",
  "data": {"...": "tool-specific payload"}
}
```

### Error

```json
{
  "status": "error",
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message",
    "operation": "tool_name"
  }
}
```

Notes:
- `request_id` is accepted by mutating/correlation-aware tools and is sanitized for logs.
- Error catalog is centralized in `src/main/java/io/github/fabb/wigai/common/error/ErrorCode.java`.

## Tool Catalog

| Tool | Required Inputs | Optional Inputs | Source |
|---|---|---|---|
| `status` | none | none | `StatusTool.java` |
| `transport_start` | none | `request_id` | `TransportTool.java` |
| `transport_stop` | none | `request_id` | `TransportTool.java` |
| `launch_clip` | `track_name`, `clip_index` | `request_id` | `ClipTool.java` |
| `session_launchSceneByIndex` | `scene_index` | `request_id` | `SceneTool.java` |
| `session_launchSceneByName` | `scene_name` | none | `SceneByNameTool.java` |
| `get_selected_device_parameters` | none | none | `DeviceParamTool.java` |
| `set_selected_device_parameter` | `parameter_index`, `value` | `request_id` | `DeviceParamTool.java` |
| `set_selected_device_parameters` | `parameters` (array of `{parameter_index, value}`) | `request_id` | `DeviceParamTool.java` |
| `get_device_details` | none (selector set must resolve a target device) | `track_index`, `track_name`, `device_index`, `device_name`, `get_for_selected_device` | `GetDeviceDetailsTool.java` |
| `list_tracks` | none | `type` | `ListTracksTool.java` |
| `get_track_details` | none (one selector path expected) | `track_index`, `track_name`, `get_selected` | `GetTrackDetailsTool.java` |
| `list_devices_on_track` | none (one selector path expected) | `track_index`, `track_name`, `get_selected` | `ListDevicesOnTrackTool.java` |
| `list_scenes` | none | none | `ListScenesTool.java` |
| `get_clips_in_scene` | one-of: `scene_index` or `scene_name` | both may be supplied | `GetClipsInSceneTool.java` |

## Behavioral Contracts

- Tool execution pipeline: schema parse/validation -> controller/facade operation -> standardized envelope.
- Mutating operations (transport start/stop, clip/scene launch, device param set) use retry-capable execution path.
- Read operations typically use no-retry path.
- Most business payloads are map/array structures produced by `BitwigApiFacade` and controller result adapters.

## Error Contract

Representative error codes observed in the runtime catalog:

- Validation: `INVALID_PARAMETER`, `INVALID_PARAMETER_INDEX`, `MISSING_REQUIRED_PARAMETER`, `INVALID_RANGE`
- Domain state: `TRACK_NOT_FOUND`, `SCENE_NOT_FOUND`, `CLIP_NOT_FOUND`, `DEVICE_NOT_SELECTED`, `DEVICE_NOT_FOUND`
- Platform/system: `BITWIG_API_ERROR`, `BITWIG_TIMEOUT`, `TRANSPORT_ERROR`, `INTERNAL_ERROR`, `OPERATION_FAILED`

## Deep Scan Notes

- API surface is tool-oriented (MCP), not REST resource-oriented.
- No separate public HTTP routes/controllers beyond the MCP servlet endpoint were identified in source.
