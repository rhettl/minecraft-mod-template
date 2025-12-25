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
 * Mimics KubeJS's StartupEvents.registry() pattern for direct registry items.
 * Note: Dimensions/biomes must use datapack JSON files (not supported via scripts).
 */
object StartupEventsAPI {

    private val registryHandlers = ConcurrentHashMap<String, MutableList<Function>>()

    // Only direct registries supported (like KubeJS)
    // Datapack registries (dimensions, biomes, etc.) must use JSON files
    private val supportedRegistryTypes = setOf("item", "block")

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
     * Get all handlers registered for a specific registry type.
     *
     * @param type The registry type
     * @return List of registered handlers
     */
    fun getRegistryHandlers(type: String): List<Any> {
        return registryHandlers[type]?.toList() ?: emptyList()
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
     * Get list of supported registry types.
     *
     * @return List of supported types
     */
    fun getSupportedRegistryTypes(): List<String> {
        return supportedRegistryTypes.toList()
    }

    /**
     * Clear all registered handlers.
     * Used for testing and reload scenarios.
     */
    fun clear() {
        registryHandlers.clear()
    }
}
