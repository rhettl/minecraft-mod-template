import Commands from "rhettjs/commands";
import World from 'rhettjs/world';
import WorldgenStructure from "rhettjs/worldgen-structure";
import {filterByPattern} from "../modules/resource-pattern.js";

const cmd = Commands.register('worldgen-structure')
  .description('Worldgen structure placement commands (villages, temples, etc.)');

// /worldgen-structure list [namespace]
cmd.subcommand('list')
  .argument('pattern', 'string', null)
  .executes(async ({caller, args}) => {
    try {
      let structures = await WorldgenStructure.list();

      if (args.pattern) {
        structures = filterByPattern(structures, args.pattern);
      }

      caller.sendMessage(`Available worldgen structures${args.namespace ? ` for namespace "${args.namespace}"` : ''}:`);

      if (structures.length === 0) {
        caller.sendMessage('  (none found)');
      } else {
        // Show first 20, then count
        const toShow = structures.slice(0, 20);
        toShow.forEach(structure => {
          caller.sendMessage(`  - ${structure}`);
        });

        if (structures.length > 20) {
          caller.sendMessage(`  ... and ${structures.length - 20} more`);
        }

        caller.sendMessage(`Total: ${structures.length} structures`);
      }
    } catch (err) {
      const errorMsg = err.message || err.toString() || String(err);
      caller.sendMessage(`Error listing structures: ${errorMsg}`);
      console.error(err);
    }
  });

// /worldgen-structure info <name>
cmd.subcommand('info')
  .argument('name', 'string')
  .executes(async ({caller, args}) => {
    try {
      const info = await WorldgenStructure.info(args.name);

      caller.sendMessage(`Structure: ${info.name}`);
      caller.sendMessage(`  Type: ${info.type}`);
      caller.sendMessage(`  Is Jigsaw: ${info.isJigsaw}`);
      caller.sendMessage(`  Terrain Adaptation: ${info.terrainAdaptation}`);
      caller.sendMessage(`  Generation Step: ${info.step}`);

      if (info.biomesTag) {
        caller.sendMessage(`  Biomes Tag: ${info.biomesTag}`);
      }

      if (info.biomes && info.biomes.length > 0) {
        caller.sendMessage(`  Biomes: ${info.biomes.slice(0, 5).join(', ')}${info.biomes.length > 5 ? ` (+${info.biomes.length - 5} more)` : ''}`);
      }

      if (info.spawnOverrides) {
        caller.sendMessage(`  Spawn Overrides: ${Object.keys(info.spawnOverrides).join(', ')}`);
      }
    } catch (err) {
      const errorMsg = err.message || err.toString() || String(err);
      caller.sendMessage(`Error getting structure info: ${errorMsg}`);
      console.error(err);
    }
  });

// /worldgen-structure place <name> <x> <z> [seed] [surface] [rotation]
// Note: String args with special chars (like :) must be quoted
cmd.subcommand('place')
  .argument('x', 'int')
  .argument('z', 'int')
  .argument('name', 'string')  // Must be quoted if contains : (e.g., "minecraft:village_plains")
  .argument('seed', 'int', null)
  .argument('surface', 'string', 'scan')  // Must be quoted (e.g., "scan")
  .argument('rotation', 'string', null)    // Must be quoted (e.g., "none")
  .executes(async ({caller, args}) => {
    const dimension = caller.isPlayer ? caller.position.dimension : caller.dimension;

    try {
      caller.sendMessage(`Placing ${args.name} at ${args.x}, ${args.z}...`);

      const options = {
        x: args.x,
        z: args.z,
        surface: args.surface,
        dimension: dimension,
      };

      if (args.seed !== null) {
        options.seed = args.seed;
      }

      if (args.rotation !== null) {
        options.rotation = args.rotation;
      }

      const result = await WorldgenStructure.place(args.name, options);

      if (result.success) {
        caller.sendMessage(`✓ Structure placed successfully!`);
        caller.sendMessage(`  Seed: ${result.seed}`);
        caller.sendMessage(`  Rotation: ${result.rotation}`);
        caller.sendMessage(`  Pieces: ${result.pieceCount}`);
        caller.sendMessage(`  Bounds: (${result.boundingBox.min.x}, ${result.boundingBox.min.y}, ${result.boundingBox.min.z}) to (${result.boundingBox.max.x}, ${result.boundingBox.max.y}, ${result.boundingBox.max.z})`);

        // Calculate size
        const sizeX = result.boundingBox.max.x - result.boundingBox.min.x;
        const sizeY = result.boundingBox.max.y - result.boundingBox.min.y;
        const sizeZ = result.boundingBox.max.z - result.boundingBox.min.z;
        caller.sendMessage(`  Size: ${sizeX}x${sizeY}x${sizeZ}`);
      } else {
        caller.sendMessage(`✗ Placement failed: ${result.error}`);
      }
    } catch (err) {
      const errorMsg = err.message || err.toString() || String(err);
      caller.sendMessage(`Error placing structure: ${errorMsg}`);
      console.error(err);
    }
  });

