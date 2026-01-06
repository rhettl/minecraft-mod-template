// RhettJS Core API Type Definitions (GraalVM)
// Version: 0.3.0
// Last updated: 2026-01-03
// Documentation: https://github.com/rhettjs/rhettjs

// ============================================================================
// Common Types
// ============================================================================

/** Position with optional dimension */
interface Position {
    x: number;
    y: number;
    z: number;
    dimension?: string; // Default: "minecraft:overworld"
}

/** Block state information */
interface Block {
    id: string; // e.g., "minecraft:stone"
    properties?: Record<string, string>; // e.g., { facing: "north", half: "bottom" }
}

/** Player object (wrapped) */
interface Player {
    name: string;
    uuid: string;
    isPlayer: boolean; // Always true
    position: Position;
    health: number;
    maxHealth: number;
    foodLevel: number;
    saturation: number;
    gameMode: "survival" | "creative" | "adventure" | "spectator";
    isOp: boolean;

    setHealth(amount: number): void;
    teleport(position: Position): void;
    sendMessage(message: string): void;
    giveItem(itemId: string, count?: number): void;
}

/** Command caller (player or console) */
interface Caller {
    name: string; // Player name or "Server"
    isPlayer: boolean;
    // If isPlayer, includes all Player properties
    [key: string]: any;
    sendMessage(message: string): void;
}

// ============================================================================
// Runtime API
// ============================================================================

/**
 * Runtime environment and lifecycle control
 */
declare namespace Runtime {
    /** Environment constants */
    const env: {
        /** Maximum worker threads (determined at boot, max 4) */
        MAX_WORKER_THREADS: number;
        /** Minecraft ticks per second (always 20) */
        TICKS_PER_SECOND: number;
        /** Debug mode enabled in config */
        IS_DEBUG: boolean;
        /** RhettJS version */
        RJS_VERSION: string;
    };

    /**
     * Stop script execution immediately
     * @example Runtime.exit();
     */
    function exit(): void;

    /**
     * Set script timeout (must be called before async operations)
     * @param timeoutMs - Timeout in milliseconds (min: 1000)
     * @example Runtime.setScriptTimeout(120000); // 2 minutes
     */
    function setScriptTimeout(timeoutMs: number): void;
}

// ============================================================================
// Console API
// ============================================================================

/**
 * Standard console logging
 */
declare namespace console {
    function log(...messages: any[]): void;
    function info(...messages: any[]): void;
    function warn(...messages: any[]): void;
    function error(...messages: any[]): void;
    function debug(...messages: any[]): void;
}

// ============================================================================
// Async Utilities
// ============================================================================

/**
 * Wait for N ticks before resolving (20 ticks = 1 second)
 * @param ticks - Number of ticks to wait
 * @returns Promise that resolves after delay
 * @example
 * await wait(20); // Wait 1 second
 * console.log('Done!');
 */
declare function wait(ticks: number): Promise<void>;

// ============================================================================
// Store API (Ephemeral Key-Value Storage)
// ============================================================================

/** Namespaced store for organizing related data */
interface NamespacedStore {
    /** Store a value */
    set(key: string, value: any): void;
    /** Retrieve a value */
    get(key: string): any | null;
    /** Check if key exists */
    has(key: string): boolean;
    /** Delete a key */
    delete(key: string): boolean;
    /** Clear all keys in this namespace */
    clear(): void;
    /** Get all keys */
    keys(): string[];
    /** Get number of items */
    size(): number;
    /** Get all entries as object */
    entries(): Record<string, any>;
}

/**
 * Ephemeral key-value store (persists until server restart)
 * @example
 * const positions = Store.namespace('positions');
 * positions.set('pos1', { x: 100, y: 64, z: 200 });
 */
declare namespace Store {
    /**
     * Create or get a namespaced store
     * @param namespace - Namespace identifier
     * @returns Namespaced store instance
     */
    function namespace(namespace: string): NamespacedStore;

    /** Get all namespace names */
    function namespaces(): string[];

    /** Clear all data across all namespaces */
    function clearAll(): void;

    /** Total items across all namespaces */
    function size(): number;
}

// ============================================================================
// NBT API
// ============================================================================

/**
 * NBT manipulation utilities
 * @example
 * const compound = NBT.compound({ display: { Name: 'Custom Item' } });
 */
declare namespace NBT {
    function compound(data?: Record<string, any>): any;
    function list(items?: any[]): any;
    function string(value: string): any;
    function int(value: number): any;
    function double(value: number): any;
    function byte(value: number): any;

    /**
     * Get value at path
     * @param nbt - NBT data
     * @param path - Dot-separated path (e.g., "display.Name")
     */
    function get(nbt: any, path: string): any;

    /**
     * Set value at path
     * @param nbt - NBT data
     * @param path - Dot-separated path
     * @param value - Value to set
     */
    function set(nbt: any, path: string, value: any): any;

