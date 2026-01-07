package com.rhett.rhettjs.config

import com.rhett.rhettjs.RhettJSCommon
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Initializes the RhettJS filesystem structure.
 *
 * Creates required directories and extracts bundled resources (type definitions, README)
 * from the mod JAR to the world directory on first load.
 */
object FilesystemInitializer {

    /**
     * Required directory structure under <minecraft>/rjs/
     */
    private val REQUIRED_DIRS = listOf(
        "__types",     // TypeScript definitions
        "modules",     // ES6 modules for imports
        "scripts",     // Utility scripts (/rjs run)
        "startup",     // Early initialization scripts
        "server",      // Event handlers and commands
        "data",        // Data files/JSON
        "assets",      // Asset files (textures, models, etc.)
        "client"       // Client-side scripts
    )

    /**
     * Initialize RhettJS filesystem structure.
     * Creates directories and extracts bundled resources if missing.
     *
     * @param rjsRootDir The root RhettJS directory (typically <world>/rjs or <config>/rjs)
     */
    fun initialize(rjsRootDir: Path) {
        try {
            // Ensure root directory exists
            if (!rjsRootDir.exists()) {
                Files.createDirectories(rjsRootDir)
                RhettJSCommon.LOGGER.info("[RhettJS] Created RhettJS root directory: $rjsRootDir")
            }

            // Create required subdirectories
            var createdCount = 0
            for (dirName in REQUIRED_DIRS) {
                val dir = rjsRootDir.resolve(dirName)
                if (!dir.exists()) {
                    Files.createDirectories(dir)
                    createdCount++
                    ConfigManager.debug("Created directory: $dirName")
                }
            }

            if (createdCount > 0) {
                RhettJSCommon.LOGGER.info("[RhettJS] Created $createdCount missing directories")
            }

            // Extract type definitions if missing
            extractTypeDefinitions(rjsRootDir)

            // Extract README if missing
            extractTypesReadme(rjsRootDir)

        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to initialize filesystem", e)
        }
    }

    /**
     * Extract TypeScript definitions from JAR to __types/ directory.
     * Extracts all modular type definition files.
     */
    private fun extractTypeDefinitions(rjsRootDir: Path) {
        val typesDir = rjsRootDir.resolve("__types")

        // List of all type definition files to extract
        val typeFiles = listOf(
            "rhettjs.d.ts",          // Barrel file
            "types.d.ts",            // Common types
            "runtime.d.ts",          // Runtime API
            "store.d.ts",            // Store API
            "nbt.d.ts",              // NBT API
            "commands.d.ts",         // Commands API
            "server.d.ts",           // Server API
            "world.d.ts",            // World API
            "structure.d.ts",        // Structure APIs
            "script.d.ts",           // Script API
            "jsconfig.json.template" // VSCode config template
        )

        var extractedCount = 0
        for (fileName in typeFiles) {
            val targetFile = typesDir.resolve(fileName)

            // Only extract if missing
            if (!targetFile.exists()) {
                try {
                    val resourcePath = "/rhettjs-types/$fileName"
                    val content = javaClass.getResourceAsStream(resourcePath)
                        ?.bufferedReader()
                        ?.readText()

                    if (content != null) {
                        targetFile.writeText(content)
                        extractedCount++
                        ConfigManager.debug("Extracted type definition: $fileName")
                    } else {
                        RhettJSCommon.LOGGER.warn("[RhettJS] Type definition resource not found: $resourcePath")
                    }
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Failed to extract $fileName", e)
                }
            }
        }

        if (extractedCount > 0) {
            RhettJSCommon.LOGGER.info("[RhettJS] Extracted $extractedCount type definition files to __types/")
        }
    }

    /**
     * Extract __types/README.md from JAR resources.
     */
    private fun extractTypesReadme(rjsRootDir: Path) {
        val readmeFile = rjsRootDir.resolve("__types/README.md")

        // Only extract if missing
        if (!readmeFile.exists()) {
            try {
                // README is now part of rhettjs-types directory
                val content = javaClass.getResourceAsStream("/rhettjs-types/README.md")
                    ?.bufferedReader()
                    ?.readText()

                if (content != null) {
                    readmeFile.writeText(content)
                    ConfigManager.debug("Extracted __types/README.md")
                }
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Failed to extract __types/README.md", e)
            }
        }
    }

    /**
     * Get list of required directories.
     * Useful for validation or external tools.
     */
    fun getRequiredDirectories(): List<String> = REQUIRED_DIRS.toList()

    /**
     * Validate that all required directories exist.
     * @return true if all required directories exist, false otherwise
     */
    fun validateStructure(rjsRootDir: Path): Boolean {
        if (!rjsRootDir.exists()) return false

        return REQUIRED_DIRS.all { dirName ->
            rjsRootDir.resolve(dirName).exists()
        }
    }
}
