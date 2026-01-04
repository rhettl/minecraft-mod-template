# RhettJS TypeScript Definitions

Comprehensive type definitions for IDE autocomplete and type safety.

**Last Updated**: 2026-01-03 (GraalVM Migration Complete)
**Source**: `common/src/main/resources/rhettjs-types/rhettjs.d.ts`
**Validation**: Automated tests ensure types match runtime APIs

## Files

- `rhettjs.d.ts` - All 11 RhettJS APIs with JSDoc examples and common types

## IDE Setup

### Visual Studio Code

Type definitions should be auto-discovered. If not:

Add to the top of your script:
```javascript
/// <reference path="../__types/rhettjs.d.ts" />
```

Or create `jsconfig.json` in your scripts directory:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "node",
    "checkJs": true
  },
  "include": ["**/*.js"],
  "typeAcquisition": {
    "include": ["__types/rhettjs.d.ts"]
  }
}
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

## Included APIs

All 11 RhettJS APIs with full type definitions:

1. **Runtime** - Environment and lifecycle (`Runtime.env`, `Runtime.exit()`)
2. **Console** - Logging (`console.log`, `console.error`)
3. **wait()** - Tick-based delays
4. **Store** - Ephemeral key-value storage
5. **NBT** - NBT manipulation utilities
6. **Commands** - Command registration
7. **Server** - Server events and properties
8. **World** - World manipulation (async)
9. **Structure** - Structure file operations (async)
10. **Large Structure** - Multi-piece structures (async)
11. **Script.argv** - Utility script arguments

## Type Validation

Types are validated against runtime via automated tests:
- **Test**: `common/src/test/kotlin/.../APITypeValidationTest.kt`
- **Runs in CI/CD**: Fails build if types drift from runtime
- **100% strict matching**: All public methods must be documented

## Updating Types

When adding new APIs:
1. Update `GraalEngine.createXAPIProxy()` (runtime binding)
2. Update `common/src/main/resources/rhettjs-types/rhettjs.d.ts` (types)
3. Update `APITypeValidationTest.getRuntimeMethods()` (test)
4. Run tests: `./gradlew test`
5. Copy to test-scripts: `cp common/src/main/resources/rhettjs-types/rhettjs.d.ts rjs-test-scripts/__types/`

## More Information

- API Documentation: `dev-docs/API-DESIGN.md`
- Migration Status: `dev-docs/GRAAL-MIGRATION-STATUS.md`
- GitHub: https://github.com/rhettjs/rhettjs
