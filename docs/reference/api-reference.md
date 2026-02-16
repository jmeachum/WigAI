# API Reference

## MCP API Specification

This document outlines the Model Context Protocol (MCP) API used for communication between the WigAI agent and the Bitwig Studio environment.

### Protocol Overview

Communication is message-based, typically using JSON-RPC or a similar structured format over a suitable transport layer (e.g., WebSockets, stdin/stdout). Each message will have a command/method name and associated parameters. Responses will indicate success or failure and may return data.

### Core Commands

#### `status`
*   **Description**: Get WigAI operational status, version information, current project name, audio engine status, detailed transport information, project parameters, selected track details, and selected device information.
*   **Parameters**: None
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "wigai_version": "x.y.z",
        "project_name": "Name of the project",
        "audio_engine_active": true,
        "transport": {
          "playing": false,
          "recording": false,
          "loop_active": false,
          "metronome_active": true,
          "current_tempo": 120.0,
          "time_signature": "4/4",
          "current_beat_str": "1.1.1:0",
          "current_time_str": "0:00.000"
        },
        "project_parameters": [
          {
            "index": 0,
            "name": "Project Parameter Name",
            "value": 0.5,
            "display_value": "50%"
          }
        ],
        "selected_track": {
          "index": 0,
          "name": "Track Name",
          "type": "audio",
          "is_group": false,
          "muted": false,
          "soloed": false,
          "armed": true
        },
        "selected_clip_slot": {
          "track_name": "Drums",
          "track_index": 0,
          "slot_index": 0,
          "scene_index": 0,
          "scene_name": "Intro",
          "has_content": true,
          "clip_name": "Drum Loop 1",
          "is_playing": false,
          "is_recording": false,
          "is_playback_queued": false,
          "is_recording_queued": false,
          "is_stop_queued": false
        },
        "selected_device": {
          "track_name": "Track Name",
          "track_index": 0,
          "index": 0,
          "name": "Device Name",
          "bypassed": false,
          "parameters": [
            {
              "index": 0,
              "name": "Parameter Name",
              "value": 0.5,
              "display_value": "50%"
            }
          ]
        }
      }
    }
    ```

*   **Partial Failure Response** (when one or more sub-fetches fail):
    ```json
    {
      "status": "success",
      "data": {
        "wigai_version": "x.y.z",
        "project_name": "Name of the project",
        "audio_engine_active": true,
        "transport": { "error": "Transport status unavailable" },
        "project_parameters": [],
        "selected_track": { "name": "Track 1", "index": 0 },
        "selected_device": null,
        "selected_clip_slot": null,
        "partial_failures": [
          "transport: Transport status unavailable",
          "selected_device: Device unavailable"
        ],
        "status_note": "Status retrieved with 2 partial failures"
      }
    }
    ```

*   **Notes**:
    - `current_beat_str`: Bitwig-style beat position format (measures.beats.sixteenths:ticks), e.g., "1.1.1:0"
    - `current_time_str`: Time format with milliseconds (MM:SS.mmm or HH:MM:SS.mmm), e.g., "0:12.345" or "1:23:45.678"
    - `project_parameters`: Array containing project parameters (0-7 parameter indexes)
    - `selected_track`: Object containing currently selected track details, or `null` if no track is selected
    - **Partial Failure Behavior**: If one or more sub-fetches fail (e.g., transport, selected_device), the tool still returns `status: "success"` with best-effort defaults and includes:
      - `partial_failures`: Array of strings describing which fields failed and why
      - `status_note`: Human-readable summary (e.g., "Status retrieved with 2 partial failures")
    - Partial failures allow clients to get available data without a full error, while clearly indicating which fields may be incomplete or defaulted
    - `selected_track.type`: Track type (e.g., "audio", "instrument", "group", "hybrid", "effect", "master")
    - `selected_track.index`: 0-based project-absolute track index (consistent with `selected_clip_slot.track_index` and `selected_device.track_index`)
    - `selected_clip_slot`: Object describing the currently selected clip slot (track context, slot/scene indices, clip status/state), or `null` if: (1) no track is selected, (2) clip launcher slot bank is unavailable, or (3) the selected track has no clip launcher slots
    - `selected_clip_slot.scene_name`: `null` when the scene is unnamed or unavailable
    - **API Limitation**: Bitwig Extension API v19 does not provide a way to detect selected clip slot position. CursorClip exists but does not expose slot position (scene/slot index). No ClipLauncherSlotCursor exists. The slot is detected as follows:
      - If a clip is playing/queued/recording: Returns that slot's information
      - If track is selected but no active clips: Returns slot 0 as default (cannot detect user's actual selection)
      - If no track is selected: Returns `null`
    - `selected_device`: Object containing currently selected device details, or `null` if no device is selected
    - `selected_device.track_name`: Name of the track containing the selected device
    - `selected_device.track_index`: 0-based project-absolute index of the track containing the device (consistent with `selected_track.index`)
    - `selected_device.index`: 0-based index of the device in the track's device chain (currently always 0; Bitwig API does not expose actual device position)
    - `selected_device.bypassed`: Boolean indicating if the device is bypassed (disabled)
    - `selected_device.parameters`: Array of accessible parameters (0-7 parameter indexes); each parameter has `index`, `name` (can be `null` for unnamed parameters), `value`, and `display_value`

#### `transport_start`
*   **Description**: Start Bitwig's transport playback.
*   **Parameters**:
    *   `request_id` (string, optional): Correlation ID for request tracing and idempotency deduplication (see [Idempotency / Deduplication](#idempotency--deduplication))
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "action": "transport_started",
        "message": "Bitwig transport started."
      }
    }
    ```

