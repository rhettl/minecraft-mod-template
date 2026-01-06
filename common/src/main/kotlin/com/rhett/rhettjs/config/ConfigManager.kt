package com.rhett.rhettjs.config

import com.rhett.rhettjs.RhettJSCommon
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import de.marhali.json5.Json5
import de.marhali.json5.Json5Object
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Manages loading and saving of RhettJS configuration.
 * Uses JSON5 for parsing (supports comments and trailing commas) and Gson for saving.
 */
object ConfigManager {
    private lateinit var configPath: Path
    private var config: RhettJSConfig = RhettJSConfig()

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val json5 = Json5.builder { it.parseComments().build() }

    /**
     * Initialize the config manager with the config directory.
     */
    fun init(configDir: Path) {
        configPath = configDir.resolve("rhettjs.json5")
        load()
    }

    /**
     * Load configuration from file, creating default if not exists.
     */
    fun load() {
        if (!configPath.exists()) {
            RhettJSCommon.LOGGER.info("[RhettJS] Creating default config at ${configPath.fileName}")
            createDefault()
            return
        }

        try {
            val content = configPath.readText()
            val json5Element = json5.parse(content)
            val json5Object = json5Element.getAsJson5Object()

            // Extract values from JSON5 object
            config = RhettJSConfig(
                enabled = json5Object.get("enabled").getAsBoolean(),
                debug_logging = json5Object.get("debug_logging").getAsBoolean(),
                debug_run_ingame_testing = json5Object.get("debug_run_ingame_testing").getAsBoolean()
            )

            RhettJSCommon.LOGGER.info("[RhettJS] Loaded config from ${configPath.fileName}")

            if (config.debug_logging) {
                RhettJSCommon.LOGGER.info("[RhettJS] Debug logging enabled")
            }
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to load config, using defaults", e)
            config = RhettJSConfig()
        }
    }

    /**
     * Save current configuration to file.
     */
    fun save() {
        try {
            // Ensure directory exists
            Files.createDirectories(configPath.parent)

            val content = gson.toJson(config)
            configPath.writeText(content)
            RhettJSCommon.LOGGER.info("[RhettJS] Saved config to ${configPath.fileName}")
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to save config", e)
        }
    }

    /**
     * Create default configuration file.
     * Extracts from bundled resources in mod JAR.
     */
    private fun createDefault() {
        try {
            Files.createDirectories(configPath.parent)

            // Extract default config from JAR resources
            val resourceContent = javaClass.getResourceAsStream("/rhettjs-defaults/rhettjs.json5")
                ?.bufferedReader()
                ?.readText()

            if (resourceContent != null) {
                configPath.writeText(resourceContent)

                // Parse with JSON5
                val json5Element = json5.parse(resourceContent)
                val json5Object = json5Element.getAsJson5Object()
                config = RhettJSConfig(
                    enabled = json5Object.get("enabled").getAsBoolean(),
                    debug_logging = json5Object.get("debug_logging").getAsBoolean(),
                    debug_run_ingame_testing = json5Object.get("debug_run_ingame_testing").getAsBoolean()
                )

                RhettJSCommon.LOGGER.info("[RhettJS] Created default config at ${configPath.fileName}")
                RhettJSCommon.LOGGER.info("[RhettJS] Config file: $configPath")
            } else {
                // Fallback to inline creation if resource not found
                RhettJSCommon.LOGGER.warn("[RhettJS] Default config resource not found, creating inline")
                config = RhettJSConfig(enabled = true, debug_logging = true)
                val content = gson.toJson(config)
                configPath.writeText(content)
                RhettJSCommon.LOGGER.info("[RhettJS] Created default config at ${configPath.fileName}")
            }
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to create default config", e)
        }
    }

    /**
     * Get current configuration.
     */
    fun get(): RhettJSConfig = config

    /**
     * Check if the mod is enabled.
     */
    fun isEnabled(): Boolean = config.enabled

    /**
     * Check if debug logging is enabled.
     */
    fun isDebugEnabled(): Boolean = config.debug_logging

    /**
     * Check if in-game testing mode is enabled.
     */
    fun isIngameTestingEnabled(): Boolean = config.debug_run_ingame_testing

    /**
     * Log a debug message if debug logging is enabled.
     */
    fun debug(message: String) {
        if (config.debug_logging) {
            RhettJSCommon.LOGGER.info("[RhettJS-DEBUG] $message")
        }
    }

    /**
     * Log a debug message with lazy evaluation.
     * Only evaluates the message if debug logging is enabled.
     */
    fun debugLazy(message: () -> String) {
        if (config.debug_logging) {
            RhettJSCommon.LOGGER.info("[RhettJS-DEBUG] ${message()}")
        }
    }
}
