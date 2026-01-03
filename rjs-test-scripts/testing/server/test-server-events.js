/**
 * Integration tests for Server API event system.
 * Tests event registration, firing, and handler execution.
 */

import Server from 'Server';

console.log('[Test] Starting Server API event tests');

// Test 1: Event handler registration
console.log('\n[Test 1] Event handler registration');
let joinHandlerCalled = false;
let leaveHandlerCalled = false;

Server.on('playerJoin', (player) => {
    console.log(`[Test 1] Player joined: ${player.name}`);
    joinHandlerCalled = true;
});

Server.on('playerLeave', (player) => {
    console.log(`[Test 1] Player left: ${player.name}`);
    leaveHandlerCalled = true;
});

console.log('[Test 1] ✓ Handlers registered without error');

// Test 2: One-time handlers
console.log('\n[Test 2] One-time handler registration');
let onceHandlerCount = 0;

Server.once('playerJoin', (player) => {
    onceHandlerCount++;
    console.log(`[Test 2] Once handler called (count: ${onceHandlerCount})`);
});

console.log('[Test 2] ✓ Once handler registered');

// Test 3: Handler removal
console.log('\n[Test 3] Handler removal');
const removableHandler = (player) => {
    console.log('[Test 3] This should not print');
};

Server.on('playerJoin', removableHandler);
Server.off('playerJoin', removableHandler);
console.log('[Test 3] ✓ Handler registered and removed');

// Test 4: Async event handlers
console.log('\n[Test 4] Async event handlers');
Server.on('playerJoin', async (player) => {
    console.log(`[Test 4] Async handler starting for ${player.name}`);
    await wait(1); // Wait 1 tick
    console.log(`[Test 4] Async handler completed for ${player.name}`);
});
console.log('[Test 4] ✓ Async handler registered');

// Test 5: Multiple handlers for same event
console.log('\n[Test 5] Multiple handlers for same event');
let handler1Called = false;
let handler2Called = false;

Server.on('playerJoin', (player) => {
    handler1Called = true;
    console.log('[Test 5] Handler 1 called');
});

Server.on('playerJoin', (player) => {
    handler2Called = true;
    console.log('[Test 5] Handler 2 called');
});

console.log('[Test 5] ✓ Multiple handlers registered');

// Test 6: Player object structure
console.log('\n[Test 6] Player object validation');
Server.on('playerJoin', (player) => {
    console.log('[Test 6] Validating player object structure');

    // Check required properties
    const requiredProps = ['name', 'uuid', 'isPlayer', 'position', 'health', 'maxHealth', 'gameMode'];
    for (const prop of requiredProps) {
        if (!(prop in player)) {
            throw new Error(`Player missing required property: ${prop}`);
        }
    }

    // Check types
    if (typeof player.name !== 'string') {
        throw new Error('Player.name should be string');
    }
    if (typeof player.uuid !== 'string') {
        throw new Error('Player.uuid should be string');
    }
    if (player.isPlayer !== true) {
        throw new Error('Player.isPlayer should be true');
    }
    if (typeof player.position !== 'object') {
        throw new Error('Player.position should be object');
    }
    if (typeof player.health !== 'number') {
        throw new Error('Player.health should be number');
    }

    // Check methods exist
    const requiredMethods = ['sendMessage', 'teleport', 'setHealth', 'giveItem'];
    for (const method of requiredMethods) {
        if (typeof player[method] !== 'function') {
            throw new Error(`Player missing required method: ${method}`);
        }
    }

    console.log('[Test 6] ✓ Player object structure valid');
});

// Test 7: Server properties
console.log('\n[Test 7] Server properties');
console.log(`Server.tps: ${Server.tps} (type: ${typeof Server.tps})`);
console.log(`Server.players: ${Server.players.length} players (type: ${typeof Server.players})`);
console.log(`Server.maxPlayers: ${Server.maxPlayers} (type: ${typeof Server.maxPlayers})`);
console.log(`Server.motd: "${Server.motd}" (type: ${typeof Server.motd})`);

if (typeof Server.tps !== 'number') {
    throw new Error('Server.tps should be a number');
}
if (!Array.isArray(Server.players)) {
    throw new Error('Server.players should be an array');
}
if (typeof Server.maxPlayers !== 'number') {
    throw new Error('Server.maxPlayers should be a number');
}
if (typeof Server.motd !== 'string') {
    throw new Error('Server.motd should be a string');
}

console.log('[Test 7] ✓ Server properties have correct types');

// Test 8: Server.broadcast()
console.log('\n[Test 8] Server broadcast');
try {
    Server.broadcast('Test broadcast message from Server API tests');
    console.log('[Test 8] ✓ Broadcast sent without error');
} catch (e) {
    console.error(`[Test 8] ✗ Broadcast failed: ${e.message}`);
}

// Test 9: Server.runCommand()
console.log('\n[Test 9] Server command execution');
try {
    Server.runCommand('time query daytime');
    console.log('[Test 9] ✓ Command executed without error');
} catch (e) {
    console.error(`[Test 9] ✗ Command execution failed: ${e.message}`);
}

console.log('\n[Test] Server API event tests registered');
console.log('[Test] Join the server to trigger playerJoin events');
console.log('[Test] Leave the server to trigger playerLeave events');