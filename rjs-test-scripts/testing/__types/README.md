# RhettJS TypeScript Definitions

Comprehensive type definitions for IDE autocomplete and type safety.

**Auto-extracted from mod JAR on first load**

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
import Structure from 'Structure';
import World from 'World';

Structure.  // Should show: capture, place, captureLarge, etc.
World.      // Should show: getBlock, setBlock, fill, etc.
```

If you see method suggestions with JSDoc tooltips, it's working! 🎉

## Files

- `rhettjs.d.ts` - All 11 RhettJS APIs with JSDoc examples and common types

## Included APIs

All 11 RhettJS APIs with full type definitions:

1. **Runtime** - Environment and lifecycle
2. **Console** - Logging
3. **wait()** - Tick-based delays
4. **Store** - Ephemeral key-value storage
5. **NBT** - NBT manipulation
6. **Commands** - Command registration
7. **Server** - Server events
8. **World** - World manipulation (async)
9. **Structure** - Structure operations (async)
10. **Large Structure** - Multi-piece structures (async)
11. **Script.argv** - Utility script arguments

## More Information

- GitHub: https://github.com/rhettjs/rhettjs
- Documentation: https://rhettjs.dev
