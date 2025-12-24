package com.rhett.rhettjs.world.logic

import com.rhett.rhettjs.world.models.BlockData

/**
 * Pure rotation functions for structure placement.
 * No Minecraft types - fully testable in isolation.
 */
object RotationHelper {

    /**
     * Create a position calculator closure for grid-based structure placement.
     *
     * Given a world starting position, rotation, and piece size, returns a function
     * that calculates world coordinates for each grid piece.
     *
     * @param startPos World position to place structure origin [x, y, z]
     * @param rotation Rotation in degrees (0, 90, 180, -90/270)
     * @param pieceSize Piece dimensions [sizeX, sizeZ] or [sizeX, sizeY, sizeZ] for 3D splitting
     * @return Function that takes grid coords [gridX, gridY, gridZ] and returns world coords [x, y, z]
     *
     * Example:
     * ```
     * val calc = createPositionCalculator(
     *     startPos = intArrayOf(1000, 64, 2000),
     *     rotation = 90,
     *     pieceSize = intArrayOf(48)
     * )
     * val worldPos = calc(intArrayOf(0, 0, 1))  // Returns position for piece 0.0.1.nbt
     * ```
     *
     * Future Y-axis splitting:
     * ```
     * val calc = createPositionCalculator(
     *     startPos = intArrayOf(1000, 64, 2000),
     *     rotation = 0,
     *     pieceSize = intArrayOf(48, 48, 48)  // 3D splitting
     * )
     * val worldPos = calc(intArrayOf(0, 1, 0))  // Returns position for piece 0.1.0.nbt (Y+48)
     * ```
     */
    fun createPositionCalculator(
        startPos: IntArray,
        rotation: Int,
        pieceSize: IntArray
    ): (IntArray) -> IntArray {
        require(startPos.size == 3) { "startPos must have 3 elements [x, y, z]" }
        require(pieceSize.isNotEmpty() && pieceSize.size <= 3) { "pieceSize must have 1-3 elements" }

        val startX = startPos[0]
        val startY = startPos[1]
        val startZ = startPos[2]

        val pieceSizeX = pieceSize[0]
        val pieceSizeY = if (pieceSize.size >= 3) pieceSize[1] else 0  // 0 = no Y splitting
        val pieceSizeZ = when (pieceSize.size) {
            1 -> pieceSize[0]      // Square: [size]
            2 -> pieceSize[1]      // Rectangle: [sizeX, sizeZ]
            else -> pieceSize[2]   // 3D: [sizeX, sizeY, sizeZ]
        }

        // Normalize rotation to 0, 90, 180, 270
        val normalizedRotation = ((rotation % 360) + 360) % 360

        // Return closure that captures the rotation transform
        return { gridCoord: IntArray ->
            require(gridCoord.size == 3) { "gridCoord must have 3 elements [gridX, gridY, gridZ]" }

            val gridX = gridCoord[0]
            val gridY = gridCoord[1]
            val gridZ = gridCoord[2]

            // Calculate offset in grid space (before rotation)
            val offsetX = gridX * pieceSizeX
            val offsetY = gridY * pieceSizeY  // Y offset (0 if not splitting on Y)
            val offsetZ = gridZ * pieceSizeZ

            // Apply rotation to horizontal offsets (Y is never rotated)
            val (rotatedOffsetX, rotatedOffsetZ) = when (normalizedRotation) {
                0 -> Pair(offsetX, offsetZ)
                90 -> Pair(-offsetZ, offsetX)
                180 -> Pair(-offsetX, -offsetZ)
                270 -> Pair(offsetZ, -offsetX)
                else -> Pair(offsetX, offsetZ) // Shouldn't happen after normalization
            }

            // Calculate final world position
            val worldX = startX + rotatedOffsetX
            val worldY = startY + offsetY  // Y offset added directly (no rotation)
            val worldZ = startZ + rotatedOffsetZ

            intArrayOf(worldX, worldY, worldZ)
        }
    }

    /**
     * Create a block state rotator closure for transforming block properties.
     *
     * Given a rotation angle, returns a function that rotates block state properties
     * like facing, axis, rotation, etc.
     *
     * @param rotation Rotation in degrees (0, 90, 180, -90/270)
     * @return Function that takes BlockData and returns rotated BlockData
     *
     * Example:
     * ```
     * val rotator = createBlockStateRotator(90)
     * val rotated = rotator(BlockData("minecraft:chest", mapOf("facing" to "north")))
     * // Returns BlockData("minecraft:chest", mapOf("facing" to "east"))
     * ```
     */
    fun createBlockStateRotator(rotation: Int): (BlockData) -> BlockData {
        // Normalize rotation to 0, 90, 180, 270
        val normalizedRotation = ((rotation % 360) + 360) % 360

        // No rotation needed
        if (normalizedRotation == 0) {
            return { block -> block }
        }

        // Return closure that applies rotation to properties
        return { block: BlockData ->
            // Check if block has directional connection properties (fences, walls, glass panes)
            val hasDirectionalConnections = block.properties.keys.any {
                it == "north" || it == "south" || it == "east" || it == "west"
            }

            val rotatedProperties = if (hasDirectionalConnections) {
                // Rotate directional connection properties (north/south/east/west)
                rotateDirectionalConnections(block.properties, normalizedRotation)
            } else {
                // Standard property rotation
                block.properties.mapValues { (key, value) ->
                    when (key) {
                        "facing" -> rotateFacing(value, normalizedRotation)
                        "axis" -> rotateAxis(value, normalizedRotation)
                        "rotation" -> rotateRotationProperty(value, normalizedRotation)
                        "half" -> value  // half (top/bottom) doesn't rotate
                        "shape" -> rotateStairShape(value, normalizedRotation)
                        "type" -> value  // type properties don't rotate
                        "powered" -> value  // boolean properties don't rotate
                        "open" -> value  // boolean properties don't rotate
                        else -> value  // Unknown properties pass through unchanged
                    }
                }
            }

            BlockData(
                name = block.name,
                properties = rotatedProperties
            )
        }
    }

