import Commands from 'Commands';
import Store from 'Store';
import Structure from 'Structure';
import Server from 'Server';
import {give, executeAsServer} from '../modules/command-helpers.js';
import {Performance} from "../modules/performance.js";
// import {Store as args} from "rhettjs";

// Create namespace for position storage
const positions = Store.namespace('large-structures:positions');

Commands.register('struct')
  .description('place a village')
  .argument('x', 'int')
  .argument('y', 'int')
  .argument('z', 'int')
  .executes(async ({ caller, args }) => {
    let pos = {
      x: args.x,
      y: args.y,
      z: args.z,
      dimension: caller.dimension,
    };

    const villages = [
      'minecraft:village_plains',
      'minecraft:village_desert',
      'minecraft:village_snowy',
      'minecraft:village_taiga',
      'minecraft:village_savanna',
    ];
    const rotations = [ 0, 90, 180, -90 ]

    await Structure.place(pos, villages[Math.floor(Math.random() * villages.length)], {
      centered: true,
      rotation: rotations[Math.floor(Math.random() * rotations.length)],
    });

  });

const cmd = Commands.register('large-structure')
  .description('Large structure management commands');

cmd.subcommand('wand')
  .executes(async ({caller}) => {
    const playerName = caller.name;
    console.log(`Player name: ${playerName}`);
    await executeAsServer('/' + give(
      `stick[item_name='{"text":"Large Structure Wand","italic":false}',enchantment_glint_override=true]`,
      1,
      playerName
    ));
  });

cmd.subcommand('pos1')
  .argument('x', "int")
  .argument('y', "int")
  .argument('z', "int")
  .executes(({caller, args}) => {
    setPosition(caller.name, 1, {
      x: args.x,
      y: args.y,
      z: args.z,
      dimension: caller.dimension || "minecraft:overworld"
    });
    caller.sendMessage(`§aPos1 set at ${args.x}, ${args.y}, ${args.z}`);
  });

cmd.subcommand('pos2')
  .argument('x', "int")
  .argument('y', "int")
  .argument('z', "int")
  .executes(({caller, args}) => {
    setPosition(caller.name, 2, {
      x: args.x,
      y: args.y,
      z: args.z,
      dimension: caller.dimension || "minecraft:overworld"
    });
    caller.sendMessage(`§aPos2 set at ${args.x}, ${args.y}, ${args.z}`);
  });

cmd.subcommand('check-positions')
  .executes(({caller, args}) => {
    const pos1 = getPosition(caller.name, 1);
    const pos2 = getPosition(caller.name, 2);

    if (!pos1) {
      caller.sendMessage('No position 1 available');
      return 0;
    }
    if (!pos2) {
      caller.sendMessage('No position 2 available');
      return 0;
    }

    const delta = {
      x: Math.abs(pos1.x - pos2.x) + 1,
      y: Math.abs(pos1.y - pos2.y) + 1,
      z: Math.abs(pos1.z - pos2.z) + 1,
    };

    caller.sendMessage(`Position 1: ${pos1.x}, ${pos1.y}, ${pos1.z}`);
    caller.sendMessage(`Position 2: ${pos2.x}, ${pos2.y}, ${pos2.z}`);
    caller.sendMessage(`Difference: ${delta.x}, ${delta.y}, ${delta.z}`);
    caller.sendMessage(`Volume: ${delta.x * delta.y * delta.z} m^3`);

  });

cmd.subcommand('clear-positions')
  .executes(({caller}) => {
    clearPositions(caller.name);
    caller.sendMessage('Positions cleared');
  });

cmd.subcommand('save')
  .argument('name', 'string')
  .argument('size', 'int', 48)
  .executes(async ({caller, args}) => {
    const playerName = caller.name;
    const pos = [
      getPosition(playerName, 1),
      getPosition(playerName, 2),
    ];

    await Structure.captureLarge(pos[0], pos[1], args.name, {
      size: args.size,
    });

  });

