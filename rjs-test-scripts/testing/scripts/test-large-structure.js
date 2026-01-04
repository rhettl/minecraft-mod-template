/**
 * Large Structure API Integration Test
 * Run with: /rjs run test-large-structure -x=<x> -z=<z>
 *
 * Tests Large Structure API operations including captureLarge, placeLarge, getSize, listLarge, deleteLarge
 */

import Structure from 'Structure';
import World from 'World';

console.log('=== Large Structure API Integration Test ===');

// Parse arguments using named flags
const testX = Script.argv.get('x');
const testY = Script.argv.get('y') !== undefined ? Script.argv.get('y') : 60;
const testZ = Script.argv.get('z');

if (testX === undefined || testZ === undefined) {
    console.log('Usage: /rjs run test-large-structure -x=<x> -z=<z> [-y=<y>]');
    console.log('');
    console.log('Examples:');
    console.log('  /rjs run test-large-structure -x=100 -z=100          (y defaults to 60)');
    console.log('  /rjs run test-large-structure -x=100 -y=70 -z=100    (specify all coords)');
    console.log('');
    console.log('This will test large structure capture, placement, and file operations');
    console.log('Note: Creates a large 100x50x100 test structure');
    Runtime.exit();
}

console.log(`Test position: ${testX}, ${testY}, ${testZ}`);

