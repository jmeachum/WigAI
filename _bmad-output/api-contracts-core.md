# API Contracts — WigAI core

## Overview
WigAI runs inside Bitwig Studio and exposes a curated MCP toolset over the embedded Jetty server bound to `/mcp`. Each tool is invoked through the MCP SSE transport with a JSON body that identifies the tool and unwraps the arguments described below.

## Access pattern
### Request envelope
- **HTTP method**: `POST` on `/mcp/call` (handled by the MCP servlet).
- **Payload**: JSON request that names a tool (e.g., `"tool": "transport_start"`) and supplies a typed arguments object. Tools validate their inputs before calling Bitwig.
- **Transport**: MCP uses synchronous (blocking) tool calls; the response arrives as a single JSON payload.

### Response envelope
Every tool uses `McpErrorHandler`, so responses share the same wrapper.

```json
{
  "status": "success",
  "data": { /* tool-specific payload */ }
}
```

On failure the `error` object replaces `data` and always writes the MCP tool name in `operation` (e.g., `"launch_clip"`). Sample error payload:

```json
{
  "status": "error",
  "error": {
    "code": "TRACK_NOT_FOUND",
    "message": "Track 'Drums' not found",
    "operation": "launch_clip"
  }
}
```

Use the canonical `ErrorCode` list (e.g., `MISSING_REQUIRED_PARAMETER`, `INVALID_PARAMETER`, `TRACK_NOT_FOUND`, `INVALID_RANGE`, `BITWIG_API_ERROR`) when handling failures.

## Tool catalogue
Tools are grouped below; each subsection lists the expected arguments, highlights of the response, and any special validation.

### Operational telemetry
#### `status`
- **Input**: `{}` (no arguments).
- **Response**: Map with `wigai_version`, `project_name`, `audio_engine_active`, `transport` (tempo, playing/recording flags, current beat/time strings), `project_parameters` (array of `ParameterInfo`), `selected_track`, `selected_device`, `selected_clip_slot`, and an optional `partial_failures` list when Bitwig exposes partial data.
- **Notes**: `project_parameters` mirrors `ParameterInfo` records (index, name, normalized value, display_string).

#### `transport_start` / `transport_stop`
- **Input**: `{}`.
- **Response**: `{ "action": "transport_started", "message": "Transport playback started." }` (or similar stop message).
- These tools return simple acknowledgements and always set `operation` to the tool name in error cases.

### Session control
#### `launch_clip`
- **Arguments**: `track_name` (case-sensitive string), `clip_index` (zero-based, validated 0–7 or more depending on clip count).
- **Response**: `{ "action": "clip_launched", "track_name": "Drums", "clip_index": 2, "message": "Clip launched." }`.
- **Errors**: `TRACK_NOT_FOUND`, `CLIP_INDEX_OUT_OF_BOUNDS`, `BITWIG_API_ERROR`.

#### `session_launchSceneByIndex`
- **Arguments**: `scene_index` (integer ≥ 0).
- **Response**: `{ "action": "scene_launched", "scene_index": 5, "message": "Scene launched on 1 track(s)." }`.

#### `session_launchSceneByName`
- **Arguments**: `scene_name` (non-empty string).
- **Response**: Includes `scene_name` and, when available, `launched_scene_index` (best-effort lookup after launch).
- **Errors**: `SCENE_NOT_FOUND`, `BITWIG_ERROR` when Bitwig rejects the launch.

#### `get_clips_in_scene`
- **Arguments**: Provide `scene_index` or `scene_name` (case-insensitive); at least one is required.
- **Response**: Array of clip slots (one entry per track) with keys such as `track_index`, `track_name`, `has_content`, `clip_name`, `clip_color`, `is_playing`, `is_recording`, `is_playback_queued`, `is_recording_queued`, `is_stop_queued`.
- **Errors**: `SCENE_NOT_FOUND`, `INVALID_PARAMETER` for invalid indexes.

### Track & device introspection
#### `list_tracks`
- **Arguments**: Optional `type` filter (`audio|instrument|group|effect|master|hybrid`).
- **Response**: Array of track summaries (`index`, `name`, `type`, `is_group`, `parent_group_index`, `activated`, `color`, `is_selected`, `devices`, `sends`, `clips`). `devices` is a list of `{ index, name, type }` objects.
- Tracks are enumerated with pagination and the current cursor position is preserved.

