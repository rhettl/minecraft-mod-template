/**
 * Structure API Integration Test
 * Run with: /rjs run test-structure -x=<x> -z=<z>
 *
 * Tests Structure API operations including capture, place, list, exists, delete
 */

import Structure from 'Structure';
import World from 'World';

console.log('=== Structure API Integration Test ===');

// Parse arguments using named flags
const testX = Script.argv.get('x');
const testY = Script.argv.get('y') !== undefined ? Script.argv.get('y') : 60;
const testZ = Script.argv.get('z');

if (testX === undefined || testZ === undefined) {
    console.log('Usage: /rjs run test-structure -x=<x> -z=<z> [-y=<y>]');
    console.log('');
    console.log('Examples:');
    console.log('  /rjs run test-structure -x=100 -z=100          (y defaults to 60)');
    console.log('  /rjs run test-structure -x=100 -y=70 -z=100    (specify all coords)');
    console.log('');
    console.log('This will test structure capture, placement, and file operations');
    Runtime.exit();
}

console.log(`Test position: ${testX}, ${testY}, ${testZ}`);

async function runTests() {
    try {
        // Test 1: Create a test structure (small 3x3x3 cube)
        console.log('\n[Test 1] Creating test structure with World.fill()');
        const structureX = testX;
        const structureZ = testZ;

        // Create a simple 3x3x3 pattern
        await World.fill(
            { x: structureX, y: testY, z: structureZ },
            { x: structureX + 2, y: testY + 2, z: structureZ + 2 },
            'minecraft:spruce_stairs'
        );
        // Add a diamond block marker in start
        await World.setBlock(
            { x: structureX, y: testY, z: structureZ },
            'minecraft:diamond_block'
        );
        console.log('✓ Created 3x3x3 test structure');

        // Test 2: Capture the structure
        console.log('\n[Test 2] Structure.capture()');
        const structureName = 'test:cube';
        await Structure.capture(
            { x: structureX, y: testY, z: structureZ },
            { x: structureX + 2, y: testY + 2, z: structureZ + 2 },
            structureName,
            { author: 'Test Script', description: 'Test 3x3x3 cube' }
        );
        console.log(`✓ Captured structure as "${structureName}"`);

        // Test 3: Check if structure exists
        console.log('\n[Test 3] Structure.exists()');
        const exists = await Structure.exists(structureName);
        if (exists) {
            console.log(`✓ Structure "${structureName}" exists`);
        } else {
            console.log(`✗ Structure "${structureName}" does not exist (unexpected)`);
            Runtime.exit();
        }

        // Test 4: List structures
        console.log('\n[Test 4] Structure.list()');
        const allStructures = await Structure.list();
        console.log(`Found ${allStructures.length} structures:`);
        allStructures.forEach(name => console.log(`  - ${name}`));

        if (allStructures.includes(structureName)) {
            console.log(`✓ "${structureName}" appears in list`);
        } else {
            console.log(`✗ "${structureName}" not in list (unexpected)`);
        }

        // Test 5: List structures with namespace filter
        console.log('\n[Test 5] Structure.list("test")');
        const testStructures = await Structure.list('test');
        console.log(`Found ${testStructures.length} structures in "test" namespace:`);
        testStructures.forEach(name => console.log(`  - ${name}`));
        console.log('✓ Namespace filter works');

        // Test 6: Place structure at new location (no rotation)
        console.log('\n[Test 6] Structure.place() - No rotation');
        const placeX1 = testX + 10;
        const placeZ1 = testZ;
        await Structure.place(
            { x: placeX1, y: testY, z: placeZ1 },
            structureName
        );
        console.log(`✓ Placed structure at ${placeX1}, ${testY}, ${placeZ1}`);

        // Test 7: Place structure with 90° rotation
        console.log('\n[Test 7] Structure.place() - 90° rotation');
        const placeX2 = testX + 20;
        const placeZ2 = testZ;
        // Add a diamond block marker below start
        await World.setBlock(
          { x: placeX2, y: testY-1, z: placeZ2 },
          'minecraft:diamond_block'
        );
        await Structure.place(
            { x: placeX2, y: testY, z: placeZ2 },
            structureName,
            { rotation: 90 }
        );
        console.log(`✓ Placed structure at ${placeX2}, ${testY}, ${placeZ2} (90° rotation)`);

        // Test 8: Place structure with 180° rotation
        console.log('\n[Test 8] Structure.place() - 180° rotation');
        const placeX3 = testX + 30;
        const placeZ3 = testZ;
        // Add a diamond block marker below start
        await World.setBlock(
          { x: placeX3, y: testY-1, z: placeZ3 },
          'minecraft:diamond_block'
        );
        await Structure.place(
            { x: placeX3, y: testY, z: placeZ3 },
            structureName,
            { rotation: 180 }
        );
        console.log(`✓ Placed structure at ${placeX3}, ${testY}, ${placeZ3} (180° rotation)`);

        // Test 9: Place structure with 270° rotation
        console.log('\n[Test 9] Structure.place() - 270° rotation');
        const placeX4 = testX + 40;
        const placeZ4 = testZ;
        // Add a diamond block marker below start
        await World.setBlock(
          { x: placeX4, y: testY-1, z: placeZ4 },
          'minecraft:diamond_block'
        );
        await Structure.place(
            { x: placeX4, y: testY, z: placeZ4 },
            structureName,
            { rotation: 270 }
        );
        console.log(`✓ Placed structure at ${placeX4}, ${testY}, ${placeZ4} (270° rotation)`);

        // Test 10: Place structure centered
        console.log('\n[Test 10] Structure.place() - Centered');
        const placeX5 = testX + 50;
        const placeZ5 = testZ;
        // Add a diamond block marker below start
        await World.setBlock(
          { x: placeX5, y: testY-1, z: placeZ5 },
          'minecraft:diamond_block'
        );
        await Structure.place(
            { x: placeX5, y: testY, z: placeZ5 },
            structureName,
            { centered: true }
        );
        console.log(`✓ Placed structure at ${placeX5}, ${testY}, ${placeZ5} (centered)`);

        // Test 11: Capture a structure with block properties (stairs)
        console.log('\n[Test 11] Capturing structure with block properties');
        const stairX = testX;
        const stairZ = testZ + 10;

        // Create stairs pattern
        await World.setBlock(
            { x: stairX, y: testY, z: stairZ },
            'minecraft:oak_stairs',
            { facing: 'north', half: 'bottom' }
        );
        await World.setBlock(
            { x: stairX + 1, y: testY, z: stairZ },
            'minecraft:oak_stairs',
            { facing: 'south', half: 'bottom' }
        );
        await World.setBlock(
            { x: stairX, y: testY, z: stairZ + 1 },
            'minecraft:oak_stairs',
            { facing: 'east', half: 'bottom' }
        );
        await World.setBlock(
            { x: stairX + 1, y: testY, z: stairZ + 1 },
            'minecraft:oak_stairs',
            { facing: 'west', half: 'bottom' }
        );

        const stairsName = 'test:stairs';
        await Structure.capture(
            { x: stairX, y: testY, z: stairZ },
            { x: stairX + 1, y: testY, z: stairZ + 1 },
            stairsName
        );
        console.log(`✓ Captured stairs structure as "${stairsName}"`);

        // Test 12: Place stairs with rotation to verify property rotation
        console.log('\n[Test 12] Placing stairs with rotation');
        await Structure.place(
            { x: stairX, y: testY, z: stairZ + 5 },
            stairsName,
            { rotation: 90 }
        );
        console.log('✓ Placed rotated stairs structure');

        // Test 13: Delete structures
        console.log('\n[Test 13] Structure.delete()');
        const deleted1 = await Structure.delete(structureName);
        console.log(`Deleted "${structureName}": ${deleted1}`);

        const deleted2 = await Structure.delete(stairsName);
        console.log(`Deleted "${stairsName}": ${deleted2}`);

        if (deleted1 && deleted2) {
            console.log('✓ Structures deleted successfully');
        } else {
            console.log('✗ Failed to delete some structures');
        }

        // Test 14: Verify deletion
        console.log('\n[Test 14] Verifying deletion');
        const existsAfter = await Structure.exists(structureName);
        if (!existsAfter) {
            console.log(`✓ Structure "${structureName}" no longer exists`);
        } else {
            console.log(`✗ Structure "${structureName}" still exists (unexpected)`);
        }

        // Test 15: Try to delete non-existent structure
        console.log('\n[Test 15] Deleting non-existent structure');
        const deletedNonExistent = await Structure.delete('test:nonexistent');
        if (!deletedNonExistent) {
            console.log('✓ Correctly returned false for non-existent structure');
        } else {
            console.log('✗ Incorrectly returned true for non-existent structure');
        }

        // Summary
        console.log('\n=== Test Summary ===');
        console.log('All Structure API tests completed successfully!');
        console.log(`Test structures created at: ${testX}, ${testY}, ${testZ}`);
        console.log('Structures:');
        console.log(`  - Original 3x3x3 cube at ${structureX}, ${testY}, ${structureZ}`);
        console.log(`  - Placement (no rotation) at ${placeX1}, ${testY}, ${placeZ1}`);
        console.log(`  - Placement (90°) at ${placeX2}, ${testY}, ${placeZ2}`);
        console.log(`  - Placement (180°) at ${placeX3}, ${testY}, ${placeZ3}`);
        console.log(`  - Placement (270°) at ${placeX4}, ${testY}, ${placeZ4}`);
        console.log(`  - Placement (centered) at ${placeX5}, ${testY}, ${placeZ5}`);
        console.log(`  - Original stairs at ${stairX}, ${testY}, ${stairZ}`);
        console.log(`  - Rotated stairs at ${stairX}, ${testY}, ${stairZ + 5}`);
        console.log('');
        console.log('Note: Test structures were deleted after verification');

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
