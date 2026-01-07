# RhettJS TypeScript Definitions

Comprehensive type definitions for IDE autocomplete and type safety.

**Last Updated**: 2026-01-06 (Modular Structure)
**Source**: `common/src/main/resources/rhettjs-types/`
**Validation**: Automated tests ensure types match runtime APIs

## Files

Modular type definitions split by API:
- `rhettjs.d.ts` - Barrel file (re-exports all APIs)
- `types.d.ts` - Common types (Position, Block, Player, Caller)
- `runtime.d.ts` - Runtime API (global)
- `store.d.ts` - Store API
- `nbt.d.ts` - NBT API
- `commands.d.ts` - Commands API
- `server.d.ts` - Server API
- `world.d.ts` - World API
- `structure.d.ts` - StructureNbt & LargeStructureNbt APIs
- `script.d.ts` - Script API

## Import Styles

RhettJS supports multiple import patterns:

```javascript
// Barrel imports (recommended)
import {World, Commands} from 'rhettjs';

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

## IDE Setup

### Visual Studio Code

Type definitions should be auto-discovered. If not:

Add to the top of your script:
```javascript
/// <reference path="../__types/rhettjs.d.ts" />
```

Or create `jsconfig.json` in your scripts directory (use `jsconfig.json.template` as a starting point).

### IntelliJ IDEA / WebStorm

Should work automatically! If not:
1. Right-click `__types/` folder
2. Mark Directory As → **Resource Root**
3. File → Invalidate Caches → Restart

## Testing Autocomplete

Create a test script:

```javascript
import {World, StructureNbt} from 'rhettjs';

StructureNbt.  // Should show: capture, place, exists, etc.
World.         // Should show: getBlock, setBlock, fill, etc.
Runtime.       // Should show: exit, setScriptTimeout, env
```

If you see method suggestions with JSDoc tooltips, it's working! 🎉

## Included APIs

All RhettJS APIs with full type definitions:

1. **Runtime** (global) - Environment and lifecycle (`Runtime.env`, `Runtime.exit()`)
2. **console** (global) - Logging (`console.log`, `console.error`)
3. **wait()** (global) - Tick-based delays
4. **Store** - Ephemeral key-value storage
5. **NBT** - NBT manipulation utilities
6. **Commands** - Command registration
7. **Server** - Server events and properties
8. **World** - World manipulation (async)
9. **StructureNbt** - Single-file structure operations (async)
10. **LargeStructureNbt** - Multi-piece structures (async)
11. **Script.argv** - Utility script arguments

## Type Validation

Types are validated against runtime via automated tests:
- **Test**: `common/src/test/kotlin/.../APITypeValidationTest.kt`
- **Runs in CI/CD**: Fails build if types drift from runtime
- **100% strict matching**: All public methods must be documented

## Updating Types

When adding new APIs:
1. Update `GraalEngine.createXAPIProxy()` (runtime binding)
2. Update appropriate `.d.ts` file in `common/src/main/resources/rhettjs-types/`
3. Run tests: `./gradlew test`
4. Types are automatically extracted to `<minecraft>/rjs/__types/` on server start

## More Information

- API Documentation: `dev-docs/API-DESIGN.md`
- Migration Status: `dev-docs/GRAAL-MIGRATION-STATUS.md`
- GitHub: https://github.com/rhettjs/rhettjs