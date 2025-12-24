package com.rhett.rhettjs.world.logic

import com.rhett.rhettjs.world.models.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for StructureBuilder.
 * Tests pure business logic with no Minecraft dependencies.
 * Easy to write, fast to run, high coverage.
 */
class StructureBuilderTest {

    @Test
    fun `validateSingleStructureSize accepts valid sizes`() {
        // 48x48x48 is the maximum valid size
        val region = Region(0, 0, 0, 47, 47, 47)
        assertDoesNotThrow {
            StructureBuilder.validateSingleStructureSize(region)
        }
    }

    @Test
    fun `validateSingleStructureSize rejects oversized X`() {
        val region = Region(0, 0, 0, 48, 47, 47)
        val exception = assertThrows<IllegalArgumentException> {
            StructureBuilder.validateSingleStructureSize(region)
        }
        assertTrue(exception.message!!.contains("too large"))
        assertTrue(exception.message!!.contains("49x48x48"))
    }

    @Test
    fun `validateSingleStructureSize rejects oversized Y`() {
        val region = Region(0, 0, 0, 47, 48, 47)
        val exception = assertThrows<IllegalArgumentException> {
            StructureBuilder.validateSingleStructureSize(region)
        }
        assertTrue(exception.message!!.contains("48x49x48"))
    }

    @Test
    fun `validateSingleStructureSize rejects oversized Z`() {
        val region = Region(0, 0, 0, 47, 47, 48)
        val exception = assertThrows<IllegalArgumentException> {
            StructureBuilder.validateSingleStructureSize(region)
        }
        assertTrue(exception.message!!.contains("48x48x49"))
    }

    @Test
    fun `buildPalette creates unique entries`() {
        val blocks = listOf(
            PositionedBlock(0, 0, 0, BlockData("minecraft:stone")),
            PositionedBlock(1, 0, 0, BlockData("minecraft:stone")),  // Duplicate
            PositionedBlock(2, 0, 0, BlockData("minecraft:dirt"))
        )

        val (palette, indexMap) = StructureBuilder.buildPalette(blocks)

        assertEquals(2, palette.size, "Should have 2 unique blocks")
        assertEquals(2, indexMap.size, "Index map should have 2 entries")

        assertEquals("minecraft:stone", palette[0]["Name"])
        assertEquals("minecraft:dirt", palette[1]["Name"])
    }

    @Test
    fun `buildPalette includes block properties`() {
        val blocks = listOf(
            PositionedBlock(
                0, 0, 0,
                BlockData("minecraft:oak_stairs", mapOf("facing" to "north", "half" to "bottom"))
            )
        )

        val (palette, _) = StructureBuilder.buildPalette(blocks)

        assertEquals(1, palette.size)
        assertEquals("minecraft:oak_stairs", palette[0]["Name"])
        assertTrue(palette[0].containsKey("Properties"))

        @Suppress("UNCHECKED_CAST")
        val properties = palette[0]["Properties"] as Map<String, String>
        assertEquals("north", properties["facing"])
        assertEquals("bottom", properties["half"])
    }

    @Test
    fun `buildStructureData creates correct structure format`() {
        val region = Region(10, 64, 20, 11, 65, 21)
        val blocks = listOf(
            PositionedBlock(10, 64, 20, BlockData("minecraft:stone")),
            PositionedBlock(11, 65, 21, BlockData("minecraft:dirt"))
        )

        val structureData = StructureBuilder.buildStructureData(region, blocks, emptyList())

        // Check data version
        assertEquals(3953, structureData["DataVersion"])

        // Check size
        @Suppress("UNCHECKED_CAST")
        val size = structureData["size"] as List<Int>
        assertEquals(listOf(2, 2, 2), size)

        // Check palette
        @Suppress("UNCHECKED_CAST")
        val palette = structureData["palette"] as List<Map<String, Any>>
        assertEquals(2, palette.size)

        // Check blocks
        @Suppress("UNCHECKED_CAST")
        val blockList = structureData["blocks"] as List<Map<String, Any>>
        assertEquals(2, blockList.size)

        // Verify first block
        assertEquals(0, blockList[0]["state"])
        @Suppress("UNCHECKED_CAST")
        val pos0 = blockList[0]["pos"] as List<Int>
        assertEquals(listOf(0, 0, 0), pos0)  // Relative to region origin

        // Verify second block
        assertEquals(1, blockList[1]["state"])
        @Suppress("UNCHECKED_CAST")
        val pos1 = blockList[1]["pos"] as List<Int>
        assertEquals(listOf(1, 1, 1), pos1)  // Relative to region origin
    }

    @Test
    fun `buildStructureData includes entities`() {
        val region = Region(0, 0, 0, 1, 1, 1)
        val entities = listOf(
            PositionedEntity(
                x = 0.5, y = 0.5, z = 0.5,
                blockX = 0, blockY = 0, blockZ = 0,
                entityData = mapOf("id" to "minecraft:painting", "variant" to "kebab")
            )
        )

        val structureData = StructureBuilder.buildStructureData(region, emptyList(), entities)

        @Suppress("UNCHECKED_CAST")
        val entityList = structureData["entities"] as List<Map<String, Any>>
        assertEquals(1, entityList.size)

        val entity = entityList[0]
        @Suppress("UNCHECKED_CAST")
        val pos = entity["pos"] as List<Double>
        assertEquals(listOf(0.5, 0.5, 0.5), pos)

        @Suppress("UNCHECKED_CAST")
        val nbt = entity["nbt"] as Map<String, Any>
        assertEquals("minecraft:painting", nbt["id"])
    }