#### `transport_stop`
*   **Description**: Stop Bitwig's transport playback.
*   **Parameters**:
    *   `request_id` (string, optional): Correlation ID for request tracing and idempotency deduplication (see [Idempotency / Deduplication](#idempotency--deduplication))
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "action": "transport_stopped",
        "message": "Bitwig transport stopped."
      }
    }
    ```
*   **Notes**:
    - The command is idempotent: calling it when playback is already stopped is handled gracefully.
    - All actions and responses are logged via the Logger service.

#### `project_getName`
*   **Description**: Get the name of the current Bitwig Studio project.
*   **Parameters**: None
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "projectName": "Name of the project"
      }
    }
    ```

#### `get_selected_device_parameters`
*   **Description**: Get the names and values of the eight addressable parameters of the user-selected device in Bitwig.
*   **Parameters**: None
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "device_name": "Name of the device or null",
        "parameters": [
          {
            "index": 0,
            "name": "Parameter Name",
            "value": 0.0, // Normalized value (0.0-1.0)
            "display_value": "Formatted Value"
          }
          // ... up to 8 parameters
        ]
      }
    }
    ```
*   **Errors**:
    *   `DEVICE_NOT_SELECTED`: No device is currently selected in Bitwig
    *   `BITWIG_API_ERROR`: Internal error occurred while retrieving parameters

#### `set_selected_device_parameter`
*   **Description**: Set a specific value for a single parameter (by its index 0-7) of the user-selected device in Bitwig.
*   **Parameters**:
    ```json
    {
      "parameter_index": 0, // Integer (0-7)
      "value": 0.5, // Float/Double (0.0-1.0)
      "request_id": "optional-correlation-id" // Optional: correlation ID for request tracing and idempotency deduplication
    }
    ```
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "action": "parameter_set",
        "parameter_index": 0,
        "new_value": 0.5,
        "message": "Parameter 0 set to 0.5."
      }
    }
    ```
*   **Errors**:
    *   `DEVICE_NOT_SELECTED`: No device is currently selected in Bitwig
    *   `INVALID_PARAMETER_INDEX`: parameter_index outside valid range (0-7)
    *   `INVALID_RANGE`: value outside valid range (0.0-1.0)
    *   `BITWIG_API_ERROR`: Internal error occurred while setting parameter

