// RhettJS World API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-06

import { Position, Block, Player } from './types';

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
     * Get block entity data at position
     * Returns null if no block entity exists at the position
     * @param position - Block position
     * @returns Block entity NBT data or null
     * @example
     * // Read lectern book and page
     * const lectern = await World.getBlockEntity({ x: 100, y: 64, z: 200 });
     * if (lectern) {
     *   console.log(`Selected page: ${lectern.Page}`);
     *   const book = JSON.parse(lectern.Book?.tag?.pages?.[lectern.Page] || '""');
     *   console.log(`Page content: ${book}`);
     * }
     *
     * // Read sign text
     * const sign = await World.getBlockEntity({ x: 101, y: 64, z: 200 });
     * if (sign) {
     *   const line1 = JSON.parse(sign.front_text?.messages?.[0] || '""');
     *   console.log(`Sign line 1: ${line1}`);
     * }
     */
    function getBlockEntity(position: Position): Promise<Record<string, any> | null>;

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

export default World;