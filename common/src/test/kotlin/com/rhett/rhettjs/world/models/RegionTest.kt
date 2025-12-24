package com.rhett.rhettjs.world.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit tests for Region model.
 * Tests coordinate normalization and size calculations.
 */
class RegionTest {

    @Test
    fun `fromCorners normalizes coordinates correctly`() {
        // Give corners in reverse order
        val region = Region.fromCorners(10, 20, 30, 5, 10, 15)

        assertEquals(5, region.minX)
        assertEquals(10, region.minY)
        assertEquals(15, region.minZ)
        assertEquals(10, region.maxX)
        assertEquals(20, region.maxY)
        assertEquals(30, region.maxZ)
    }

    @Test
    fun `fromCorners handles identical corners`() {
        val region = Region.fromCorners(5, 10, 15, 5, 10, 15)

        assertEquals(5, region.minX)
        assertEquals(10, region.minY)
        assertEquals(15, region.minZ)
        assertEquals(5, region.maxX)
        assertEquals(10, region.maxY)
        assertEquals(15, region.maxZ)
    }

    @Test
    fun `size calculations are correct`() {
        val region = Region(0, 0, 0, 9, 4, 19)

        assertEquals(10, region.sizeX)  // 9 - 0 + 1
        assertEquals(5, region.sizeY)   // 4 - 0 + 1
        assertEquals(20, region.sizeZ)  // 19 - 0 + 1
    }

    @Test
    fun `size calculations with negative coordinates`() {
        val region = Region(-5, -10, -15, 5, 10, 15)

        assertEquals(11, region.sizeX)  // 5 - (-5) + 1
        assertEquals(21, region.sizeY)  // 10 - (-10) + 1
        assertEquals(31, region.sizeZ)  // 15 - (-15) + 1
    }

    @Test
    fun `single block region has size 1x1x1`() {
        val region = Region(0, 0, 0, 0, 0, 0)

        assertEquals(1, region.sizeX)
        assertEquals(1, region.sizeY)
        assertEquals(1, region.sizeZ)
    }
}