cmd.subcommand('list')
  .argument('namespace', 'string', null)
  .executes(async ({caller, args}) => {
    try {
      let structures = await Structure.listLarge(args.namespace);
      caller.sendMessage(`Available Large structures${args.namespace ? ` for namespace "${args.namespace}"` : ''}:`);
      structures.forEach(structure => {
        caller.sendMessage(`  - ${structure}`);
      })
    } catch (err) {
      caller.sendMessage('Failed to parse structures');
      console.error(err.stack);
    }
  });

cmd.subcommand('place')
  .argument('name', 'string')
  .argument('rotation', 'int', 0)
  .argument('mode', 'string', 'replace') // keep_air, overlay
  .executes(async ({caller, args}) => {
    return await doPlaceLarge({ caller, args, centered: false });
  })

cmd.subcommand('place-centered')
  .argument('name', 'string')
  .argument('rotation', 'int', 0)
  .argument('mode', 'string', 'replace') // keep_air, overlay
  .executes(async ({caller, args}) => {
    return await doPlaceLarge({caller, args, centered: true});
  })

// Left-click to set pos1 (position already includes dimension)
Server.on(Server.eventTypes.BLOCK_LEFT_CLICK, (event) => {
  const playerName = event.player.name;
  const item = event.item;
  const isWand = /Large Structure Wand/i.test(item?.displayName ?? '');

  if (!isWand) {
    return 1;
  }

  event.cancel();
  setPosition(playerName, 1, event.position);
  event.player.sendMessage(`§aPos1 set at ${event.position.x}, ${event.position.y}, ${event.position.z}`);
});

// Right-click to set pos2 (position already includes dimension)
Server.on(Server.eventTypes.BLOCK_RIGHT_CLICK, (event) => {
  const playerName = event.player.name;
  const item = event.item;
  const isWand = /Large Structure Wand/i.test(item?.displayName ?? '');
  if (!isWand) {
    return 1;
  }
  event.cancel();
  setPosition(playerName, 2, event.position);
  event.player.sendMessage(`§aPos2 set at ${event.position.x}, ${event.position.y}, ${event.position.z}`);
});

// Helper functions
function setPosition (playerName, posNum, position) {
  positions.set(`${playerName}:pos${posNum}`, position);
}

function getPosition (playerName, posNum) {
  return positions.get(`${playerName}:pos${posNum}`);
}

function clearPositions (playerName) {
  positions.clear(`${playerName}:pos1`);
  positions.clear(`${playerName}:pos2`);
}
async function doPlaceLarge ({caller, args, centered} = {}) {
  if (![0, 90, 180, -90].includes(args.rotation)) {
    caller.sendMessage(`Invalid rotation, must be one of [0, 90, 180, -90] Provided: ${args.rotation}`);
    return 0;
  }
  if (!['replace', 'keep_air', 'overlay'].includes(args.mode)) {
    caller.sendMessage(`Invalid mode, must be one of [replace, keep_air, overlay] Provided: ${args.mode}`);
    return 0;
  }


  const name = args.name.includes(':') ? args.name : 'minecraft:'+args.name;
  const namespace = name.split(':')[0];
  const timer = new Performance();
  const pos1 = getPosition(caller.name, 1);
  if (!pos1) {
    caller.sendMessage(`No position 1 available; please set`);
    return 0;
  }

  try {
    timer.start();

    const structures = await Structure.listLarge(namespace);
    console.log(structures, typeof structures);
    if (!structures.includes(name)) {
      caller.sendMessage(`Structure ${name} doesn't exist`);
      return 0;
    }
    timer.mark('placing')

    await Structure.placeLarge(
      pos1,
      name,
      {
        rotation: args.rotation,
        mode: args.mode,
        centered
      }
    );

    caller.sendMessage(`Structure Successfully Placed`);
  } catch (err) {
    caller.sendMessage('Failed to place structures');
    console.error(err);
  } finally {
    timer.stop();
    caller.sendMessage(`Placement took ${timer.formatElapsed()}`);
  }
}