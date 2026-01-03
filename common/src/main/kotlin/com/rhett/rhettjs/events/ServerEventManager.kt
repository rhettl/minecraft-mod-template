package com.rhett.rhettjs.events

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.adapter.PlayerAdapter
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.GraalEngine
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for Server API event system.
 *
 * This is the anti-corruption layer between Minecraft events and JavaScript.
 * It stores event handlers registered from JavaScript and provides methods
 * to trigger those handlers from Minecraft platform code (Fabric/NeoForge).
 *
 * Design principles:
 * - No Minecraft types exposed to JavaScript
 * - All objects wrapped using adapters (PlayerAdapter, etc.)
 * - Thread-safe handler storage
 * - Async handler support
 */
object ServerEventManager {

    // Event handler storage
    private val eventHandlers = ConcurrentHashMap<String, MutableList<Value>>()
    private val oneTimeHandlers = ConcurrentHashMap<String, MutableList<Value>>()

    // Reference to MinecraftServer for accessing properties
    @Volatile
    private var minecraftServer: MinecraftServer? = null

    // Reference to GraalVM context for event execution
    @Volatile
    private var graalContext: Context? = null

    /**
     * Set the Minecraft server reference.
     * Called during server startup.
     */
    fun setServer(server: MinecraftServer) {
        minecraftServer = server
        ConfigManager.debug("[ServerEventManager] Minecraft server reference set")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        ConfigManager.debug("[ServerEventManager] GraalVM context reference set")
    }

    /**
     * Register an event handler from JavaScript.
     *
     * @param event The event name (e.g., "playerJoin")
     * @param handler The JavaScript function to call
     */
    fun on(event: String, handler: Value) {
        if (!handler.canExecute()) {
            throw IllegalArgumentException("Handler must be a function")
        }

        eventHandlers.getOrPut(event) { mutableListOf() }.add(handler)
        ConfigManager.debug("[ServerEventManager] Registered handler for event: $event")
    }

    /**
     * Register a one-time event handler from JavaScript.
     * The handler will be removed after first execution.
     *
     * @param event The event name
     * @param handler The JavaScript function to call
     */
    fun once(event: String, handler: Value) {
        if (!handler.canExecute()) {
            throw IllegalArgumentException("Handler must be a function")
        }

        oneTimeHandlers.getOrPut(event) { mutableListOf() }.add(handler)
        ConfigManager.debug("[ServerEventManager] Registered one-time handler for event: $event")
    }

    /**
     * Unregister an event handler from JavaScript.
     *
     * @param event The event name
     * @param handler The JavaScript function to remove
     */
    fun off(event: String, handler: Value) {
        eventHandlers[event]?.remove(handler)
        oneTimeHandlers[event]?.remove(handler)
        ConfigManager.debug("[ServerEventManager] Unregistered handler for event: $event")
    }

    /**
     * Trigger a player join event.
     * Called from platform code when a player joins the server.
     *
     * @param player The ServerPlayer who joined
     */
    fun triggerPlayerJoin(player: ServerPlayer) {
        val context = graalContext ?: run {
            RhettJSCommon.LOGGER.warn("[ServerEventManager] Cannot trigger playerJoin: GraalVM context not available")
            return
        }

        // Wrap player using adapter (anti-corruption shield)
        val wrappedPlayer = PlayerAdapter.toJS(player, context)

        // Trigger the event
        triggerEvent("playerJoin", wrappedPlayer)
    }

    /**
     * Trigger a player leave event.
     * Called from platform code when a player leaves the server.
     *
     * @param player The ServerPlayer who left
     */
    fun triggerPlayerLeave(player: ServerPlayer) {
        val context = graalContext ?: run {
            RhettJSCommon.LOGGER.warn("[ServerEventManager] Cannot trigger playerLeave: GraalVM context not available")
            return
        }

        // Wrap player using adapter (anti-corruption shield)
        val wrappedPlayer = PlayerAdapter.toJS(player, context)

        // Trigger the event
        triggerEvent("playerLeave", wrappedPlayer)
    }