#### `set_selected_device_parameters`
*   **Description**: Set multiple parameter values (by index 0-7) of the user-selected device in Bitwig simultaneously.
*   **Parameters**:
    ```json
    {
      "parameters": [
        { "parameter_index": 0, "value": 0.25 },
        { "parameter_index": 1, "value": 0.80 }
        // ... more parameters
      ],
      "request_id": "optional-correlation-id" // Optional: correlation ID for request tracing and idempotency deduplication
    }
    ```
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "action": "multiple_parameters_set",
        "results": [
          {
            "parameter_index": 0,
            "status": "success", // or "error"
            "new_value": 0.25, // if success
            "error_code": "INVALID_PARAMETER_INDEX", // if error
            "message": "Optional message for this specific parameter" // if error
          }
          // ... results for each parameter
        ]
      }
    }
    ```
*   **Errors**:
    *   Top-level: `DEVICE_NOT_SELECTED`, `MISSING_REQUIRED_PARAMETER` (for missing parameters array), `EMPTY_PARAMETER` (for empty parameters array), `INVALID_PARAMETER` (for malformed parameters: not an array or entries not objects), `INVALID_PARAMETER_INDEX` (for parameter_index outside valid range 0-7 or overflow), `INVALID_RANGE` (for value outside 0.0-1.0)
    *   Per-item in `results`: `BITWIG_API_ERROR`

### Session Control Commands

#### `launch_clip`
*   **Description**: Launch a specific clip in Bitwig by providing its track name and clip slot index. Track targeting uses exact normalized matching (`trim` + case-insensitive). When duplicate tracks share the same normalized exact `track_name`, the tool refuses implicit mutation and returns deterministic candidate guidance that requires explicit `track_index` confirmation.
*   **Track-name lookup scope**:
    - Name resolution scans the currently materialized track-bank window.
*   **Parameters**:
    ```json
    {
      "track_name": "Drums", // Exact match after trim + case-insensitive normalization
      "clip_index": 0, // Non-negative integer (0-based)
      "track_index": 3, // Optional explicit index; when provided, index is authoritative and track_name is confirmation
      "request_id": "optional-correlation-id" // Optional: correlation ID for request tracing and idempotency deduplication
    }
    ```
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "action": "clip_launched",
        "track_name": "Drums",
        "track_index": 3,
        "clip_index": 0,
        "message": "Clip at Drums[0] launched."
      }
    }
    ```
*   **Errors**:
    *   `MISSING_REQUIRED_PARAMETER`: track_name or clip_index not provided
    *   `EMPTY_PARAMETER`: track_name cannot be empty
    *   `INVALID_PARAMETER_INDEX`: clip_index is negative/out of range for the track, or `track_index` is not a valid integer index
    *   `INVALID_PARAMETER`: ambiguous duplicate `track_name` without explicit `track_index` confirmation, or `track_index`/`track_name` mismatch
        - Ambiguity refusals include `error.details` with `confirmation_parameter` and deterministic `candidates` list
        - Ambiguity refusal example:
          ```json
          {
            "status": "error",
            "error": {
              "code": "INVALID_PARAMETER",
              "message": "Ambiguous track_name 'Drums'. Provide track_index to confirm target.",
              "operation": "launch_clip",
              "details": {
                "reason": "ambiguous_track_name",
                "track_name": "Drums",
                "clip_index": 0,
                "confirmation_parameter": "track_index",
                "candidates": [
                  { "track_index": 1, "track_name": "Drums" },
                  { "track_index": 3, "track_name": "Drums" }
                ]
              }
            }
          }
          ```
    *   `TRACK_NOT_FOUND`: The specified track name was not found
    *   `BITWIG_API_ERROR`: Internal error occurred while launching clip

#### `session_launchSceneByIndex`
*   **Description**: Launch an entire scene in Bitwig by providing its numerical index.
*   **Parameters**:
    ```json
    {
      "scene_index": 1, // Non-negative integer (0-based)
      "request_id": "optional-correlation-id" // Optional: correlation ID for request tracing and idempotency deduplication
    }
    ```
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "action": "scene_launched",
        "scene_index": 1,
        "message": "Scene 1 launched."
      }
    }
    ```
*   **Errors**:
    *   `MISSING_REQUIRED_PARAMETER`: scene_index not provided
    *   `INVALID_PARAMETER_INDEX`: scene_index is negative or exceeds valid bounds
    *   `SCENE_NOT_FOUND`: No tracks found in Bitwig session
    *   `BITWIG_API_ERROR`: Internal error occurred while launching scene

#### `session_launchSceneByName`
*   **Description**: Launch an entire scene in Bitwig by providing its name.
*   **Parameters**:
    ```json
    {
      "scene_name": "Verse 1", // Case-sensitive
      "request_id": "optional-correlation-id" // Optional: correlation ID for request tracing and idempotency deduplication
    }
    ```
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "action": "scene_launched",
        "scene_name": "Verse 1",
        // "launched_scene_index": 0, // Optional
        "message": "Scene 'Verse 1' launched."
      }
    }
    ```
