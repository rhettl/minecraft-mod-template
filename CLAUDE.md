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

**Test-Driven Development for APIs**:
When adding or modifying JavaScript APIs, follow this workflow:

1. **Update TypeScript definitions** (`common/src/main/resources/rhettjs-types/rhettjs.d.ts`)
   - Add/update method signatures with JSDoc comments
   - Define parameter and return types
   - Include usage examples

2. **Update validation test** (`common/src/test/kotlin/.../APITypeValidationTest.kt`)
   - Add expected method names to `getRuntimeMethods()`
   - Test will fail if .d.ts doesn't match expected methods

3. **Implement runtime binding** (`GraalEngine.kt`)
   - Create/update `createXAPIProxy()` methods
   - Bind Kotlin implementations to JavaScript API surface

4. **Run tests** - `./gradlew test`
   - Type validation test ensures .d.ts matches runtime
   - Integration tests verify functionality

This TDD approach ensures:
- TypeScript definitions stay in sync with runtime
- APIs are designed before implementation
- No method drift between types and code
- 100% API coverage in type definitions (excluding `_` prefixed private methods)

### Layered Architecture
Isolate external access (registries, world state, APIs, file I/O) into dedicated service/adapter files. Business logic should receive pure data or internal models, not registry handles or Minecraft types directly. Commands should contain only orchestration—parse input, call business logic, format output—never operational logic. This enables testability and prevents duplication of external access patterns.

**Layer summary**:
- **Models**: Internal data types that shield business logic from external types
- **Services/Adapters**: Handle registry access, world queries, external APIs
- **Business Logic**: Pure functions operating on models
- **Commands/Controllers**: Thin orchestration layer only

### Anti-Corruption Layer (JavaScript APIs)
**CRITICAL**: RhettJS exposes a JavaScript scripting API to Minecraft. All JavaScript APIs MUST follow the anti-corruption pattern:

- **No Java objects exposed** - Convert all Minecraft types to pure JavaScript primitives, objects, or arrays
- **No `.toString()` pollution** - Everything must be `console.log()`-able without `Runtime.inspect()`
- **Properties for data** - Use properties (`player.name`), not getters
- **Methods for actions** - Use methods only for side effects (`player.teleport(pos)`)
- **Consistent shapes** - Same data types always use same structure across all APIs
- **Async for I/O** - File/world operations return Promises
- **Sync for immediate** - In-memory operations are synchronous

**Example violation**:
```javascript
// BAD - Exposes Java Player object
const player = World.getPlayer("Steve");
console.log(player);  // [JavaObject Player@abc123]
```

**Correct implementation**:
```javascript
// GOOD - Pure JavaScript object
const player = World.getPlayer("Steve");
console.log(player);  // { name: "Steve", uuid: "...", health: 20, ... }
```

All adapters (PlayerAdapter, BlockAdapter, etc.) exist to enforce this boundary.

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
  - `dev-docs/TODO.md` — **Inter-session todo list** for tracking tasks across Claude Code sessions
- Root — Only essential files (README.md, CLAUDE.md, build scripts)

When creating documentation or notes, use the appropriate directory. Do not litter the root with files.

**Task Management**:
- **Session todos**: Use Claude Code's built-in TodoWrite tool during active sessions
- **Inter-session todos**: Use `dev-docs/TODO.md` for tasks that need to persist across sessions
  - Keep the completed tasks section pruned to only 1-2 sessions of recent completions
  - Archive or delete older completed tasks to keep the file focused
- **Long-term issues**: Use GitHub Issues for user-facing bugs/features

## Tech Stack

- **Language**: Kotlin (not Java)
- **JavaScript Engine**: GraalVM JS 24.1.1 (ES2022, async/await, ES6 modules)
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

## TypeScript Definitions & IDE Support

RhettJS provides modular TypeScript definitions for all JavaScript APIs to enable IDE autocomplete and type checking.

