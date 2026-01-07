package com.rhett.rhettjs.structure

import com.rhett.rhettjs.config.ConfigManager
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Context for worldgen structure placement with custom height resolution.
 *
 * Manages height overrides during structure generation to allow structures
 * to follow custom platforms/surfaces instead of the world's natural heightmap.
 *
 * Thread-safe: Uses ThreadLocal for active context to support concurrent placements.
 */
object WorldgenStructurePlacementContext {

    /**
     * Surface resolution mode for structure placement.
     */
    sealed class SurfaceMode {
        /** Use vanilla heightmap (default behavior) */
        object Heightmap : SurfaceMode()

        /** Scan actual blocks to find surface (for custom platforms) */
        object Scan : SurfaceMode()

        /** Use a fixed Y level for all placements */
        data class Fixed(val y: Int) : SurfaceMode()

        /** Force rigid placement (all pieces use start Y, no terrain following) */
        object Rigid : SurfaceMode()

        companion object {
            fun parse(value: String): SurfaceMode {
                return when {
                    value == "heightmap" || value == "terrain" -> Heightmap
                    value == "scan" -> Scan
                    value == "rigid" -> Rigid
                    value.startsWith("fixed:") -> {
                        val y = value.removePrefix("fixed:").toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid fixed height: $value")
                        Fixed(y)
                    }
                    else -> throw IllegalArgumentException("Unknown surface mode: $value")
                }
            }
        }
    }

    /**
     * Active placement context per thread.
     */
    private val activeContext = ThreadLocal<PlacementContext?>()

    /**
     * Get the currently active placement context, if any.
     */
    fun getActive(): PlacementContext? = activeContext.get()

    /**
     * Check if there's an active placement context with height overrides.
     */
    fun isOverrideActive(): Boolean {
        val ctx = activeContext.get() ?: return false
        return ctx.surfaceMode != SurfaceMode.Heightmap
    }

    /**
     * Execute a block with a placement context active.
     * The context is automatically cleared when the block completes.
     */
    fun <T> withContext(
        level: ServerLevel,
        surfaceMode: SurfaceMode,
        block: (PlacementContext) -> T
    ): T {
        val context = PlacementContext(level, surfaceMode)
        activeContext.set(context)
        ConfigManager.debug("[WorldgenStructurePlacement] Context activated: mode=$surfaceMode")
        try {
            return block(context)
        } finally {
            activeContext.set(null)
            ConfigManager.debug("[WorldgenStructurePlacement] Context deactivated, ${context.cacheSize} heights cached")
        }
    }

    /**
     * Get height override for a position, if active context exists and has override.
     * Returns null if no override (use vanilla behavior).
     */
    fun getHeightOverride(x: Int, z: Int): Int? {
        val ctx = activeContext.get() ?: return null
        return ctx.getHeight(x, z)
    }

    /**
     * Placement context holding height cache and scanning logic.
     */
    class PlacementContext(
        private val level: ServerLevel,
        val surfaceMode: SurfaceMode
    ) {
        private val heightCache = ConcurrentHashMap<Long, Int>()

        val cacheSize: Int get() = heightCache.size

        /**
         * Get height for a position, scanning and caching as needed.
         * Returns null if using vanilla heightmap mode.
         */
        fun getHeight(x: Int, z: Int): Int? {
            return when (surfaceMode) {
                is SurfaceMode.Heightmap -> null // Use vanilla
                is SurfaceMode.Scan -> getOrScanHeight(x, z)
                is SurfaceMode.Fixed -> surfaceMode.y
                is SurfaceMode.Rigid -> null // Rigid is handled differently (at piece level)
            }
        }

        /**
         * Scan and cache height for a position.
         */
        private fun getOrScanHeight(x: Int, z: Int): Int {
            val key = packXZ(x, z)
            return heightCache.computeIfAbsent(key) {
                scanSurfaceHeight(x, z)
            }
        }

        /**
         * Scan a column to find the surface height.
         * Uses motion-blocking logic similar to vanilla's WORLD_SURFACE heightmap.
         */
        private fun scanSurfaceHeight(x: Int, z: Int): Int {
            val pos = BlockPos.MutableBlockPos(x, level.maxBuildHeight, z)

            // Scan downward from build limit
            for (y in level.maxBuildHeight downTo level.minBuildHeight) {
                pos.setY(y)
                val state = level.getBlockState(pos)

                // Use same logic as WORLD_SURFACE heightmap: motion blocking
                if (Heightmap.Types.WORLD_SURFACE.isOpaque().test(state)) {
                    return y + 1 // Return one above the solid block
                }
            }

            // No solid block found, return min build height
            return level.minBuildHeight
        }

        companion object {
            private fun packXZ(x: Int, z: Int): Long {
                return (x.toLong() and 0xFFFFFFFFL) or ((z.toLong() and 0xFFFFFFFFL) shl 32)
            }
        }
    }
}