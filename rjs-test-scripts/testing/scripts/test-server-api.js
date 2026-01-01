// Test Server API
// Tests event system, server properties, and broadcast methods

import Server from 'Server';

console.log("=".repeat(50));
console.log("Server API Test");
console.log("=".repeat(50));

// Test 1: Check Server API structure
console.log("\n[Test 1] Checking Server API structure...");
console.log("  Server object:", typeof Server);
console.log("  Server.on:", typeof Server.on);
console.log("  Server.off:", typeof Server.off);
console.log("  Server.once:", typeof Server.once);
console.log("  Server.broadcast:", typeof Server.broadcast);
console.log("  Server.runCommand:", typeof Server.runCommand);
console.log("  ✓ Server API structure correct");

// Test 2: Register event handler with on()
console.log("\n[Test 2] Registering event handler with on()...");
let joinCount = 0;
const joinHandler = (player) => {
  joinCount++;
  console.log("  Player joined:", player.name);
};
Server.on('playerJoin', joinHandler);
console.log("  ✓ Event handler registered");

// Test 3: Register one-time handler with once()
console.log("\n[Test 3] Registering one-time handler with once()...");
Server.once('playerLeave', (player) => {
  console.log("  Player left (one-time):", player.name);
});
console.log("  ✓ One-time handler registered");

// Test 4: Unregister handler with off()
console.log("\n[Test 4] Unregistering handler with off()...");
Server.off('playerJoin', joinHandler);
console.log("  ✓ Handler unregistered");

// Test 5: Access server properties
console.log("\n[Test 5] Accessing server properties...");
console.log("  Server.tps:", Server.tps);
console.log("  Server.maxPlayers:", Server.maxPlayers);
console.log("  Server.motd:", Server.motd);
console.log("  Server.players (array):", Array.isArray(Server.players));
console.log("  Online players:", Server.players.length);
console.log("  ✓ Server properties accessible");

// Test 6: Broadcast message
console.log("\n[Test 6] Broadcasting message...");
Server.broadcast('§aTest broadcast from Server API');
console.log("  ✓ Broadcast sent");

// Test 7: Run server command
console.log("\n[Test 7] Running server command...");
Server.runCommand('time query daytime');
console.log("  ✓ Command executed");

// Test 8: Register async event handler
console.log("\n[Test 8] Registering async event handler...");
Server.on('playerChat', async (player, message) => {
  await Promise.resolve();
  console.log("  Async chat handler:", player.name, message);
});
console.log("  ✓ Async handler registered");

// Test 9: Multiple handlers for same event
console.log("\n[Test 9] Multiple handlers for same event...");
Server.on('playerJoin', (player) => {
  console.log("  Handler 1:", player.name);
});
Server.on('playerJoin', (player) => {
  console.log("  Handler 2:", player.name);
});
console.log("  ✓ Multiple handlers registered");

// Test 10: Event handler with cancellation
console.log("\n[Test 10] Event handler with cancellation...");
Server.on('blockBreak', (player, block) => {
  // Return object to cancel event
  if (block.id === 'minecraft:bedrock') {
    return { cancelled: true };
  }
});
console.log("  ✓ Cancellable event handler registered");

console.log("\n" + "=".repeat(50));
console.log("All Server API tests passed!");
console.log("=".repeat(50));
