package com.rhett.rhettjs.worldgen

import com.rhett.rhettjs.RhettJSCommon
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.dimension.LevelStem

/**
 * Registry for custom dimensions created via JavaScript.
 *
 * Dimensions registered here are:
 * - Ephemeral: Only exist when script registers them
 * - Runtime: No datapack JSON files created
 * - Persistent data: World save data (region files) persists even when unregistered
 *
 * When a script is commented out or removed, the dimension becomes inaccessible
 * but the world data remains intact. Re-registering the dimension will load existing data.
 */
object DimensionRegistry {

    private val registeredDimensions = mutableMapOf<ResourceKey<Level>, DimensionConfig>()

    /**
     * Configuration for a custom dimension.
     */
    data class DimensionConfig(
        val name: String,              // e.g., "structure-test"
        val namespace: String = "rhettjs",
        val type: DimensionType,       // OVERWORLD, NETHER, END
        val biome: String,             // e.g., "plains"
        val generator: GeneratorType,  // FLAT, VOID, NOISE, DEBUG
        val sky: Boolean = true,
        val ambientLight: Float = 1.0f,  // 0.0 to 1.0, full brightness = 1.0
        val fixedTime: Long? = null,   // null = normal cycle, 6000 = noon, 18000 = midnight
        val weather: Boolean = false,
        val respawnAnchor: Boolean = true,
        val bed: Boolean = true,
        val coordinateScale: Double = 1.0,
        val spawning: Boolean = false,
        val minY: Int = -64,
        val maxY: Int = 320,
        // Generator-specific settings
        val generatorSettings: GeneratorSettings? = null
    )

    /**
     * Generator-specific settings.
     */
    data class GeneratorSettings(
        val layers: List<Layer>? = null,           // For flat generator
        val features: Boolean = false,              // Generate features
        val lakes: Boolean = false,                 // Generate lakes
        val structureOverrides: Map<String, Any>? = null  // Structure configuration
    )

    /**
     * Layer definition for flat generator.
     */
    data class Layer(
        val block: String,   // e.g., "minecraft:stone"
        val height: Int      // Number of blocks
    )

    enum class DimensionType {
        OVERWORLD,
        NETHER,
        END
    }

    enum class GeneratorType {
        FLAT,   // Flat world with customizable layers
        VOID,   // Complete void (no layers)
        NOISE,  // Noise-based terrain generation
        DEBUG   // Debug world type
    }

    /**
     * Register a dimension from JavaScript configuration.
     *
     * @param config Map of configuration from JavaScript
     */
    fun registerDimension(config: Map<String, Any>) {
        try {
            val dimConfig = parseDimensionConfig(config)
            val resourceKey = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(dimConfig.namespace, dimConfig.name)
            )

            registeredDimensions[resourceKey] = dimConfig
            RhettJSCommon.LOGGER.info("[RhettJS] Registered dimension: ${dimConfig.namespace}:${dimConfig.name}")

        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to register dimension", e)
            throw e
        }
    }

    /**
     * Parse JavaScript config map into DimensionConfig.
     */
    private fun parseDimensionConfig(config: Map<String, Any>): DimensionConfig {
        val name = config["name"]?.toString()
            ?: throw IllegalArgumentException("Dimension config missing 'name'")

        val namespace = config["namespace"]?.toString() ?: "rhettjs"

        val type = when (config["type"]?.toString()?.lowercase()) {
            "nether" -> DimensionType.NETHER
            "end", "the_end" -> DimensionType.END
            else -> DimensionType.OVERWORLD
        }

        val biome = config["biome"]?.toString() ?: "plains"

        val generator = when (config["generator"]?.toString()?.lowercase()) {
            "void" -> GeneratorType.VOID
            "noise" -> GeneratorType.NOISE
            "debug" -> GeneratorType.DEBUG
            else -> GeneratorType.FLAT
        }

        val sky = config["sky"]?.let { it as? Boolean } ?: true
        val ambientLight = (config["ambientLight"] as? Number)?.toFloat() ?: 1.0f
        val fixedTime = (config["fixedTime"] as? Number)?.toLong()
        val weather = config["weather"]?.let { it as? Boolean } ?: false
        val respawnAnchor = config["respawnAnchor"]?.let { it as? Boolean } ?: true
        val bed = config["bed"]?.let { it as? Boolean } ?: true
        val coordinateScale = (config["scale"] as? Number)?.toDouble() ?: 1.0
        val spawning = config["spawning"]?.let { it as? Boolean } ?: false
        val minY = (config["minY"] as? Number)?.toInt() ?: -64
        val maxY = (config["maxY"] as? Number)?.toInt() ?: 320

        // Parse generator settings
        val generatorSettings = config["generatorSettings"]?.let { parseGeneratorSettings(it) }

        return DimensionConfig(
            name = name,
            namespace = namespace,
            type = type,
            biome = biome,
            generator = generator,
            sky = sky,
            ambientLight = ambientLight.coerceIn(0.0f, 1.0f),
            fixedTime = fixedTime,
            weather = weather,
            respawnAnchor = respawnAnchor,
            bed = bed,
            coordinateScale = coordinateScale,
            spawning = spawning,
            minY = minY,
            maxY = maxY,
            generatorSettings = generatorSettings
        )
    }

    /**
     * Parse generator settings from JavaScript.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseGeneratorSettings(settings: Any): GeneratorSettings {
        if (settings !is Map<*, *>) {
            throw IllegalArgumentException("generatorSettings must be an object")
        }
        val settingsMap = settings as Map<String, Any>

        // Parse layers
        val layers = settingsMap["layers"]?.let { layersObj ->
            if (layersObj !is List<*>) {
                throw IllegalArgumentException("layers must be an array")
            }
            layersObj.map { layerObj ->
                if (layerObj !is Map<*, *>) {
                    throw IllegalArgumentException("Each layer must be an object")
                }
                val layerMap = layerObj as Map<String, Any>
                Layer(
                    block = layerMap["block"]?.toString()
                        ?: throw IllegalArgumentException("Layer missing 'block'"),
                    height = (layerMap["height"] as? Number)?.toInt()
                        ?: throw IllegalArgumentException("Layer missing 'height'")
                )
            }
        }

        val features = settingsMap["features"]?.let { it as? Boolean } ?: false
        val lakes = settingsMap["lakes"]?.let { it as? Boolean } ?: false
        val structureOverrides = settingsMap["structureOverrides"] as? Map<String, Any>

        return GeneratorSettings(
            layers = layers,
            features = features,
            lakes = lakes,
            structureOverrides = structureOverrides
        )
    }

    /**
     * Get all registered dimension configs.
     */
    fun getRegisteredDimensions(): Map<ResourceKey<Level>, DimensionConfig> {
        return registeredDimensions.toMap()
    }

    /**
     * Clear all registered dimensions.
     * Called on reload to allow re-registration.
     */
    fun clear() {
        registeredDimensions.clear()
        RhettJSCommon.LOGGER.info("[RhettJS] Cleared dimension registry")
    }

    /**
     * Platform-specific: Actually register dimensions with Minecraft.
     * Must be implemented in Fabric/NeoForge modules.
     *
     * @param server The Minecraft server instance
     */
    fun applyRegistrations(server: MinecraftServer) {
        // Platform-specific implementation will be called from Fabric/NeoForge
        RhettJSCommon.LOGGER.info("[RhettJS] Applying ${registeredDimensions.size} dimension registrations")
    }
}