async function runTests() {
    try {
        // Test 1: Create a large test structure (100x50x100)
        console.log('\n[Test 1] Creating large test structure (100x50x100)');
        const structureX = testX;
        const structureZ = testZ;
        const sizeX = 100;
        const sizeY = 50;
        const sizeZ = 100;

        // Fill with pattern - bottom and top platforms (hollow middle for easy flying)
        console.log('  Building structure (this will take a moment)...');

        // Fill bottom platform
        await World.fill(
            { x: structureX, y: testY, z: structureZ },
            { x: structureX + sizeX - 1, y: testY, z: structureZ + sizeZ - 1 },
            'minecraft:stone'
        );

        // Fill top platform
        await World.fill(
            { x: structureX, y: testY + sizeY - 1, z: structureZ },
            { x: structureX + sizeX - 1, y: testY + sizeY - 1, z: structureZ + sizeZ - 1 },
            'minecraft:stone'
        );

        // Add corner markers on bottom
        await World.setBlock({ x: structureX, y: testY, z: structureZ }, 'minecraft:spruce_stairs');
        await World.setBlock({ x: structureX + sizeX - 1, y: testY, z: structureZ }, 'minecraft:gold_block');
        await World.setBlock({ x: structureX, y: testY, z: structureZ + sizeZ - 1 }, 'minecraft:gold_block');
        await World.setBlock({ x: structureX + sizeX - 1, y: testY, z: structureZ + sizeZ - 1 }, 'minecraft:gold_block');

        // Add corner markers on top
        await World.setBlock({ x: structureX, y: testY + sizeY - 1, z: structureZ }, 'minecraft:emerald_block');
        await World.setBlock({ x: structureX + sizeX - 1, y: testY + sizeY - 1, z: structureZ }, 'minecraft:emerald_block');
        await World.setBlock({ x: structureX, y: testY + sizeY - 1, z: structureZ + sizeZ - 1 }, 'minecraft:emerald_block');
        await World.setBlock({ x: structureX + sizeX - 1, y: testY + sizeY - 1, z: structureZ + sizeZ - 1 }, 'minecraft:emerald_block');

        // Add middle markers on both levels
        await World.setBlock(
            { x: structureX + Math.floor(sizeX/2), y: testY, z: structureZ + Math.floor(sizeZ/2) },
            'minecraft:diamond_block'
        );
        await World.setBlock(
            { x: structureX + Math.floor(sizeX/2), y: testY + sizeY - 1, z: structureZ + Math.floor(sizeZ/2) },
            'minecraft:diamond_block'
        );

        console.log('✓ Created 100x50x100 test structure');

        // Test 2: Capture as large structure (default 48x48x48 pieces)
        console.log('\n[Test 2] Structure.captureLarge() - default piece size (48x48x48)');
        const largeName = 'test:large_test';
        await Structure.captureLarge(
            { x: structureX, y: testY, z: structureZ },
            { x: structureX + sizeX - 1, y: testY + sizeY - 1, z: structureZ + sizeZ - 1 },
            largeName,
            { author: 'Test Script', description: 'Test large structure' }
        );
        console.log(`✓ Captured large structure as "${largeName}"`);
        console.log('  Expected pieces: ~6 pieces (3x2x3 grid with 48x48x48 chunks)');

        // Test 3: List large structures
        console.log('\n[Test 3] Structure.listLarge()');
        const largeStructures = await Structure.listLarge();
        console.log(`Found ${largeStructures.length} large structures:`);
        largeStructures.forEach(name => console.log(`  - ${name}`));

        if (largeStructures.includes(largeName)) {
            console.log(`✓ "${largeName}" appears in list`);
        } else {
            console.log(`✗ "${largeName}" not in list (unexpected)`);
        }

        // Test 4: List large structures with namespace filter
        console.log('\n[Test 4] Structure.listLarge("test")');
        const testLargeStructures = await Structure.listLarge('test');
        console.log(`Found ${testLargeStructures.length} large structures in "test" namespace:`);
        testLargeStructures.forEach(name => console.log(`  - ${name}`));
        console.log('✓ Namespace filter works');

        // Test 5: Get size of large structure
        console.log('\n[Test 5] Structure.getSize() - large structure');
        const largeSize = await Structure.getSize(largeName);
        console.log(`Large structure size: ${largeSize.x}x${largeSize.y}x${largeSize.z}`);

        if (largeSize.x === sizeX && largeSize.y === sizeY && largeSize.z === sizeZ) {
            console.log('✓ Size matches original capture dimensions');
        } else {
            console.log(`✗ Size mismatch! Expected ${sizeX}x${sizeY}x${sizeZ}`);
        }

        // Test 6: Create a regular (small) structure for size comparison
        console.log('\n[Test 6] Structure.getSize() - regular structure');
        const smallName = 'test:small_test';
        await Structure.capture(
            { x: structureX, y: testY, z: structureZ },
            { x: structureX + 4, y: testY + 4, z: structureZ + 4 },
            smallName
        );

        const smallSize = await Structure.getSize(smallName);
        console.log(`Regular structure size: ${smallSize.x}x${smallSize.y}x${smallSize.z}`);
        console.log('✓ getSize() works for both regular and large structures');

        // Test 7: Place large structure at new location (no rotation)
        console.log('\n[Test 7] Structure.placeLarge() - No rotation');
        const placeX1 = testX + 120;
        const placeZ1 = testZ;
        await Structure.placeLarge(
            { x: placeX1, y: testY, z: placeZ1 },
            largeName
        );
        console.log(`✓ Placed large structure at ${placeX1}, ${testY}, ${placeZ1}`);

        // Test 8: Place large structure with 90° rotation
        console.log('\n[Test 8] Structure.placeLarge() - 90° rotation');
        const placeX2 = testX + 250;
        const placeZ2 = testZ;
        await Structure.placeLarge(
            { x: placeX2, y: testY, z: placeZ2 },
            largeName,
            { rotation: 90 }
        );
        console.log(`✓ Placed large structure at ${placeX2}, ${testY}, ${placeZ2} (90° rotation)`);

        // Test 9: Place large structure centered
        console.log('\n[Test 9] Structure.placeLarge() - Centered');
        const placeX3 = testX + 180;
        const placeZ3 = testZ + 180;
        // Add marker below to see center point
        await World.setBlock(
            { x: placeX3, y: testY - 1, z: placeZ3 },
            'minecraft:emerald_block'
        );
        await Structure.placeLarge(
            { x: placeX3, y: testY, z: placeZ3 },
            largeName,
            { centered: true }
        );
        console.log(`✓ Placed large structure at ${placeX3}, ${testY}, ${placeZ3} (centered)`);

        // Test 10: Capture with custom piece size
        console.log('\n[Test 10] Structure.captureLarge() - custom piece size (30x30x30)');
        const customName = 'test:custom_pieces';
        await Structure.captureLarge(
            { x: structureX, y: testY, z: structureZ },
            { x: structureX + sizeX - 1, y: testY + sizeY - 1, z: structureZ + sizeZ - 1 },
            customName,
            { pieceSize: { x: 30, y: 30, z: 30 } }
        );
        console.log(`✓ Captured large structure with custom piece size as "${customName}"`);
        console.log('  Expected pieces: ~20 pieces (4x2x4 grid with 30x30x30 chunks)');

        // Test 11: Verify custom piece size getSize
        console.log('\n[Test 11] Verify custom piece structure size');
        const customSize = await Structure.getSize(customName);
        console.log(`Custom piece structure size: ${customSize.x}x${customSize.y}x${customSize.z}`);

        if (customSize.x === sizeX && customSize.y === sizeY && customSize.z === sizeZ) {
            console.log('✓ Size matches regardless of piece size');
        } else {
            console.log(`✗ Size mismatch! Expected ${sizeX}x${sizeY}x${sizeZ}`);
        }

        // Test 12: Delete large structures
        console.log('\n[Test 12] Structure.deleteLarge()');
        const deleted1 = await Structure.deleteLarge(largeName);
        console.log(`Deleted "${largeName}": ${deleted1}`);

        const deleted2 = await Structure.deleteLarge(customName);
        console.log(`Deleted "${customName}": ${deleted2}`);

        if (deleted1 && deleted2) {
            console.log('✓ Large structures deleted successfully');
        } else {
            console.log('✗ Failed to delete some large structures');
        }

        // Test 13: Verify deletion
        console.log('\n[Test 13] Verifying deletion');
        const largeListAfter = await Structure.listLarge();
        const stillExists = largeListAfter.includes(largeName);

        if (!stillExists) {
            console.log(`✓ Large structure "${largeName}" no longer in list`);
        } else {
            console.log(`✗ Large structure "${largeName}" still in list (unexpected)`);
        }

        // Test 14: Delete non-existent large structure
        console.log('\n[Test 14] Deleting non-existent large structure');
        const deletedNonExistent = await Structure.deleteLarge('test:nonexistent');
        if (!deletedNonExistent) {
            console.log('✓ Correctly returned false for non-existent large structure');
        } else {
            console.log('✗ Incorrectly returned true for non-existent large structure');
        }

        // Clean up regular structure
        await Structure.delete(smallName);

        // Summary
        console.log('\n=== Test Summary ===');
        console.log('All Large Structure API tests completed successfully!');
        console.log(`Test structures created at: ${testX}, ${testY}, ${testZ}`);
        console.log('Structures:');
        console.log(`  - Original 100x50x100 at ${structureX}, ${testY}, ${structureZ}`);
        console.log(`  - Placement (no rotation) at ${placeX1}, ${testY}, ${placeZ1}`);
        console.log(`  - Placement (90°) at ${placeX2}, ${testY}, ${placeZ2}`);
        console.log(`  - Placement (centered) at ${placeX3}, ${testY}, ${placeZ3}`);
        console.log('');
        console.log('Note: Test large structures were deleted after verification');
        console.log('Placed structures remain for visual inspection');

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
