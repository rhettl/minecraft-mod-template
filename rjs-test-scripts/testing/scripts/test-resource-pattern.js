// Test script for resource-pattern module

import {
    createMatcher,
    matchesPattern,
    filterByPattern,
    createMultiMatcher,
    normalizeResourceLocation
} from '../../modules/resource-pattern.js';

console.log("=== Resource Pattern Tests ===\n");

// Test 1: Basic wildcard patterns
console.log("Test 1: Basic wildcard patterns");
const testResources = [
    "minecraft:village_plains",
    "minecraft:village_desert",
    "minecraft:desert_pyramid",
    "minecraft:jungle_temple",
    "terralith:fortified_village",
    "terralith:desert_oasis"
];

console.log("\nPattern: '*:village*'");
const villageMatcher = createMatcher("*:village*");
testResources.forEach(r => {
    const matches = villageMatcher(r);
    console.log(`  ${r}: ${matches ? '✓' : '✗'}`);
});

// Test 2: Namespace-specific patterns
console.log("\nTest 2: Namespace-specific patterns");
console.log("Pattern: 'minecraft:*'");
const minecraftMatcher = createMatcher("minecraft:*");
testResources.forEach(r => {
    const matches = minecraftMatcher(r);
    console.log(`  ${r}: ${matches ? '✓' : '✗'}`);
});

// Test 3: Partial namespace wildcards
console.log("\nTest 3: Partial namespace wildcards");
console.log("Pattern: 'terra*:*'");
const terraMatcher = createMatcher("terra*:*");
testResources.forEach(r => {
    const matches = terraMatcher(r);
    console.log(`  ${r}: ${matches ? '✓' : '✗'}`);
});

// Test 4: Complex pattern
console.log("\nTest 4: Complex pattern");
console.log("Pattern: '*:*desert*'");
const desertMatcher = createMatcher("*:*desert*");
testResources.forEach(r => {
    const matches = desertMatcher(r);
    console.log(`  ${r}: ${matches ? '✓' : '✗'}`);
});

// Test 5: Exact match (no wildcards)
console.log("\nTest 5: Exact match (no wildcards)");
console.log("Pattern: 'minecraft:village_plains'");
console.log(`  minecraft:village_plains: ${matchesPattern("minecraft:village_plains", "minecraft:village_plains") ? '✓' : '✗'}`);
console.log(`  minecraft:village_desert: ${matchesPattern("minecraft:village_desert", "minecraft:village_plains") ? '✓' : '✗'}`);

// Test 6: Filter by pattern
console.log("\nTest 6: Filter by pattern");
const villageStructures = filterByPattern(testResources, "*:*village*");
console.log(`Structures matching '*:*village*':`);
villageStructures.forEach(s => console.log(`  - ${s}`));

// Test 7: Multi-pattern matching
console.log("\nTest 7: Multi-pattern matching (OR logic)");
const multiMatcher = createMultiMatcher(["*:village*", "*:temple*"]);
console.log("Patterns: ['*:village*', '*:temple*']");
testResources.forEach(r => {
    const matches = multiMatcher(r);
    console.log(`  ${r}: ${matches ? '✓' : '✗'}`);
});

// Test 8: Normalize resource locations
console.log("\nTest 8: Normalize resource locations");
console.log(`  'village_plains' -> '${normalizeResourceLocation('village_plains')}'`);
console.log(`  'minecraft:village_plains' -> '${normalizeResourceLocation('minecraft:village_plains')}'`);
console.log(`  'terralith:village' -> '${normalizeResourceLocation('terralith:village')}'`);

// Test 9: Edge cases
console.log("\nTest 9: Edge cases");
console.log(`  Pattern '*:*' matches 'anything:anything': ${matchesPattern("anything:anything", "*:*") ? '✓' : '✗'}`);
console.log(`  Pattern 'namespace:*' matches 'namespace:': ${matchesPattern("namespace:", "namespace:*") ? '✓' : '✗'}`);
console.log(`  Pattern '*:path' matches ':path': ${matchesPattern(":path", "*:path") ? '✓' : '✗'}`);

console.log("\n=== All tests complete ===");
