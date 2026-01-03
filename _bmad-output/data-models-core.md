# Data Models — WigAI core

## Parameter models
### `ParameterInfo`
| Field | Type | Description |
| --- | --- | --- |
| `index` | `int` | 0-based parameter slot (0‑7) sourced from Bitwig remote controls. |
| `name` | `String` | Optional label Bitwig provides for the parameter. |
| `value` | `double` | Normalized 0.0‑1.0 value used for setting parameters. |
| `display_value` | `String` | Human-readable string from Bitwig (e.g., `"78%"`). |

Used consistently in `get_selected_device_parameters`, `get_device_details`, and status responses (`project_parameters`).

### `ParameterSetting`
Simple request record for individual parameter changes. Fields mirror the MCP tool arguments (`parameter_index`, `value`). Validation occurs in `DeviceParamTool` before handing the values to `DeviceController`.

### `ParameterSettingResult`
| Field | Type | Notes |
| --- | --- | --- |
| `parameter_index` | `int` | Echoes which slot was processed. |
| `status` | `String` | `"success"` or `"error"`. |
| `new_value` | `Double` | Present only on success; shows the value that was applied. |
| `error_code` | `String` | Filled when `status == "error"`. Values reuse the canonical `ErrorCode` list. |
| `message` | `String` | Textual success or failure detail. |

The batch tool (`set_selected_device_parameters`) serializes each result into a response array.

## Device & track models
### `DeviceParametersResult`
Returned by `DeviceController.getSelectedDeviceParameters`. Contains:
- `device_name`: the current device's name (nullable).
- `parameters`: list of `ParameterInfo` entries describing the active remote controls.

### Device details map
`DeviceController.DeviceDetailsResult.toMap()` exposes:
- `track_index`, `track_name`, `index`, `name`, `type`
- `is_bypassed`, `is_selected`
- `remote_controls`: array of parameter objects mirroring `ParameterInfo` (index, exists, name, value, display_value). Each remote control also records `exists` — always true in the current implementation.

### Track summaries (`list_tracks` response)
Each track entry is a map with:
- Identity: `index`, `name`, `type`, `is_group`, `parent_group_index`, `activated`, `color`, `is_selected`.
- `devices`: array of `{ index, name, type }` describing devices that exist on the track (populated via `getTrackDevices`).
- `sends`: (only in detailed track info) arrays of `{ name, volume, volume_str, activated }` found when the track has send banks.
- `clips`: per-slot clip metadata (see Clip models).

### Detailed track info (`get_track_details`)
Enriches the summary with channel controls:
- `volume`, `volume_str`, `pan`, `pan_str`, `muted`, `soloed`, `armed`.
- Monitoring info: `monitor_enabled`, `auto_monitor_enabled`.
- `devices`, `sends`, and `clips` as described above.

### Device inventory map (`list_devices_on_track`) includes:
- `index` (device bank index), `name`, `type`, `is_bypassed`, `is_selected`, and inferred flags from Bitwig's device bank state.

## Clip & scene models
### Clip slot detail map (`get_clips_in_scene` response)
Each entry carries:
- `track_index`, `track_name`, optional `clip_name`, optional `clip_color` (RGB string), and boolean playback flags (`has_content`, `is_playing`, `is_recording`, `is_playback_queued`, `is_recording_queued`, `is_stop_queued`).
- Missing data defaults to safe values (`false`, `null`, etc.).

### Launch results
`ClipSceneController` uses two helper classes:
- `ClipLaunchResult`: `{ success: boolean, errorCode: String?, message: String }`. Successful responses yield `success == true` and a friendly message; failures surface the mapped error code for `BITWIG_ERROR`, `TRACK_NOT_FOUND`, or `CLIP_INDEX_OUT_OF_BOUNDS`.
- `SceneLaunchResult`: identical shape but populates failure codes such as `SCENE_NOT_FOUND`.

## Status payload shape
The `status` tool bundles many of the models above:
- `project_parameters`: array of `ParameterInfo`.
- `transport`: map with `playing`, `recording`, `loop_active`, `metronome_active`, `current_tempo`, `time_signature`, `current_beat_str`, `current_time_str`.
- `selected_track`, `selected_device`, and `selected_clip_slot`: each reuses the track/device/clip structures described earlier.
- `partial_failures`: list of strings describing data points that could not be populated; the presence of this array indicates a best-effort read rather than a hard failure.
