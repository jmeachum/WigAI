# Deployment Configuration

## Runtime Deployment Model

- Artifact type: `WigAI.bwextension`
- Runtime host: Bitwig Studio extension runtime (local desktop host)
- Network exposure: embedded Jetty endpoint `/mcp`, loopback-only host enforcement

## Build and Packaging Path

- Build file: `build.gradle.kts`
- Packaging task: `bwextension`
- Output artifact path: `build/extensions/WigAI.bwextension`
- Fat-jar assembly: Shadow plugin (`com.gradleup.shadow`)

## CI/CD Deployment Pipeline

### Validation and Build Workflows

- `.github/workflows/pr-validation.yml`
  - Runs change detection
  - Invokes reusable build/test workflow for code changes

- `.github/workflows/build-and-test.yml`
  - Test job: `./gradlew test`
  - Build job: `./gradlew bwextension -x test`
  - Optional artifact upload

### Release Workflow

- `.github/workflows/release.yml`
  - Trigger: push to `main` and manual dispatch
  - Build command: `./gradlew clean build --warning-mode=none`
  - Publish command: `./gradlew nyxPublish --warning-mode=none --stacktrace`
  - Release asset expects `build/extensions/WigAI.bwextension`

## Versioning and Release Controls

- Semantic release system: Nyx (`com.mooltiverse.oss.nyx` in `settings.gradle.kts`)
- Publication target: GitHub release service
- Version bumps derive from conventional commit history

## Branch and Policy Gates Affecting Deployment

- `.github/workflows/branch-policy.yml` enforces source/target branch rules before promotion
- Main release path expects merges from `develop/cycle-*` or `hotfix/*`

## Operational Constraints

- No cloud/Kubernetes/Terraform deployment stack detected for runtime service.
- Deployment is desktop-host-centric: install extension into Bitwig's extension directory.
- Loopback binding is mandatory under current MVP no-auth model.

## Deployment Checklist (Current Project)

1. Ensure tests pass (`./gradlew test`) for code changes.
2. Build extension (`./gradlew build` or `./gradlew bwextension`).
3. Verify artifact exists at `build/extensions/WigAI.bwextension`.
4. Install extension into Bitwig extensions directory.
5. Enable extension and verify MCP endpoint availability.
6. For official releases, merge to `main` and let Nyx/GitHub Actions publish.
