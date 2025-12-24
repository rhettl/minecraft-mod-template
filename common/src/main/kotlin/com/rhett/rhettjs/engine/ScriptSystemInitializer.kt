package com.rhett.rhettjs.engine

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.api.StructureAPI
import com.rhett.rhettjs.api.StructureAPIWrapper
import com.rhett.rhettjs.api.WorldAPI
import com.rhett.rhettjs.api.WorldAPIWrapper
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.events.ServerEventsAPI
import com.rhett.rhettjs.events.StartupEventsAPI
import com.rhett.rhettjs.worldgen.DimensionRegistry
import com.rhett.rhettjs.worldgen.DatapackGenerator
import net.minecraft.server.MinecraftServer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Common initialization logic for the script system.
 * Used by both Fabric and NeoForge to avoid code duplication.
 */
object ScriptSystemInitializer {

    /**
     * Initialize the script system on server start.
     *
     * @param server The Minecraft server instance
     */
    fun initialize(server: MinecraftServer) {
        val serverDirectory = server.serverDirectory
        ConfigManager.debug("Server starting, initializing script system")

        val scriptsDir = getScriptsDirectory(serverDirectory)
        ConfigManager.debug("Script directory: $scriptsDir")

        // Ensure script directories exist
        createDirectories(scriptsDir)

        // Initialize Structure API
        initializeStructureAPI(serverDirectory)

        // Initialize World API
        initializeWorldAPI(server)

        // Scan for scripts
        RhettJSCommon.LOGGER.info("[RhettJS] Scanning for scripts...")
        ScriptRegistry.scan(scriptsDir)

        // Load global libraries
        GlobalsLoader.reload(scriptsDir)
        ConfigManager.debug("Loaded global libraries")

        // Execute startup scripts
        executeStartupScripts()

        // Register worldgen (dimensions, biomes, etc.)
        registerWorldgen(scriptsDir)

        // Load server scripts (register event handlers)
        loadServerScripts()

        RhettJSCommon.LOGGER.info("[RhettJS] Ready! Use /rjs list to see available scripts")
        ConfigManager.debug("Script system initialization complete")
    }