*   **Errors**:
    *   `MISSING_REQUIRED_PARAMETER`: When `scene_name` is not provided
    *   `EMPTY_PARAMETER`: When `scene_name` is empty or whitespace-only
    *   `SCENE_NOT_FOUND`: When no scene matches the provided name
    *   `BITWIG_API_ERROR`: When a Bitwig API error occurs during scene launch

### Track Information Commands

#### `list_tracks`
*   **Description**: List all tracks in the current project with summary information including name, type, selection state, parent group, and basic device list.
*   **Parameters**:
    ```json
    {
      "type": "audio" // Optional filter by track type (e.g., "audio", "instrument", "group", "effect", "master", "hybrid")
    }
    ```
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "index": 0,
          "name": "Drums",
          "type": "audio",
          "is_group": false,
          "parent_group_index": null,
          "activated": true,
          "color": "rgb(255,128,0)",
          "is_selected": true,
          "devices": [
            {
              "index": 0,
              "name": "EQ Eight",
              "type": "AudioFX"
            },
            {
              "index": 1,
              "name": "Compressor",
              "type": "AudioFX"
            }
          ]
        },
        {
          "index": 1,
          "name": "Bass",
          "type": "instrument",
          "is_group": false,
          "parent_group_index": null,
          "activated": true,
          "color": "rgb(0,255,128)",
          "is_selected": false,
          "devices": [
            {
              "index": 0,
              "name": "Wavetable",
              "type": "Instrument"
            }
          ]
        }
      ]
    }
    ```
*   **Notes**:
    - `index`: 0-based position in the project's main track list
    - `type`: Track type (e.g., "audio", "instrument", "hybrid", "group", "effect", "master")
    - `is_group`: True if the track is a group track
    - `parent_group_index`: 0-based index of the parent group track if this track is inside a group, `null` otherwise
    - `activated`: Is the track activated
    - `color`: Color of the track in RGB format (e.g., "rgb(255,128,0)")
    - `is_selected`: True if this is the currently selected track
    - `devices`: Array of devices on this track with summary information
    - `devices[].index`: 0-based position in this track's device chain
    - `devices[].name`: Name of the device
    - `devices[].type`: Type of the device ("Instrument", "AudioFX", "NoteFX")
*   **Errors**:
    *   `INVALID_PARAMETER`: Invalid track type filter
    *   `BITWIG_API_ERROR`: Internal error occurred while retrieving tracks

#### `get_track_details`
*   **Description**: Retrieve detailed information for a specific track by index, name, or the currently selected track.
*   **Parameters**:
    ```json
    {
      "track_index": 3,
      "track_name": "Drums",
      "get_selected": true
    }
    ```
    Rules:
    - Selector precedence is `track_index` → `track_name` → selected-track fallback.
    - If both `track_index` and `track_name` are provided, `track_index` is authoritative and `track_name` must match the indexed track after normalization (`trim` + case-insensitive).
    - If neither selector is provided, behaves as selected-track fallback.
    - `get_selected` is optional compatibility input; do not combine it with `track_index`/`track_name`.
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": {
        "index": 0,
        "name": "Drums",
        "type": "audio",
        "is_group": false,
        "parent_group_index": null,
        "activated": true,
        "color": "rgb(255,128,0)",
        "is_selected": true,
        "devices": [
          { "index": 0, "name": "EQ Eight", "type": "AudioFX" },
          { "index": 1, "name": "Compressor", "type": "AudioFX" }
        ],
        "volume": 0.63,
        "volume_str": "-4.0 dB",
        "pan": 0.5,
        "pan_str": "C",
        "muted": false,
        "soloed": false,
        "armed": false,
        "monitor_enabled": true,
        "auto_monitor_enabled": false,
        "sends": [
          { "name": "A", "volume": 0.4, "volume_str": "-8.0 dB", "activated": true },
          { "name": "B", "volume": 0.0, "volume_str": "-inf", "activated": false }
        ],
        "clips": [
          {
            "slot_index": 0,
            "scene_name": "Intro",
            "has_content": true,
            "clip_name": "Beat 1",
            "clip_color": "rgb(255,128,0)",
            "is_playing": false,
            "is_recording": false,
            "is_playback_queued": false
          },
          {
            "slot_index": 1,
            "scene_name": "Verse 1",
            "has_content": false,
            "clip_name": null,
            "clip_color": null,
            "is_playing": null,
            "is_recording": null,
            "is_playback_queued": null
          }
        ]
      }
    }
    ```