    /**
     * Generic event triggering mechanism.
     * Calls all registered handlers with the provided arguments.
     *
     * @param event The event name
     * @param args The arguments to pass to handlers
     */
    private fun triggerEvent(event: String, vararg args: Any?) {
        val regularHandlers = eventHandlers[event] ?: mutableListOf()
        val onceHandlers = oneTimeHandlers[event] ?: mutableListOf()

        val allHandlers = regularHandlers + onceHandlers

        if (allHandlers.isEmpty()) {
            ConfigManager.debug("[ServerEventManager] No handlers for event: $event")
            return
        }

        ConfigManager.debug("[ServerEventManager] Triggering ${allHandlers.size} handlers for event: $event")

        allHandlers.forEach { handler ->
            try {
                // Execute handler with arguments
                handler.execute(*args)

                ConfigManager.debug("[ServerEventManager] Handler executed successfully for event: $event")

            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[ServerEventManager] Error in event handler for $event", e)
                // Continue with other handlers even if one fails
            }
        }

        // Remove one-time handlers after execution
        if (onceHandlers.isNotEmpty()) {
            oneTimeHandlers[event]?.removeAll(onceHandlers.toSet())
            ConfigManager.debug("[ServerEventManager] Removed ${onceHandlers.size} one-time handlers for event: $event")
        }
    }

    /**
     * Get current server TPS (ticks per second).
     * Returns 20.0 as placeholder (TODO: implement real TPS tracking).
     */
    fun getServerTPS(): Double {
        // TODO: Implement actual TPS tracking using server tick times
        return 20.0
    }

    /**
     * Get list of online players as wrapped JavaScript objects.
     */
    fun getOnlinePlayers(): List<Value> {
        val server = minecraftServer ?: return emptyList()
        val context = graalContext ?: return emptyList()

        return server.playerList.players.map { player ->
            PlayerAdapter.toJS(player, context)
        }
    }

    /**
     * Get maximum player count.
     */
    fun getMaxPlayers(): Int {
        return minecraftServer?.playerList?.maxPlayers ?: 20
    }

    /**
     * Get server MOTD (Message of the Day).
     */
    fun getMOTD(): String {
        return minecraftServer?.motd ?: "A Minecraft Server"
    }

    /**
     * Broadcast a message to all online players.
     *
     * @param message The message to broadcast
     */
    fun broadcast(message: String) {
        val server = minecraftServer ?: run {
            RhettJSCommon.LOGGER.warn("[ServerEventManager] Cannot broadcast: Server not available")
            return
        }

        val component = Component.literal(message)
        server.playerList.broadcastSystemMessage(component, false)
        ConfigManager.debug("[ServerEventManager] Broadcast: $message")
    }

    /**
     * Execute a command on the server.
     *
     * @param command The command to execute (without leading slash)
     */
    fun runCommand(command: String) {
        val server = minecraftServer ?: run {
            RhettJSCommon.LOGGER.warn("[ServerEventManager] Cannot run command: Server not available")
            return
        }

        // Execute on main thread
        server.execute {
            val commandManager = server.commands
            val source = server.createCommandSourceStack()

            try {
                commandManager.performPrefixedCommand(source, command)
                ConfigManager.debug("[ServerEventManager] Executed command: /$command")
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[ServerEventManager] Failed to execute command: /$command", e)
            }
        }
    }

    /**
     * Clear all event handlers.
     * Called on reload/shutdown.
     */
    fun clear() {
        eventHandlers.clear()
        oneTimeHandlers.clear()
        ConfigManager.debug("[ServerEventManager] Cleared all event handlers")
    }

    /**
     * Reset the manager (called on GraalEngine reset).
     * Clears handlers and context reference (context will be recreated).
     */
    fun reset() {
        clear()
        graalContext = null
        ConfigManager.debug("[ServerEventManager] Reset complete")
    }
}