# Data Models - main

## Summary

- **Persistence model:** No repository-level database schema/migration layer detected.
- **Schema artifacts scanned:** `migrations/**`, `alembic/**`, `flyway/**`, `liquibase/**`, `prisma/**`, `*.prisma`, `*migration*.sql`, `*migration*.ts`
- **Result:** No matching schema/migration files found in the project tree.

## Runtime Domain Models (In-Memory / API Payload)

### Parameter and device-control records

- `ParameterInfo` (`src/main/java/io/github/fabb/wigai/common/data/ParameterInfo.java`)
  - Fields: `index`, `name`, `value`, `display_value`
  - Purpose: reports selected device parameter state in MCP responses.

- `ParameterSetting` (`src/main/java/io/github/fabb/wigai/common/data/ParameterSetting.java`)
  - Fields: `parameter_index`, `value`
  - Purpose: typed input for batch parameter mutation requests.

- `ParameterSettingResult` (`src/main/java/io/github/fabb/wigai/common/data/ParameterSettingResult.java`)
  - Fields: `parameter_index`, `status`, `new_value`, `error_code`, `message`
  - Purpose: per-item result envelope for bulk parameter updates.

### Aggregated response models

- `DeviceController.DeviceParametersResult`
- `DeviceController.DeviceDetailsResult` (serializes to a map with track/device context and remote controls)
- `ClipSceneController.ClipLaunchResult`
- `ClipSceneController.SceneLaunchResult`

### Facade-produced map structures

`BitwigApiFacade` and `SceneBankFacade` produce structured `Map<String, Object>` / `List<Map<String, Object>>` payloads for:

- Transport status
- Selected track/device/clip slot snapshots
- Track/device/scene inventories
- Clip slot details per scene/track

## Relationship Model

- MCP tools -> feature controllers -> `BitwigApiFacade` -> Bitwig host runtime.
- Data is predominantly transient runtime state from Bitwig, normalized into typed records/maps for MCP outputs.
- No ORM entities, repository classes, or SQL migration lineage detected.

## Constraints and Implications

- Data consistency is tied to live Bitwig host state, not long-lived persisted storage.
- Contract stability depends on tool schema + map/record field compatibility rather than DB schema versioning.
