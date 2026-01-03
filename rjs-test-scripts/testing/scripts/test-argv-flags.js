/**
 * Test Script.argv enhanced flag parsing with values
 *
 * Test commands:
 * /rjs run test-argv-flags -abc
 * /rjs run test-argv-flags -a=3 -b=hello -c
 * /rjs run test-argv-flags --name=John --age=25 --verbose
 * /rjs run test-argv-flags -x=100 -y=60 -z=200 --dimension="the_nether"
 *
 * Note: Flags MUST start with - or --
 *   -x=100    → flag x with value 100
 *   x=100     → positional string argument "x=100"
 */

console.log('=== Testing Enhanced Script.argv Flag Parsing ===\n');

// Test boolean flags
console.log('[Boolean Flags]');
console.log(`  a = ${Script.argv.get('a')}`);
console.log(`  b = ${Script.argv.get('b')}`);
console.log(`  c = ${Script.argv.get('c')}`);
console.log(`  verbose = ${Script.argv.get('verbose')}`);

// Test flags with values
console.log('\n[Flags with Values]');
console.log(`  x = ${Script.argv.get('x')} (type: ${typeof Script.argv.get('x')})`);
console.log(`  y = ${Script.argv.get('y')} (type: ${typeof Script.argv.get('y')})`);
console.log(`  z = ${Script.argv.get('z')} (type: ${typeof Script.argv.get('z')})`);
console.log(`  name = ${Script.argv.get('name')} (type: ${typeof Script.argv.get('name')})`);
console.log(`  age = ${Script.argv.get('age')} (type: ${typeof Script.argv.get('age')})`);
console.log(`  dimension = ${Script.argv.get('dimension')} (type: ${typeof Script.argv.get('dimension')})`);

// Test hasFlag (backward compatibility)
console.log('\n[hasFlag() Method]');
console.log(`  hasFlag('a') = ${Script.argv.hasFlag('a')}`);
console.log(`  hasFlag('x') = ${Script.argv.hasFlag('x')}`);
console.log(`  hasFlag('missing') = ${Script.argv.hasFlag('missing')}`);

// Test positional args
console.log('\n[Positional Arguments]');
const allArgs = Script.argv.getAll();
console.log(`  Count: ${allArgs.length}`);
allArgs.forEach((arg, i) => {
    console.log(`  [${i}] = ${arg}`);
});

// Test raw args
console.log('\n[Raw Arguments]');
console.log(`  ${Script.argv.raw.join(' ')}`);

console.log('\n=== Test Complete ===');