    @Test
    fun `splitIntoGrid calculates correct grid size`() {
        val region = Region(0, 0, 0, 99, 10, 99)  // 100x11x100
        val pieces = StructureBuilder.splitIntoGrid(region, 48, 48)

        // Should be 3x3 grid (ceil(100/48) = 3)
        assertEquals(9, pieces.size)

        // Check grid coordinates
        val coords = pieces.map { it.first }.toSet()
        assertTrue(coords.contains(GridCoordinate(x = 0, z = 0)))
        assertTrue(coords.contains(GridCoordinate(x = 2, z = 2)))
    }

    @Test
    fun `splitIntoGrid creates correct piece regions`() {
        val region = Region(0, 0, 0, 99, 10, 99)
        val pieces = StructureBuilder.splitIntoGrid(region, 48, 48)

        // Find piece (0, 0)
        val piece00 = pieces.first { it.first == GridCoordinate(x = 0, z = 0) }.second
        assertEquals(0, piece00.minX)
        assertEquals(47, piece00.maxX)
        assertEquals(0, piece00.minZ)
        assertEquals(47, piece00.maxZ)
        assertEquals(0, piece00.minY)
        assertEquals(10, piece00.maxY)

        // Find piece (2, 2) - should be smaller (remainder)
        val piece22 = pieces.first { it.first == GridCoordinate(x = 2, z = 2) }.second
        assertEquals(96, piece22.minX)
        assertEquals(99, piece22.maxX)  // Only 4 blocks wide
        assertEquals(96, piece22.minZ)
        assertEquals(99, piece22.maxZ)
    }

    @Test
    fun `splitIntoGrid handles exact multiples`() {
        val region = Region(0, 0, 0, 95, 10, 47)  // 96x11x48 (exactly 2x1 grid)
        val pieces = StructureBuilder.splitIntoGrid(region, 48, 48)

        assertEquals(2, pieces.size)
        assertTrue(pieces.any { it.first == GridCoordinate(x = 0, z = 0) })
        assertTrue(pieces.any { it.first == GridCoordinate(x = 1, z = 0) })
    }

    @Test
    fun `createLargeStructureMetadata calculates correct values`() {
        val region = Region(0, 64, 0, 99, 74, 99)  // 100x11x100
        val metadata = StructureBuilder.createLargeStructureMetadata(
            name = "test_structure",
            region = region,
            pieceSizeX = 48,
            pieceSizeZ = 48,
            requiredMods = listOf("mod1", "mod2")
        )

        assertEquals("test_structure", metadata.name)
        assertEquals(48, metadata.pieceSizeX)
        assertEquals(48, metadata.pieceSizeZ)
        assertEquals(3, metadata.gridSizeX)  // ceil(100/48) = 3
        assertEquals(3, metadata.gridSizeZ)
        assertEquals(100, metadata.totalSizeX)
        assertEquals(11, metadata.totalSizeY)
        assertEquals(100, metadata.totalSizeZ)
        assertEquals(listOf("mod1", "mod2"), metadata.requiredMods)
    }

    @Test
    fun `GridCoordinate toFilename formats correctly`() {
        assertEquals("0.0.0.nbt", GridCoordinate(x = 0, z = 0).toFilename())
        assertEquals("5.0.12.nbt", GridCoordinate(x = 5, z = 12).toFilename())
        assertEquals("5.3.12.nbt", GridCoordinate(x = 5, y = 3, z = 12).toFilename())
    }

    // ====== Rotation Helper Tests ======

    @Test
    fun `calculateGridPiecePositions with rotation 0 degrees`() {
        val positions = StructureBuilder.calculateGridPiecePositions(
            originX = 100, originY = 64, originZ = 200,
            rotation = 0,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("0.0.0", "1.0.0", "0.0.1", "1.0.1")
        )

        assertEquals(4, positions.size)

        // No rotation: X stays X, Z stays Z
        assertEquals(Triple(100, 64, 200), positions["0.0.0"])  // Origin
        assertEquals(Triple(148, 64, 200), positions["1.0.0"])  // +48 in X
        assertEquals(Triple(100, 64, 248), positions["0.0.1"])  // +48 in Z
        assertEquals(Triple(148, 64, 248), positions["1.0.1"])  // +48 in both
    }

