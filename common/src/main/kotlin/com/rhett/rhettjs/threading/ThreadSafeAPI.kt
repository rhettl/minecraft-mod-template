package com.rhett.rhettjs.threading

/**
 * Marker interface for APIs that are safe to use in worker threads.
 *
 * Thread-safe APIs typically:
 * - Don't access Minecraft game objects (player, world, server)
 * - Only perform file I/O, computation, or logging
 * - Don't modify shared mutable state
 *
 * Examples:
 * - Structure API (file I/O)
 * - console/logger (logging)
 * - NBT utilities (file operations)
 *
 * Counter-examples (NOT thread-safe):
 * - Player API (Minecraft objects)
 * - World API (Minecraft objects)
 * - Server API (Minecraft objects)
 *
 * When implementing a new API, add this interface if the API can safely
 * be used from worker threads. The task() function will automatically
 * make it available in worker thread contexts.
 */
interface ThreadSafeAPI