*   **Notes**:
    - Uses the same track index semantics and device summary format as `list_tracks`.
    - Color values are returned as RGB strings (e.g., `rgb(255,128,0)`).
    - Clip slot provides clip name and state flags; clip length and loop status are not exposed via slot API and are omitted.
*   **Errors**:
    *   `INVALID_PARAMETER`: Invalid combination or types of parameters
    *   `INVALID_PARAMETER_INDEX`: Index outside valid bounds (e.g., negative track_index)
    *   `TRACK_NOT_FOUND`: Target track not found or no track selected when required
    *   `BITWIG_API_ERROR`: Internal Bitwig API error

#### `list_devices_on_track`
*   **Description**: List devices on a specific track (by index, name, or currently selected track) with summary fields and selection state.
*   **Parameters**:
    ```json
    {
      "track_index": 3,
      "track_name": "Drums",
      "get_selected": true
    }
    ```
    Rules:
    - Selector precedence is `track_index` → `track_name` → selected-track fallback.
    - If both `track_index` and `track_name` are provided, `track_index` is authoritative and `track_name` must match the indexed track after normalization (`trim` + case-insensitive).
    - If neither selector is provided, behaves as selected-track fallback.
    - `get_selected` is optional compatibility input; do not combine it with `track_index`/`track_name`.
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "index": 0,
          "name": "EQ Eight",
          "type": "AudioFX",
          "bypassed": false,
          "is_selected": false
        },
        {
          "index": 1,
          "name": "Compressor",
          "type": "AudioFX",
          "bypassed": false,
          "is_selected": true
        }
      ]
    }
    ```
*   **Notes**:
    - `type`: One of "Instrument", "AudioFX", "NoteFX", or "Unknown".
    - Deterministic enumeration: results are ordered by 0-based device chain index; only top-level devices are included (no recursion into container devices).
    - Selection semantics:
      - If the target track is not the globally selected track (determined by track name match), `is_selected` is `false` for all devices.
      - If the target track is the globally selected track, `is_selected` is `true` for the device whose name matches the currently selected device's name.

*   **Examples**:

    Request by index:
    ```json
    { "track_index": 2 }
    ```

    Request by name:
    ```json
    { "track_name": "Drums" }
    ```

    Request selected track:
    ```json
    { "get_selected": true }
    ```

    Default (no parameters → behaves as get_selected=true):
    ```json
    { }
    ```

    Empty device list response:
    ```json
    {
      "status": "success",
      "data": []
    }
    ```

    Error response (standardized envelope):
    ```json
    {
      "status": "error",
      "error": {
        "code": "TRACK_NOT_FOUND",
        "message": "No track is currently selected",
        "operation": "list_devices_on_track"
      }
    }
    ```

*   **Errors**:
    *   `INVALID_PARAMETER`: Invalid combination or types of parameters
    *   `INVALID_PARAMETER_INDEX`: Index outside of valid range
    *   `TRACK_NOT_FOUND`: Target track not found or no track selected when required
    *   `BITWIG_API_ERROR`: Internal Bitwig API error

### Scene Information Commands

#### `list_scenes`
*   **Description**: List all scenes in the current project with their name and color. Returns information about scene structure.
*   **Parameters**: None
*   **JSON Schema**:
    ```json
    {
      "type": "object",
      "properties": {},
      "additionalProperties": false
    }
    ```
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "index": 0,
          "name": "Intro",
          "color": "rgb(255,128,0)"
        },
        {
          "index": 1,
          "name": "Verse",
          "color": "rgb(0,180,255)"
        },
        {
          "index": 2,
          "name": "Chorus",
          "color": null
        }
      ]
    }
    ```
