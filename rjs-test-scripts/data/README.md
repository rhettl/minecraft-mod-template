# RhettJS Datapack

This `rjs/data/` directory works as a Minecraft datapack.

On server start, RhettJS automatically creates a symlink from `world/datapacks/rjs` → `rjs/`, making all JSON files here available to Minecraft.

## Custom Dimensions

To create a custom dimension:

1. **Create dimension type** in `data/rhettjs/dimension_type/<name>.json`
2. **Create dimension** in `data/rhettjs/dimension/<name>.json`
3. **Restart server** for changes to take effect

### Example: Structure Test Dimension

See the included files:
- `data/rhettjs/dimension_type/structure-test.json` - Dimension properties
- `data/rhettjs/dimension/structure-test.json` - Dimension configuration

To use:
```javascript
// Teleport to the dimension
Command.executeAsServer("execute in rhettjs:structure-test run tp @s 0 64 0");
```

## Other Datapack Features

You can also use this directory for:
- **Recipes**: `data/<namespace>/recipe/<name>.json`
- **Loot Tables**: `data/<namespace>/loot_table/<name>.json`
- **Advancements**: `data/<namespace>/advancement/<name>.json`
- **Tags**: `data/<namespace>/tags/<type>/<name>.json`
- **Functions**: `data/<namespace>/function/<name>.mcfunction`

All standard Minecraft datapack features work here!

## Resources (Future)

In the future, `rjs/assets/` will work as a resource pack for:
- Custom item textures
- Custom block models
- Custom sounds
- GUI overlays
