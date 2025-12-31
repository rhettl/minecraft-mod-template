package com.rhett.rhettjs.api

import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.rhett.rhettjs.util.JSConversion
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

/**
 * API for executing Minecraft commands from JavaScript.
 * Commands are executed on the server thread and feedback is captured and returned.
 *
 * This is a Level 0-3 foundation API - enables user-built helper libraries without
 * needing dedicated Kotlin APIs for every Minecraft command.
 *
 * All methods return CompletableFuture for async execution on server thread.
 */
class CommandAPI(
    private val server: MinecraftServer,
    private val caller: ServerPlayer?  // The player/entity executing the script
) {

    /**
     * Result from command execution containing feedback and success status.
     */
    data class CommandResult(
        val success: Boolean,
        val resultCount: Int,
        val feedback: List<String>,
        val error: String?
    )

    /**
     * Execute a command as the caller (player running the script).
     * Returns CompletableFuture with command result.
     *
     * @param command Command string (without leading slash)
     * @return CompletableFuture<CommandResult>
     * @throws IllegalStateException if no caller available
     */
    fun execute(command: String): CompletableFuture<CommandResult> {
        if (caller == null) {
            return CompletableFuture.failedFuture(
                IllegalStateException("Command.execute() requires a caller (player). Use Command.executeAsServer() for console commands.")
            )
        }

        return CompletableFuture.supplyAsync({
            val source = caller.createCommandSourceStack()
            executeWithFeedback(source, command)
        }, server::execute)  // Execute on server thread
    }

    /**
     * Execute a command at a specific position.
     * Useful for position-dependent commands like /fill, /setblock, etc.
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param command Command string (without leading slash)
     * @return CompletableFuture<CommandResult>
     */
    fun executeAt(x: Double, y: Double, z: Double, command: String): CompletableFuture<CommandResult> {
        return CompletableFuture.supplyAsync({
            val source = if (caller != null) {
                // Execute as caller but at specified position
                caller.createCommandSourceStack()
                    .withPosition(Vec3(x, y, z))
            } else {
                // Execute as server (console) at specified position
                server.createCommandSourceStack()
                    .withPosition(Vec3(x, y, z))
                    .withLevel(server.overworld())
            }

            executeWithFeedback(source, command)
        }, server::execute)  // Execute on server thread
    }

    /**
     * Execute a command as the server/console.
     * Has full permissions and no position context.
     *
     * @param command Command string (without leading slash)
     * @return CompletableFuture<CommandResult>
     */
    fun executeAsServer(command: String): CompletableFuture<CommandResult> {
        return CompletableFuture.supplyAsync({
            val source = server.createCommandSourceStack()

            executeWithFeedback(source, command)
        }, server::execute)  // Execute on server thread
    }

    /**
     * Get command suggestions for a partial command string.
     * Useful for auto-completion and command discovery.
     *
     * @param partialCommand Partial command string (without leading slash)
     * @return CompletableFuture<List<String>> List of suggested completions
     */
    fun suggest(partialCommand: String): CompletableFuture<List<String>> {
        return CompletableFuture.supplyAsync({
            try {
                val source = if (caller != null) {
                    caller.createCommandSourceStack()
                } else {
                    server.createCommandSourceStack()
                }

                // Parse the partial command to get suggestions
                val parseResults = server.commands.dispatcher.parse(partialCommand, source)
                val suggestions = server.commands.dispatcher.getCompletionSuggestions(parseResults)
                    .get()  // Block until suggestions are ready

                // Extract suggestion text from results
                suggestions.list.map { it.text }

            } catch (e: Exception) {
                // If suggestion fails, return empty list
                emptyList()
            }
        }, server::execute)  // Execute on server thread
    }

    /**
     * Internal: Execute command with feedback capture.
     * Must be called on the server thread.
     */
    private fun executeWithFeedback(source: CommandSourceStack, command: String): CommandResult {
        val feedback = ConcurrentLinkedQueue<String>()
        var commandSuccess = false
        var commandResultCount = 0

        try {
            // Create a custom consumer to capture feedback
            val feedbackConsumer = Consumer<Component> { component ->
                feedback.add(JSConversion.componentToJS(component))
            }

            // Create a command source that captures feedback and success/result count
            val feedbackSource = source
                .withCallback { success, resultCount ->
                    commandSuccess = success
                    commandResultCount = resultCount
                }
                .withSource(source.entity)
                .withLevel(source.level)

            // Parse and execute the command
            val parseResults = server.commands.dispatcher.parse(command, feedbackSource)
            val result = server.commands.dispatcher.execute(parseResults)

            // Note: Minecraft's command feedback system is complex and doesn't provide
            // an easy way to capture feedback messages. The callback only provides
            // success status and result count, not the actual feedback text.
            // For now, we return empty feedback list.
            // TODO: Implement proper feedback capture if needed (requires hooking into chat system)

            return CommandResult(
                success = commandSuccess,
                resultCount = result,
                feedback = feedback.toList(),
                error = null
            )

        } catch (e: CommandSyntaxException) {
            // Command syntax error
            return CommandResult(
                success = false,
                resultCount = 0,
                feedback = feedback.toList(),
                error = "Command syntax error: ${e.message}"
            )
        } catch (e: Exception) {
            // Other command execution error
            return CommandResult(
                success = false,
                resultCount = 0,
                feedback = feedback.toList(),
                error = "Command execution failed: ${e.message}"
            )
        }
    }
}
