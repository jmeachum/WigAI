# Component Inventory

## Runtime Entry and Lifecycle

| Component | Type | Responsibility |
|---|---|---|
| `WigAIExtensionDefinition` | Extension definition | Declares extension identity and API version contract |
| `WigAIExtension` | Lifecycle orchestrator | Initializes runtime services and handles start/stop/restart flow |

## Hosting and Protocol Core

| Component | Type | Responsibility |
|---|---|---|
| `JettyServerManager` | Server manager | Embedded Jetty lifecycle and loopback bind enforcement |
| `McpServerManager` | Protocol manager | MCP server configuration and tool registration |
| `McpErrorHandler` | Error middleware | Unified success/error envelopes, retry policy integration |

## Feature Controllers

| Component | Type | Responsibility |
|---|---|---|
| `TransportController` | Feature controller | Start/stop transport operations |
| `DeviceController` | Feature controller | Selected-device parameter and detail workflows |
| `ClipSceneController` | Feature controller | Clip/scene launch and clip-in-scene retrieval flows |

## MCP Tool Surface (`mcp/tool`)

- `StatusTool`
- `TransportTool`
- `ClipTool`
- `SceneTool`
- `SceneByNameTool`
- `ListTracksTool`
- `GetTrackDetailsTool`
- `ListDevicesOnTrackTool`
- `GetDeviceDetailsTool`
- `ListScenesTool`
- `GetClipsInSceneTool`
- `DeviceParamTool`

## Bitwig Integration Layer

| Component | Type | Responsibility |
|---|---|---|
| `BitwigApiFacade` | Facade | Encapsulates Bitwig host API access and data shaping |
| `SceneBankFacade` | Adapter utility | Scene-bank specific convenience and extraction logic |

## Configuration Layer

| Component | Type | Responsibility |
|---|---|---|
| `ConfigManager` | Interface | Abstract runtime host/port config contract |
| `PreferencesBackedConfigManager` | Implementation | Bitwig preference-backed config + observer notifications |
| `ConfigChangeObserver` | Interface | Callback contract for config change reactions |

## Cross-Cutting Utilities

### `common/error`
- `ErrorCode`, `BitwigApiException`, `WigAIErrorHandler`

### `common/retry`
- `RetryPolicy`, `RetryExecutor`

### `common/logging`
- `StructuredLogger`

### `common/validation`
- `ParameterValidator`

### `common/data`
- `ParameterInfo`, `ParameterSetting`, `ParameterSettingResult`

## Test and Operations Components

- Test suite: `src/test/java` (53 Java test files discovered)
- CI/CD workflows: `.github/workflows` (branch policy, PR validation, reusable build/test, release)
