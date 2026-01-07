# RhettJS TypeScript Definitions

Comprehensive modular type definitions for IDE autocomplete and type safety.

**Auto-extracted from mod JAR on first load**

## Import Styles

RhettJS supports multiple import patterns:

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

## IDE Setup

### Visual Studio Code

Type definitions should be auto-discovered. If not:

Add to the top of your script:
```javascript
/// <reference path="../__types/rhettjs.d.ts" />
```

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

## Files

Modular type definitions split by API:
- `rhettjs.d.ts` - Barrel file (re-exports all APIs)
- `types.d.ts` - Common types (Position, Block, Player, Caller)
- Individual API files: `runtime.d.ts`, `world.d.ts`, `commands.d.ts`, `server.d.ts`, `store.d.ts`, `nbt.d.ts`, `structure.d.ts`, `script.d.ts`

## Included APIs

All RhettJS APIs with full type definitions:

1. **Runtime** (global) - Environment and lifecycle
2. **console** (global) - Logging
3. **wait()** (global) - Tick-based delays
4. **Store** - Ephemeral key-value storage
5. **NBT** - NBT manipulation
6. **Commands** - Command registration
7. **Server** - Server events
8. **World** - World manipulation (async)
9. **StructureNbt** - Single-file structure operations (async)
10. **LargeStructureNbt** - Multi-piece structures (async)
11. **Script.argv** - Utility script arguments

## More Information

- GitHub: https://github.com/rhettjs/rhettjs
- Documentation: https://rhettjs.dev