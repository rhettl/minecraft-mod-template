package com.rhett.rhettjs.world.logic

import com.rhett.rhettjs.world.models.*
import kotlin.math.ceil

/**
 * Business logic for building structure data from world blocks.
 * Pure functions - no Minecraft types, no side effects.
 * All inputs/outputs are internal models.
 */
object StructureBuilder {

    /**
     * Build a Minecraft structure NBT data map from blocks.
     * Pure function - testable without Minecraft.
     *
     * @param region The region bounds
     * @param blocks List of blocks in the region
     * @param entities List of entities in the region
     * @return Structure data as Map (compatible with Structure.write())
     */
    fun buildStructureData(
        region: Region,
        blocks: List<PositionedBlock>,
        entities: List<PositionedEntity> = emptyList()
    ): Map<String, Any> {
        // Build palette (unique block states)
        val (palette, stateIndexMap) = buildPalette(blocks)

        // Build block placements list
        val blockPlacements = blocks.map { positioned ->
            val stateIndex = stateIndexMap[positioned.block] ?: 0

            // Calculate position relative to region origin
            val relX = positioned.x - region.minX
            val relY = positioned.y - region.minY
            val relZ = positioned.z - region.minZ

            val placement = mutableMapOf<String, Any>(
                "state" to stateIndex,
                "pos" to listOf(relX, relY, relZ)
            )

            // Add block entity data if present
            positioned.blockEntityData?.let { nbt ->
                placement["nbt"] = nbt
            }

            placement
        }

        // Build entity list
        val entityList = entities.map { entity ->
            // Calculate relative positions
            val relX = entity.x - region.minX
            val relY = entity.y - region.minY
            val relZ = entity.z - region.minZ
            val relBlockX = entity.blockX - region.minX
            val relBlockY = entity.blockY - region.minY
            val relBlockZ = entity.blockZ - region.minZ

            mapOf(
                "pos" to listOf(relX, relY, relZ),
                "blockPos" to listOf(relBlockX, relBlockY, relBlockZ),
                "nbt" to entity.entityData
            )
        }

        // Build final structure data
        return mapOf(
            "DataVersion" to 3953, // Minecraft 1.21.1 data version
            "size" to listOf(region.sizeX, region.sizeY, region.sizeZ),
            "palette" to palette,
            "blocks" to blockPlacements,
            "entities" to entityList
        )
    }

    /**
     * Build a palette of unique block states.
     * Returns the palette list and a map from BlockData to palette index.
     *
     * Pure function - testable.
     */
    fun buildPalette(blocks: List<PositionedBlock>): Pair<List<Map<String, Any>>, Map<BlockData, Int>> {
        val palette = mutableListOf<Map<String, Any>>()
        val stateIndexMap = mutableMapOf<BlockData, Int>()

        blocks.forEach { positioned ->
            val blockData = positioned.block

            // Add to palette if not already present
            if (blockData !in stateIndexMap) {
                val paletteEntry = mutableMapOf<String, Any>(
                    "Name" to blockData.name
                )

                // Add properties if present
                if (blockData.properties.isNotEmpty()) {
                    paletteEntry["Properties"] = blockData.properties
                }

                palette.add(paletteEntry)
                stateIndexMap[blockData] = palette.size - 1
            }
        }

        return Pair(palette, stateIndexMap)
    }

    /**
     * Validate region size against structure block limits.
     * Pure function - testable.
     *
     * @throws IllegalArgumentException if region exceeds limits
     */
    fun validateSingleStructureSize(region: Region) {
        val maxSize = 48

        if (region.sizeX > maxSize || region.sizeY > maxSize || region.sizeZ > maxSize) {
            throw IllegalArgumentException(
                "Region too large: ${region.sizeX}x${region.sizeY}x${region.sizeZ}. " +
                "Maximum size is ${maxSize}x${maxSize}x${maxSize}. " +
                "Use grabLarge() for larger structures."
            )
        }
    }

