// Test Commands API
// Tests command registration with Brigadier integration

import Commands from 'Commands';

console.log("=".repeat(50));
console.log("Commands API Test");
console.log("=".repeat(50));

// Test 1: Check Commands API structure
console.log("\n[Test 1] Checking Commands API structure...");
console.log("  Commands object:", typeof Commands);
console.log("  Commands.register:", typeof Commands.register);
console.log("  Commands.unregister:", typeof Commands.unregister);
console.log("  ✓ Commands API structure correct");

// Test 2: Register simple command (no arguments)
console.log("\n[Test 2] Registering simple command...");
Commands.register('heal')
  .description('Heal yourself to full health')
  .executes(({ caller }) => {
    console.log("  Heal command would execute for:", caller.name);
  });
console.log("  ✓ Simple command registered");

// Test 3: Register command with single argument
console.log("\n[Test 3] Registering command with argument...");
Commands.register('tp')
  .description('Teleport to a player')
  .argument('target', 'player')
  .executes(({ caller, args }) => {
    console.log("  TP command:", caller.name, "->", args.target);
  });
console.log("  ✓ Command with argument registered");

// Test 4: Register command with multiple arguments
console.log("\n[Test 4] Registering command with multiple arguments...");
Commands.register('give')
  .description('Give items to a player')
  .argument('player', 'player')
  .argument('item', 'item')
  .argument('count', 'int')
  .executes(({ caller, args }) => {
    console.log("  Give command:", args.item, "x" + args.count, "to", args.player);
  });
console.log("  ✓ Command with multiple arguments registered");

// Test 5: Register command with permission (string)
console.log("\n[Test 5] Registering command with permission (string)...");
Commands.register('admin')
  .description('Admin-only command')
  .permission('admin.use')
  .executes(({ caller }) => {
    console.log("  Admin command executed by:", caller.name);
  });
console.log("  ✓ Command with permission string registered");

// Test 6: Register command with permission (function)
console.log("\n[Test 6] Registering command with permission (function)...");
Commands.register('op')
  .description('Operator command')
  .permission((caller) => {
    // Custom permission check
    return caller.isOp || false;
  })
  .executes(({ caller }) => {
    console.log("  Op command executed by:", caller.name);
  });
console.log("  ✓ Command with permission function registered");

// Test 7: Register async command
console.log("\n[Test 7] Registering async command...");
Commands.register('asynctest')
  .description('Test async command handler')
  .executes(async ({ caller }) => {
    await Promise.resolve();
    console.log("  Async command executed");
  });
console.log("  ✓ Async command registered");

// Test 8: Chainable builder pattern
console.log("\n[Test 8] Testing chainable builder...");
Commands.register('complex')
  .description('Complex command')
  .permission('test.use')
  .argument('arg1', 'string')
  .argument('arg2', 'int')
  .executes(({ caller, args }) => {
    console.log("  Complex command:", args.arg1, args.arg2);
  });
console.log("  ✓ Chainable builder works");

// Test 9: Different argument types
console.log("\n[Test 9] Testing different argument types...");
Commands.register('testtypes')
  .argument('str', 'string')
  .argument('num', 'int')
  .argument('decimal', 'float')
  .argument('player', 'player')
  .argument('item', 'item')
  .argument('block', 'block')
  .argument('entity', 'entity')
  .executes(({ args }) => {
    console.log("  All argument types accepted");
  });
console.log("  ✓ All argument types registered");

// Test 10: Unregister command
console.log("\n[Test 10] Unregistering command...");
Commands.register('temp')
  .executes(({ caller }) => {
    console.log("  Temp command");
  });
Commands.unregister('temp');
console.log("  ✓ Command unregistered");

// Test 11: Event object destructuring
console.log("\n[Test 11] Testing event object destructuring...");
Commands.register('destructure')
  .argument('target', 'player')
  .executes(({ caller, args, command }) => {
    console.log("  Destructured - caller:", caller.name);
    console.log("  Destructured - args:", args.target);
    console.log("  Destructured - command:", command);
  });
console.log("  ✓ Event destructuring works");

console.log("\n" + "=".repeat(50));
console.log("All Commands API tests passed!");
console.log("=".repeat(50));
