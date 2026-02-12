# Story 1.6 — Test Summary: Align Index Validation Error Codes with Canonical Semantics

## Index-Semantic AC Coverage

### AC 1 & 2: Negative and out-of-bounds index → `INVALID_PARAMETER_INDEX`
- [x] `ParameterValidatorTest` — `validateParameterIndex`, `validateClipIndex`, `validateSceneIndex` assert `INVALID_PARAMETER_INDEX` for negative and overflow
- [x] `BitwigApiFacadeTest` — track/device index bounds failures assert `INVALID_PARAMETER_INDEX`
- [x] `GetDeviceDetailsToolTest` — `track_index`/`device_index` invalid values assert `INVALID_PARAMETER_INDEX`
- [x] `ListDevicesOnTrackToolTest` — negative `track_index` asserts `INVALID_PARAMETER_INDEX`

### AC 3: API docs reference `INVALID_PARAMETER_INDEX` for index parameters
- [x] `docs/reference/api-reference.md` updated for all affected tools (`launch_clip`, `get_clips_in_scene`, `get_device_details`, `list_devices_on_track`, `get_track_details`, `session_launchSceneByIndex`)

### AC 4: Unit tests expect `INVALID_PARAMETER_INDEX`
- [x] `ParameterValidatorTest` — index bounds error code assertions updated
- [x] `BitwigApiFacadeTest` — index bounds error code assertions updated
- [x] `BaselineToolEnvelopeAtddTest` — `launchClipInvalidParameterIndexError` renamed and asserts correct code
- [x] `ErrorCodeTest` — `CLIP_INDEX_OUT_OF_BOUNDS` alias maps to `INVALID_PARAMETER_INDEX`

### AC 5: `ErrorContractComplianceTest` covers index paths
- [x] Negative index scenarios: `clip_index`, `scene_index`, `track_index`, `device_index` (8 scenarios)
- [x] Non-negative overflow scenarios: `scene_index` exceeding scene count, `track_index` exceeding track count, `scene_index` exceeding clip counts in `session_launchSceneByIndex` (4 scenarios)
- [x] Integer overflow (4294967296) scenarios: `get_device_details` track/device, `get_clips_in_scene`, `get_track_details`, `list_devices_on_track` (5 scenarios)
- [x] Non-integer (1.5) rejection scenarios: `get_clips_in_scene`, `get_device_details` track/device (3 scenarios)
- [x] Value-range scenarios remain `INVALID_RANGE` (no regression)

### Review Follow-up Coverage
- [x] `GetClipsInSceneTool` negative `scene_index` → `INVALID_PARAMETER_INDEX`
- [x] `GetTrackDetailsTool` negative `track_index` → `INVALID_PARAMETER_INDEX`
- [x] `ListDevicesOnTrackTool` negative `track_index` → `INVALID_PARAMETER_INDEX`
- [x] `ClipSceneController.getClipsInScene` out-of-bounds `scene_index` → `INVALID_PARAMETER_INDEX`
- [x] Non-integer index rejection (float coercion guard) in `GetTrackDetailsTool`, `ListDevicesOnTrackTool`, `GetClipsInSceneTool`
- [x] `ClipSceneController.launchSceneByIndex` out-of-bounds → `INVALID_PARAMETER_INDEX` (not `SCENE_NOT_FOUND`)
- [x] `SceneToolTest` updated mocks for out-of-bounds to expect `INVALID_PARAMETER_INDEX`
- [x] Integer overflow guard (`Integer.MIN_VALUE..MAX_VALUE`) in `ParameterValidator.validateRequiredInteger()` and tool-level validators
- [x] `GetDeviceDetailsTool` overflow index → `INVALID_PARAMETER_INDEX` (inline validation replacing `validateRequiredInteger()`)
- [x] Non-mock overflow contract tests for `get_device_details`, `get_clips_in_scene`, `get_track_details`, `list_devices_on_track`
- [x] Non-mock non-integer contract tests for `get_clips_in_scene`, `get_device_details`
- [x] `ParameterValidator.validateRequiredIndexInteger()` — overflow emits `INVALID_PARAMETER_INDEX` for index selectors
- [x] `ClipTool`/`SceneTool` updated to use `validateRequiredIndexInteger` for `clip_index`/`scene_index`
- [x] Overflow contract tests for `launch_clip` (clip_index=4294967296) and `session_launchSceneByIndex` (scene_index=4294967296)
- [x] `ClipSceneControllerTest` — non-mock regression tests for `launchSceneByIndex` negative/out-of-bounds and `getClipsInScene` negative/out-of-bounds
- [x] `GetDeviceDetailsToolTest` — replaced mirrored helper with real tool handler invocation
- [x] `ClipSceneController.launchSceneByIndex` preserves non-index `BitwigApiException` errors instead of collapsing to `INVALID_PARAMETER_INDEX`
- [x] `ClipSceneController.launchSceneByIndex` uses index-based APIs (`getTrackClipCountByIndex`, `launchClipByTrackIndex`) to avoid duplicate-track-name ambiguity
- [x] Contract test for `session_launchSceneByIndex` no-track path → `SCENE_NOT_FOUND`

## Test Files Changed by Story 1.6
- `src/test/java/io/github/fabb/wigai/common/validation/ParameterValidatorTest.java`
- `src/test/java/io/github/fabb/wigai/bitwig/BitwigApiFacadeTest.java`
- `src/test/java/io/github/fabb/wigai/contract/ErrorContractComplianceTest.java`
- `src/test/java/io/github/fabb/wigai/mcp/tool/BaselineToolEnvelopeAtddTest.java`
- `src/test/java/io/github/fabb/wigai/mcp/tool/GetDeviceDetailsToolTest.java`
- `src/test/java/io/github/fabb/wigai/mcp/tool/ListDevicesOnTrackToolTest.java`
- `src/test/java/io/github/fabb/wigai/common/error/ErrorCodeTest.java`
- `src/test/java/io/github/fabb/wigai/bitwig/SceneBankFacadeTest.java` (new)
- `src/test/java/io/github/fabb/wigai/common/error/WigAIErrorHandlerTest.java` (new)
- `src/test/java/io/github/fabb/wigai/mcp/tool/SceneToolTest.java`
- `src/test/java/io/github/fabb/wigai/features/ClipSceneControllerTest.java`

## Validation Run
Full suite executed:

```bash
./gradlew test --rerun
```

Result: `BUILD SUCCESSFUL` — 600 tests, 0 failures (February 11, 2026)
