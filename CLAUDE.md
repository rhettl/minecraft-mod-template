# Claude Instructions

This is a Minecraft mod project using a multi-loader, multi-version architecture.

## Coding Guidelines

### No Side-Effect Mutations
Functions should return results, not mutate their arguments. Avoid pass-by-reference side effects. If a function needs to "change" something, return the new value. Methods on objects may mutate `this`, but standalone functions should be pure.

### File Organization
Prefer functions ≤200 lines and files <800 lines. When approaching these limits, consider splitting. Distribute code into relevant directories and classes—do not pile unrelated code into single files. When uncertain about where code belongs, ask.

### Use Existing Libraries
Before writing new code to implement common functionality, ask if there's an existing library or internal utility that already does it. Check the codebase for existing helpers before creating new ones.

### Test Discipline
- **When writing/fixing implementation code**: Do not modify existing tests. If tests fail, fix the code to match the test specification. If you believe a test is wrong, stop and explain why before touching it.
- **When explicitly asked to write tests**: You may create and modify tests freely.

### Layered Architecture
Isolate external access (registries, world state, APIs, file I/O) into dedicated service/adapter files. Business logic should receive pure data or internal models, not registry handles or Minecraft types directly. Commands should contain only orchestration—parse input, call business logic, format output—never operational logic. This enables testability and prevents duplication of external access patterns.

**Layer summary**:
- **Models**: Internal data types that shield business logic from external types
- **Services/Adapters**: Handle registry access, world queries, external APIs
- **Business Logic**: Pure functions operating on models
- **Commands/Controllers**: Thin orchestration layer only

## Directory Map

```
├── common/              # Shared mod code (both loaders)
├── fabric/              # Fabric-specific code & resources
├── neoforge/            # NeoForge-specific code & resources
├── docs/                # Public documentation (committed)
├── dev-docs/            # Private working docs (gitignored)
├── .claude/             # Claude Code settings
├── .github/workflows/   # CI/CD workflows
└── gradle/              # Gradle wrapper
```

**Documentation locations**:
- `docs/` — Public documentation, guides, API docs (committed to repo)
- `dev-docs/` — Internal working files, investigations, scratch notes, screenshots (gitignored, for you and Claude to collaborate)
- Root — Only essential files (README.md, CLAUDE.md, build scripts)

When creating documentation or notes, use the appropriate directory. Do not litter the root with files.

## Tech Stack

- **Language**: Kotlin (not Java)
- **Build**: Gradle with Kotlin DSL (`.gradle.kts` files)
- **Multi-loader**: Architectury (Fabric + NeoForge from single codebase)
- **Multi-version**: Stonecutter (version-specific code via preprocessor comments)

## Project Structure

```
├── common/          # Shared code (both loaders)
├── fabric/          # Fabric-specific code & resources
├── neoforge/        # NeoForge-specific code & resources
├── gradle.properties    # Mod version, MC version, dependencies
├── stonecutter.gradle.kts   # Active version control
└── settings.gradle.kts      # Stonecutter + Architectury setup
```

## Building

User uses IDE runConfiguration now, but the below still exists.

```bash
./gradlew build          # Build all platforms
./build.sh               # Build and deploy to local Minecraft instances
```

## Available Tools

- **gh CLI**: GitHub CLI is available for creating PRs, issues, releases, etc.
- **gradlew**: Gradle wrapper for building
- **build.sh**: Build and deploy to local Minecraft instances, only used for external prod testing instead of dev testing.
- **tag-release.sh**: Create version tags and push

## Key Concepts

### Architectury (Multi-loader)
- `common/` contains platform-agnostic code
- `fabric/` and `neoforge/` contain loader-specific implementations
- Use `@ExpectPlatform` for platform-specific method implementations

### Stonecutter (Multi-version)
- Version-specific code uses preprocessor comments:
  ```kotlin
  //? if >=1.21.1
  newApiCall()
  //? else
  /*oldApiCall()*/
  //? endif
  ```
- Current active version is in `stonecutter.gradle.kts`
- Version properties are in `versions/{version}/gradle.properties`

### Kotlin on Fabric
- Uses `fabric-language-kotlin` runtime
- Entrypoints use Kotlin adapter in `fabric.mod.json`:
  ```json
  "entrypoints": {
    "main": [{ "adapter": "kotlin", "value": "com.example.MyMod" }]
  }
  ```

## Common Tasks

### Adding a dependency
1. Add to `gradle.properties` (version)
2. Add to appropriate `build.gradle.kts` (common, fabric, or neoforge)

### Adding platform-specific code
1. Create interface/expect in `common/`
2. Implement in `fabric/` and `neoforge/`

### Creating a release
```bash
./tag-release.sh 0.2.0                    # Simple release
./tag-release.sh 0.2.0 -m "New feature"   # With custom message
```

## Gotchas

- **loom.platform**: Each loader subproject needs `gradle.properties` with `loom.platform = fabric|neoforge`
- **Stonecutter siblings**: Use `stonecutter.node.sibling("common")` to reference common project, not `project(":common")`
- **Mixin configs**: Named `{modid}.mixins.json` in common, `{modid}-{loader}.mixins.json` in loader dirs
- **No Architectury API**: This template uses Architectury Plugin only (not the runtime API)

## Files to Update for New Mod

If using as template, run `./setup.sh` or manually update:
- `gradle.properties` (modVersion, mavenGroup, archivesBaseName)
- Package directories and imports
- `fabric.mod.json`, `neoforge.mods.toml`
- Mixin config filenames
- `.github/workflows/publish-modrinth.yml` (modrinth-id)
