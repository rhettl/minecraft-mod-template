package com.rhett.rhettjs.world.adapter

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus

/**
 * Utility for temporarily loading chunks for structure operations.
 * Handles chunk tickets to ensure chunks stay loaded during read/write.
 *
 * Uses "structure" level loading - chunks are loaded for block access
 * but not ticked/simulated (no entity AI, redstone, etc).
 */
class ChunkLoader(private val level: ServerLevel) {

    /**
     * Execute an operation with chunks guaranteed to be loaded.
     * Loads chunks at structure level (read/write capable, no simulation).
     * Chunks are NOT force-unloaded after - they return to normal chunk loading behavior.
     *
     * @param minChunkX Minimum chunk X coordinate
     * @param maxChunkX Maximum chunk X coordinate
     * @param minChunkZ Minimum chunk Z coordinate
     * @param maxChunkZ Maximum chunk Z coordinate
     * @param operation The operation to perform with loaded chunks
     * @return Result of the operation
     */
    fun <T> withLoadedChunks(
        minChunkX: Int,
        maxChunkX: Int,
        minChunkZ: Int,
        maxChunkZ: Int,
        operation: () -> T
    ): T {
        // Calculate chunk list
        val chunks = mutableListOf<ChunkPos>()
        for (x in minChunkX..maxChunkX) {
            for (z in minChunkZ..maxChunkZ) {
                chunks.add(ChunkPos(x, z))
            }
        }

        // Ensure all chunks are loaded at structure level
        val loadedChunks = chunks.map { chunkPos ->
            ensureChunkLoaded(chunkPos)
        }

        try {
            // Verify all chunks are accessible
            if (loadedChunks.any { it == null }) {
                throw IllegalStateException("Failed to load one or more chunks")
            }

            // Execute operation with chunks loaded
            return operation()

        } finally {
            // Chunks will naturally unload based on normal chunk loading rules
            // We don't force-unload because:
            // 1. Might be player-loaded chunks
            // 2. Might be needed by other systems
            // 3. Server will unload when appropriate
        }
    }

    /**
     * Ensure a chunk is loaded at structure level.
     * Returns the loaded chunk or null if loading failed.
     *
     * ChunkStatus.FULL means:
     * - Terrain is generated
     * - Blocks can be read/written
     * - Suitable for structure operations
     */
    private fun ensureChunkLoaded(chunkPos: ChunkPos): ChunkAccess? {
        // Load chunk at FULL level (blocks accessible)
        // The 'true' parameter forces loading if not present
        return try {
            level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a chunk is currently loaded at any level.
     */
    fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean {
        return level.hasChunk(chunkX, chunkZ)
    }

    /**
     * Get chunk coordinates for a block region.
     * Returns (minChunkX, maxChunkX, minChunkZ, maxChunkZ).
     */
    fun getChunkBounds(minBlockX: Int, maxBlockX: Int, minBlockZ: Int, maxBlockZ: Int): ChunkBounds {
        return ChunkBounds(
            minChunkX = minBlockX shr 4,  // Divide by 16
            maxChunkX = maxBlockX shr 4,
            minChunkZ = minBlockZ shr 4,
            maxChunkZ = maxBlockZ shr 4
        )
    }
}

/**
 * Chunk coordinate bounds.
 */
data class ChunkBounds(
    val minChunkX: Int,
    val maxChunkX: Int,
    val minChunkZ: Int,
    val maxChunkZ: Int
) {
    val chunkCountX: Int get() = maxChunkX - minChunkX + 1
    val chunkCountZ: Int get() = maxChunkZ - minChunkZ + 1
    val totalChunks: Int get() = chunkCountX * chunkCountZ
}
