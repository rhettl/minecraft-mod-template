package com.rhett.rhettjs.events

import com.rhett.rhettjs.RhettJSCommon
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.ConcurrentHashMap

/**
 * API for registering handlers during mod initialization.
 * Available in startup/ scripts only.
 *
 * Allows registration of items, blocks, dimensions, and other game objects.
 */
object StartupEventsAPI {

    private val registryHandlers = ConcurrentHashMap<String, MutableList<Function>>()
    private val worldgenHandlers = ConcurrentHashMap<String, MutableList<Function>>()
    private val commandHandlers = ConcurrentHashMap<String, Function>()

    private val supportedRegistryTypes = setOf("item", "block")
    private val supportedWorldgenTypes = setOf("dimension", "biome")

    /**
     * Register a handler for a specific registry type (items, blocks).
     *
     * @param type The registry type (e.g., "item", "block")
     * @param handler JavaScript function to call
     */
    fun registry(type: String, handler: Any) {
        if (type !in supportedRegistryTypes) {
            throw IllegalArgumentException("Unsupported registry type: $type. Supported: ${supportedRegistryTypes.joinToString()}")
        }

        if (handler !is Function) {
            throw IllegalArgumentException("Handler must be a function")
        }

        registryHandlers.getOrPut(type) { mutableListOf() }.add(handler)
        RhettJSCommon.LOGGER.info("[RhettJS] Registered startup registry handler for: $type")
    }

    /**
     * Register a handler for worldgen registry types (dimensions, biomes).
     *
     * @param type The worldgen type (e.g., "dimension", "biome")
     * @param handler JavaScript function to call
     */
    fun worldgenRegistry(type: String, handler: Any) {
        if (type !in supportedWorldgenTypes) {
            throw IllegalArgumentException("Unsupported worldgen type: $type. Supported: ${supportedWorldgenTypes.joinToString()}")
        }

        if (handler !is Function) {
            throw IllegalArgumentException("Handler must be a function")
        }

        worldgenHandlers.getOrPut(type) { mutableListOf() }.add(handler)
        RhettJSCommon.LOGGER.info("[RhettJS] Registered worldgen registry handler for: $type")
    }

    /**
     * Get all handlers registered for a specific registry type.
     *
     * @param type The registry type
     * @return List of registered handlers
     */
    fun getRegistryHandlers(type: String): List<Any> {
        return registryHandlers[type]?.toList() ?: emptyList()
    }

    /**
     * Get all handlers registered for a specific worldgen type.
     *
     * @param type The worldgen type
     * @return List of registered handlers
     */
    fun getWorldgenHandlers(type: String): List<Any> {
        return worldgenHandlers[type]?.toList() ?: emptyList()
    }

    /**
     * Execute all registered handlers for a specific registry type.
     *
     * @param type The registry type
     * @param scope The JavaScript scope to execute in
     */
    fun executeRegistrations(type: String, scope: Scriptable) {
        val typeHandlers = registryHandlers[type] ?: return

        RhettJSCommon.LOGGER.info("[RhettJS] Executing ${typeHandlers.size} startup registry handlers for: $type")

        typeHandlers.forEach { handler ->
            try {
                val cx = Context.getCurrentContext()

                // Create event object with register() method
                val event = cx.newObject(scope)
                ScriptableObject.putProperty(event, "type", type)

                // Call handler with event
                handler.call(cx, scope, scope, arrayOf(event))

                // Process microtasks (Promise .then() callbacks)
                cx.processMicrotasks()

            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Error in startup registry handler for $type", e)
                // Continue with other handlers
            }
        }
    }

    /**
     * Execute all registered handlers for a specific worldgen type.
     *
     * @param type The worldgen type
     * @param scope The JavaScript scope to execute in
     * @return List of dimension configurations to register
     */
    fun executeWorldgenRegistrations(type: String, scope: Scriptable): List<Map<String, Any>> {
        val typeHandlers = worldgenHandlers[type] ?: return emptyList()

        RhettJSCommon.LOGGER.info("[RhettJS] Executing ${typeHandlers.size} worldgen registry handlers for: $type")

        val registrations = mutableListOf<Map<String, Any>>()

        typeHandlers.forEach { handler ->
            try {
                val cx = Context.getCurrentContext()

                // Create event object with register() method that captures configurations
                val event = cx.newObject(scope)
                ScriptableObject.putProperty(event, "type", type)

                // Add register method to event
                val registerFn = object : org.mozilla.javascript.BaseFunction() {
                    override fun call(
                        cx: Context,
                        scope: Scriptable,
                        thisObj: Scriptable?,
                        args: Array<Any?>
                    ): Any? {
                        if (args.isEmpty() || args[0] !is Scriptable) {
                            throw IllegalArgumentException("register() requires a configuration object")
                        }

                        // Convert JS object to Map
                        val config = mutableMapOf<String, Any>()
                        val jsConfig = args[0] as Scriptable
                        for (id in jsConfig.ids) {
                            val key = id.toString()
                            val value = jsConfig.get(key, jsConfig)
                            if (value != Scriptable.NOT_FOUND) {
                                config[key] = value
                            }
                        }

                        registrations.add(config)
                        return null
                    }
                }
                ScriptableObject.putProperty(event, "register", registerFn)

                // Call handler with event
                handler.call(cx, scope, scope, arrayOf(event))

                // Process microtasks (Promise .then() callbacks)
                cx.processMicrotasks()

            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Error in worldgen registry handler for $type", e)
                // Continue with other handlers
            }
        }

        return registrations
    }

    /**
     * Get list of supported registry types.
     *
     * @return List of supported types
     */
    fun getSupportedRegistryTypes(): List<String> {
        return supportedRegistryTypes.toList()
    }

    /**
     * Get list of supported worldgen types.
     *
     * @return List of supported types
     */
    fun getSupportedWorldgenTypes(): List<String> {
        return supportedWorldgenTypes.toList()
    }

    /**
     * Register a custom command handler.
     *
     * @param commandName The command name (without leading slash)
     * @param handler JavaScript function to call when command is executed
     */
    fun command(commandName: String, handler: Any) {
        if (handler !is Function) {
            throw IllegalArgumentException("Handler must be a function")
        }

        if (commandName.contains(" ")) {
            throw IllegalArgumentException("Command name cannot contain spaces: $commandName")
        }

        if (commandHandlers.containsKey(commandName)) {
            RhettJSCommon.LOGGER.warn("[RhettJS] Overwriting existing command handler for: $commandName")
        }

        commandHandlers[commandName] = handler
        RhettJSCommon.LOGGER.info("[RhettJS] Registered command: /$commandName")
    }

    /**
     * Get all registered command handlers.
     *
     * @return Map of command names to handlers
     */
    fun getCommandHandlers(): Map<String, Function> {
        return commandHandlers.toMap()
    }

    /**
     * Clear all registered handlers.
     * Used for testing and reload scenarios.
     */
    fun clear() {
        registryHandlers.clear()
        worldgenHandlers.clear()
        commandHandlers.clear()
    }
}