*   **Notes**:
    - `index`: 0-based index of the scene across the entire project (not limited to the visible window)
    - `name`: Name of the scene as displayed in Bitwig Studio
    - `color`: Scene color as an RGB string formatted exactly as `rgb(r,g,b)` where `r`, `g`, and `b` are integers in the range 0–255. Use `null` if the color is not available from the API
    - The tool queries the Bitwig API (SceneBank) to retrieve all scenes, correctly handling paged/scrolling access so that all scenes are returned, not just the currently visible window
    - The tool functions correctly in an empty project (returns `[]`)
*   **Errors**:
    *   `BITWIG_API_ERROR`: Internal error occurred while retrieving scenes or Bitwig API unavailable

#### `get_clips_in_scene`
*   **Description**: Get detailed information for all clips within a specific scene, including their track context, content properties (name, color, length, loop status), and playback states.
*   **Parameters**: One of `scene_index` or `scene_name` must be provided. If both are provided, `scene_name` takes precedence.
    *   `scene_index` (integer, optional): 0-based index of the scene. Must be >= 0.
    *   `scene_name` (string, optional): Name of the scene (case-insensitive, trimmed).
*   **JSON Schema**:
    ```json
    {
      "type": "object",
      "properties": {
        "scene_index": {
          "type": "integer",
          "description": "0-based index of the scene. Must be >= 0.",
          "minimum": 0
        },
        "scene_name": {
          "type": "string",
          "description": "Name of the scene (case-insensitive, trimmed)"
        }
      },
      "oneOf": [
        {"required": ["scene_index"]},
        {"required": ["scene_name"]},
        {"required": ["scene_index", "scene_name"]}
      ]
    }
    ```