    @Test
    fun `calculateGridPiecePositions with rotation 90 degrees`() {
        val positions = StructureBuilder.calculateGridPiecePositions(
            originX = 100, originY = 64, originZ = 200,
            rotation = 90,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("0.0.0", "1.0.0", "0.0.1", "1.0.1")
        )

        assertEquals(4, positions.size)

        // 90° rotation: X→-Z, Z→X
        assertEquals(Triple(100, 64, 200), positions["0.0.0"])  // Origin unchanged
        assertEquals(Triple(100, 64, 248), positions["1.0.0"])  // X→Z: was +48 X, now +48 Z
        assertEquals(Triple(52, 64, 200), positions["0.0.1"])   // Z→-X: was +48 Z, now -48 X
        assertEquals(Triple(52, 64, 248), positions["1.0.1"])   // Both rotated
    }

    @Test
    fun `calculateGridPiecePositions with rotation 180 degrees`() {
        val positions = StructureBuilder.calculateGridPiecePositions(
            originX = 100, originY = 64, originZ = 200,
            rotation = 180,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("0.0.0", "1.0.0", "0.0.1", "1.0.1")
        )

        assertEquals(4, positions.size)

        // 180° rotation: X→-X, Z→-Z
        assertEquals(Triple(100, 64, 200), positions["0.0.0"])  // Origin unchanged
        assertEquals(Triple(52, 64, 200), positions["1.0.0"])   // X→-X: -48 X
        assertEquals(Triple(100, 64, 152), positions["0.0.1"])  // Z→-Z: -48 Z
        assertEquals(Triple(52, 64, 152), positions["1.0.1"])   // Both inverted
    }

    @Test
    fun `calculateGridPiecePositions with rotation 270 degrees`() {
        val positions = StructureBuilder.calculateGridPiecePositions(
            originX = 100, originY = 64, originZ = 200,
            rotation = 270,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("0.0.0", "1.0.0", "0.0.1", "1.0.1")
        )

        assertEquals(4, positions.size)

        // 270° rotation: X→Z, Z→-X
        assertEquals(Triple(100, 64, 200), positions["0.0.0"])  // Origin unchanged
        assertEquals(Triple(100, 64, 152), positions["1.0.0"])  // X→-Z: was +48 X, now -48 Z
        assertEquals(Triple(148, 64, 200), positions["0.0.1"])  // Z→X: was +48 Z, now +48 X
        assertEquals(Triple(148, 64, 152), positions["1.0.1"])  // Both rotated
    }

    @Test
    fun `calculateGridPiecePositions with negative rotation normalizes`() {
        // -90 should be same as 270
        val positions1 = StructureBuilder.calculateGridPiecePositions(
            originX = 100, originY = 64, originZ = 200,
            rotation = -90,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("1.0.0")
        )

        val positions2 = StructureBuilder.calculateGridPiecePositions(
            originX = 100, originY = 64, originZ = 200,
            rotation = 270,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("1.0.0")
        )

        assertEquals(positions2["1.0.0"], positions1["1.0.0"])
    }

    @Test
    fun `calculateGridPiecePositions with rotation over 360 normalizes`() {
        // 450 (360 + 90) should be same as 90
        val positions1 = StructureBuilder.calculateGridPiecePositions(
            originX = 100, originY = 64, originZ = 200,
            rotation = 450,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("1.0.0")
        )

        val positions2 = StructureBuilder.calculateGridPiecePositions(
            originX = 100, originY = 64, originZ = 200,
            rotation = 90,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("1.0.0")
        )

        assertEquals(positions2["1.0.0"], positions1["1.0.0"])
    }

    @Test
    fun `calculateGridPiecePositions with non-square pieces`() {
        val positions = StructureBuilder.calculateGridPiecePositions(
            originX = 0, originY = 64, originZ = 0,
            rotation = 0,
            pieceSizeX = 64, pieceSizeZ = 32,
            gridCoordinates = listOf("0.0.0", "1.0.0", "0.0.1")
        )

        assertEquals(Triple(0, 64, 0), positions["0.0.0"])
        assertEquals(Triple(64, 64, 0), positions["1.0.0"])    // 64-wide piece
        assertEquals(Triple(0, 64, 32), positions["0.0.1"])    // 32-deep piece
    }

    @Test
    fun `calculateGridPiecePositions ignores invalid grid coordinates`() {
        val positions = StructureBuilder.calculateGridPiecePositions(
            originX = 0, originY = 64, originZ = 0,
            rotation = 0,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("0.0.0", "invalid", "1.x.0", "")
        )

        // Should only process valid coordinate
        assertEquals(1, positions.size)
        assertTrue(positions.containsKey("0.0.0"))
    }

    @Test
    fun `calculateGridPiecePositions with Y coordinate in grid`() {
        // Y coordinate exists but should be ignored in position calculation (Y=0 reserved)
        val positions = StructureBuilder.calculateGridPiecePositions(
            originX = 100, originY = 64, originZ = 200,
            rotation = 0,
            pieceSizeX = 48, pieceSizeZ = 48,
            gridCoordinates = listOf("0.5.0", "1.5.1")  // Y=5, but Y stays at originY
        )

        assertEquals(2, positions.size)

        // Y coordinate in grid doesn't affect world Y (stays at originY=64)
        assertEquals(Triple(100, 64, 200), positions["0.5.0"])
        assertEquals(Triple(148, 64, 248), positions["1.5.1"])
    }
}