    /**
     * Check if path exists
     * @param nbt - NBT data
     * @param path - Dot-separated path
     */
    function has(nbt: any, path: string): boolean;

    /**
     * Delete path
     * @param nbt - NBT data
     * @param path - Dot-separated path
     */
    function delete(nbt: any, path: string): any;

    /**
     * Merge NBT data
     * @param target - Target NBT
     * @param source - Source NBT
     * @param options - Merge options
     */
    function merge(target: any, source: any, options?: { deep?: boolean }): any;
}

// ============================================================================
// Commands API
// ============================================================================

/** Command builder for registration */
interface CommandBuilder {
    /**
     * Set command description
     * @param desc - Description text
     */
    description(desc: string): CommandBuilder;

    /**
     * Set permission requirement
     * @param perm - Permission string or function
     */
    permission(perm: string | ((caller: Caller) => boolean)): CommandBuilder;

    /**
     * Add command argument
     * @param name - Argument name
     * @param type - Argument type
     */
    argument(name: string, type: "string" | "int" | "float" | "player" | "item" | "block" | "entity"): CommandBuilder;

    /**
     * Set command executor
     * @param handler - Execution handler
     */
    executes(handler: (event: { caller: Caller; args: Record<string, any>; command: string }) => void | Promise<void>): CommandBuilder;
}

/**
 * Command registration API
 * @example
 * Commands.register('heal')
 *   .description('Heal a player')
 *   .argument('target', 'player')
 *   .executes(({ caller, args }) => {
 *     args.target.setHealth(args.target.maxHealth);
 *     caller.sendMessage(`Healed ${args.target.name}`);
 *   });
 */
declare namespace Commands {
    /**
     * Register a new command
     * @param name - Command name
     * @returns Command builder
     */
    function register(name: string): CommandBuilder;

    /**
     * Unregister a command
     * @param name - Command name
     */
    function unregister(name: string): void;
}

// ============================================================================
// Server API
// ============================================================================

/** Server event handler */
type ServerEventHandler = (event: any) => void | Promise<void>;

/**
 * Server events and properties
 * @example
 * Server.on('playerJoin', (event) => {
 *   console.log(`${event.player.name} joined`);
 * });
 */
declare namespace Server {
    /** Current TPS (ticks per second) */
    const tps: number;

    /** Online players count */
    const players: number;

    /** Maximum players allowed */
    const maxPlayers: number;

    /** Server MOTD (message of the day) */
    const motd: string;

    /**
     * Register event handler
     * @param event - Event name
     * @param handler - Event handler
     */
    function on(event: "playerJoin" | "playerLeave" | string, handler: ServerEventHandler): void;

    /**
     * Register one-time event handler
     * @param event - Event name
     * @param handler - Event handler
     */
    function once(event: "playerJoin" | "playerLeave" | string, handler: ServerEventHandler): void;

    /**
     * Remove event handler
     * @param event - Event name
     * @param handler - Event handler
     */
    function off(event: string, handler: ServerEventHandler): void;

    /**
     * Broadcast message to all players
     * @param message - Message text
     */
    function broadcast(message: string): void;

    /**
     * Run server command
     * @param command - Command to execute
     */
    function runCommand(command: string): void;
}

// ============================================================================
// World API
// ============================================================================

/**
 * World manipulation and queries (all async)
 * @example
 * const block = await World.getBlock({ x: 100, y: 64, z: 200 });
 * console.log(`Block: ${block.id}`);
 */
declare namespace World {
    /** List of dimension identifiers */
    const dimensions: string[];

    /**
     * Get block at position
     * @param position - Block position
     * @returns Block data
     */
    function getBlock(position: Position): Promise<Block>;

    /**
     * Set block at position
     * @param position - Block position
     * @param blockId - Block identifier (e.g., "minecraft:stone")
     * @param properties - Block properties
     */
    function setBlock(position: Position, blockId: string, properties?: Record<string, string>): Promise<void>;

    /**
     * Fill region with blocks
     * @param pos1 - First corner
     * @param pos2 - Second corner
     * @param blockId - Block identifier
     * @returns Number of blocks placed
     */
    function fill(pos1: Position, pos2: Position, blockId: string): Promise<number>;

    /**
     * Replace blocks in region matching filter
     * @param pos1 - First corner
     * @param pos2 - Second corner
     * @param filter - Block ID or predicate to match
     * @param replacement - Block ID to replace with
     * @returns Number of blocks replaced
     */
    function replace(pos1: Position, pos2: Position, filter: string, replacement: string): Promise<number>;

    /**
     * Get entities within radius of position
     * @param position - Center position
     * @param radius - Search radius
     * @returns Array of entity objects
     */
    function getEntities(position: Position, radius: number): Promise<any[]>;

