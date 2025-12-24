package com.rhett.rhettjs.worldgen

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.rhett.rhettjs.RhettJSCommon
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Generates datapack JSON files for custom dimensions.
 *
 * Creates files in rjs/data/ which is registered as a datapack source.
 */
object DatapackGenerator {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Generate pack.mcmeta for the rjs datapack.
     *
     * @param rjsDirectory The rjs/ directory
     */
    fun generatePackMeta(rjsDirectory: Path) {
        val packMeta = rjsDirectory.resolve("pack.mcmeta")

        if (packMeta.exists()) {
            return // Already exists
        }

        val json = JsonObject().apply {
            val pack = JsonObject().apply {
                addProperty("description", "RhettJS Generated Datapack")
                addProperty("pack_format", 48) // 1.21.1 format
            }
            add("pack", pack)
        }

        packMeta.writeText(gson.toJson(json))
        RhettJSCommon.LOGGER.info("[RhettJS] Created pack.mcmeta for RhettJS datapack")
    }

    /**
     * Generate dimension JSON files from registered dimensions.
     *
     * @param rjsDirectory The rjs/ directory
     * @param dimensions Map of dimensions to generate
     */
    fun generateDimensions(rjsDirectory: Path, dimensions: Map<*, DimensionRegistry.DimensionConfig>) {
        val dataDir = rjsDirectory.resolve("data")

        // Clear old dimension files
        dimensions.values.forEach { config ->
            val namespace = config.namespace
            val dimensionDir = dataDir.resolve(namespace).resolve("dimension")
            val dimensionTypeDir = dataDir.resolve(namespace).resolve("dimension_type")

            // Create directories
            Files.createDirectories(dimensionDir)
            Files.createDirectories(dimensionTypeDir)

            // Generate dimension type JSON
            generateDimensionType(dimensionTypeDir, config)

            // Generate dimension JSON
            generateDimension(dimensionDir, config)
        }

        RhettJSCommon.LOGGER.info("[RhettJS] Generated ${dimensions.size} dimension(s) in datapack")
    }

    /**
     * Generate dimension_type JSON file.
     */
    private fun generateDimensionType(dir: Path, config: DimensionRegistry.DimensionConfig) {
        val file = dir.resolve("${config.name}.json")

        val json = JsonObject().apply {
            // Ambient light level (0.0 to 1.0)
            addProperty("ambient_light", config.ambientLight)

            // Whether the dimension has a fixed time
            config.fixedTime?.let {
                addProperty("fixed_time", it)
            }

            // Infiniburn tag (what burns forever)
            addProperty("infiniburn", "#minecraft:infiniburn_overworld")

            // Logical height (affects sky rendering)
            addProperty("logical_height", config.maxY - config.minY)

            // Min and max Y
            addProperty("min_y", config.minY)
            addProperty("height", config.maxY - config.minY)

            // Natural spawning
            addProperty("natural", config.spawning)

            // Piglin safe (whether piglins zombify)
            addProperty("piglin_safe", false)

            // Bed works
            addProperty("bed_works", config.bed)

            // Respawn anchor works
            addProperty("respawn_anchor_works", config.respawnAnchor)

            // Has skylight
            addProperty("has_skylight", config.sky)

            // Has ceiling
            addProperty("has_ceiling", !config.sky)

            // Ultrawarm (nether-like)
            addProperty("ultrawarm", false)

            // Has raids
            addProperty("has_raids", false)

            // Coordinate scale
            addProperty("coordinate_scale", config.coordinateScale)

            // Monster spawn light level
            val monsterSpawnLightLevel = JsonObject().apply {
                addProperty("type", "minecraft:uniform")
                val value = JsonObject().apply {
                    addProperty("min_inclusive", 0)
                    addProperty("max_inclusive", 7)
                }
                add("value", value)
            }
            add("monster_spawn_light_level", monsterSpawnLightLevel)

            // Monster spawn block light limit
            addProperty("monster_spawn_block_light_limit", 0)

            // Effects (visual style)
            val effects = when (config.type) {
                DimensionRegistry.DimensionType.NETHER -> "minecraft:the_nether"
                DimensionRegistry.DimensionType.END -> "minecraft:the_end"
                else -> "minecraft:overworld"
            }
            addProperty("effects", effects)
        }

        file.writeText(gson.toJson(json))
        RhettJSCommon.LOGGER.debug("[RhettJS] Generated dimension_type: ${config.name}")
    }

    /**
     * Generate dimension JSON file.
     */
    private fun generateDimension(dir: Path, config: DimensionRegistry.DimensionConfig) {
        val file = dir.resolve("${config.name}.json")

        val json = JsonObject().apply {
            // Type reference
            addProperty("type", "${config.namespace}:${config.name}")

            // Generator
            val generator = JsonObject().apply {
                when (config.generator) {
                    DimensionRegistry.GeneratorType.FLAT -> {
                        addProperty("type", "minecraft:flat")

                        val settings = JsonObject().apply {
                            // Biome
                            addProperty("biome", "minecraft:${config.biome}")

                            // Layers (empty for void)
                            add("layers", gson.toJsonTree(emptyList<Any>()))

                            // Lakes (none)
                            addProperty("lakes", false)

                            // Features (none)
                            addProperty("features", false)

                            // Structures
                            val structures = JsonObject().apply {
                                add("structures", JsonObject())
                            }
                            add("structure_overrides", structures)
                        }
                        add("settings", settings)
                    }
                    DimensionRegistry.GeneratorType.VOID -> {
                        // Flat generator with no layers = void
                        addProperty("type", "minecraft:flat")

                        val settings = JsonObject().apply {
                            addProperty("biome", "minecraft:${config.biome}")
                            add("layers", gson.toJsonTree(emptyList<Any>()))
                            addProperty("lakes", false)
                            addProperty("features", false)
                        }
                        add("settings", settings)
                    }
                }
            }
            add("generator", generator)
        }

        file.writeText(gson.toJson(json))
        RhettJSCommon.LOGGER.debug("[RhettJS] Generated dimension: ${config.name}")
    }

    /**
     * Clean up dimension files that are no longer registered.
     *
     * @param rjsDirectory The rjs/ directory
     * @param registeredNames Set of dimension names that should exist
     */
    fun cleanupUnregisteredDimensions(rjsDirectory: Path, registeredNames: Set<String>) {
        val dataDir = rjsDirectory.resolve("data")

        if (!dataDir.exists()) {
            return
        }

        // Find all namespace directories
        Files.list(dataDir).use { namespaces ->
            namespaces.forEach { namespaceDir ->
                if (!Files.isDirectory(namespaceDir)) return@forEach

                // Check dimension directory
                val dimensionDir = namespaceDir.resolve("dimension")
                if (dimensionDir.exists()) {
                    Files.list(dimensionDir).use { files ->
                        files.forEach { file ->
                            val name = file.fileName.toString().removeSuffix(".json")
                            if (name !in registeredNames) {
                                Files.delete(file)
                                RhettJSCommon.LOGGER.info("[RhettJS] Cleaned up dimension: $name")
                            }
                        }
                    }
                }

                // Check dimension_type directory
                val dimensionTypeDir = namespaceDir.resolve("dimension_type")
                if (dimensionTypeDir.exists()) {
                    Files.list(dimensionTypeDir).use { files ->
                        files.forEach { file ->
                            val name = file.fileName.toString().removeSuffix(".json")
                            if (name !in registeredNames) {
                                Files.delete(file)
                                RhettJSCommon.LOGGER.info("[RhettJS] Cleaned up dimension_type: $name")
                            }
                        }
                    }
                }
            }
        }
    }
}