// /worldgen-structure place-jigsaw <pool> <target> <depth> <x> <z> [surface]
cmd.subcommand('place-jigsaw')
  .argument('pool', 'string')
  .argument('target', 'string')
  .argument('depth', 'int')
  .argument('x', 'int')
  .argument('z', 'int')
  .argument('surface', 'string', 'scan')
  .executes(async ({caller, args}) => {
    const dimension = caller.isPlayer ? caller.position.dimension : caller.dimension;

    try {
      if (args.depth < 1 || args.depth > 20) {
        caller.sendMessage('Depth must be between 1 and 20');
        return 0;
      }

      caller.sendMessage(`Placing jigsaw from pool ${args.pool} at ${args.x}, ${args.z}...`);

      const result = await WorldgenStructure.placeJigsaw({
        pool: args.pool,
        target: args.target,
        maxDepth: args.depth,
        x: args.x,
        z: args.z,
        surface: args.surface,
        dimension: dimension
      });

      if (result.success) {
        caller.sendMessage(`✓ Jigsaw placed successfully!`);
        caller.sendMessage(`  Pool: ${result.pool}`);
        caller.sendMessage(`  Target: ${result.target}`);
        caller.sendMessage(`  Max Depth: ${result.maxDepth}`);
      } else {
        caller.sendMessage(`✗ Placement failed: ${result.error}`);
      }
    } catch (err) {
      const errorMsg = err.message || err.toString() || String(err);
      caller.sendMessage(`Error placing jigsaw: ${errorMsg}`);
      console.error(err);
    }
  });

// /worldgen-structure exists <name>
cmd.subcommand('exists')
  .argument('name', 'string')
  .executes(async ({caller, args}) => {
    try {
      const exists = await WorldgenStructure.exists(args.name);
      caller.sendMessage(`${args.name}: ${exists ? '✓ exists' : '✗ does not exist'}`);
    } catch (err) {
      const errorMsg = err.message || err.toString() || String(err);
      caller.sendMessage(`Error checking existence: ${errorMsg}`);
      console.error(err);
    }
  });


// /worldgen-structure place <name> <x> <z> [seed] [surface] [rotation]
// Note: String args with special chars (like :) must be quoted
cmd.subcommand('controller-place')
  .argument('signX', 'int')
  .argument('signY', 'int')
  .argument('signZ', 'int')
  // .argument('seed', 'int', null)
  // .argument('surface', 'string', 'scan')  // Must be quoted (e.g., "scan")
  // .argument('rotation', 'string', null)    // Must be quoted (e.g., "none")
  .executes(async ({caller, args}) => {
    const dimension = caller.isPlayer ? caller.position.dimension : caller.dimension;
    let name = '';
    const signPos = {
      x: args.signX,
      y: args.signY,
      z: args.signZ,
      dimension,
    }
    try {
      const sign = await World.getBlockEntity(signPos);
      if (sign) {
        const lines = sign.front_text?.messages ?? [];
        name = lines
          .map(i => JSON.parse(i))
          .join('')
          .trim()
          .replace(/\s+/g, '')
        ;
      } else {
        console.error(`no sign at ${signPos}`);
        return 0;
      }
    } catch (err) {
      console.error(`failed getting sign at ${signPos}: ${err}`);
      return 0;
    }

    try {
      caller.sendMessage(`Placing ${args.name} at ${args.x}, ${args.z}...`);

      const options = {
        x: 0,
        z: 0,
        surface: 'scan',
        dimension: dimension,
      };

      // if (args.seed !== null) {
      //   options.seed = args.seed;
      // }
      //
      // if (args.rotation !== null) {
      //   options.rotation = args.rotation;
      // }

      const result = await WorldgenStructure.place(name, options);

      if (result.success) {
        caller.sendMessage(`✓ Structure placed successfully!`);
        caller.sendMessage(`  Seed: ${result.seed}`);
        caller.sendMessage(`  Rotation: ${result.rotation}`);
        caller.sendMessage(`  Pieces: ${result.pieceCount}`);
        caller.sendMessage(`  Bounds: (${result.boundingBox.min.x}, ${result.boundingBox.min.y}, ${result.boundingBox.min.z}) to (${result.boundingBox.max.x}, ${result.boundingBox.max.y}, ${result.boundingBox.max.z})`);

        // Calculate size
        const sizeX = result.boundingBox.max.x - result.boundingBox.min.x;
        const sizeY = result.boundingBox.max.y - result.boundingBox.min.y;
        const sizeZ = result.boundingBox.max.z - result.boundingBox.min.z;
        caller.sendMessage(`  Size: ${sizeX}x${sizeY}x${sizeZ}`);
      } else {
        caller.sendMessage(`✗ Placement failed: ${result.error}`);
      }
    } catch (err) {
      const errorMsg = err.message || err.toString() || String(err);
      caller.sendMessage(`Error placing structure: ${errorMsg}`);
      console.error(err);
    }
  });



console.log('[worldgen-structures.js] WorldgenStructure commands registered');
