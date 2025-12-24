package com.rhett.rhettjs.world.logic

/**
 * Business logic for block listing and replacement in structures.
 * Pure functions - no Minecraft types, no side effects.
 */
object BlockReplacer {

    /**
     * Count blocks in structure data.
     * Returns map of block ID → count (sorted alphabetically by ID).
     *
     * @param structureData Structure NBT as Map
     * @return Map of block ID to count
     */
    fun countBlocks(structureData: Map<*, *>): Map<String, Int> {
        val palette = structureData["palette"] as? List<*> ?: return emptyMap()
        val blocks = structureData["blocks"] as? List<*> ?: return emptyMap()

        // Count how many times each palette state is used
        val stateCounts = mutableMapOf<Int, Int>()
        blocks.forEach { blockEntry ->
            val blockMap = blockEntry as? Map<*, *> ?: return@forEach
            val stateIndex = (blockMap["state"] as? Number)?.toInt() ?: return@forEach
            stateCounts[stateIndex] = stateCounts.getOrDefault(stateIndex, 0) + 1
        }

        // Map palette index → block name
        val blockCounts = mutableMapOf<String, Int>()
        palette.forEachIndexed { index, paletteEntry ->
            val entryMap = paletteEntry as? Map<*, *> ?: return@forEachIndexed
            val blockName = entryMap["Name"] as? String ?: return@forEachIndexed

            val count = stateCounts[index] ?: 0
            if (count > 0) {
                blockCounts[blockName] = blockCounts.getOrDefault(blockName, 0) + count
            }
        }

        // Return sorted by block name
        return blockCounts.toSortedMap()
    }

    /**
     * Replace blocks in structure data according to replacement map.
     * Returns new structure data with replacements applied.
     *
     * @param structureData Structure NBT as Map
     * @param replacementMap Map of oldBlockId → newBlockId
     * @return New structure data with replacements applied
     */
    fun replaceBlocks(
        structureData: Map<*, *>,
        replacementMap: Map<String, String>
    ): Map<String, Any> {
        val palette = structureData["palette"] as? List<*> ?: return structureData.toMap()

        // Create new palette with replacements
        val newPalette = palette.map { paletteEntry ->
            val entryMap = paletteEntry as? Map<*, *> ?: return@map paletteEntry
            val blockName = entryMap["Name"] as? String ?: return@map paletteEntry

            // Check if this block should be replaced
            val replacement = replacementMap[blockName]
            if (replacement != null) {
                // Replace block name, keep properties
                val mutableEntry = entryMap.toMutableMap()
                mutableEntry["Name"] = replacement
                mutableEntry
            } else {
                entryMap
            }
        }

        // Build new structure data
        val result = mutableMapOf<String, Any>()
        structureData.forEach { (k, v) ->
            result[k.toString()] = v ?: Unit
        }
        result["palette"] = newPalette
        return result
    }

    /**
     * Generate vanilla replacement map for modded blocks using duck-typing.
     * Detects block types by keywords and maps to vanilla equivalents.
     *
     * @param blockIds List of block IDs to check
     * @param woodType Wood type for replacements (default: "spruce")
     * @return Map of moddedBlockId → vanillaBlockId
     */
    fun generateVanillaReplacementMap(
        blockIds: List<String>,
        woodType: String = "spruce"
    ): VanillaReplacementResult {
        val replacements = mutableMapOf<String, String>()
        val warnings = mutableListOf<String>()

        blockIds.forEach { blockId ->
            // Skip vanilla blocks
            if (blockId.startsWith("minecraft:")) {
                return@forEach
            }

            // Extract just the block name (after namespace:)
            val blockName = blockId.substringAfter(":")

            // Duck-type detection (order matters - check specific before generic)
            val replacement = when {
                blockName.contains("stair") -> "minecraft:cobblestone_stairs"
                blockName.contains("slab") -> "minecraft:cobblestone_slab"
                blockName.contains("leaves") -> "minecraft:${woodType}_leaves"
                blockName.contains("log") -> "minecraft:${woodType}_log"
                blockName.contains("wood") && !blockName.contains("_wood") -> "minecraft:${woodType}_wood"
                blockName.contains("planks") -> "minecraft:${woodType}_planks"
                blockName.contains("door") && !blockName.contains("trapdoor") -> "minecraft:${woodType}_door"
                blockName.contains("trapdoor") -> "minecraft:${woodType}_trapdoor"
                blockName.contains("fence_gate") -> "minecraft:${woodType}_fence_gate"
                blockName.contains("fence") -> "minecraft:${woodType}_fence"
                blockName.contains("button") -> "minecraft:${woodType}_button"
                blockName.contains("pressure_plate") -> "minecraft:${woodType}_pressure_plate"
                blockName.contains("sign") && !blockName.contains("hanging") -> "minecraft:${woodType}_sign"
                blockName.contains("hanging_sign") -> "minecraft:${woodType}_hanging_sign"
                else -> {
                    // Unmappable - warn and replace with air
                    warnings.add("Could not confidently replace $blockId → air")
                    "minecraft:air"
                }
            }

            replacements[blockId] = replacement
        }

        return VanillaReplacementResult(
            replacements = replacements,
            warnings = warnings
        )
    }

    /**
     * Apply wood type override to replacement map.
     * Replaces default wood type with user-specified type in all values.
     *
     * @param replacementMap Original replacement map
     * @param woodTypeOverride Map of category → type (e.g., {"wood": "oak"})
     * @return New replacement map with overrides applied
     */
    fun applyWoodTypeOverride(
        replacementMap: Map<String, String>,
        woodTypeOverride: Map<String, String>
    ): Map<String, String> {
        val newWoodType = woodTypeOverride["wood"] ?: return replacementMap

        // Replace "spruce" with new wood type in all values
        return replacementMap.mapValues { (_, vanillaBlock) ->
            vanillaBlock.replace("spruce", newWoodType)
        }
    }

    /**
     * Result of vanilla replacement generation.
     */
    data class VanillaReplacementResult(
        val replacements: Map<String, String>,
        val warnings: List<String>
    )

    /**
     * Helper to convert Map<*, *> to Map<String, Any>
     */
    private fun Map<*, *>.toMap(): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return this.mapKeys { it.key.toString() }.mapValues { it.value as Any }
    }
}
