package com.rhett.rhettjs.events

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.engine.ScriptCategory
import com.rhett.rhettjs.engine.ScriptEngine
import org.mozilla.javascript.Context

/**
 * Helper for triggering block events from platform-specific code.
 * Isolates Rhino dependency from platform modules.
 */
object BlockEventTrigger {

    /**
     * Trigger a block event by creating a fresh Context and scope.
     * This is called from platform-specific event handlers (Fabric/NeoForge).
     *
     * @param eventType The event type (e.g., "blockRightClicked")
     * @param eventData The block event data
     */
    fun trigger(eventType: String, eventData: BlockEventData) {
        try {
            val cx = Context.enter()
            try {
                cx.optimizationLevel = -1
                cx.languageVersion = Context.VERSION_ES6
                val scope = ScriptEngine.createScope(ScriptCategory.SERVER)
                ServerEventsAPI.triggerBlockEvent(eventType, scope, eventData)
            } finally {
                Context.exit()
            }
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Error triggering $eventType event", e)
        }
    }
}
