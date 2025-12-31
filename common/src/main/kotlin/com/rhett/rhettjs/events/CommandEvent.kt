package com.rhett.rhettjs.events

import com.rhett.rhettjs.util.JSConversion
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

/**
 * Event object passed to command handlers registered via StartupEvents.command().
 * Provides access to the command source, player, level, and utilities for responding.
 */
class CommandEvent(
    private val source: CommandSourceStack,
    val commandName: String,
    val args: Array<String>
) {
    val level: ServerLevel = source.level
    val entity: Entity? = source.entity
    val player: ServerPlayer? = entity as? ServerPlayer
    val position: BlockPos = BlockPos.containing(source.position)

    /**
     * Send a message back to the command sender.
     *
     * @param message The message to send (supports Minecraft formatting codes)
     */
    fun sendMessage(message: String) {
        source.sendSuccess({ Component.literal(message) }, false)
    }

    /**
     * Send a success message (green).
     */
    fun sendSuccess(message: String) {
        source.sendSuccess({ Component.literal("§a$message") }, false)
    }

    /**
     * Send an error message (red).
     */
    fun sendError(message: String) {
        source.sendFailure(Component.literal("§c$message"))
    }

    /**
     * Send a message to all operators.
     */
    fun broadcast(message: String) {
        source.sendSuccess({ Component.literal(message) }, true)
    }

    /**
     * Check if the command source has a specific permission level.
     *
     * @param level Permission level (0-4, where 2 is standard operator)
     */
    fun hasPermission(level: Int): Boolean {
        return source.hasPermission(level)
    }

    /**
     * Get the command source's name (player name or "Server").
     */
    fun getSenderName(): String {
        return JSConversion.componentToJS(source.displayName)
    }

    /**
     * Check if the command was executed by a player.
     */
    fun isPlayer(): Boolean {
        return player != null
    }

    /**
     * Check if the command was executed from the server console.
     */
    fun isServer(): Boolean {
        return player == null
    }
}
