// Test script for WorldgenStructure API
// Tests structure placement with various surface modes

import { WorldgenStructure } from 'rhettjs';

console.log("=== WorldgenStructure API Tests ===\n");

// Test 1: List structures
console.log("Test 1: List vanilla structures");
try {
    const structures = await WorldgenStructure.list("minecraft");
    console.log(`Found ${structures.length} vanilla structures`);
    console.log(`First 5: ${structures.slice(0, 5).join(", ")}`);
} catch (error) {
    console.error(`Error listing structures: ${error}`);
}

// Test 2: Check if structure exists
console.log("\nTest 2: Check if village_plains exists");
try {
    const exists = await WorldgenStructure.exists("minecraft:village_plains");
    console.log(`village_plains exists: ${exists}`);
} catch (error) {
    console.error(`Error checking existence: ${error}`);
}

// Test 3: Get structure info
console.log("\nTest 3: Get village_plains info");
try {
    const info = await WorldgenStructure.info("minecraft:village_plains");
    console.log(`Name: ${info.name}`);
    console.log(`Type: ${info.type}`);
    console.log(`Is Jigsaw: ${info.isJigsaw}`);
    console.log(`Terrain Adaptation: ${info.terrainAdaptation}`);
    console.log(`Step: ${info.step}`);
    if (info.biomesTag) console.log(`Biomes Tag: ${info.biomesTag}`);
} catch (error) {
    console.error(`Error getting info: ${error}`);
}

// Test 4: Place structure with scan mode (for custom platforms)
console.log("\nTest 4: Place village with surface scan");
try {
    const result = await WorldgenStructure.place("minecraft:village_plains", {
        x: 0,
        z: 0,
        seed: 12345,
        surface: "scan",
        rotation: "none"
    });

    if (result.success) {
        console.log(`✓ Placement successful!`);
        console.log(`  Seed: ${result.seed}`);
        console.log(`  Rotation: ${result.rotation}`);
        console.log(`  Pieces: ${result.pieceCount}`);
        console.log(`  Bounding Box: (${result.boundingBox.min.x}, ${result.boundingBox.min.y}, ${result.boundingBox.min.z}) to (${result.boundingBox.max.x}, ${result.boundingBox.max.y}, ${result.boundingBox.max.z})`);
    } else {
        console.error(`✗ Placement failed: ${result.error}`);
    }
} catch (error) {
    console.error(`Error placing structure: ${error}`);
}

// Test 5: Place jigsaw from pool
console.log("\nTest 5: Place jigsaw from village pool");
try {
    const result = await WorldgenStructure.placeJigsaw({
        pool: "minecraft:village/plains/houses",
        target: "minecraft:bottom",
        maxDepth: 3,
        x: 100,
        z: 100,
        surface: "scan"
    });

    if (result.success) {
        console.log(`✓ Jigsaw placement successful!`);
        console.log(`  Pool: ${result.pool}`);
        console.log(`  Max Depth: ${result.maxDepth}`);
    } else {
        console.error(`✗ Jigsaw placement failed: ${result.error}`);
    }
} catch (error) {
    console.error(`Error placing jigsaw: ${error}`);
}

// Test 6: Test different surface modes
console.log("\nTest 6: Place with fixed height");
try {
    const result = await WorldgenStructure.place("minecraft:desert_pyramid", {
        x: 200,
        z: 200,
        surface: "fixed:65"
    });

    if (result.success) {
        console.log(`✓ Fixed height placement successful (${result.pieceCount} pieces)`);
    } else {
        console.error(`✗ Placement failed: ${result.error}`);
    }
} catch (error) {
    console.error(`Error with fixed height: ${error}`);
}

console.log("\n=== All tests complete ===");
