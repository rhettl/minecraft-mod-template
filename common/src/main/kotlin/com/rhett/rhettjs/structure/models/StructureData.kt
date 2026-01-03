package com.rhett.rhettjs.structure.models

import com.rhett.rhettjs.world.models.PositionedBlock

/**
 * Internal model representing a captured structure.
 * This is the anti-corruption layer between Minecraft NBT structures and JavaScript.
 */
data class StructureData(
    /**
     * Size of the structure (width, height, depth)
     */
    val size: StructureSize,

    /**
     * List of blocks in the structure (relative coordinates)
     */
    val blocks: List<PositionedBlock>,

    /**
     * Structure metadata (author, description, etc.)
     */
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Structure dimensions
 */
data class StructureSize(
    val x: Int,
    val y: Int,
    val z: Int
)
