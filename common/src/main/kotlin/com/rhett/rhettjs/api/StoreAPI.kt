package com.rhett.rhettjs.api

import java.util.concurrent.ConcurrentHashMap

/**
 * Ephemeral key-value store API for sharing data across script executions.
 * Data persists in memory until server restart or explicit clear.
 * Thread-safe for use from any script context.
 *
 * Use cases:
 * - Store player selections (positions, blocks, etc.)
 * - Cache computed data between script runs
 * - Share state between event handlers and utility scripts
 *
 * JavaScript usage:
 * ```javascript
 * // Get a namespaced store
 * const positions = Store.namespace('positions');
 *
 * // Store and retrieve values within namespace
 * positions.set('player1:pos1', { x: 100, y: 64, z: 200, dimension: 'minecraft:overworld' });
 * const pos = positions.get('player1:pos1');
 *
 * // Check existence
 * if (positions.has('player1:pos1')) { ... }
 *
 * // Delete a key
 * positions.delete('player1:pos1');
 *
 * // List all keys in namespace
 * const keys = positions.keys();
 *
 * // Clear only this namespace
 * positions.clear();
 * ```
 */
object StoreAPI {

    // Thread-safe storage backing - stores namespaced keys as "namespace:key"
    private val storage = ConcurrentHashMap<String, Any?>()

    /**
     * Create a namespaced store for organizing related data.
     * All keys within a namespace are isolated from other namespaces.
     *
     * @param namespace The namespace identifier
     * @return A NamespacedStore instance scoped to this namespace
     */
    fun namespace(namespace: String): NamespacedStore {
        return NamespacedStore(namespace, storage)
    }

    /**
     * Get all namespaces currently in use.
     *
     * @return List of namespace names
     */
    fun namespaces(): List<String> {
        return storage.keys().toList()
            .mapNotNull { it.substringBefore(':', missingDelimiterValue = "") }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * Clear all stored data across all namespaces.
     * Use with caution - this wipes everything.
     */
    fun clearAll() {
        storage.clear()
    }

    /**
     * Get the total number of stored items across all namespaces.
     *
     * @return Total count of stored keys
     */
    fun size(): Int {
        return storage.size
    }
}

/**
 * A namespaced key-value store.
 * All operations are scoped to this namespace, isolated from other namespaces.
 *
 * Thread-safe for concurrent access from multiple scripts.
 */
class NamespacedStore(
    private val namespace: String,
    private val storage: ConcurrentHashMap<String, Any?>
) {

    /**
     * Build a fully qualified key with namespace prefix.
     */
    private fun qualifiedKey(key: String): String = "$namespace:$key"

    /**
     * Store a value under a key in this namespace.
     * Overwrites existing value if key exists.
     *
     * @param key The key to store under (within this namespace)
     * @param value The value to store (any JavaScript type)
     */
    fun set(key: String, value: Any?) {
        storage[qualifiedKey(key)] = value
    }

    /**
     * Retrieve a value by key from this namespace.
     *
     * @param key The key to retrieve
     * @return The stored value, or null if key doesn't exist
     */
    fun get(key: String): Any? {
        return storage[qualifiedKey(key)]
    }

    /**
     * Check if a key exists in this namespace.
     *
     * @param key The key to check
     * @return true if key exists, false otherwise
     */
    fun has(key: String): Boolean {
        return storage.containsKey(qualifiedKey(key))
    }

    /**
     * Delete a key from this namespace.
     *
     * @param key The key to delete
     * @return true if key was deleted, false if it didn't exist
     */
    fun delete(key: String): Boolean {
        return storage.remove(qualifiedKey(key)) != null
    }

    /**
     * Clear all keys in this namespace only.
     * Does not affect other namespaces.
     */
    fun clear() {
        val prefix = "$namespace:"
        val keysToRemove = storage.keys().toList().filter { it.startsWith(prefix) }
        keysToRemove.forEach { storage.remove(it) }
    }

    /**
     * Get all keys in this namespace (without namespace prefix).
     *
     * @return List of keys in this namespace
     */
    fun keys(): List<String> {
        val prefix = "$namespace:"
        return storage.keys().toList()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
    }

    /**
     * Get the number of stored items in this namespace.
     *
     * @return Count of keys in this namespace
     */
    fun size(): Int {
        val prefix = "$namespace:"
        return storage.keys().toList().count { it.startsWith(prefix) }
    }

    /**
     * Get all entries in this namespace as a map (without namespace prefix).
     *
     * @return Map of key-value pairs in this namespace
     */
    fun entries(): Map<String, Any?> {
        val prefix = "$namespace:"
        return storage.entries
            .filter { it.key.startsWith(prefix) }
            .associate { it.key.removePrefix(prefix) to it.value }
    }
}