    /**
     * Rotate a facing property (north/south/east/west/up/down).
     * Up and down don't rotate on horizontal rotation.
     */
    private fun rotateFacing(facing: String, rotation: Int): String {
        if (facing == "up" || facing == "down") {
            return facing
        }

        return when (rotation) {
            90 -> when (facing) {
                "north" -> "east"
                "east" -> "south"
                "south" -> "west"
                "west" -> "north"
                else -> facing
            }
            180 -> when (facing) {
                "north" -> "south"
                "south" -> "north"
                "east" -> "west"
                "west" -> "east"
                else -> facing
            }
            270 -> when (facing) {
                "north" -> "west"
                "west" -> "south"
                "south" -> "east"
                "east" -> "north"
                else -> facing
            }
            else -> facing
        }
    }

    /**
     * Rotate an axis property (x/y/z).
     * Y axis doesn't rotate on horizontal rotation.
     */
    private fun rotateAxis(axis: String, rotation: Int): String {
        if (axis == "y") {
            return axis
        }

        return when (rotation) {
            90, 270 -> when (axis) {
                "x" -> "z"
                "z" -> "x"
                else -> axis
            }
            180 -> axis  // 180° rotation doesn't change x/z axes
            else -> axis
        }
    }

    /**
     * Rotate a rotation property (0-15 for banners, signs, etc.).
     * These use 16 values representing 22.5° increments.
     */
    private fun rotateRotationProperty(rotation: String, degrees: Int): String {
        val value = rotation.toIntOrNull() ?: return rotation

        // Each rotation step is 22.5° (16 steps = 360°)
        val steps = when (degrees) {
            90 -> 4    // 90° = 4 steps
            180 -> 8   // 180° = 8 steps
            270 -> 12  // 270° = 12 steps
            else -> 0
        }

        val newValue = (value + steps) % 16
        return newValue.toString()
    }

    /**
     * Rotate directional connection properties (north/south/east/west).
     * Used for fences, walls, glass panes, iron bars, etc.
     */
    private fun rotateDirectionalConnections(
        properties: Map<String, String>,
        rotation: Int
    ): Map<String, String> {
        // Get current connection values
        val north = properties["north"] ?: "false"
        val south = properties["south"] ?: "false"
        val east = properties["east"] ?: "false"
        val west = properties["west"] ?: "false"

        // Rotate the connections
        val (newNorth, newSouth, newEast, newWest) = when (rotation) {
            90 -> {
                // 90° clockwise: north→east, east→south, south→west, west→north
                Quadruple(west, east, north, south)
            }
            180 -> {
                // 180°: north↔south, east↔west
                Quadruple(south, north, west, east)
            }
            270 -> {
                // 270° clockwise: north→west, west→south, south→east, east→north
                Quadruple(east, west, south, north)
            }
            else -> {
                Quadruple(north, south, east, west)
            }
        }

        // Build new properties map with rotated connections and any other properties
        return properties.mapValues { (key, value) ->
            when (key) {
                "north" -> newNorth
                "south" -> newSouth
                "east" -> newEast
                "west" -> newWest
                else -> value  // Keep other properties unchanged
            }
        }
    }

    /**
     * Helper data class for four directional values.
     */
    private data class Quadruple<T>(val a: T, val b: T, val c: T, val d: T)

    /**
     * Rotate stair shape property (straight/inner_left/inner_right/outer_left/outer_right).
     * This is complex because shapes depend on adjacent stairs.
     */
    private fun rotateStairShape(shape: String, rotation: Int): String {
        // For 180° rotation, left/right flip
        if (rotation == 180) {
            return when (shape) {
                "inner_left" -> "inner_right"
                "inner_right" -> "inner_left"
                "outer_left" -> "outer_right"
                "outer_right" -> "outer_left"
                else -> shape
            }
        }

        // For 90° and 270° rotations, left/right stay the same
        // (because the rotation changes which side is "left")
        return shape
    }

    /**
     * Rotate relative position within a piece.
     * Used for rotating block positions before placement.
     *
     * @param x Relative X position within piece
     * @param z Relative Z position within piece
     * @param rotation Rotation in degrees (0, 90, 180, -90/270)
     * @param pieceSizeX Width of piece
     * @param pieceSizeZ Depth of piece
     * @return Rotated (x, z) position
     *
     * Note: Y coordinate is not affected by horizontal rotation.
     */
    fun rotatePosition(
        x: Int,
        z: Int,
        rotation: Int,
        structureSizeX: Int,
        structureSizeZ: Int
    ): Pair<Int, Int> {
        val normalizedRotation = ((rotation % 360) + 360) % 360

        // Rotate around origin (0,0) allowing negative coordinates for pinwheel effect
        // This makes non-centered placements extend into different quadrants based on rotation
        return when (normalizedRotation) {
            0 -> Pair(x, z)                    // +X, +Z (northeast)
            90 -> Pair(-z, x)                  // -X, +Z (northwest)
            180 -> Pair(-x, -z)                // -X, -Z (southwest)
            270 -> Pair(z, -x)                 // +X, -Z (southeast)
            else -> Pair(x, z)
        }
    }
}