    /**
     * Register worldgen elements (dimensions, biomes) from startup scripts.
     * Note: This must be called BEFORE the server loads worlds.
     */
    private fun registerWorldgen(scriptsDir: Path) {
        RhettJSCommon.LOGGER.info("[RhettJS] Registering worldgen elements...")

        // Clear previous registrations
        DimensionRegistry.clear()

        // Create a temporary scope for executing worldgen registration scripts
        val cx = org.mozilla.javascript.Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = org.mozilla.javascript.Context.VERSION_ES6

            val scope = ScriptEngine.createScope(ScriptCategory.STARTUP)

            // Execute worldgen registrations and collect dimension configs
            val dimensionConfigs = StartupEventsAPI.executeWorldgenRegistrations("dimension", scope)

            // Register each dimension
            dimensionConfigs.forEach { config ->
                DimensionRegistry.registerDimension(config)
            }

            ConfigManager.debug("Registered ${dimensionConfigs.size} dimensions")

        } finally {
            org.mozilla.javascript.Context.exit()
        }
    }

    /**
     * Generate dimension datapack files after server has started.
     * This generates files in world/datapacks/rhettjs/ for next restart.
     *
     * @param server The Minecraft server instance
     */
    fun generateDimensionDatapack(server: MinecraftServer) {
        val dimensions = DimensionRegistry.getRegisteredDimensions()

        if (dimensions.isEmpty()) {
            return
        }

        try {
            // Get world/datapacks/rhettjs/ directory
            val worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            val datapacksDir = worldPath.resolve("datapacks")
            val rhettjsDatapackDir = datapacksDir.resolve("rhettjs")

            // Create directories
            Files.createDirectories(rhettjsDatapackDir)

            // Generate pack.mcmeta
            DatapackGenerator.generatePackMeta(rhettjsDatapackDir)

            // Generate dimension JSON files
            DatapackGenerator.generateDimensions(rhettjsDatapackDir, dimensions)

            // Clean up old dimension files
            val registeredNames = dimensions.values.map { it.name }.toSet()
            DatapackGenerator.cleanupUnregisteredDimensions(rhettjsDatapackDir, registeredNames)

            RhettJSCommon.LOGGER.warn("[RhettJS] Dimension datapack generated at world/datapacks/rhettjs/")
            RhettJSCommon.LOGGER.warn("[RhettJS] Server restart required for dimension changes to take effect!")

        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to generate dimension datapack", e)
        }
    }

    /**
     * Reload all scripts (used by /rjs reload command).
     * Note: Does not reinitialize APIs (Structure, World) as they persist across reloads.
     *
     * @param serverDirectory The server's root directory
     */
    fun reload(serverDirectory: Path) {
        val scriptsDir = getScriptsDirectory(serverDirectory)

        // Clear all event handlers and globals
        RhettJSCommon.LOGGER.info("[RhettJS] Clearing event handlers...")
        StartupEventsAPI.clear()
        ServerEventsAPI.clear()
        GlobalsLoader.clear()

        // Rescan all scripts
        RhettJSCommon.LOGGER.info("[RhettJS] Rescanning scripts...")
        ScriptRegistry.scan(scriptsDir)

        // Reload globals
        RhettJSCommon.LOGGER.info("[RhettJS] Reloading globals...")
        GlobalsLoader.reload(scriptsDir)

        // Re-execute startup scripts
        executeStartupScripts()

        // Re-register worldgen (dimensions will be re-registered)
        registerWorldgen(scriptsDir)

        // Reload server scripts (re-register event handlers)
        loadServerScripts()
    }

    /**
     * Get the scripts directory, checking for testing mode.
     */
    private fun getScriptsDirectory(serverDirectory: Path): Path {
        val baseScriptsDir = serverDirectory.resolve("rjs")

        // Check if in-game testing mode is enabled
        return if (ConfigManager.isIngameTestingEnabled()) {
            val testingDir = baseScriptsDir.resolve("testing")
            if (testingDir.exists()) {
                RhettJSCommon.LOGGER.info("[RhettJS] In-game testing mode enabled, using: rjs/testing/")
                ConfigManager.debug("Testing directory exists at: $testingDir")
                testingDir
            } else {
                RhettJSCommon.LOGGER.warn("[RhettJS] In-game testing mode enabled but rjs/testing/ not found, using default: rjs/")
                ConfigManager.debug("Testing directory not found, falling back to: $baseScriptsDir")
                baseScriptsDir
            }
        } else {
            baseScriptsDir
        }
    }

    /**
     * Initialize Structure API.
     * Uses server directory temporarily during initialization.
     * Will be re-initialized with world paths once worlds are loaded.
     */
    private fun initializeStructureAPI(serverDirectory: Path) {
        // Use server directory as temporary location during initialization
        // This allows initialization before worlds are loaded
        val structuresDir = serverDirectory.resolve("structures")
        val structureBackupsDir = serverDirectory.resolve("backups/structures")

        Files.createDirectories(structuresDir)
        Files.createDirectories(structureBackupsDir)

        val structureApi = StructureAPI(structuresDir, structureBackupsDir)
        val structureWrapper = StructureAPIWrapper(structureApi)

        // Register Structure API globally so it's available in all script contexts
        ScriptEngine.initializeStructureAPI(structureWrapper)
        ConfigManager.debug("Initialized Structure API (temporary) with paths: structures=$structuresDir, backups=$structureBackupsDir")
    }

    /**
     * Re-initialize Structure API with world paths.
     * Called after worlds are loaded (SERVER_STARTED event) to use the proper generated directory.
     */
    fun reinitializeWithWorldPaths(server: MinecraftServer) {
        // Get overworld to access world save directory
        val overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD)

        if (overworld == null) {
            RhettJSCommon.LOGGER.warn("[RhettJS] Could not re-initialize Structure API: Overworld not loaded yet")
            ConfigManager.debug("Keeping temporary Structure API paths")
            return
        }

        // Get world path: <world>/generated/minecraft/structures/
        val worldPath = overworld.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
        val structuresDir = worldPath
            .resolve("generated")
            .resolve("minecraft")
            .resolve("structures")
        val structureBackupsDir = worldPath.resolve("backups/structures")

        Files.createDirectories(structuresDir)
        Files.createDirectories(structureBackupsDir)

        val structureApi = StructureAPI(structuresDir, structureBackupsDir)
        val structureWrapper = StructureAPIWrapper(structureApi)

        // Replace globally registered Structure API
        ScriptEngine.initializeStructureAPI(structureWrapper)
        ConfigManager.debug("Re-initialized Structure API with world paths: structures=$structuresDir, backups=$structureBackupsDir")
        RhettJSCommon.LOGGER.info("[RhettJS] Structure API using world directory: generated/minecraft/structures/")
    }

    /**
     * Initialize World API.
     */
    private fun initializeWorldAPI(server: MinecraftServer) {
        val worldApi = WorldAPI(server)
        val worldWrapper = WorldAPIWrapper(worldApi)

        // Register World API globally so it's available in all script contexts
        ScriptEngine.initializeWorldAPI(worldWrapper)
        ConfigManager.debug("Initialized World API")
    }

    /**
     * Execute startup scripts.
     */
    private fun executeStartupScripts() {
        val startupScripts = ScriptRegistry.getScripts(ScriptCategory.STARTUP)
        if (startupScripts.isNotEmpty()) {
            RhettJSCommon.LOGGER.info("[RhettJS] Executing ${startupScripts.size} startup scripts...")
            startupScripts.forEach { script ->
                try {
                    ScriptEngine.executeScript(script)
                    ConfigManager.debug("Executed startup script: ${script.name}")
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Failed to execute startup script: ${script.name}", e)
                }
            }
        }

        ConfigManager.debug("Startup handlers registered")
    }

    /**
     * Load server scripts (register event handlers).
     */
    private fun loadServerScripts() {
        val serverScripts = ScriptRegistry.getScripts(ScriptCategory.SERVER)
        if (serverScripts.isNotEmpty()) {
            RhettJSCommon.LOGGER.info("[RhettJS] Loading ${serverScripts.size} server scripts...")
            serverScripts.forEach { script ->
                try {
                    ScriptEngine.executeScript(script)
                    ConfigManager.debug("Loaded server script: ${script.name}")
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Failed to load server script: ${script.name}", e)
                }
            }
        }
    }

    /**
     * Create script category directories.
     */
    private fun createDirectories(baseDir: Path) {
        ScriptCategory.values().forEach { category ->
            val dir = baseDir.resolve(category.dirName)
            if (!dir.exists()) {
                Files.createDirectories(dir)
                RhettJSCommon.LOGGER.info("[RhettJS] Created directory: ${category.dirName}/")
                ConfigManager.debug("Created script directory: ${category.dirName}")
            }
        }
    }
}