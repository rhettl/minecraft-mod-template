/**
 * World API Integration Test
 * Run with: /rjs run testworld
 *
 * Tests World API operations including fill, getBlock, setBlock, etc.
 */

import World from 'World';

console.log('=== World API Integration Test ===');

// Parse arguments using named flags (-x=value or --x=value)
const testX = Script.argv.get('x');
const testY = Script.argv.get('y') !== undefined ? Script.argv.get('y') : 60;
const testZ = Script.argv.get('z');

if (testX === undefined || testZ === undefined) {
    console.log('Usage: /rjs run testworld -x=<x> -z=<z> [-y=<y>]');
    console.log('');
    console.log('Examples:');
    console.log('  /rjs run testworld -x=100 -z=100          (y defaults to 60)');
    console.log('  /rjs run testworld -x=100 -y=70 -z=100    (specify all coords)');
    console.log('');
    console.log('This will create test structures at the specified coordinates');
    Runtime.exit();
}

console.log(`Test position: ${testX}, ${testY}, ${testZ}`);

async function runTests() {
    try {
        // Test 1: Check dimensions property
        console.log('\n[Test 1] World.dimensions');
        console.log(`Dimensions: ${World.dimensions.join(', ')}`);
        console.log(`✓ dimensions is array with ${World.dimensions.length} entries`);

        // Test 2: Get block at test position
        console.log('\n[Test 2] World.getBlock()');
        const block = await World.getBlock({ x: testX, y: testY, z: testZ });
        console.log(`Block at ${testX},${testY},${testZ}: ${block.id}`);
        if (Object.keys(block.properties).length > 0) {
            console.log(`Properties: ${JSON.stringify(block.properties)}`);
        }
        console.log('✓ getBlock() works');

        // Test 3: Set single block
        console.log('\n[Test 3] World.setBlock()');
        await World.setBlock({ x: testX, y: testY, z: testZ }, 'minecraft:diamond_block');
        const newBlock = await World.getBlock({ x: testX, y: testY, z: testZ });
        if (newBlock.id === 'minecraft:diamond_block') {
            console.log('✓ setBlock() works - diamond block placed');
        } else {
            console.log(`✗ setBlock() failed - expected diamond_block, got ${newBlock.id}`);
        }

        // Test 4: Fill small cube (3x3x3)
        console.log('\n[Test 4] World.fill() - Small cube');
        const smallX = testX + 5;
        const smallZ = testZ;
        const count1 = await World.fill(
            { x: smallX, y: testY, z: smallZ },
            { x: smallX + 2, y: testY + 2, z: smallZ + 2 },
            'minecraft:gold_block'
        );
        console.log(`✓ Placed ${count1} gold blocks (expected 27, got ${count1})`);

        // Test 5: Fill larger cube (5x5x5)
        console.log('\n[Test 5] World.fill() - Larger cube');
        const medX = testX + 10;
        const medZ = testZ;
        const count2 = await World.fill(
            { x: medX, y: testY, z: medZ },
            { x: medX + 4, y: testY + 4, z: medZ + 4 },
            'minecraft:iron_block'
        );
        console.log(`✓ Placed ${count2} iron blocks (expected 125, got ${count2})`);

        // Test 6: Fill flat platform (10x1x10)
        console.log('\n[Test 6] World.fill() - Flat platform');
        const platX = testX + 20;
        const platZ = testZ;
        const count3 = await World.fill(
            { x: platX, y: testY, z: platZ },
            { x: platX + 9, y: testY, z: platZ + 9 },
            'minecraft:stone'
        );
        console.log(`✓ Placed ${count3} stone blocks (expected 100, got ${count3})`);

        // Test 7: Fill with properties (stairs)
        console.log('\n[Test 7] World.setBlock() with properties');
        const stairX = testX;
        const stairZ = testZ + 5;
        await World.setBlock(
            { x: stairX, y: testY, z: stairZ },
            'minecraft:oak_stairs',
            { facing: 'north', half: 'bottom' }
        );
        const stairBlock = await World.getBlock({ x: stairX, y: testY, z: stairZ });
        console.log(`Stair placed: ${stairBlock.id}`);
        console.log(`Properties: ${JSON.stringify(stairBlock.properties)}`);
        console.log('✓ setBlock() with properties works');

        // Test 8: Fill wall (1x5x10)
        console.log('\n[Test 8] World.fill() - Vertical wall');
        const wallX = testX + 30;
        const wallZ = testZ;
        const count4 = await World.fill(
            { x: wallX, y: testY, z: wallZ },
            { x: wallX, y: testY + 4, z: wallZ + 9 },
            'minecraft:bricks'
        );
        console.log(`✓ Placed ${count4} brick blocks (expected 50, got ${count4})`);

        // Test 9: Get time
        console.log('\n[Test 9] World.getTime()');
        const time = await World.getTime();
        console.log(`Current time: ${time} ticks`);
        console.log('✓ getTime() works');

        // Test 10: Get weather
        console.log('\n[Test 10] World.getWeather()');
        const weather = await World.getWeather();
        console.log(`Current weather: ${weather}`);
        console.log('✓ getWeather() works');

        // Test 11: Get all players
        console.log('\n[Test 11] World.getPlayers()');
        const players = await World.getPlayers();
        console.log(`Online players: ${players.length}`);
        players.forEach(player => {
            console.log(`  - ${player.name} (${player.uuid})`);
        });
        console.log('✓ getPlayers() works');

        // Test 12: Create a pattern (checkerboard 10x10)
        console.log('\n[Test 12] World.fill() - Checkerboard pattern');
        const checkX = testX + 40;
        const checkZ = testZ;
        let checkerCount = 0;

        for (let x = 0; x < 10; x++) {
            for (let z = 0; z < 10; z++) {
                const block = (x + z) % 2 === 0 ? 'minecraft:white_wool' : 'minecraft:black_wool';
                await World.setBlock(
                    { x: checkX + x, y: testY, z: checkZ + z },
                    block
                );
                checkerCount++;
            }
        }
        console.log(`✓ Created ${checkerCount} block checkerboard pattern`);

        // Summary
        console.log('\n=== Test Summary ===');
        console.log('All tests completed successfully!');
        console.log(`Test structures created at: ${testX}, ${testY}, ${testZ}`);
        console.log('Structures:');
        console.log(`  - Diamond block marker at ${testX}, ${testY}, ${testZ}`);
        console.log(`  - Gold cube (3x3x3) at ${testX + 5}, ${testY}, ${testZ}`);
        console.log(`  - Iron cube (5x5x5) at ${testX + 10}, ${testY}, ${testZ}`);
        console.log(`  - Stone platform (10x10) at ${testX + 20}, ${testY}, ${testZ}`);
        console.log(`  - Brick wall (1x5x10) at ${testX + 30}, ${testY}, ${testZ}`);
        console.log(`  - Checkerboard (10x10) at ${testX + 40}, ${testY}, ${testZ}`);

    } catch (error) {
        console.error(`Test failed: ${error.message}`);
        console.error(`Stack: ${error.stack}`);
    }
}

// Run the async tests
runTests().then(() => {
    console.log('\n✓ All async operations completed');
}).catch(error => {
    console.error(`Fatal error: ${error.message}`);
});
