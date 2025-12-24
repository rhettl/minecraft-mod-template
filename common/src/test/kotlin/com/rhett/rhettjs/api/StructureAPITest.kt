package com.rhett.rhettjs.api

import com.rhett.rhettjs.config.ConfigManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * TDD tests for Structure API.
 * High-level wrapper around NBT API for Minecraft structures.
 */
class StructureAPITest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var structureApi: StructureAPI
    private lateinit var structuresDir: Path

    @BeforeEach
    fun setup() {
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        ConfigManager.init(configDir)

        structuresDir = tempDir.resolve("structures")
        Files.createDirectories(structuresDir)

        structureApi = StructureAPI(structuresDir, tempDir.resolve("backups/structures"))
    }

    // ====== List Tests ======

    @Test
    fun `test list all structures`() {
        // Create test structure files
        Files.createDirectories(structuresDir.resolve("village/houses"))
        Files.createDirectories(structuresDir.resolve("desert/temples"))

        structuresDir.resolve("village/houses/house_1.nbt").writeText("")
        structuresDir.resolve("village/houses/house_2.nbt").writeText("")
        structuresDir.resolve("desert/temples/temple_1.nbt").writeText("")

        val structures = structureApi.list()

        assertEquals(3, structures.size, "Should find 3 structures")
        assertTrue(structures.contains("village/houses/house_1"))
        assertTrue(structures.contains("village/houses/house_2"))
        assertTrue(structures.contains("desert/temples/temple_1"))
    }

    @Test
    fun `test list structures in specific pool`() {
        Files.createDirectories(structuresDir.resolve("village/houses"))
        Files.createDirectories(structuresDir.resolve("desert/temples"))

        structuresDir.resolve("village/houses/house_1.nbt").writeText("")
        structuresDir.resolve("village/houses/house_2.nbt").writeText("")
        structuresDir.resolve("desert/temples/temple_1.nbt").writeText("")

        val villageStructures = structureApi.list("village")

        assertEquals(2, villageStructures.size, "Should find 2 village structures")
        assertTrue(villageStructures.contains("village/houses/house_1"))
        assertTrue(villageStructures.contains("village/houses/house_2"))
        assertFalse(villageStructures.contains("desert/temples/temple_1"))
    }

    @Test
    fun `test list returns empty for nonexistent pool`() {
        val structures = structureApi.list("nonexistent")
        assertEquals(0, structures.size, "Should return empty list for nonexistent pool")
    }

    @Test
    fun `test list excludes backup files`() {
        Files.createDirectories(structuresDir.resolve("village"))

        structuresDir.resolve("village/house_1.nbt").writeText("")
        structuresDir.resolve("village/house_1.nbt.bak").writeText("")

        val structures = structureApi.list()

        assertEquals(1, structures.size, "Should exclude .bak files")
        assertTrue(structures.contains("village/house_1"))
    }

    // ====== Read Tests ======

    @Test
    fun `test read structure by name`() {
        Files.createDirectories(structuresDir.resolve("village"))

        val testData = mapOf(
            "size" to listOf(16, 5, 16),
            "palette" to listOf(mapOf("Name" to "minecraft:stone"))
        )

        // Use NBT API to create a valid structure file
        val nbtApi = NBTAPI(structuresDir, tempDir.resolve("backups"))
        nbtApi.write("village/house_1.nbt", testData)

        // Read using Structure API (no .nbt extension needed)
        val data = structureApi.read("village/house_1")

        assertNotNull(data)
        assertTrue(data is Map<*, *>)

        val map = data as Map<*, *>
        assertTrue(map.containsKey("size"))
        assertTrue(map.containsKey("palette"))
    }

    @Test
    fun `test read nonexistent structure returns null`() {
        val data = structureApi.read("nonexistent/structure")
        assertNull(data, "Reading nonexistent structure should return null")
    }

    @Test
    fun `test read structure with nbt extension works`() {
        Files.createDirectories(structuresDir.resolve("village"))

        val testData = mapOf("name" to "test")
        val nbtApi = NBTAPI(structuresDir, tempDir.resolve("backups"))
        nbtApi.write("village/house_1.nbt", testData)

        // Should work with or without .nbt extension
        val data1 = structureApi.read("village/house_1")
        val data2 = structureApi.read("village/house_1.nbt")

        assertNotNull(data1)
        assertNotNull(data2)
    }

    // ====== Write Tests ======

    @Test
    fun `test write structure by name`() {
        val testData = mapOf(
            "size" to listOf(16, 5, 16),
            "palette" to listOf(mapOf("Name" to "minecraft:stone"))
        )

        structureApi.write("village/house_1", testData)

        val file = structuresDir.resolve("village/house_1.nbt")
        assertTrue(file.exists(), "Structure file should be created")
    }

    @Test
    fun `test write creates parent directories`() {
        val testData = mapOf("name" to "test")

        structureApi.write("village/houses/common/house_1", testData)

        val file = structuresDir.resolve("village/houses/common/house_1.nbt")
        assertTrue(file.exists(), "Structure file should be created")
        assertTrue(file.parent.exists(), "Parent directories should be created")
    }

    @Test
    fun `test write with nbt extension works`() {
        val testData = mapOf("name" to "test")

        // Should work with or without .nbt extension
        structureApi.write("village/house_1.nbt", testData)

        val file = structuresDir.resolve("village/house_1.nbt")
        assertTrue(file.exists(), "Structure file should be created")
    }

    @Test
    fun `test write and read roundtrip`() {
        val originalData = mapOf(
            "size" to listOf(16, 5, 16),
            "palette" to listOf(
                mapOf("Name" to "minecraft:stone"),
                mapOf("Name" to "minecraft:dirt")
            ),
            "entities" to listOf(
                mapOf(
                    "blockPos" to listOf(5, 2, 8),
                    "pos" to listOf(5.5, 2.5, 8.03),
                    "nbt" to mapOf(
                        "id" to "minecraft:painting",
                        "facing" to 2,
                        "variant" to "kebab"
                    )
                )
            )
        )

        structureApi.write("test/structure", originalData)
        val readData = structureApi.read("test/structure")

        assertNotNull(readData)
        assertTrue(readData is Map<*, *>)

        val map = readData as Map<*, *>
        assertEquals(3, (map["size"] as List<*>).size)
        assertEquals(2, (map["palette"] as List<*>).size)
        assertEquals(1, (map["entities"] as List<*>).size)
    }

    // ====== Path Validation Tests ======

    @Test
    fun `test write rejects parent directory traversal`() {
        val testData = mapOf("name" to "test")

        assertThrows(SecurityException::class.java) {
            structureApi.write("../../../etc/passwd", testData)
        }
    }

    @Test
    fun `test read rejects parent directory traversal`() {
        assertThrows(SecurityException::class.java) {
            structureApi.read("../../../etc/passwd")
        }
    }

    // ====== Backup Tests ======

    @Test
    fun `test write creates backup when overwriting`() {
        val originalData = mapOf("version" to 1)
        val updatedData = mapOf("version" to 2)

        structureApi.write("test/structure", originalData)
        structureApi.write("test/structure", updatedData)

        // Check that at least one backup file was created (with timestamp)
        val backupDir = tempDir.resolve("backups/structures/test")
        val hasBackup = if (backupDir.exists()) {
            Files.walk(backupDir, 1)
                .filter { it.fileName.toString().startsWith("structure.nbt.") }
                .filter { it.fileName.toString().endsWith(".bak") }
                .findFirst()
                .isPresent
        } else {
            false
        }

        assertTrue(hasBackup, "Backup should be created")
    }

    // ====== Edge Cases ======

    @Test
    fun `test list handles empty structures directory`() {
        val structures = structureApi.list()
        assertEquals(0, structures.size, "Should return empty list for empty directory")
    }

    @Test
    fun `test list handles deep directory structures`() {
        Files.createDirectories(structuresDir.resolve("a/b/c/d"))
        structuresDir.resolve("a/b/c/d/structure.nbt").writeText("")

        val structures = structureApi.list()
        assertEquals(1, structures.size)
        assertTrue(structures.contains("a/b/c/d/structure"))
    }

    @Test
    fun `test structure name normalization`() {
        val testData = mapOf("name" to "test")

        // All these should refer to the same file
        structureApi.write("village/house_1", testData)

        assertNotNull(structureApi.read("village/house_1"))
        assertNotNull(structureApi.read("village/house_1.nbt"))

        val file = structuresDir.resolve("village/house_1.nbt")
        assertEquals(1, Files.walk(structuresDir).filter { it.toString().endsWith(".nbt") }.count())
    }

    // ====== Path Resolver Tests ======

    @Test
    fun `test resolveStructurePath with direct path`() {
        // Create test structure
        Files.createDirectories(structuresDir.resolve("custom"))
        structuresDir.resolve("custom/castle.nbt").writeText("")

        val resolved = structureApi.resolveStructurePath("custom/castle")

        assertNotNull(resolved, "Should resolve direct path")
        assertTrue(resolved!!.exists(), "Resolved path should exist")
        assertTrue(resolved.toString().endsWith("custom/castle.nbt"))
    }

    @Test
    fun `test resolveStructurePath with captured directory`() {
        // Create test structure in captured/
        Files.createDirectories(structuresDir.resolve("captured"))
        structuresDir.resolve("captured/house.nbt").writeText("")

        val resolved = structureApi.resolveStructurePath("house")

        assertNotNull(resolved, "Should resolve from captured directory")
        assertTrue(resolved!!.exists(), "Resolved path should exist")
        assertTrue(resolved.toString().endsWith("captured/house.nbt"))
    }

    @Test
    fun `test resolveStructurePath with namespace prefix`() {
        // Create test structure
        Files.createDirectories(structuresDir.resolve("captured"))
        structuresDir.resolve("captured/temple.nbt").writeText("")

        val resolved = structureApi.resolveStructurePath("minecraft:temple")

        assertNotNull(resolved, "Should strip namespace and resolve")
        assertTrue(resolved!!.exists(), "Resolved path should exist")
        assertTrue(resolved.toString().endsWith("captured/temple.nbt"))
    }

    @Test
    fun `test resolveStructurePath with nbt extension`() {
        // Create test structure
        Files.createDirectories(structuresDir.resolve("captured"))
        structuresDir.resolve("captured/bridge.nbt").writeText("")

        val resolved1 = structureApi.resolveStructurePath("bridge")
        val resolved2 = structureApi.resolveStructurePath("bridge.nbt")

        assertNotNull(resolved1, "Should work without extension")
        assertNotNull(resolved2, "Should work with extension")
        assertEquals(resolved1, resolved2, "Both should resolve to same path")
    }

    @Test
    fun `test resolveStructurePath returns null for nonexistent`() {
        val resolved = structureApi.resolveStructurePath("nonexistent")
        assertNull(resolved, "Should return null for nonexistent structure")
    }

    @Test
    fun `test resolveLargeStructurePath finds large structure`() {
        // Create large structure directory structure
        val piecesDir = structuresDir.resolve("pieces/castle")
        val largeDir = structuresDir.resolve("large")

        Files.createDirectories(piecesDir)
        Files.createDirectories(largeDir)

        // Create piece files
        piecesDir.resolve("0.0.0.nbt").writeText("")
        piecesDir.resolve("1.0.0.nbt").writeText("")

        // Create metadata
        largeDir.resolve("castle.json").writeText("""
            {
                "description": "Test large structure",
                "piece_size": {"x": 48, "z": 48}
            }
        """.trimIndent())

        val resolved = structureApi.resolveLargeStructurePath("castle")

        assertNotNull(resolved, "Should resolve large structure path")
        assertEquals(structuresDir, resolved, "Should return base structures directory")
    }

    @Test
    fun `test resolveLargeStructurePath with namespace prefix`() {
        // Create large structure
        val piecesDir = structuresDir.resolve("pieces/village")
        val largeDir = structuresDir.resolve("large")

        Files.createDirectories(piecesDir)
        Files.createDirectories(largeDir)

        piecesDir.resolve("0.0.0.nbt").writeText("")
        largeDir.resolve("village.json").writeText("{}")

        val resolved = structureApi.resolveLargeStructurePath("minecraft:village")

        assertNotNull(resolved, "Should strip namespace and resolve")
        assertEquals(structuresDir, resolved)
    }

    @Test
    fun `test resolveLargeStructurePath returns null if pieces missing`() {
        // Create only metadata, no pieces directory
        val largeDir = structuresDir.resolve("large")
        Files.createDirectories(largeDir)
        largeDir.resolve("incomplete.json").writeText("{}")

        val resolved = structureApi.resolveLargeStructurePath("incomplete")

        assertNull(resolved, "Should return null if pieces directory missing")
    }

    @Test
    fun `test resolveLargeStructurePath returns null if metadata missing`() {
        // Create only pieces directory, no metadata
        val piecesDir = structuresDir.resolve("pieces/incomplete")
        Files.createDirectories(piecesDir)
        piecesDir.resolve("0.0.0.nbt").writeText("")

        val resolved = structureApi.resolveLargeStructurePath("incomplete")

        assertNull(resolved, "Should return null if metadata missing")
    }

    @Test
    fun `test resolveLargeStructurePath returns null for nonexistent`() {
        val resolved = structureApi.resolveLargeStructurePath("nonexistent")
        assertNull(resolved, "Should return null for nonexistent large structure")
    }
}