    /**
     * Spawn entity at position
     * @param position - Spawn position
     * @param entityId - Entity type ID (e.g., "minecraft:zombie")
     * @returns Spawned entity object
     */
    function spawnEntity(position: Position, entityId: string): Promise<any>;

    /**
     * Get all online players
     * @returns Array of player objects
     */
    function getPlayers(): Promise<Player[]>;

    /**
     * Get player by name or UUID
     * @param nameOrUuid - Player name or UUID
     * @returns Player object or null
     */
    function getPlayer(nameOrUuid: string): Promise<Player | null>;

    /**
     * Get world time
     * @param dimension - Dimension identifier (optional)
     * @returns Time in ticks
     */
    function getTime(dimension?: string): Promise<number>;

    /**
     * Set world time
     * @param time - Time in ticks
     * @param dimension - Dimension identifier (optional)
     */
    function setTime(time: number, dimension?: string): Promise<void>;

    /**
     * Get weather
     * @param dimension - Dimension identifier (optional)
     * @returns Weather type
     */
    function getWeather(dimension?: string): Promise<"clear" | "rain" | "thunder">;

    /**
     * Set weather
     * @param weather - Weather type
     * @param dimension - Dimension identifier (optional)
     */
    function setWeather(weather: "clear" | "rain" | "thunder", dimension?: string): Promise<void>;
}

// ============================================================================
// Structure API
// ============================================================================

/** Options for structure capture */
interface CaptureOptions {
    author?: string;
    description?: string;
    dimension?: string;
}

/** Options for structure placement */
interface PlaceOptions {
    rotation?: 0 | 90 | 180 | 270;
    centered?: boolean;
    dimension?: string;
}

/** Options for large structure capture */
interface CaptureLargeOptions extends CaptureOptions {
    pieceSize?: { x: number; y: number; z: number }; // Default: 48x48x48
}

/**
 * Structure file operations (all async)
 * @example
 * await Structure.capture(
 *   { x: 100, y: 60, z: 100 },
 *   { x: 110, y: 70, z: 110 },
 *   'test:house'
 * );
 */
declare namespace Structure {
    /**
     * Check if structure exists
     * @param name - Structure name in format "[namespace:]name"
     * @returns True if exists
     */
    function exists(name: string): Promise<boolean>;

    /**
     * List structures
     * @param namespace - Optional namespace filter
     * @returns Array of structure names
     */
    function list(namespace?: string): Promise<string[]>;

    /**
     * Delete structure
     * @param name - Structure name
     * @returns True if deleted
     */
    function delete(name: string): Promise<boolean>;

    /**
     * Capture region as structure
     * @param pos1 - First corner
     * @param pos2 - Second corner
     * @param name - Structure name
     * @param options - Optional metadata
     */
    function capture(pos1: Position, pos2: Position, name: string, options?: CaptureOptions): Promise<void>;

    /**
     * Place structure at position
     * @param position - Placement position
     * @param name - Structure name
     * @param options - Placement options
     */
    function place(position: Position, name: string, options?: PlaceOptions): Promise<void>;

    /**
     * Capture large region split into pieces
     * @param pos1 - First corner
     * @param pos2 - Second corner
     * @param name - Structure name
     * @param options - Capture options with piece size
     * @example
     * await Structure.captureLarge(
     *   { x: 100, y: 60, z: 100 },
     *   { x: 199, y: 109, z: 149 },
     *   'test:castle',
     *   { pieceSize: { x: 48, y: 48, z: 48 } }
     * );
     */
    function captureLarge(pos1: Position, pos2: Position, name: string, options?: CaptureLargeOptions): Promise<void>;

    /**
     * Place large multi-piece structure
     * @param position - Placement position
     * @param name - Structure name
     * @param options - Placement options
     * @example
     * await Structure.placeLarge(
     *   { x: 500, y: 60, z: 500 },
     *   'test:castle',
     *   { rotation: 90, centered: true }
     * );
     */
    function placeLarge(position: Position, name: string, options?: PlaceOptions): Promise<void>;

    /**
     * Get structure size (works for both regular and large)
     * @param name - Structure name
     * @returns Size dimensions
     * @example
     * const size = await Structure.getSize('test:castle');
     * console.log(`${size.x}x${size.y}x${size.z}`);
     */
    function getSize(name: string): Promise<{ x: number; y: number; z: number }>;

    /**
     * List large structures
     * @param namespace - Optional namespace filter
     * @returns Array of large structure names
     */
    function listLarge(namespace?: string): Promise<string[]>;

    /**
     * Delete large structure (all pieces)
     * @param name - Structure name
     * @returns True if deleted
     */
    function deleteLarge(name: string): Promise<boolean>;
}

// ============================================================================
// Script.argv (Utility Scripts)
// ============================================================================

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

// ============================================================================
// Module Exports
// ============================================================================

export default Runtime;
export { console, wait, Store, NBT, Commands, Server, World, Structure, Script };