#### `list_scenes`
- **Arguments**: `{}` (no properties).
- **Response**: Each entry includes `scene_index`, `scene_name`, and presentation data such as color.

#### `list_devices_on_track`
- **Arguments**: Exactly one of `track_index`, `track_name`, or `get_selected` (defaults to true when omitted). `track_index` and `track_name` are exclusive.
- **Response**: Detailed devices with `{ index, name, type, is_bypassed, is_selected }` and additional metadata about device position.
- **Errors**: `TRACK_NOT_FOUND`, `INVALID_PARAMETER` when arguments violate exclusivity rules.

#### `get_track_details`
- **Arguments**: Provide exactly one of `track_index`, `track_name`, or `get_selected` (true). Defaults to the selected track when no argument is given.
- **Response**: Rich map containing `index`, `name`, `type`, `is_group`, `is_selected`, `devices`, channel controls (`volume`, `volume_str`, `pan`, `pan_str`, `muted`, `soloed`, `armed`), monitor flags (`monitor_enabled`, `auto_monitor_enabled`), `sends` (array of `{ name, volume, volume_str, activated }`), and `clips` (per-slot info similar to `get_clips_in_scene`).

#### `get_device_details`
- **Arguments**: Choose **either** track context + device identifier **or** `get_for_selected_device=true`. When identifiers are provided, exactly one of `track_index`/`track_name` and one of `device_index`/`device_name` must appear; `get_for_selected_device=true` cannot be combined with other identifiers.
- **Response**: Mirrors `DeviceController.DeviceDetailsResult.toMap()` with keys such as `track_index`, `track_name`, `name`, `type`, `is_bypassed`, `is_selected`, and `remote_controls` (array of ParameterInfo maps).
- **Errors**: `INVALID_PARAMETER`, `TRACK_NOT_FOUND`, `DEVICE_NOT_FOUND` (via Bitwig API), and `INVALID_RANGE` for negative indexes.

### Parameter management
#### `get_selected_device_parameters`
- **Arguments**: `{}`.
- **Response**: `{ "device_name": "Mixer", "parameters": [ { "index": 0, "name": "Volume", "value": 0.78, "display_value": "78%" }, ... ] }`.

#### `set_selected_device_parameter`
- **Arguments**: `parameter_index` (0–7), `value` (0.0–1.0).
- **Response**: `{ "action": "parameter_set", "parameter_index": 3, "new_value": 0.5, "message": "Parameter 3 set to 0.5." }`.
- **Errors**: `INVALID_PARAMETER_INDEX`, `INVALID_RANGE`, `DEVICE_NOT_SELECTED` when nothing is selected.

#### `set_selected_device_parameters`
- **Arguments**: `parameters` array with entries `{ "parameter_index": int, "value": double }`.
- **Response**: `results` array holding a `success` or `error` entry per parameter (see `ParameterSettingResult` in data models). Example entry:

```json
{
  "parameter_index": 2,
  "status": "error",
  "error_code": "INVALID_RANGE",
  "message": "Value must be 0.0..1.0"
}
```

The response also includes `message`: "Batch operation completed: 7 succeeded, 1 failed." This summary string aids quick verification.

## Error responses to surface
- **Input validation**: `MISSING_REQUIRED_PARAMETER`, `EMPTY_PARAMETER`, `INVALID_PARAMETER`, `INVALID_PARAMETER_TYPE`, `INVALID_PARAMETER_INDEX`, `INVALID_RANGE`.
- **State errors**: `DEVICE_NOT_SELECTED`, `DEVICE_NOT_FOUND`, `TRACK_NOT_FOUND`, `SCENE_NOT_FOUND`, `CLIP_NOT_FOUND`.
- **Bitwig API failures**: `BITWIG_API_ERROR`, `TRANSPORT_ERROR`.
- **System errors**: `INTERNAL_ERROR`, `OPERATION_FAILED`.

Every error payload includes the `operation` key with the MCP tool name, enabling tooling to map problems back to business operations.