**Location**: `common/src/main/resources/rhettjs-types/`
- **Modular structure** (2026-01-06): Split into individual API files for better organization
- `rhettjs.d.ts` - Barrel file (re-exports all APIs)
- `types.d.ts` - Common types (Position, Block, Player, Caller)
- Individual API files: `runtime.d.ts`, `world.d.ts`, `commands.d.ts`, `server.d.ts`, `store.d.ts`, `nbt.d.ts`, `structure.d.ts`, `script.d.ts`

**Auto-extraction**: All type definition files are automatically extracted from the mod JAR to `<minecraft>/rjs/__types/` on first load via `FilesystemInitializer`.

**Import Styles**: RhettJS supports multiple import patterns:
```javascript
// Barrel imports (recommended)
import {World, Commands, StructureNbt} from 'rhettjs';

// Submodule imports
import World from 'rhettjs/world';
import Commands from 'rhettjs/commands';

// Legacy bare specifiers (still supported)
import World from 'World';
import Commands from 'Commands';

// Runtime is always global (like window or process)
Runtime.exit();
console.log(Runtime.env.RJS_VERSION);
```

**Validation**: `APITypeValidationTest.kt` ensures runtime API methods match TypeScript definitions:
- Parses individual .d.ts files to extract declared methods
- Compares against actual runtime methods via GraalVM introspection
- Fails build if types drift from runtime
- 100% strict matching (excluding `_` prefixed private methods and properties)

**Workflow**:
1. Add new API method to appropriate `.d.ts` file with JSDoc
2. Implement method in GraalEngine ProxyObject
3. Run `./gradlew test` - **automatically validates** .d.ts matches runtime
4. Types are symlinked from `common/src/main/resources/rhettjs-types/` to `rjs-test-scripts/__types/` (no manual copy needed)

**Current status** (2026-01-06):
- ✅ **ALL APIs validated automatically** - Runtime, StructureNbt, LargeStructureNbt, World, Store, Commands, Server, NBT, Script
- ✅ **Modular structure**: Each API has its own `.d.ts` file
- ✅ **Multiple import styles**: Barrel, submodule, and legacy imports all supported
- ✅ **Runtime is global**: No import needed, available everywhere like `console` or `process`
- Note: Properties (like `Server.tps`, `World.dimensions`) are automatically excluded from validation

**IDE Setup**:
- VSCode: Auto-discovered or use `/// <reference path="../__types/rhettjs.d.ts" />`
- IntelliJ: Right-click `__types/` → Mark Directory As → Resource Root

## Session Documentation

When working on significant features or investigations, document your work in `dev-docs/`:

**Active docs** (keep updated):
- `API-DESIGN.md` - JavaScript API specifications
- `GRAAL-MIGRATION-STATUS.md` - Migration status and completed phases
- `CURRENT_SPEC.md` - Comprehensive implementation specification (read by future Claude sessions)
- `INGAME-TESTING.md` - Testing mode documentation
- `MODULE-IMPORTS.md` - ES6 module resolution details
- `CUSTOM-MODULE-RESOLUTION.md` - Virtual module system documentation

**Temporary docs** (delete when obsolete):
- Investigation documents (prefix with date: `2026-01-03-feature-name.md`)
- Migration/refactor plans (delete after completion)
- Session summaries (delete after merging insights into active docs)

**Important**: `dev-docs/` is gitignored. Keep only relevant, current documentation. Delete obsolete files regularly.

**For future Claude sessions**: Read `dev-docs/CURRENT_SPEC.md` for comprehensive project context.

## Files to Update for New Mod

If using as template, run `./setup.sh` or manually update:
- `gradle.properties` (modVersion, mavenGroup, archivesBaseName)
- Package directories and imports
- `fabric.mod.json`, `neoforge.mods.toml`
- Mixin config filenames
- `.github/workflows/publish-modrinth.yml` (modrinth-id)