*   **Returns**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "track_index": 0,
          "track_name": "Bass",
          "has_content": true,
          "clip_name": "Bass Line",
          "clip_color": "#FF8000",
          "is_playing": false,
          "is_recording": false,
          "is_playback_queued": true,
          "is_recording_queued": false,
          "is_stop_queued": false
        },
        {
          "track_index": 1,
          "track_name": "Drums",
          "has_content": false,
          "clip_name": null,
          "clip_color": null,
          "is_playing": false,
          "is_recording": false,
          "is_playback_queued": false,
          "is_recording_queued": false,
          "is_stop_queued": false
        }
      ]
    }
    ```
*   **Notes**:
    - Returns an array of clip slot objects for all tracks at the specified scene index
    - `track_index`: 0-based index of the track this slot belongs to
    - `track_name`: Name of the track this slot belongs to
    - `has_content`: True if a clip exists in this slot
    - `clip_name`: Name of the clip if `has_content` is true; otherwise null
    - `clip_color`: Hex color in "#RRGGBB" format if `has_content` is true; otherwise null
    - `is_playing`: True if the clip in this slot is currently playing
    - `is_recording`: True if the clip in this slot is currently recording
    - `is_playback_queued`: True if playback is queued for the clip in this slot
    - `is_recording_queued`: True if recording is queued for the clip in this slot
    - `is_stop_queued`: True if a stop is queued for the clip in this slot
    - Scene name comparison is case-insensitive and trimmed
    - If multiple scenes share the same name, the first match by index is used
    - Values reflect a consistent snapshot at query time (single API tick)
*   **Validation Rules**:
    - At least one of `scene_index` or `scene_name` must be provided
    - `scene_index` must be >= 0 if provided
    - Negative `scene_index` results in `INVALID_PARAMETER_INDEX` error
    - Non-negative `scene_index` exceeding available scenes results in `INVALID_PARAMETER_INDEX` error
*   **Errors**:
    *   `SCENE_NOT_FOUND`: Scene not found by name (scene_name lookup fails)
    *   `INVALID_PARAMETER_INDEX`: Index outside valid bounds (negative or exceeding scene count)
    *   `INVALID_PARAMETER`: Non-integer `scene_index` value (e.g., `1.5`)
    *   `MISSING_REQUIRED_PARAMETER`: Neither scene_index nor scene_name provided
    *   `BITWIG_API_ERROR`: Internal error occurred while retrieving clip information

### Device Information Commands

#### `get_device_details`
- Description: Retrieve detailed information for a specific device by track/device identifiers or for the currently selected device, including current remote control page parameters and all page names with selection state.
- Parameters:
```json
{
  "track_index": 1,
  "track_name": "Bass",
  "device_index": 0,
  "device_name": "EQ Eight",
  "get_for_selected_device": true
}
```
Rules:
- Two targeting modes:
  - Selected-device mode: if `get_for_selected_device=true` OR no identifiers provided, return the globally selected device.
  - Target-by-identifiers mode: if any of `track_index`, `track_name`, `device_index`, or `device_name` is provided, target that specific device.
- Constraints:
  - Providing `get_for_selected_device=true` together with any identifier → `INVALID_PARAMETER`.
  - Exactly one of `device_index` OR `device_name` must be provided in identifier mode; not both → `INVALID_PARAMETER`.
  - Track selector precedence in identifier mode: `track_index` → `track_name` → selected-track fallback.
  - If both `track_index` and `track_name` are provided, `track_index` is authoritative and `track_name` must match the indexed track after normalization (`trim` + case-insensitive).
  - If identifier mode provides no track selector, WigAI falls back to the currently selected track.
  - `track_name` uses exact normalized matching (`trim` + case-insensitive). `device_name` remains exact case-sensitive.
  - If `get_for_selected_device` is omitted and no identifiers are provided, WigAI defaults to selected-device mode.
  - If `get_for_selected_device=false` and no identifiers are provided → `INVALID_PARAMETER`.

- Returns:
```json
{
  "status": "success",
  "data": {
    "track_index": 0,
    "track_name": "Drums",
    "index": 1,
    "name": "Compressor",
    "type": "AudioFX", // "Instrument" | "AudioFX" | "NoteFX" | "Unknown"
    "is_bypassed": false,
    "is_selected": true,
    "remote_controls": [
      {
        "index": 0,
        "exists": true,
        "name": "Threshold",
        "value": 0.52,
        "raw_value": null,      // null if raw not available
        "display_value": "-12.0 dB"
      }
      // ... only existing controls are included
    ]
  }
}
```

- Notes:
  - `remote_controls` reflect the currently selected remote control page for the device (via `device.remoteControls()`).
  - **API Limitation**: When targeting a non-selected device (by track/device identifiers), `remote_controls` returns an empty array. The Bitwig Controller API does not expose remote controls for non-selected devices without temporarily selecting them, which would disrupt the user experience.
  - `exists` is always `true` since only existing parameters are included in the response array.
  - `value` is normalized (0.0-1.0). `raw_value` is provided only if the Controller API exposes a raw accessor; otherwise `null`.
  - Only existing controls are included in the response array.

- Errors:
  - `INVALID_PARAMETER`
  - `INVALID_PARAMETER_INDEX`
  - `TRACK_NOT_FOUND`
  - `DEVICE_NOT_FOUND`
  - `DEVICE_NOT_SELECTED`
  - `BITWIG_API_ERROR`

### Error Handling

All MCP tools use a standardized response envelope.

- Success:
```json
{
  "status": "success",
  "data": { }
}
```

- Error:
```json
{
  "status": "error",
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "Human-readable error message",
    "operation": "tool_name"
  }
}
```

- error.code is one of the values defined in the ErrorCode enum.
- error.operation identifies the tool/operation that failed.

### Idempotency / Deduplication

Mutating tools support opt-in idempotency deduplication via the `request_id` parameter.

**How it works:**

- When a mutating tool receives a `request_id`, the result is cached using the composite key `(tool_name, request_id)`.
- If the same `(tool_name, request_id)` pair is received again within the TTL window, the cached first result is returned without re-executing the operation.
- If no `request_id` is provided, no deduplication occurs (backward compatible).

**Keying semantics:**

- The dedupe key is `(tool_name, request_id)` — the same `request_id` used across different tools does not collide.
- The full `request_id` value is used for cache keying (no truncation) when accepted. For logging, values are sanitized to 256 characters.
- `request_id` must be a non-empty string containing only printable characters (ASCII 32–126). Non-string, empty, blank, or control-character-containing values are ignored and the request executes normally without deduplication.
- Oversized `request_id` values (`length > 1024`) are rejected for dedupe keying and the request executes normally without deduplication.
- Concurrent requests with the same `(tool_name, request_id)` are serialized atomically — only one executes; the other receives the cached result.
- Payload consistency is enforced: reusing the same `request_id` with a different payload (different non-`request_id` arguments) is rejected with an `INVALID_PARAMETER` error. Each `request_id` must map to a single unique request.
- Numeric values in payload consistency checks are normalized by value semantics (canonical decimal form) rather than runtime number subtype, so equivalent JSON numbers (e.g., `1`, `1.0`, `1.00`) are treated as the same value.

**Bounded storage defaults:**

| Parameter | Default | System Property | Description |
|-----------|---------|-----------------|-------------|
| TTL | 60 seconds | `wigai.idempotency.ttl.millis` | Time-to-live for cached entries |
| Max entries | 1000 | `wigai.idempotency.max.entries` | Maximum cache entries before eviction |

Both values can be overridden at startup via JVM system properties (e.g., `-Dwigai.idempotency.ttl.millis=120000`).

**Eviction policy:**

1. Expired entries (past TTL) are evicted first.
2. If still at capacity, the oldest entry is evicted.

**Cached result behavior:**

- Both success and error results are cached. A failed first execution returns the same error on dedupe hit.
- The cached result is the complete response envelope — identical to the original response.

**Logging:**

- On dedupe hit, a structured log entry is emitted: `Dedupe hit | request_id=<id> | outcome=success|error | returning cached result`.
- No re-execution logging occurs on dedupe hit (the operation is not re-executed).

**Scope:**

- Idempotency is in-memory only; cache does not persist across server restarts.
- Read-only tools (e.g., `status`, `list_tracks`) do not participate in deduplication.

## External APIs Consumed

WigAI does not consume any external APIs.

## MCP Tool Development Guidelines

These guidelines assist AI agents in effectively choosing and using MCP tools.

### 1. Tool Design & Scope
*   **Focus & Atomicity**: Tools should perform single, clear tasks. Avoid overly complex tools.
*   **Meaningful Workflows**: Expose tools representing goal-oriented tasks, not just raw API endpoints.
*   **Manageable Number**: Prefer a curated set. For larger sets, use:
    *   **Logical Grouping/Namespacing**: e.g., `userManagement_createUser`.
    *   **Abstraction Layers**: Offer high-level (common operations) and lower-level (fine-grained control) tools.
    *   **Phased Exposure**: Start with critical tools and iterate.

### 2. Naming Conventions
*   **Clear & Descriptive**: Short names indicating function.
*   **Consistent & Unique**: Use a standard convention (e.g., `snake_case`) and ensure uniqueness.
*   **Unambiguous**: Differentiate similar tools clearly.

### 3. Tool Descriptions
*   **Detailed & Unambiguous**: Crucial for AI selection. Explain:
    *   What the tool does.
    *   When to use it.
    *   Expected outcome.
*   **Examples**: Provide sample inputs/outputs.
*   **Markdown**: Use for readability.

### 4. Parameter Handling
*   **Clear Definitions**: For each parameter: name, description, data type, required/optional, constraints/formats.
*   **JSON Schema**: Use for structured, machine-readable definitions.
*   **"Think Like the Model"**: Descriptions should be obvious to a new developer.
*   **Input Validation**: Define validation rules clearly.

### 5. Output Handling
*   **Document Structure**: Clearly describe output structure, including variability based on inputs.
*   **Context for Interpretation**: Explain how to interpret outputs if needed.
*   **Deterministic Behavior**: State if output can be random or change with identical inputs.
*   **Error Reporting**: Describe how errors are communicated in the output.

### 6. General Best Practices
*   **Prioritize Safety**: Be cautious with data-modifying tools (PUT, DELETE). Prefer GET. Implement robust safety/consent mechanisms.
*   **User Consent & Control**: Align with MCP principles for data access and operations.
*   **Error Handling**: Implement proper error handling with clear messages (no sensitive internal details).
*   **Rich Metadata**: Comprehensive and accurate tool metadata is key for discovery and use.
*   **Test Thoroughly**: Observe AI interaction and iterate on definitions.
*   **Consistent Documentation**: Standardize format and structure.
