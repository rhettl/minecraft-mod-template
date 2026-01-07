// RhettJS NBT API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-06

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
     * Delete path from NBT data
     * @param nbt - NBT data
     * @param path - Dot-separated path
     * @returns Modified NBT data
     */
    function remove(nbt: any, path: string): any;

    /**
     * Merge NBT data
     * @param target - Target NBT
     * @param source - Source NBT
     * @param options - Merge options
     */
    function merge(target: any, source: any, options?: { deep?: boolean }): any;
}

export default NBT;