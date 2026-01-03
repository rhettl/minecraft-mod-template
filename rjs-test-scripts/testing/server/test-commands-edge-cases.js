// Test Commands API Edge Cases and Error Handling
// This script tests error conditions, invalid inputs, and edge cases

import Commands from 'Commands';

console.log('=== Commands API Edge Cases Test ===');
console.log('');

// Test 1: Command without executor (should fail validation)
console.log('Test 1: Command without executor');
try {
    Commands.register('testnoexec')
        .description('This should fail - no executor');
    console.error('✗ Should have thrown error for missing executor');
} catch (e) {
    console.log('✓ Correctly rejected command without executor');
}
console.log('');

// Test 2: Invalid argument type
console.log('Test 2: Invalid argument type');
try {
    Commands.register('testinvalidarg')
        .argument('value', 'invalid_type')
        .executes(() => {
            console.log('This should not execute');
        });
    console.error('✗ Should have thrown error for invalid argument type');
} catch (e) {
    console.log('✓ Correctly rejected invalid argument type:', e.message);
}
console.log('');

// Test 3: Duplicate argument names
console.log('Test 3: Register command with duplicate argument names');
try {
    Commands.register('testdupargs')
        .argument('value', 'int')
        .argument('value', 'string')
        .executes(({ args }) => {
            console.log('Args:', args);
        });
    console.log('✓ Registered command with duplicate arg names (last wins)');
} catch (e) {
    console.error('✗ Failed to register:', e.message);
}
console.log('');

// Test 4: Empty command name
console.log('Test 4: Empty command name');
try {
    Commands.register('')
        .executes(() => {});
    console.error('✗ Should have thrown error for empty name');
} catch (e) {
    console.log('✓ Correctly rejected empty command name');
}
console.log('');

// Test 5: Command with no description (should work)
console.log('Test 5: Command with no description');
try {
    Commands.register('testnodesc')
        .executes(({ caller }) => {
            caller.sendMessage('Command with no description works!');
        });
    console.log('✓ Registered command without description');
} catch (e) {
    console.error('✗ Failed:', e.message);
}
console.log('');

// Test 6: Permission function that throws error
console.log('Test 6: Permission function that throws error');
try {
    Commands.register('testbadperm')
        .permission((caller) => {
            throw new Error('Permission check failed!');
        })
        .executes(({ caller }) => {
            caller.sendMessage('This should not execute if permission throws');
        });
    console.log('✓ Registered command with error-throwing permission');
} catch (e) {
    console.error('✗ Failed to register:', e.message);
}
console.log('');

// Test 7: Executor that throws error
console.log('Test 7: Executor that throws error');
try {
    Commands.register('testerror')
        .description('Test error handling in executor')
        .executes(({ caller }) => {
            caller.sendMessage('About to throw error...');
            throw new Error('Intentional error in executor');
        });
    console.log('✓ Registered command with error-throwing executor');
} catch (e) {
    console.error('✗ Failed to register:', e.message);
}
console.log('');

// Test 8: Async executor that rejects
console.log('Test 8: Async executor that rejects');
try {
    Commands.register('testasyncerror')
        .description('Test async error handling')
        .executes(async ({ caller }) => {
            caller.sendMessage('About to reject promise...');
            await Promise.reject(new Error('Async error!'));
        });
    console.log('✓ Registered command with rejecting async executor');
} catch (e) {
    console.error('✗ Failed to register:', e.message);
}
console.log('');

// Test 9: Command with many arguments
console.log('Test 9: Command with many arguments');
try {
    Commands.register('testmanyargs')
        .description('Test command with many arguments')
        .argument('arg1', 'int')
        .argument('arg2', 'int')
        .argument('arg3', 'int')
        .argument('arg4', 'int')
        .argument('arg5', 'int')
        .executes(({ caller, args }) => {
            const sum = args.arg1 + args.arg2 + args.arg3 + args.arg4 + args.arg5;
            caller.sendMessage(`Sum: ${sum}`);
        });
    console.log('✓ Registered command with 5 arguments');
} catch (e) {
    console.error('✗ Failed:', e.message);
}
console.log('');

// Test 10: Unregister non-existent command
console.log('Test 10: Unregister non-existent command');
try {
    Commands.unregister('nonexistent');
    console.log('✓ Unregister non-existent command handled gracefully');
} catch (e) {
    console.error('✗ Threw error:', e.message);
}
console.log('');

// Test 11: Register command, then register again (replace)
console.log('Test 11: Replace existing command');
try {
    Commands.register('testreplace')
        .description('Original command')
        .executes(({ caller }) => {
            caller.sendMessage('Original version');
        });

    Commands.register('testreplace')
        .description('Replaced command')
        .executes(({ caller }) => {
            caller.sendMessage('Replaced version');
        });
    console.log('✓ Replaced existing command');
} catch (e) {
    console.error('✗ Failed:', e.message);
}
console.log('');

// Test 12: Argument with spaces in name
console.log('Test 12: Argument with spaces in name');
try {
    Commands.register('testspacename')
        .argument('arg with spaces', 'string')
        .executes(({ args }) => {
            console.log('Args:', args);
        });
    console.log('✓ Registered argument with spaces in name (might cause issues in-game)');
} catch (e) {
    console.error('✗ Failed:', e.message);
}
console.log('');

// Test 13: Null/undefined in various places
console.log('Test 13: Null/undefined handling');
try {
    Commands.register('testnull')
        .description(null)
        .executes(({ caller }) => {
            caller.sendMessage('Null description handled');
        });
    console.log('✓ Handled null description');
} catch (e) {
    console.error('✗ Failed:', e.message);
}
console.log('');

// Test 14: Executor returns non-integer
console.log('Test 14: Executor returns non-integer');
try {
    Commands.register('testreturn')
        .description('Test return value handling')
        .executes(({ caller }) => {
            caller.sendMessage('Returning string instead of int');
            return 'not a number';
        });
    console.log('✓ Registered command with non-integer return');
} catch (e) {
    console.error('✗ Failed:', e.message);
}
console.log('');

// Test 15: Access args without any arguments defined
console.log('Test 15: Access args on command with no arguments');
try {
    Commands.register('testnoargs')
        .description('Test args object when no args defined')
        .executes(({ caller, args }) => {
            console.log('Args object:', args);
            console.log('Args keys:', Object.keys(args));
            caller.sendMessage(`Args is ${args ? 'defined' : 'undefined'}`);
        });
    console.log('✓ Registered command that accesses args without defining any');
} catch (e) {
    console.error('✗ Failed:', e.message);
}
console.log('');

console.log('=== Edge Cases Test Complete ===');
console.log('');
console.log('Test the following commands in-game:');
console.log('  /testdupargs 10 hello');
console.log('  /testnodesc');
console.log('  /testbadperm (should be blocked by permission)');
console.log('  /testerror (should handle error gracefully)');
console.log('  /testasyncerror (should handle async error)');
console.log('  /testmanyargs 1 2 3 4 5');
console.log('  /testreplace (should show "Replaced version")');
console.log('  /testnoargs');
console.log('');
