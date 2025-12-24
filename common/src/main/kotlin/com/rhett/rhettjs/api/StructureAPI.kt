package com.rhett.rhettjs.api

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension

/**
 * High-level API for working with Minecraft structure files.
 * Wraps NBTAPI with structure-specific convenience methods.
 *
 * Structures are stored in: <world>/structures/
 * Organized in pools: village/, desert/, etc.
 */
class StructureAPI(
    private val structuresDir: Path,
    private val backupsDir: Path
) {

    private val nbtApi = NBTAPI(structuresDir, backupsDir)

    /**
     * List all structure files, optionally filtered by pool.
     *
     * @param pool Optional pool name to filter by (e.g., "village", "desert")
     * @return List of structure names (relative paths without .nbt extension)
     *
     * Example:
     *   list()           → ["village/houses/house_1", "desert/temples/temple_1", ...]
     *   list("village")  → ["village/houses/house_1", "village/houses/house_2", ...]
     */
    fun list(pool: String? = null): List<String> {
        if (!structuresDir.exists()) {
            return emptyList()
        }

        val searchDir = if (pool != null) {
            structuresDir.resolve(pool)
        } else {
            structuresDir
        }

        if (!searchDir.exists()) {
            return emptyList()
        }

        return Files.walk(searchDir)
            .filter { it.isRegularFile() }
            .filter { it.extension == "nbt" }
            .filter { !it.fileName.toString().endsWith(".bak") }
            .map { file ->
                // Get path relative to structures directory
                val relativePath = structuresDir.relativize(file)
                // Remove .nbt extension
                removeNbtExtension(relativePath.toString())
            }
            .sorted()
            .toList()
    }

    /**
     * Read a structure file by name.
     *
     * @param name Structure name (with or without .nbt extension)
     * @return Structure data as Map/List, or null if not found
     *
     * Example:
     *   read("village/houses/house_1")      → {size: [16,5,16], palette: [...], ...}
     *   read("village/houses/house_1.nbt")  → same (extension optional)
     */
    fun read(name: String): Any? {
        val normalizedName = ensureNbtExtension(name)
        return nbtApi.read(normalizedName)
    }

    /**
     * Write a structure file by name.
     *
     * @param name Structure name (with or without .nbt extension)
     * @param data Structure data (Map with size, palette, blocks, entities, etc.)
     * @param skipBackup If true, skip automatic backup creation (default: false)
     *
     * Example:
     *   write("village/houses/house_1", structureData)                 // Auto-backup enabled
     *   write("village/houses/house_1", structureData, skipBackup: true)  // No auto-backup
     */
    fun write(name: String, data: Any, skipBackup: Boolean = false) {
        val normalizedName = ensureNbtExtension(name)
        nbtApi.write(normalizedName, data, skipBackup)
    }

    /**
     * Manually create a timestamped backup of a structure file.
     * Automatically cleans up old backups (keeps last 5).
     *
     * @param name Structure name (with or without .nbt extension)
     * @return The backup filename that was created, or null if structure doesn't exist
     *
     * Example:
     *   backup("village/houses/house_1")
     *   → Creates: backups/structures/village/houses/house_1.nbt.2024-12-19_00-42-15.bak
     *   → Returns: "house_1.nbt.2024-12-19_00-42-15.bak"
     */
    fun backup(name: String): String? {
        val normalizedName = ensureNbtExtension(name)
        return nbtApi.createManualBackup(normalizedName)
    }

    /**
     * List all available backups for a structure, sorted by timestamp (newest first).
     *
     * @param name Structure name (with or without .nbt extension)
     * @return List of backup filenames (e.g., ["house_1.nbt.2024-12-19_00-42-15.bak", ...])
     *
     * Example:
     *   listBackups("village/houses/house_1")
     *   → ["house_1.nbt.2024-12-19_00-45-00.bak", "house_1.nbt.2024-12-19_00-42-15.bak"]
     */
    fun listBackups(name: String): List<String> {
        val normalizedName = ensureNbtExtension(name)
        return nbtApi.listBackups(normalizedName)
    }

    /**
     * Restore a structure from its backup.
     * By default, restores to the original location. Optionally restore to a new name.
     *
     * @param name Source structure name (with or without .nbt extension)
     * @param targetName Optional new name for restored structure (defaults to original name)
     * @param backupTimestamp Optional specific backup timestamp (defaults to most recent)
     * @return true if restore succeeded, false if backup not found
     *
     * Examples:
     *   restore("academy")
     *   → Restores most recent backup to: structures/academy.nbt
     *
     *   restore("academy", "academy-restored")
     *   → Restores most recent backup to: structures/academy-restored.nbt
     *
     *   restore("academy", null, "2024-12-19_00-42-15")
     *   → Restores specific backup to: structures/academy.nbt
     */
    fun restore(name: String, targetName: String? = null, backupTimestamp: String? = null): Boolean {
        val normalizedName = ensureNbtExtension(name)

        // If restoring to same location, use NBTAPI's restore method
        if (targetName == null) {
            return nbtApi.restoreFromBackup(normalizedName, backupTimestamp)
        }

        // Otherwise, restore to a different name
        val backups = nbtApi.listBackups(normalizedName)
        if (backups.isEmpty()) {
            return false
        }

        val backupToRestore = if (backupTimestamp != null) {
            backups.firstOrNull { it.contains(backupTimestamp) }
        } else {
            backups.firstOrNull()
        } ?: return false

        // Read backup data
        val backupPath = backupsDir.resolve(normalizedName).parent?.resolve(backupToRestore) ?: return false
        if (!backupPath.exists()) {
            return false
        }

        val backupData = nbtApi.read(backupPath.toString().removePrefix(structuresDir.toString()).removePrefix("/"))
            ?: return false

        // Write to target location
        write(targetName, backupData)

        return true
    }

    /**
     * Resolve a structure name to its file path.
     * Handles namespace prefixes (minecraft:, mods:, etc.) and searches common directories.
     *
     * @param name Structure name (with optional namespace)
     * @return Absolute Path to structure file, or null if not found
     *
     * Examples:
     *   resolveStructurePath("castle")              → structures/captured/castle.nbt
     *   resolveStructurePath("minecraft:castle")    → structures/captured/castle.nbt
     *   resolveStructurePath("exported/village/house") → structures/exported/village/house.nbt
     *
     * Search order:
     *   1. Exact path: structures/{name}.nbt
     *   2. Captured dir: structures/captured/{name}.nbt
     */
    fun resolveStructurePath(name: String): Path? {
        // Strip namespace prefix if present (minecraft:castle → castle)
        val cleanName = if (name.contains(':')) {
            name.substringAfter(':')
        } else {
            name
        }

        // Ensure .nbt extension
        val nameWithExt = ensureNbtExtension(cleanName)

        // Search locations in order
        val searchPaths = listOf(
            structuresDir.resolve(nameWithExt),                // Direct path
            structuresDir.resolve("captured").resolve(nameWithExt)  // Captured directory
        )

        return searchPaths.firstOrNull { it.exists() && it.isRegularFile() }
    }

    /**
     * Resolve a large structure name to its base directory path.
     * Returns the directory containing pieces/<name>/ and large/<name>.json
     *
     * @param name Large structure name (with optional namespace)
     * @return Absolute Path to base directory (containing pieces/ and large/ subdirs), or null if not found
     *
     * Examples:
     *   resolveLargeStructurePath("castle")           → structures/
     *   resolveLargeStructurePath("exported/village") → structures/exported/
     *
     * Validates that both pieces/<name>/ directory and large/<name>.json exist.
     */
    fun resolveLargeStructurePath(name: String): Path? {
        // Strip namespace prefix if present
        val cleanName = if (name.contains(':')) {
            name.substringAfter(':')
        } else {
            name
        }

        // Search for large structure in common output locations
        val searchDirs = listOf(
            structuresDir,                                      // Direct: structures/
            structuresDir.parent?.resolve("structures") ?: structuresDir  // Fallback
        )

        for (baseDir in searchDirs) {
            val piecesDir = baseDir.resolve("pieces").resolve(cleanName)
            val metadataFile = baseDir.resolve("large").resolve("$cleanName.json")

            // Check if both pieces directory and metadata exist
            if (piecesDir.exists() && Files.isDirectory(piecesDir) &&
                metadataFile.exists() && metadataFile.isRegularFile()) {
                return baseDir
            }
        }

        return null
    }

    /**
     * Get the NBTAPI instance for advanced operations.
     * Allows access to forEach, filter, find, some methods.
     */
    fun getNbtApi(): NBTAPI = nbtApi

    /**
     * List all unique blocks in a structure with their counts.
     * Does not include block positions or entities.
     *
     * @param structureData Structure data (from read() or World.grab())
     * @return Map of block name to count (sorted by count descending)
     *
     * Example:
     *   listBlocks(data) → {"minecraft:stone": 150, "minecraft:dirt": 80, ...}
     */
    fun listBlocks(structureData: Any): Map<String, Int> {
        if (structureData !is Map<*, *>) {
            return emptyMap()
        }

        // Get palette
        val palette = structureData["palette"] as? List<*> ?: return emptyMap()

        // Get blocks list
        val blocks = structureData["blocks"] as? List<*> ?: return emptyMap()

        // Count how many times each palette index is used
        val paletteUsage = mutableMapOf<Int, Int>()
        blocks.forEach { block ->
            if (block is Map<*, *>) {
                val stateIndex = block["state"] as? Int ?: return@forEach
                paletteUsage[stateIndex] = paletteUsage.getOrDefault(stateIndex, 0) + 1
            }
        }

        // Convert to block name → count map
        val blockCounts = mutableMapOf<String, Int>()
        paletteUsage.forEach { (paletteIndex, count) ->
            if (paletteIndex < palette.size) {
                val paletteEntry = palette[paletteIndex] as? Map<*, *> ?: return@forEach
                val blockName = paletteEntry["Name"] as? String ?: return@forEach
                blockCounts[blockName] = blockCounts.getOrDefault(blockName, 0) + count
            }
        }

        // Sort by count (descending)
        return blockCounts.toList()
            .sortedByDescending { it.second }
            .toMap()
    }

    /**
     * Replace blocks in a structure according to a replacement map.
     * Modifies the palette entries. Assumes same block states (stairs→stairs, slabs→slabs).
     *
     * @param structureData Structure data (from read() or World.grab())
     * @param replacements Map of old block name → new block name
     * @return Modified structure data with replacements applied
     *
     * Example:
     *   val replacements = mapOf(
     *       "terralith:volcanic_rock" to "minecraft:stone",
     *       "terralith:packed_mud" to "minecraft:packed_mud"
     *   )
     *   val newData = replaceBlocks(oldData, replacements)
     */
    fun replaceBlocks(structureData: Any, replacements: Map<String, String>): Any {
        if (structureData !is MutableMap<*, *>) {
            // Can't modify immutable map, return as-is
            return structureData
        }

        @Suppress("UNCHECKED_CAST")
        val mutableData = structureData as MutableMap<String, Any>

        // Get palette (mutable)
        val palette = mutableData["palette"] as? MutableList<*> ?: return structureData

        // Replace block names in palette
        palette.forEachIndexed { index, entry ->
            if (entry is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val mutableEntry = entry as MutableMap<String, Any>
                val blockName = mutableEntry["Name"] as? String ?: return@forEachIndexed

                // Check if this block should be replaced
                val newName = replacements[blockName]
                if (newName != null) {
                    mutableEntry["Name"] = newName
                }
            }
        }

        return mutableData
    }

    // ====== Helper Methods ======

    /**
     * List all blocks in a structure with their counts.
     * Returns map of block ID → count, sorted alphabetically.
     *
     * @param path Structure path (with or without .nbt extension)
     * @return Map of block ID to count
     */
    fun blocksList(path: String): Map<String, Int> {
        val structureData = nbtApi.read(ensureNbtExtension(path)) as? Map<*, *>
            ?: return emptyMap()

        return com.rhett.rhettjs.world.logic.BlockReplacer.countBlocks(structureData)
    }

    /**
     * Replace blocks in a structure according to replacement map.
     * Modifies structure file in-place (with automatic backup).
     *
     * @param path Structure path (with or without .nbt extension)
     * @param replacementMap Map of oldBlockId → newBlockId
     */
    fun blocksReplace(path: String, replacementMap: Map<String, String>) {
        val pathWithExt = ensureNbtExtension(path)

        // Read structure
        val structureData = nbtApi.read(pathWithExt) as? Map<*, *>
            ?: throw RuntimeException("Structure not found or invalid: $path")

        // Apply replacements
        val newData = com.rhett.rhettjs.world.logic.BlockReplacer.replaceBlocks(
            structureData,
            replacementMap
        )

        // Write back (with backup)
        nbtApi.write(pathWithExt, newData)
    }

    /**
     * Replace modded blocks with vanilla equivalents using duck-typing.
     * Modifies structure file in-place (with automatic backup).
     *
     * @param path Structure path (with or without .nbt extension)
     * @param typeOverrides Optional map of type → value (e.g., {"wood": "oak"})
     * @return List of warning messages for unmapped blocks
     */
    fun blocksReplaceVanilla(
        path: String,
        typeOverrides: Map<String, String>? = null
    ): List<String> {
        val pathWithExt = ensureNbtExtension(path)

        // Read structure
        val structureData = nbtApi.read(pathWithExt) as? Map<*, *>
            ?: throw RuntimeException("Structure not found or invalid: $path")

        // Get list of all blocks
        val blockCounts = com.rhett.rhettjs.world.logic.BlockReplacer.countBlocks(structureData)
        val blockIds = blockCounts.keys.toList()

        // Generate vanilla replacement map
        val woodType = typeOverrides?.get("wood") ?: "spruce"
        val result = com.rhett.rhettjs.world.logic.BlockReplacer.generateVanillaReplacementMap(
            blockIds,
            woodType
        )

        // Apply wood type overrides if provided
        val finalReplacements = if (typeOverrides != null) {
            com.rhett.rhettjs.world.logic.BlockReplacer.applyWoodTypeOverride(
                result.replacements,
                typeOverrides
            )
        } else {
            result.replacements
        }

        // Apply replacements
        val newData = com.rhett.rhettjs.world.logic.BlockReplacer.replaceBlocks(
            structureData,
            finalReplacements
        )

        // Write back (with backup)
        nbtApi.write(pathWithExt, newData)

        return result.warnings
    }

    /**
     * Ensure path has .nbt extension.
     */
    private fun ensureNbtExtension(path: String): String {
        return if (path.endsWith(".nbt")) {
            path
        } else {
            "$path.nbt"
        }
    }

    /**
     * Remove .nbt extension from path.
     */
    private fun removeNbtExtension(path: String): String {
        return if (path.endsWith(".nbt")) {
            path.substring(0, path.length - 4)
        } else {
            path
        }
    }
}
