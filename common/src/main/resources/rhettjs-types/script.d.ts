// RhettJS Script API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-06

/**
 * Command-line argument parsing for utility scripts
 * @example
 * // Executed as: /rjs run myscript player1 -x=100 --name=Steve -abc
 * Script.argv.get('x')      // 100
 * Script.argv.get('name')   // "Steve"
 * Script.argv.get('a')      // true
 * Script.argv.get(0)        // "player1"
 */
declare namespace Script {
    namespace argv {
        /**
         * Get flag value by name or positional argument by index
         * @param flagOrIndex - Flag name or position index
         * @returns Value (string, number, boolean, or undefined)
         */
        function get(flagOrIndex: string | number): string | number | boolean | undefined;

        /**
         * Get all positional arguments (non-flag)
         * @returns Array of positional arguments
         */
        function getAll(): string[];

        /**
         * Check if flag exists
         * @param flag - Flag name
         * @returns True if flag present
         */
        function hasFlag(flag: string): boolean;

        /** Raw arguments array */
        const raw: string[];
    }
}

export default Script;