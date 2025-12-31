package com.rhett.rhettjs.util

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.mozilla.javascript.ScriptRuntime

/**
 * Utilities for converting Java objects to JavaScript primitives.
 *
 * Problem: Rhino's Context.javaToJS() creates NativeJavaObject wrappers
 * for certain Java types (ResourceLocation, Component, etc.), which show
 * as typeof "object" instead of "string" in JavaScript.
 *
 * Solution: Explicitly convert to JavaScript primitives using ScriptRuntime.toString()
 * to ensure proper type coercion.
 *
 * Usage:
 * ```kotlin
 * // Instead of:
 * val dim = level.dimension().location().toString()
 *
 * // Use:
 * val dim = JSConversion.resourceLocationToJS(level.dimension().location())
 * ```
 */
object JSConversion {
    /**
     * Convert any value to a JavaScript primitive string.
     * Ensures the result is a JS string, not a Java String wrapper.
     *
     * @param value Any value to convert
     * @return JavaScript primitive string
     */
    fun toJSString(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> ScriptRuntime.toString(value)
            else -> ScriptRuntime.toString(value.toString())
        }
    }

    /**
     * Convert ResourceLocation to JavaScript primitive string.
     * Handles Minecraft resource locations (e.g., "minecraft:stone").
     *
     * @param location ResourceLocation to convert
     * @return JavaScript primitive string
     */
    fun resourceLocationToJS(location: ResourceLocation): String {
        return ScriptRuntime.toString(location.toString())
    }

    /**
     * Convert Component to JavaScript primitive string.
     * Handles Minecraft text components.
     *
     * @param component Component to convert
     * @return JavaScript primitive string
     */
    fun componentToJS(component: Component): String {
        return ScriptRuntime.toString(component.string)
    }

    /**
     * Convert a map of properties to JavaScript-safe map.
     * Ensures all values are JavaScript primitives.
     *
     * @param properties Map to convert
     * @return Map with JavaScript primitive values
     */
    fun propertiesToJS(properties: Map<String, Any>): Map<String, String> {
        return properties.mapValues { (_, value) -> toJSString(value) }
    }
}