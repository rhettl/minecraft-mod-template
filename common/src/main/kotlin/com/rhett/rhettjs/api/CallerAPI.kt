package com.rhett.rhettjs.api

import com.rhett.rhettjs.threading.ThreadSafeAPI
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Caller API for sending messages to whoever invoked the script.
 *
 * Available in utility scripts run via /rjs run.
 * The caller can be a player, console, command block, or any entity.
 * Provides chat/console message functionality and caller context (position, dimension, etc).
 *
 * Thread-safe: Can be called from worker threads (schedules message to main thread).
 */
class CallerAPI(private val source: CommandSourceStack) : ThreadSafeAPI {

    /**
     * Send a message to the caller.
     * Appears in chat for players, console for server.
     *
     * @param message The message to send
     */
    fun sendMessage(message: String) {
        // Send on main thread to be safe
        source.server.execute {
            source.sendSuccess({ Component.literal(message) }, false)
        }
    }

    /**
     * Send a success message (green color).
     *
     * @param message The message to send
     */
    fun sendSuccess(message: String) {
        source.server.execute {
            source.sendSuccess({ Component.literal("§a$message") }, false)
        }
    }

    /**
     * Send an error message (red color).
     *
     * @param message The message to send
     */
    fun sendError(message: String) {
        source.server.execute {
            source.sendFailure(Component.literal("§c$message"))
        }
    }

    /**
     * Send a warning message (yellow color).
     *
     * @param message The message to send
     */
    fun sendWarning(message: String) {
        source.server.execute {
            source.sendSuccess({ Component.literal("§e$message") }, false)
        }
    }

    /**
     * Send an info message (gray color).
     *
     * @param message The message to send
     */
    fun sendInfo(message: String) {
        source.server.execute {
            source.sendSuccess({ Component.literal("§7$message") }, false)
        }
    }

    /**
     * Send a raw tellraw JSON component.
     *
     * @param json The JSON string representing a text component
     */
    fun sendRaw(json: String) {
        source.server.execute {
            try {
                // In 1.21.1, fromJson requires RegistryAccess for components with registry data
                val component = Component.Serializer.fromJson(json, source.registryAccess())
                if (component != null) {
                    source.sendSuccess({ component }, false)
                } else {
                    source.sendFailure(Component.literal("§c[RhettJS] Invalid JSON component"))
                }
            } catch (e: Exception) {
                source.sendFailure(Component.literal("§c[RhettJS] Failed to parse JSON: ${e.message}"))
            }
        }
    }


    /**
     * Get the caller's name.
     *
     * @return The caller's name (player name or "Server")
     */
    fun getName(): String {
        return source.displayName.string
    }

    /**
     * Check if the caller is a player (vs console/command block).
     *
     * @return true if caller is a player
     */
    fun isPlayer(): Boolean {
        return source.entity != null
    }

    /**
     * Get the caller's current dimension.
     *
     * @return Dimension key (e.g., "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")
     */
    fun getDimension(): String {
        return source.level.dimension().location().toString()
    }

    /**
     * Get the caller's current position.
     *
     * @return Map with x, y, z coordinates (doubles)
     */
    fun getPosition(): Map<String, Double> {
        val pos = source.position
        return mapOf(
            "x" to pos.x,
            "y" to pos.y,
            "z" to pos.z
        )
    }

    /**
     * Get the caller's rotation (yaw and pitch).
     * Only available if caller is an entity.
     *
     * @return Map with yaw and pitch (degrees), or null if caller is not an entity
     */
    fun getRotation(): Map<String, Float>? {
        val entity = source.entity ?: return null
        return mapOf(
            "yaw" to entity.yRot,
            "pitch" to entity.xRot
        )
    }

    /**
     * Perform a raycast from the caller's eye position in the direction they're looking.
     * Returns the block or entity they're looking at within the specified range.
     *
     * @param maxDistance Maximum distance to raycast (default: 5.0, max: 128.0)
     * @param fluid Whether to include fluid blocks in the raycast (default: false)
     * @return Map with hit information, or null if caller is not an entity
     *
     * Result format:
     * {
     *   "hit": true/false,
     *   "type": "block" or "miss",
     *   "x": block x coordinate (if hit),
     *   "y": block y coordinate (if hit),
     *   "z": block z coordinate (if hit),
     *   "block": "minecraft:stone" (if hit),
     *   "face": "up", "down", "north", "south", "east", "west" (if hit),
     *   "distance": distance to hit point
     * }
     */
    fun raycast(maxDistance: Double = 5.0, fluid: Boolean = false): Map<String, Any>? {
        val entity = source.entity ?: return null

        // Clamp max distance to reasonable values
        val clampedDistance = maxDistance.coerceIn(0.0, 128.0)

        // Get eye position and look direction
        val eyePos = entity.getEyePosition(1.0f)
        val lookVec = entity.lookAngle
        val endPos = eyePos.add(lookVec.scale(clampedDistance))

        // Perform raycast
        val fluidHandling = if (fluid) {
            ClipContext.Fluid.ANY
        } else {
            ClipContext.Fluid.NONE
        }

        val clipContext = ClipContext(
            eyePos,
            endPos,
            ClipContext.Block.OUTLINE,
            fluidHandling,
            entity
        )

        val hitResult = source.level.clip(clipContext)

        return when (hitResult.type) {
            HitResult.Type.BLOCK -> {
                val blockHit = hitResult as BlockHitResult
                val blockPos = blockHit.blockPos
                val blockState = source.level.getBlockState(blockPos)
                val distance = eyePos.distanceTo(Vec3.atCenterOf(blockPos))

                mapOf(
                    "hit" to true,
                    "type" to "block",
                    "x" to blockPos.x,
                    "y" to blockPos.y,
                    "z" to blockPos.z,
                    "block" to blockState.block.descriptionId.replace("block.minecraft.", "minecraft:"),
                    "face" to blockHit.direction.name.lowercase(),
                    "distance" to distance
                )
            }
            HitResult.Type.MISS -> {
                mapOf(
                    "hit" to false,
                    "type" to "miss",
                    "distance" to clampedDistance
                )
            }
            else -> {
                mapOf(
                    "hit" to false,
                    "type" to "unknown",
                    "distance" to clampedDistance
                )
            }
        }
    }
}
