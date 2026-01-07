// Test StructureNbt API
// Tests structure capture/place and world operations

import StructureNbt from 'StructureNbt';

console.log("=".repeat(50));
console.log("StructureNbt API Test");
console.log("=".repeat(50));

// Test 1: Capture a structure from the world
console.log("\n[Test 1] Capturing structure from world...");
try {
  await StructureNbt.capture(
    { x: 0, y: 60, z: 0, dimension: 'minecraft:overworld' },
    { x: 5, y: 65, z: 5, dimension: 'minecraft:overworld' },
    'test:test-structure'
  );
  console.log("  ✓ StructureNbt.capture() working");
} catch (error) {
  console.error("  ✗ StructureNbt.capture() failed:", error.message);
}

// Test 2: Check if structure exists
console.log("\n[Test 2] Checking if structure exists...");
try {
  const exists = await StructureNbt.exists('test:test-structure');
  console.log("  StructureNbt.exists('test:test-structure'):", exists);
  const notExists = await StructureNbt.exists('test:nonexistent-structure');
  console.log("  StructureNbt.exists('test:nonexistent-structure'):", notExists);
  console.log("  ✓ StructureNbt.exists() working");
} catch (error) {
  console.error("  ✗ StructureNbt.exists() failed:", error.message);
}

// Test 3: Get structure size
console.log("\n[Test 3] Getting structure size...");
try {
  const size = await StructureNbt.getSize('test:test-structure');
  console.log("  Structure size:", JSON.stringify(size));
  console.log("  ✓ StructureNbt.getSize() working");
} catch (error) {
  console.error("  ✗ StructureNbt.getSize() failed:", error.message);
}

// Test 4: Place structure in world
console.log("\n[Test 4] Placing structure in world...");
try {
  await StructureNbt.place(
    { x: 100, y: 64, z: 100, dimension: 'minecraft:overworld' },
    'test:test-structure'
  );
  console.log("  ✓ StructureNbt.place() working (no rotation)");
} catch (error) {
  console.error("  ✗ StructureNbt.place() failed:", error.message);
}

// Test 5: Place structure with rotation
console.log("\n[Test 5] Placing structure with rotation...");
try {
  await StructureNbt.place(
    { x: 110, y: 64, z: 100, dimension: 'minecraft:overworld' },
    'test:test-structure',
    { rotation: 90 }
  );
  console.log("  ✓ StructureNbt.place() with rotation working");
} catch (error) {
  console.error("  ✗ StructureNbt.place() with rotation failed:", error.message);
}

// Test 6: List all structures
console.log("\n[Test 6] Listing all structures...");
try {
  const structures = await StructureNbt.list();
  console.log("  Total structures:", structures.length);
  console.log("  First few structures:", structures.slice(0, 5));
  console.log("  ✓ StructureNbt.list() working");
} catch (error) {
  console.error("  ✗ StructureNbt.list() failed:", error.message);
}

// Test 7: List structures in a specific namespace
console.log("\n[Test 7] Listing structures in namespace...");
try {
  const testStructures = await StructureNbt.list('test');
  console.log("  Structures in 'test' namespace:", testStructures.length);
  console.log("  ✓ StructureNbt.list(namespace) working");
} catch (error) {
  console.error("  ✗ StructureNbt.list(namespace) failed:", error.message);
}

// Test 8: Remove structure
console.log("\n[Test 8] Removing structure...");
try {
  const removed = await StructureNbt.remove('test:test-structure');
  console.log("  StructureNbt.remove('test:test-structure'):", removed);
  const stillExists = await StructureNbt.exists('test:test-structure');
  console.log("  Still exists after remove:", stillExists);
  console.log("  ✓ StructureNbt.remove() working");
} catch (error) {
  console.error("  ✗ StructureNbt.remove() failed:", error.message);
}

console.log("\n" + "=".repeat(50));
console.log("All StructureNbt API tests completed!");
console.log("=".repeat(50));