    /**
     * Split a region into grid pieces for large structures.
     * Pure function - testable.
     *
     * @param pieceSizeY Optional Y piece size (null = no vertical splitting)
     * @return List of (GridCoordinate, Region) pairs
     */
    fun splitIntoGrid(
        region: Region,
        pieceSizeX: Int,
        pieceSizeZ: Int,
        pieceSizeY: Int? = null
    ): List<Pair<GridCoordinate, Region>> {
        val gridSizeX = ceil(region.sizeX.toDouble() / pieceSizeX).toInt()
        val gridSizeZ = ceil(region.sizeZ.toDouble() / pieceSizeZ).toInt()
        val gridSizeY = if (pieceSizeY != null) {
            ceil(region.sizeY.toDouble() / pieceSizeY).toInt()
        } else {
            1
        }

        val pieces = mutableListOf<Pair<GridCoordinate, Region>>()

        for (gridX in 0 until gridSizeX) {
            for (gridY in 0 until gridSizeY) {
                for (gridZ in 0 until gridSizeZ) {
                    val pieceMinX = region.minX + (gridX * pieceSizeX)
                    val pieceMaxX = minOf(pieceMinX + pieceSizeX - 1, region.maxX)

                    val pieceMinY = if (pieceSizeY != null) {
                        region.minY + (gridY * pieceSizeY)
                    } else {
                        region.minY
                    }
                    val pieceMaxY = if (pieceSizeY != null) {
                        minOf(pieceMinY + pieceSizeY - 1, region.maxY)
                    } else {
                        region.maxY
                    }

                    val pieceMinZ = region.minZ + (gridZ * pieceSizeZ)
                    val pieceMaxZ = minOf(pieceMinZ + pieceSizeZ - 1, region.maxZ)

                    val pieceRegion = Region(
                        minX = pieceMinX,
                        minY = pieceMinY,
                        minZ = pieceMinZ,
                        maxX = pieceMaxX,
                        maxY = pieceMaxY,
                        maxZ = pieceMaxZ
                    )

                    pieces.add(Pair(GridCoordinate(x = gridX, y = gridY, z = gridZ), pieceRegion))
                }
            }
        }

        return pieces
    }

    /**
     * Create metadata for a large structure.
     * Pure function - testable.
     */
    fun createLargeStructureMetadata(
        name: String,
        region: Region,
        pieceSizeX: Int,
        pieceSizeZ: Int,
        pieceSizeY: Int? = null,
        requiredMods: List<String> = emptyList()
    ): LargeStructureMetadata {
        val gridSizeX = ceil(region.sizeX.toDouble() / pieceSizeX).toInt()
        val gridSizeZ = ceil(region.sizeZ.toDouble() / pieceSizeZ).toInt()
        val gridSizeY = if (pieceSizeY != null) {
            ceil(region.sizeY.toDouble() / pieceSizeY).toInt()
        } else {
            1
        }

        return LargeStructureMetadata(
            name = name,
            pieceSizeX = pieceSizeX,
            pieceSizeZ = pieceSizeZ,
            pieceSizeY = pieceSizeY,
            gridSizeX = gridSizeX,
            gridSizeZ = gridSizeZ,
            gridSizeY = gridSizeY,
            totalSizeX = region.sizeX,
            totalSizeY = region.sizeY,
            totalSizeZ = region.sizeZ,
            requiredMods = requiredMods
        )
    }

    /**
     * Calculate world positions for grid pieces with rotation applied.
     * Pure function - testable.
     *
     * @param originX Starting X position in world
     * @param originY Starting Y position in world
     * @param originZ Starting Z position in world
     * @param rotation Rotation angle (0, 90, 180, 270 degrees)
     * @param pieceSizeX Width of each piece
     * @param pieceSizeZ Depth of each piece
     * @param gridCoordinates List of grid coordinates (e.g., ["0.0.0", "1.0.0", "0.0.1"])
     * @return Map of grid coordinate string to world position triple (x, y, z)
     *
     * Example:
     *   calculateGridPiecePositions(100, 64, 200, 0, 48, 48, ["0.0.0", "1.0.0", "0.0.1"])
     *   → {"0.0.0" to Triple(100, 64, 200),
     *      "1.0.0" to Triple(148, 64, 200),
     *      "0.0.1" to Triple(100, 64, 248)}
     */
    fun calculateGridPiecePositions(
        originX: Int,
        originY: Int,
        originZ: Int,
        rotation: Int,
        pieceSizeX: Int,
        pieceSizeZ: Int,
        gridCoordinates: List<String>
    ): Map<String, Triple<Int, Int, Int>> {
        // Normalize rotation to 0, 90, 180, 270
        val normalizedRotation = ((rotation % 360) + 360) % 360

        val positions = mutableMapOf<String, Triple<Int, Int, Int>>()

        gridCoordinates.forEach { coordString ->
            // Parse grid coordinate (e.g., "1.0.2" → x=1, y=0, z=2)
            val parts = coordString.split('.')
            if (parts.size != 3) return@forEach

            val gridX = parts[0].toIntOrNull() ?: return@forEach
            val gridY = parts[1].toIntOrNull() ?: return@forEach
            val gridZ = parts[2].toIntOrNull() ?: return@forEach

            // Calculate offset in grid space (before rotation)
            val offsetX = gridX * pieceSizeX
            val offsetZ = gridZ * pieceSizeZ

            // Apply rotation to offset
            val (rotatedOffsetX, rotatedOffsetZ) = when (normalizedRotation) {
                0 -> Pair(offsetX, offsetZ)
                90 -> Pair(-offsetZ, offsetX)
                180 -> Pair(-offsetX, -offsetZ)
                270 -> Pair(offsetZ, -offsetX)
                else -> Pair(offsetX, offsetZ) // Shouldn't happen after normalization
            }

            // Calculate final world position
            val worldX = originX + rotatedOffsetX
            val worldY = originY
            val worldZ = originZ + rotatedOffsetZ

            positions[coordString] = Triple(worldX, worldY, worldZ)
        }

        return positions
    }
}
