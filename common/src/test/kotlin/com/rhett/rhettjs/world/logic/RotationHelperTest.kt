package com.rhett.rhettjs.world.logic

import com.rhett.rhettjs.world.models.BlockData
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class RotationHelperTest {

    @Nested
    inner class PositionCalculatorTests {

        @Test
        fun `createPositionCalculator with 0 degree rotation`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(1000, 64, 2000),
                rotation = 0,
                pieceSize = intArrayOf(48)
            )

            // Origin piece
            val pos1 = calc(intArrayOf(0, 0, 0))
            assertEquals(intArrayOf(1000, 64, 2000).toList(), pos1.toList())

            // Piece to the right (X+)
            val pos2 = calc(intArrayOf(1, 0, 0))
            assertEquals(intArrayOf(1048, 64, 2000).toList(), pos2.toList())

            // Piece forward (Z+)
            val pos3 = calc(intArrayOf(0, 0, 1))
            assertEquals(intArrayOf(1000, 64, 2048).toList(), pos3.toList())

            // Diagonal piece
            val pos4 = calc(intArrayOf(2, 0, 2))
            assertEquals(intArrayOf(1096, 64, 2096).toList(), pos4.toList())
        }

        @Test
        fun `createPositionCalculator with 90 degree rotation`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(1000, 64, 2000),
                rotation = 90,
                pieceSize = intArrayOf(48)
            )

            // Origin piece
            val pos1 = calc(intArrayOf(0, 0, 0))
            assertEquals(intArrayOf(1000, 64, 2000).toList(), pos1.toList())

            // Piece that was to the right (X+) is now forward (Z+)
            // offsetX=48, offsetZ=0 → rotated: offsetX=0, offsetZ=48
            val pos2 = calc(intArrayOf(1, 0, 0))
            assertEquals(intArrayOf(1000, 64, 2048).toList(), pos2.toList())

            // Piece that was forward (Z+) is now to the left (X-)
            // offsetX=0, offsetZ=48 → rotated: offsetX=-48, offsetZ=0
            val pos3 = calc(intArrayOf(0, 0, 1))
            assertEquals(intArrayOf(952, 64, 2000).toList(), pos3.toList())

            // Diagonal piece (2, 0, 2)
            // offsetX=96, offsetZ=96 → rotated: offsetX=-96, offsetZ=96
            val pos4 = calc(intArrayOf(2, 0, 2))
            assertEquals(intArrayOf(904, 64, 2096).toList(), pos4.toList())
        }

        @Test
        fun `createPositionCalculator with 180 degree rotation`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(1000, 64, 2000),
                rotation = 180,
                pieceSize = intArrayOf(48)
            )

            // Origin piece
            val pos1 = calc(intArrayOf(0, 0, 0))
            assertEquals(intArrayOf(1000, 64, 2000).toList(), pos1.toList())

            // Piece to the right (X+) is now to the left (X-)
            // offsetX=48, offsetZ=0 → rotated: offsetX=-48, offsetZ=0
            val pos2 = calc(intArrayOf(1, 0, 0))
            assertEquals(intArrayOf(952, 64, 2000).toList(), pos2.toList())

            // Piece forward (Z+) is now backward (Z-)
            // offsetX=0, offsetZ=48 → rotated: offsetX=0, offsetZ=-48
            val pos3 = calc(intArrayOf(0, 0, 1))
            assertEquals(intArrayOf(1000, 64, 1952).toList(), pos3.toList())

            // Diagonal piece flips
            val pos4 = calc(intArrayOf(2, 0, 2))
            assertEquals(intArrayOf(904, 64, 1904).toList(), pos4.toList())
        }

        @Test
        fun `createPositionCalculator with 270 degree rotation`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(1000, 64, 2000),
                rotation = 270,
                pieceSize = intArrayOf(48)
            )

            // Origin piece
            val pos1 = calc(intArrayOf(0, 0, 0))
            assertEquals(intArrayOf(1000, 64, 2000).toList(), pos1.toList())

            // Piece to the right (X+) is now backward (Z-)
            // offsetX=48, offsetZ=0 → rotated: offsetX=0, offsetZ=-48
            val pos2 = calc(intArrayOf(1, 0, 0))
            assertEquals(intArrayOf(1000, 64, 1952).toList(), pos2.toList())

            // Piece forward (Z+) is now to the right (X+)
            // offsetX=0, offsetZ=48 → rotated: offsetX=48, offsetZ=0
            val pos3 = calc(intArrayOf(0, 0, 1))
            assertEquals(intArrayOf(1048, 64, 2000).toList(), pos3.toList())
        }

        @Test
        fun `createPositionCalculator with negative rotation is normalized`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(1000, 64, 2000),
                rotation = -90,
                pieceSize = intArrayOf(48)
            )

            // -90 should be equivalent to 270
            val pos = calc(intArrayOf(1, 0, 0))
            assertEquals(intArrayOf(1000, 64, 1952).toList(), pos.toList())
        }

        @Test
        fun `createPositionCalculator with rectangular pieces`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(0, 0, 0),
                rotation = 0,
                pieceSize = intArrayOf(48, 32)  // 48x32 pieces
            )

            // Piece to the right (X+)
            val pos1 = calc(intArrayOf(1, 0, 0))
            assertEquals(intArrayOf(48, 0, 0).toList(), pos1.toList())

            // Piece forward (Z+) uses different size
            val pos2 = calc(intArrayOf(0, 0, 1))
            assertEquals(intArrayOf(0, 0, 32).toList(), pos2.toList())
        }

        @Test
        fun `createPositionCalculator with 3D piece size for Y splitting`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(1000, 64, 2000),
                rotation = 0,
                pieceSize = intArrayOf(48, 48, 48)  // [sizeX, sizeY, sizeZ]
            )

            // Origin piece
            val pos1 = calc(intArrayOf(0, 0, 0))
            assertEquals(intArrayOf(1000, 64, 2000).toList(), pos1.toList())

            // Piece above (Y+)
            val pos2 = calc(intArrayOf(0, 1, 0))
            assertEquals(intArrayOf(1000, 112, 2000).toList(), pos2.toList())

            // Two levels above
            val pos3 = calc(intArrayOf(0, 2, 0))
            assertEquals(intArrayOf(1000, 160, 2000).toList(), pos3.toList())

            // Diagonal in 3D space
            val pos4 = calc(intArrayOf(1, 1, 1))
            assertEquals(intArrayOf(1048, 112, 2048).toList(), pos4.toList())
        }

        @Test
        fun `createPositionCalculator with 3D piece size and rotation`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(1000, 64, 2000),
                rotation = 90,
                pieceSize = intArrayOf(48, 32, 48)  // [sizeX, sizeY, sizeZ]
            )

            // Origin piece
            val pos1 = calc(intArrayOf(0, 0, 0))
            assertEquals(intArrayOf(1000, 64, 2000).toList(), pos1.toList())

            // Piece to the right rotates to forward
            val pos2 = calc(intArrayOf(1, 0, 0))
            assertEquals(intArrayOf(1000, 64, 2048).toList(), pos2.toList())

            // Piece above (Y doesn't rotate)
            val pos3 = calc(intArrayOf(0, 1, 0))
            assertEquals(intArrayOf(1000, 96, 2000).toList(), pos3.toList())

            // Piece above and to the right
            val pos4 = calc(intArrayOf(1, 1, 0))
            assertEquals(intArrayOf(1000, 96, 2048).toList(), pos4.toList())

            // Full 3D diagonal with rotation
            val pos5 = calc(intArrayOf(2, 3, 2))
            assertEquals(intArrayOf(904, 160, 2096).toList(), pos5.toList())
        }

        @Test
        fun `Y offset is zero when not using 3D piece size`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(1000, 64, 2000),
                rotation = 0,
                pieceSize = intArrayOf(48, 48)  // Only 2 elements (no Y splitting)
            )

            // Y coordinate should never change
            val pos1 = calc(intArrayOf(0, 0, 0))
            assertEquals(64, pos1[1])

            val pos2 = calc(intArrayOf(0, 1, 0))
            assertEquals(64, pos2[1])  // gridY=1 but Y doesn't change

            val pos3 = calc(intArrayOf(0, 5, 0))
            assertEquals(64, pos3[1])  // gridY=5 but Y doesn't change
        }

        @Test
        fun `createPositionCalculator validates input sizes`() {
            assertThrows<IllegalArgumentException> {
                RotationHelper.createPositionCalculator(
                    startPos = intArrayOf(1000, 64),  // Only 2 elements
                    rotation = 0,
                    pieceSize = intArrayOf(48)
                )
            }

            assertThrows<IllegalArgumentException> {
                RotationHelper.createPositionCalculator(
                    startPos = intArrayOf(1000, 64, 2000),
                    rotation = 0,
                    pieceSize = intArrayOf()  // Empty
                )
            }

            assertThrows<IllegalArgumentException> {
                RotationHelper.createPositionCalculator(
                    startPos = intArrayOf(1000, 64, 2000),
                    rotation = 0,
                    pieceSize = intArrayOf(48, 48, 48, 48)  // Too many elements
                )
            }
        }

        @Test
        fun `position calculator validates grid coord size`() {
            val calc = RotationHelper.createPositionCalculator(
                startPos = intArrayOf(0, 0, 0),
                rotation = 0,
                pieceSize = intArrayOf(48)
            )

            assertThrows<IllegalArgumentException> {
                calc(intArrayOf(0, 0))  // Only 2 elements
            }
        }
    }

    @Nested
    inner class BlockStateRotatorTests {

        @Test
        fun `createBlockStateRotator with 0 degree rotation returns unchanged`() {
            val rotator = RotationHelper.createBlockStateRotator(0)

            val block = BlockData(
                name = "minecraft:chest",
                properties = mapOf("facing" to "north")
            )

            val rotated = rotator(block)
            assertEquals(block, rotated)
        }

        @Test
        fun `rotate facing property 90 degrees`() {
            val rotator = RotationHelper.createBlockStateRotator(90)

            val tests = listOf(
                "north" to "east",
                "east" to "south",
                "south" to "west",
                "west" to "north",
                "up" to "up",      // Up doesn't rotate
                "down" to "down"   // Down doesn't rotate
            )

            tests.forEach { (input, expected) ->
                val block = BlockData("minecraft:chest", mapOf("facing" to input))
                val rotated = rotator(block)
                assertEquals(expected, rotated.properties["facing"], "Failed for facing=$input")
            }
        }

        @Test
        fun `rotate facing property 180 degrees`() {
            val rotator = RotationHelper.createBlockStateRotator(180)

            val tests = listOf(
                "north" to "south",
                "south" to "north",
                "east" to "west",
                "west" to "east",
                "up" to "up",
                "down" to "down"
            )

            tests.forEach { (input, expected) ->
                val block = BlockData("minecraft:chest", mapOf("facing" to input))
                val rotated = rotator(block)
                assertEquals(expected, rotated.properties["facing"])
            }
        }

        @Test
        fun `rotate facing property 270 degrees`() {
            val rotator = RotationHelper.createBlockStateRotator(270)

            val tests = listOf(
                "north" to "west",
                "west" to "south",
                "south" to "east",
                "east" to "north"
            )

            tests.forEach { (input, expected) ->
                val block = BlockData("minecraft:chest", mapOf("facing" to input))
                val rotated = rotator(block)
                assertEquals(expected, rotated.properties["facing"])
            }
        }

        @Test
        fun `rotate axis property 90 degrees`() {
            val rotator = RotationHelper.createBlockStateRotator(90)

            val tests = listOf(
                "x" to "z",
                "z" to "x",
                "y" to "y"  // Y doesn't rotate
            )

            tests.forEach { (input, expected) ->
                val block = BlockData("minecraft:log", mapOf("axis" to input))
                val rotated = rotator(block)
                assertEquals(expected, rotated.properties["axis"])
            }
        }

        @Test
        fun `rotate axis property 180 degrees`() {
            val rotator = RotationHelper.createBlockStateRotator(180)

            // 180° rotation doesn't change x/z axes
            val tests = listOf(
                "x" to "x",
                "z" to "z",
                "y" to "y"
            )

            tests.forEach { (input, expected) ->
                val block = BlockData("minecraft:log", mapOf("axis" to input))
                val rotated = rotator(block)
                assertEquals(expected, rotated.properties["axis"])
            }
        }

        @Test
        fun `rotate rotation property for signs and banners`() {
            val rotator = RotationHelper.createBlockStateRotator(90)

            // 90° = 4 steps (16 steps = 360°, so 4 steps = 90°)
            val tests = listOf(
                "0" to "4",
                "4" to "8",
                "12" to "0",  // Wraps around
                "15" to "3"
            )

            tests.forEach { (input, expected) ->
                val block = BlockData("minecraft:banner", mapOf("rotation" to input))
                val rotated = rotator(block)
                assertEquals(expected, rotated.properties["rotation"])
            }
        }

        @Test
        fun `rotate stair shape property 180 degrees flips left and right`() {
            val rotator = RotationHelper.createBlockStateRotator(180)

            val tests = listOf(
                "inner_left" to "inner_right",
                "inner_right" to "inner_left",
                "outer_left" to "outer_right",
                "outer_right" to "outer_left",
                "straight" to "straight"
            )

            tests.forEach { (input, expected) ->
                val block = BlockData("minecraft:stairs", mapOf("shape" to input))
                val rotated = rotator(block)
                assertEquals(expected, rotated.properties["shape"])
            }
        }

        @Test
        fun `rotate stair shape property 90 degrees keeps left and right`() {
            val rotator = RotationHelper.createBlockStateRotator(90)

            val tests = listOf(
                "inner_left" to "inner_left",
                "inner_right" to "inner_right",
                "outer_left" to "outer_left",
                "outer_right" to "outer_right",
                "straight" to "straight"
            )

            tests.forEach { (input, expected) ->
                val block = BlockData("minecraft:stairs", mapOf("shape" to input))
                val rotated = rotator(block)
                assertEquals(expected, rotated.properties["shape"])
            }
        }

        @Test
        fun `unknown properties pass through unchanged`() {
            val rotator = RotationHelper.createBlockStateRotator(90)

            val block = BlockData(
                name = "minecraft:chest",
                properties = mapOf(
                    "facing" to "north",
                    "waterlogged" to "true",
                    "powered" to "false",
                    "custom_prop" to "value"
                )
            )

            val rotated = rotator(block)
            assertEquals("east", rotated.properties["facing"])
            assertEquals("true", rotated.properties["waterlogged"])
            assertEquals("false", rotated.properties["powered"])
            assertEquals("value", rotated.properties["custom_prop"])
        }

        @Test
        fun `block name is preserved`() {
            val rotator = RotationHelper.createBlockStateRotator(90)

            val block = BlockData(
                name = "minecraft:oak_stairs",
                properties = mapOf("facing" to "north")
            )

            val rotated = rotator(block)
            assertEquals("minecraft:oak_stairs", rotated.name)
        }
    }

    @Nested
    inner class RotatePositionTests {

        @Test
        fun `rotate position 0 degrees returns unchanged`() {
            val (x, z) = RotationHelper.rotatePosition(5, 10, 0, 48, 48)
            assertEquals(5, x)
            assertEquals(10, z)
        }

        @Test
        fun `rotate position 90 degrees in square piece`() {
            // 90° rotation: (x, z) → (-z, x) for pinwheel effect
            // (0, 0) → (0, 0) - origin stays at origin
            val (x1, z1) = RotationHelper.rotatePosition(0, 0, 90, 48, 48)
            assertEquals(0, x1)
            assertEquals(0, z1)

            // (47, 0) → (0, 47) - far edge rotates
            val (x2, z2) = RotationHelper.rotatePosition(47, 0, 90, 48, 48)
            assertEquals(0, x2)
            assertEquals(47, z2)

            // (0, 47) → (-47, 0) - extends into negative X
            val (x3, z3) = RotationHelper.rotatePosition(0, 47, 90, 48, 48)
            assertEquals(-47, x3)
            assertEquals(0, z3)

            // Center point
            val (x4, z4) = RotationHelper.rotatePosition(24, 24, 90, 48, 48)
            assertEquals(-24, x4)
            assertEquals(24, z4)
        }

        @Test
        fun `rotate position 180 degrees`() {
            // 180° rotation: (x, z) → (-x, -z) for pinwheel effect
            // (0, 0) → (0, 0) - origin stays at origin
            val (x1, z1) = RotationHelper.rotatePosition(0, 0, 180, 48, 48)
            assertEquals(0, x1)
            assertEquals(0, z1)

            // (47, 47) → (-47, -47) - far corner extends into negative quadrant
            val (x2, z2) = RotationHelper.rotatePosition(47, 47, 180, 48, 48)
            assertEquals(-47, x2)
            assertEquals(-47, z2)
        }

        @Test
        fun `rotate position 270 degrees`() {
            // 270° rotation: (x, z) → (z, -x) for pinwheel effect
            // (0, 0) → (0, 0) - origin stays at origin
            val (x1, z1) = RotationHelper.rotatePosition(0, 0, 270, 48, 48)
            assertEquals(0, x1)
            assertEquals(0, z1)

            // (47, 0) → (0, -47) - extends into negative Z
            val (x2, z2) = RotationHelper.rotatePosition(47, 0, 270, 48, 48)
            assertEquals(0, x2)
            assertEquals(-47, z2)
        }

        @Test
        fun `rotate position in rectangular piece`() {
            // In a 48x32 piece, 90° rotation: (x, z) → (-z, x)
            // (0, 0) → (0, 0) - origin stays at origin
            val (x, z) = RotationHelper.rotatePosition(0, 0, 90, 48, 32)
            assertEquals(0, x)
            assertEquals(0, z)

            // (0, 31) → (-31, 0) - extends into negative X
            val (x2, z2) = RotationHelper.rotatePosition(0, 31, 90, 48, 32)
            assertEquals(-31, x2)
            assertEquals(0, z2)
        }
    }
}
