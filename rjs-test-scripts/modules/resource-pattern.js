/**
 * Resource Pattern Matching Module
 *
 * Handles Minecraft resource location patterns with wildcards.
 * Supports patterns like:
 * - "*:abc*" - Any namespace, path starts with "abc"
 * - "teralith*:*plains*" - Namespace starts with "teralith", path contains "plains"
 * - "namespace:*" - Specific namespace, any path
 * - "*:plains" - Any namespace, path is exactly "plains"
 *
 * @example
 * import { createMatcher, matchesPattern } from './resource-pattern.js';
 *
 * const matcher = createMatcher("*:village*");
 * console.log(matcher("minecraft:village_plains")); // true
 * console.log(matcher("minecraft:temple")); // false
 *
 * // Or use the convenience function
 * console.log(matchesPattern("minecraft:village_plains", "*:village*")); // true
 */

/**
 * Escape regex special characters except for our wildcards
 */
function escapeRegex(str) {
    return str.replace(/[.+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Convert a wildcard pattern to a regex pattern
 * @param {string} pattern - Pattern with * wildcards
 * @returns {string} Regex pattern string
 */
function wildcardToRegex(pattern) {
    // Escape regex special chars, but preserve *
    const escaped = escapeRegex(pattern);

    // Convert * to .*
    return escaped.replace(/\*/g, '.*');
}

/**
 * Parse a resource location pattern into namespace and path parts
 * @param {string} pattern - Pattern like "namespace:path" or "*:path" or "namespace:*"
 * @returns {{namespace: string, path: string}} Parsed parts
 */
function parsePattern(pattern) {
    // Handle missing colon (assume minecraft namespace)
    if (!pattern.includes(':')) {
        return {
            namespace: 'minecraft',
            path: pattern
        };
    }

    const [namespace, path] = pattern.split(':', 2);
    return { namespace, path };
}

/**
 * Create a regex matcher for a resource location pattern
 * @param {string} pattern - Pattern with wildcards (e.g., "*:village*", "minecraft:*")
 * @returns {RegExp} Regular expression that matches the pattern
 */
export function createRegex(pattern) {
    const { namespace, path } = parsePattern(pattern);

    // Build regex pattern
    const namespaceRegex = wildcardToRegex(namespace);
    const pathRegex = wildcardToRegex(path);

    // Full pattern: namespace:path
    const fullPattern = `^${namespaceRegex}:${pathRegex}$`;

    return new RegExp(fullPattern);
}

/**
 * Create a matcher function for a resource location pattern
 * @param {string} pattern - Pattern with wildcards
 * @returns {(resourceLocation: string) => boolean} Function that tests if a resource location matches
 */
export function createMatcher(pattern) {
    const regex = createRegex(pattern);
    return (resourceLocation) => regex.test(resourceLocation);
}

/**
 * Test if a resource location matches a pattern
 * @param {string} resourceLocation - Full resource location (e.g., "minecraft:village_plains")
 * @param {string} pattern - Pattern with wildcards
 * @returns {boolean} True if the resource location matches the pattern
 */
export function matchesPattern(resourceLocation, pattern) {
    return createRegex(pattern).test(resourceLocation);
}

/**
 * Filter an array of resource locations by a pattern
 * @param {string[]} resourceLocations - Array of resource locations
 * @param {string} pattern - Pattern with wildcards
 * @returns {string[]} Filtered array of matching resource locations
 */
export function filterByPattern(resourceLocations, pattern) {
    const matcher = createMatcher(pattern);
    return resourceLocations.filter(matcher);
}

/**
 * Create a matcher for multiple patterns (OR logic)
 * @param {string[]} patterns - Array of patterns
 * @returns {(resourceLocation: string) => boolean} Function that tests if a resource location matches any pattern
 */
export function createMultiMatcher(patterns) {
    const matchers = patterns.map(createMatcher);
    return (resourceLocation) => matchers.some(matcher => matcher(resourceLocation));
}

/**
 * Parse and normalize a resource location (add default namespace if missing)
 * @param {string} input - Input string (e.g., "village_plains" or "minecraft:village_plains")
 * @returns {string} Normalized resource location with namespace
 */
export function normalizeResourceLocation(input) {
    if (input.includes(':')) {
        return input;
    }
    return `minecraft:${input}`;
}

// Export default object with all functions
export default {
    createRegex,
    createMatcher,
    matchesPattern,
    filterByPattern,
    createMultiMatcher,
    normalizeResourceLocation
};
