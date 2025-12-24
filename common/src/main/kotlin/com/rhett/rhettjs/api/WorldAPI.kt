package com.rhett.rhettjs.api

import com.rhett.rhettjs.world.adapter.WorldAdapter
import com.rhett.rhettjs.world.logic.StructureBuilder
import com.rhett.rhettjs.world.models.Region
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * World API for reading and writing world blocks.
 * Thin orchestration layer - delegates to adapter and business logic.
 *
 * Enables terrain grabbing (world → structure files) and placement (structure files → world).
 *
 * All world access is automatically synchronized to main thread via server.execute().
 */
class WorldAPI(
    private val server: MinecraftServer
) {

    private val adapter = WorldAdapter(server)

    /**
     * Grab a region of blocks from the world and convert to structure NBT format.
     * Coordinates are inclusive on all axes.
     *
     * @param world World name/dimension ("overworld", "the_nether", "the_end")
     * @param x1 Start X coordinate
     * @param y1 Start Y coordinate
     * @param z1 Start Z coordinate
     * @param x2 End X coordinate
     * @param y2 End Y coordinate
     * @param z2 End Z coordinate
     * @return Structure data as Map (can be written with Structure.write())
     */
    fun grab(world: String, x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): Map<String, Any>? {
        // Normalize coordinates
        val region = Region.fromCorners(x1, y1, z1, x2, y2, z2)

        // Validate size
        StructureBuilder.validateSingleStructureSize(region)

        // Get ServerLevel via adapter
        val level = adapter.getLevel(world) ?: return null

        // Get blocks and entities via adapter (isolates Minecraft access)
        val blocks = adapter.getBlocksInRegion(level, region)
        val entities = adapter.getEntitiesInRegion(level, region)

        // Build structure data using pure business logic
        return StructureBuilder.buildStructureData(region, blocks, entities)
    }

    /**
     * Grab a region and write it to a structure file.
     * Writes to <world>/generated/minecraft/structures/
     *
     * @param world World name/dimension
     * @param x1 Start X
     * @param y1 Start Y
     * @param z1 Start Z
     * @param x2 End X
     * @param y2 End Y
     * @param z2 End Z
     * @param filename Structure filename (without .nbt extension)
     * @param subdirectory Optional subdirectory (defaults to "captured")
     * @return The full path where the file was written
     */
    fun grabToFile(
        world: String,
        x1: Int, y1: Int, z1: Int,
        x2: Int, y2: Int, z2: Int,
        filename: String,
        subdirectory: String? = null
    ): String {
        val structureData = grab(world, x1, y1, z1, x2, y2, z2)
            ?: throw RuntimeException("Failed to grab region from world")

        // Get world path
        val level = adapter.getLevel(world)
            ?: throw RuntimeException("World not found: $world")
        val worldPath = level.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)

        // Build path: <world>/generated/minecraft/structures/<subdirectory>/<filename>.nbt
        val subdir = subdirectory ?: "captured"
        val structuresDir = worldPath
            .resolve("generated")
            .resolve("minecraft")
            .resolve("structures")
            .resolve(subdir)

        structuresDir.createDirectories()

        val targetFile = structuresDir.resolve("$filename.nbt")

        // Create NBTAPI for writing
        val backupsDir = worldPath.resolve("backups/structures")
        val nbtApi = NBTAPI(structuresDir.parent, backupsDir)
        nbtApi.write("$subdir/$filename.nbt", structureData, skipBackup = true)

        return targetFile.toString()
    }

    /**
     * Grab a large region and split it into grid pieces.
     * Saves to <world>/generated/<namespace>/structures/rjs-large/<name>/
     * Embeds metadata in 0.0.0.nbt under "large" tag.
     *
     * @param world World name/dimension
     * @param x1 Start X
     * @param y1 Start Y
     * @param z1 Start Z
     * @param x2 End X
     * @param y2 End Y
     * @param z2 End Z
     * @param name Large structure name (path)
     * @param pieceSize Optional [x, z] piece size (default [48, 48])
     * @param namespace Optional namespace (default "minecraft")
     * @return Metadata object
     */
    fun grabLarge(
        world: String,
        x1: Int, y1: Int, z1: Int,
        x2: Int, y2: Int, z2: Int,
        name: String,
        pieceSize: IntArray? = null,
        namespace: String? = null
    ): Map<String, Any> {
        // Parse piece size
        // pieceSize can be: null (default 48x48), [size] (square), [x,z], or [x,y,z]
        val pieceSizeX = pieceSize?.getOrNull(0) ?: 48
        val pieceSizeZ = pieceSize?.getOrNull(1) ?: pieceSizeX  // If only 1 element, use square
        val pieceSizeY = pieceSize?.getOrNull(2)  // Optional Y splitting

        // Normalize coordinates
        val region = Region.fromCorners(x1, y1, z1, x2, y2, z2)

        // Get ServerLevel via adapter
        val level = adapter.getLevel(world)
            ?: throw RuntimeException("World not found: $world")

        // Get world save directory
        val worldPath = level.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)

        // Build path: <world>/generated/<namespace>/structures/rjs-large/<name>/
        val targetNamespace = namespace ?: "minecraft"
        val structureDir = worldPath
            .resolve("generated")
            .resolve(targetNamespace)
            .resolve("structures")
            .resolve("rjs-large")
            .resolve(name)

        structureDir.createDirectories()

        // Split into grid pieces using pure business logic (with optional Y splitting)
        val pieces = StructureBuilder.splitIntoGrid(region, pieceSizeX, pieceSizeZ, pieceSizeY)

        // Collect all blocks and detect mod namespaces
        val allBlockNamespaces = mutableSetOf<String>()

        // Store piece data temporarily
        val piecesData = mutableListOf<Pair<String, Map<String, Any>>>()

        // Grab each grid piece
        pieces.forEach { (gridCoord, pieceRegion) ->
            // Get blocks for this piece via adapter
            val blocks = adapter.getBlocksInRegion(level, pieceRegion)
            val entities = adapter.getEntitiesInRegion(level, pieceRegion)

            // Extract block namespaces for mod detection
            blocks.forEach { positionedBlock ->
                val blockName = positionedBlock.block.name
                // Extract namespace (e.g., "terralith:volcanic_rock" -> "terralith")
                val blockNamespace = blockName.substringBefore(':')
                if (blockNamespace != "minecraft") {
                    allBlockNamespaces.add(blockNamespace)
                }
            }

            // Build structure data using pure business logic
            val pieceData = StructureBuilder.buildStructureData(pieceRegion, blocks, entities)

            piecesData.add(Pair(gridCoord.toFilename(), pieceData))
        }

        // Build metadata
        val requiredMods = allBlockNamespaces.sorted()

        // Create NBTAPI for writing
        val backupsDir = worldPath.resolve("backups/structures")
        val baseStructuresDir = worldPath.resolve("generated").resolve(targetNamespace).resolve("structures")
        val nbtApi = NBTAPI(baseStructuresDir, backupsDir)

        // Calculate grid dimensions for metadata
        val gridSizeX = kotlin.math.ceil(region.sizeX.toDouble() / pieceSizeX).toInt()
        val gridSizeZ = kotlin.math.ceil(region.sizeZ.toDouble() / pieceSizeZ).toInt()
        val gridSizeY = if (pieceSizeY != null) {
            kotlin.math.ceil(region.sizeY.toDouble() / pieceSizeY).toInt()
        } else {
            1
        }

        // Write pieces
        piecesData.forEachIndexed { index, (filename, pieceData) ->
            // Embed metadata in 0.0.0.nbt (first piece)
            val dataToWrite = if (index == 0) {
                // Add "large" metadata to first piece
                val mutableData = pieceData.toMutableMap()

                // Build piece size map
                val pieceSizeMap = mutableMapOf(
                    "x" to pieceSizeX,
                    "z" to pieceSizeZ
                )
                if (pieceSizeY != null) {
                    pieceSizeMap["y"] = pieceSizeY
                }

                // Build grid size map
                val gridSizeMap = mutableMapOf(
                    "x" to gridSizeX,
                    "z" to gridSizeZ
                )
                if (gridSizeY > 1) {
                    gridSizeMap["y"] = gridSizeY
                }

                mutableData["large"] = mapOf(
                    "requires" to requiredMods,
                    "pieceSize" to pieceSizeMap,
                    "gridSize" to gridSizeMap,
                    "totalSize" to mapOf(
                        "x" to region.sizeX,
                        "y" to region.sizeY,
                        "z" to region.sizeZ
                    )
                )
                mutableData
            } else {
                pieceData
            }

            // Write using NBTAPI
            nbtApi.write(
                "rjs-large/$name/$filename",
                dataToWrite,
                skipBackup = true
            )
        }

        // Return metadata for user feedback
        return mapOf(
            "name" to name,
            "namespace" to targetNamespace,
            "pieces" to piecesData.size,
            "requires" to requiredMods,
            "path" to structureDir.toString()
        )
    }

    /**
     * List all available large structures.
     * Uses StructureTemplateManager to find all rjs-large structures.
     *
     * @param namespace Optional namespace filter (e.g., "minecraft", "bca")
     * @return List of large structure info maps
     */
    fun listLarge(namespace: String? = null): List<Map<String, Any>> {
        // Use adapter to scan all resource sources (isolated from Minecraft types)
        val resources = adapter.listResources("structures") { ns, path ->
            // Filter for rjs-large master pieces
            path.contains("/rjs-large/") &&
            path.endsWith("/0.0.0.nbt") &&
            (namespace == null || ns == namespace)
        }

        // Convert pure (namespace, path) pairs to our format
        return resources.map { (ns, path) ->
            // Extract structure name from path
            // Path: "structures/rjs-large/<name>/0.0.0.nbt"
            val structureName = path
                .removePrefix("structures/rjs-large/")
                .removeSuffix("/0.0.0.nbt")

            mapOf(
                "namespace" to ns,
                "name" to structureName,
                "location" to "$ns:rjs-large/$structureName"
            )
        }
    }

    /**
     * Place a large multi-piece structure at world position with rotation.
     *
     * @param world World name/dimension
     * @param x World X coordinate (origin)
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @param namespace Structure namespace
     * @param name Structure name/path
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     * @return Map with placement info (pieces placed, blocks total, metadata)
     */
    fun placeLarge(
        world: String,
        x: Int, y: Int, z: Int,
        namespace: String,
        name: String,
        rotation: Int = 0
    ): Map<String, Any> {
        // Get metadata to determine piece layout
        val metadata = getLargeMetadata(namespace, name)
            ?: throw RuntimeException("Large structure not found: $namespace:rjs-large/$name")

        // Extract metadata
        val pieceSize = metadata["pieceSize"] as? Map<*, *>
            ?: throw RuntimeException("Invalid metadata: missing pieceSize")
        val gridSize = metadata["gridSize"] as? Map<*, *>
            ?: throw RuntimeException("Invalid metadata: missing gridSize")

        val pieceSizeX = (pieceSize["x"] as? Number)?.toInt() ?: 48
        val pieceSizeZ = (pieceSize["z"] as? Number)?.toInt() ?: 48
        val pieceSizeY = (pieceSize["y"] as? Number)?.toInt()  // Optional Y splitting
        val gridSizeX = (gridSize["x"] as? Number)?.toInt() ?: 1
        val gridSizeZ = (gridSize["z"] as? Number)?.toInt() ?: 1
        val gridSizeY = (gridSize["y"] as? Number)?.toInt() ?: 1

        // Get ServerLevel
        val level = adapter.getLevel(world)
            ?: throw RuntimeException("World not found: $world")

        // Get structure directory
        val worldPath = level.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
        val structureDir = worldPath
            .resolve("generated")
            .resolve(namespace)
            .resolve("structures")
            .resolve("rjs-large")
            .resolve(name)

        if (!structureDir.toFile().exists()) {
            throw RuntimeException("Structure directory not found: $structureDir")
        }

        // Create rotation helpers
        // Include Y in piece size if structure has vertical splitting
        val pieceSizeArray = if (pieceSizeY != null) {
            intArrayOf(pieceSizeX, pieceSizeY, pieceSizeZ)
        } else {
            intArrayOf(pieceSizeX, pieceSizeZ)
        }

        val positionCalc = com.rhett.rhettjs.world.logic.RotationHelper.createPositionCalculator(
            startPos = intArrayOf(x, y, z),
            rotation = rotation,
            pieceSize = pieceSizeArray
        )

        val blockRotator = com.rhett.rhettjs.world.logic.RotationHelper.createBlockStateRotator(rotation)

        // Create NBTAPI for reading pieces
        val backupsDir = worldPath.resolve("backups/structures")
        val baseStructuresDir = worldPath.resolve("generated").resolve(namespace).resolve("structures")
        val nbtApi = NBTAPI(baseStructuresDir, backupsDir)

        var totalBlocksPlaced = 0
        var piecesPlaced = 0

        // Place each piece (iterate through 3D grid if Y splitting is enabled)
        for (gridX in 0 until gridSizeX) {
            for (gridY in 0 until gridSizeY) {
                for (gridZ in 0 until gridSizeZ) {
                    val filename = "$gridX.$gridY.$gridZ.nbt"
                    val piecePath = "rjs-large/$name/$filename"

                    // Read piece NBT
                    val pieceData = try {
                        nbtApi.read(piecePath) as? Map<*, *>
                    } catch (e: Exception) {
                        com.rhett.rhettjs.config.ConfigManager.debug(
                            "[WorldAPI] Failed to read piece $piecePath: ${e.message}"
                        )
                        continue
                    }

                    if (pieceData == null) {
                        com.rhett.rhettjs.config.ConfigManager.debug("[WorldAPI] Piece not found: $piecePath")
                        continue
                    }

                    // Calculate world position for this piece
                    val worldPos = positionCalc(intArrayOf(gridX, gridY, gridZ))
                    val pieceOriginX = worldPos[0]
                val pieceOriginY = worldPos[1]
                val pieceOriginZ = worldPos[2]

                // Extract structure size from NBT
                val sizeList = pieceData["size"] as? List<*>
                val structureSizeX = (sizeList?.getOrNull(0) as? Number)?.toInt() ?: pieceSizeX
                val structureSizeZ = (sizeList?.getOrNull(2) as? Number)?.toInt() ?: pieceSizeZ

                // Extract blocks from piece NBT (use actual structure size, not piece size)
                val blocks = extractBlocksFromStructure(pieceData, blockRotator, rotation, structureSizeX, structureSizeZ)

                // Convert to positioned blocks with world coordinates
                val positionedBlocks = blocks.map { (relPos, blockData, blockEntityData) ->
                    com.rhett.rhettjs.world.models.PositionedBlock(
                        x = pieceOriginX + relPos[0],
                        y = pieceOriginY + relPos[1],
                        z = pieceOriginZ + relPos[2],
                        block = blockData,
                        blockEntityData = blockEntityData
                    )
                }

                // Place blocks using adapter
                adapter.setBlocksInRegion(level, positionedBlocks, updateNeighbors = false)

                totalBlocksPlaced += positionedBlocks.size
                piecesPlaced++
                }
            }
        }

        // Return placement info
        return mapOf(
            "piecesPlaced" to piecesPlaced,
            "blocksPlaced" to totalBlocksPlaced,
            "metadata" to metadata,
            "rotation" to rotation,
            "position" to mapOf("x" to x, "y" to y, "z" to z)
        )
    }

    /**
     * Extract blocks from structure NBT and apply rotation.
     * Returns list of (relativePos, blockData, blockEntityData).
     *
     * @param structureSizeX Actual structure width (from NBT size field)
     * @param structureSizeZ Actual structure depth (from NBT size field)
     */
    private fun extractBlocksFromStructure(
        structureData: Map<*, *>,
        blockRotator: (com.rhett.rhettjs.world.models.BlockData) -> com.rhett.rhettjs.world.models.BlockData,
        rotation: Int,
        structureSizeX: Int,
        structureSizeZ: Int
    ): List<Triple<IntArray, com.rhett.rhettjs.world.models.BlockData, Map<String, Any>?>> {
        // Extract palette and blocks
        val palette = structureData["palette"] as? List<*> ?: return emptyList()
        val blocksList = structureData["blocks"] as? List<*> ?: return emptyList()

        // Convert palette to BlockData
        val paletteData = palette.map { entry ->
            val entryMap = entry as? Map<*, *> ?: return@map null
            val name = entryMap["Name"] as? String ?: return@map null
            val properties = (entryMap["Properties"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap()

            com.rhett.rhettjs.world.models.BlockData(
                name = name,
                properties = properties
            )
        }

        // Extract and rotate blocks
        val blocks = mutableListOf<Triple<IntArray, com.rhett.rhettjs.world.models.BlockData, Map<String, Any>?>>()

        blocksList.forEach { blockEntry ->
            val blockMap = blockEntry as? Map<*, *> ?: return@forEach

            // Get palette state index
            val stateIndex = (blockMap["state"] as? Number)?.toInt() ?: return@forEach
            val blockData = paletteData.getOrNull(stateIndex) ?: return@forEach

            // Get relative position
            val posList = blockMap["pos"] as? List<*> ?: return@forEach
            if (posList.size < 3) return@forEach

            val relX = (posList[0] as? Number)?.toInt() ?: 0
            val relY = (posList[1] as? Number)?.toInt() ?: 0
            val relZ = (posList[2] as? Number)?.toInt() ?: 0

            // Rotate position within structure (using actual structure size, not piece size)
            val (rotatedX, rotatedZ) = com.rhett.rhettjs.world.logic.RotationHelper.rotatePosition(
                relX, relZ, rotation, structureSizeX, structureSizeZ
            )

            // Rotate block state
            val rotatedBlockData = blockRotator(blockData)

            // Extract block entity data if present
            val blockEntityData = blockMap["nbt"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val blockEntityDataTyped = blockEntityData?.mapKeys { it.key.toString() }?.mapValues { it.value } as? Map<String, Any>

            blocks.add(Triple(
                intArrayOf(rotatedX, relY, rotatedZ),
                rotatedBlockData,
                blockEntityDataTyped
            ))
        }

        return blocks
    }

    /**
     * Get complete metadata from a large structure.
     * Reads the 0.0.0.nbt master piece and extracts embedded metadata.
     *
     * @param namespace Namespace of the structure
     * @param name Name/path of the structure
     * @return Metadata map with: requires, pieceSize, gridSize, totalSize, pieceCount
     *         Returns null if structure not found
     */
    fun getLargeMetadata(namespace: String, name: String): Map<String, Any>? {
        val structureManager = server.structureManager

        // Build ResourceLocation for master piece (0.0.0.nbt)
        val masterLoc = ResourceLocation.fromNamespaceAndPath(
            namespace,
            "rjs-large/$name/0.0.0"
        )

        // Try to load the template
        val template = structureManager.get(masterLoc)
        if (template.isEmpty) {
            return null
        }

        // Read structure NBT using NBTAPI (reuse existing conversion)
        // We need to get the actual file path to read it
        val level = server.overworld()
        val worldPath = level.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
        val structureFile = worldPath
            .resolve("generated")
            .resolve(namespace)
            .resolve("structures")
            .resolve("rjs-large")
            .resolve(name)
            .resolve("0.0.0.nbt")

        if (!structureFile.toFile().exists()) {
            return null
        }

        // Read using NBTAPI
        val backupsDir = worldPath.resolve("backups/structures")
        val baseStructuresDir = worldPath.resolve("generated").resolve(namespace).resolve("structures")
        val nbtApi = NBTAPI(baseStructuresDir, backupsDir)

        val structureData = nbtApi.read("rjs-large/$name/0.0.0.nbt") as? Map<*, *>
            ?: return null

        // Extract "large" metadata
        val largeMetadata = structureData["large"] as? Map<*, *>
            ?: return null

        // Parse metadata fields
        val requires = largeMetadata["requires"] as? List<*>
            ?: emptyList<String>()

        val pieceSize = largeMetadata["pieceSize"] as? Map<*, *>
        val gridSize = largeMetadata["gridSize"] as? Map<*, *>
        val totalSize = largeMetadata["totalSize"] as? Map<*, *>

        // Calculate piece count from grid size
        val gridX = (gridSize?.get("x") as? Number)?.toInt() ?: 1
        val gridZ = (gridSize?.get("z") as? Number)?.toInt() ?: 1
        val pieceCount = gridX * gridZ

        return mapOf(
            "requires" to requires,
            "pieceSize" to (pieceSize ?: mapOf<String, Int>()),
            "gridSize" to (gridSize ?: mapOf<String, Int>()),
            "totalSize" to (totalSize ?: mapOf<String, Int>()),
            "pieceCount" to pieceCount,
            "location" to "$namespace:rjs-large/$name"
        )
    }

    /**
     * List all blocks across all pieces of a large structure with their counts.
     * Returns combined map of block ID → total count, sorted alphabetically.
     *
     * @param namespace Namespace of the structure
     * @param name Name/path of the structure
     * @return Map of block ID to total count across all pieces
     */
    fun blocksListLarge(namespace: String, name: String): Map<String, Int> {
        // Get metadata to know grid size
        val metadata = getLargeMetadata(namespace, name)
            ?: throw RuntimeException("Large structure not found: $namespace:rjs-large/$name")

        val gridSize = metadata["gridSize"] as? Map<*, *>
            ?: throw RuntimeException("Invalid metadata: missing gridSize")

        val gridSizeX = (gridSize["x"] as? Number)?.toInt() ?: 1
        val gridSizeZ = (gridSize["z"] as? Number)?.toInt() ?: 1

        // Get world path for reading pieces
        val level = server.overworld()
        val worldPath = level.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
        val baseStructuresDir = worldPath.resolve("generated").resolve(namespace).resolve("structures")
        val backupsDir = worldPath.resolve("backups/structures")
        val nbtApi = NBTAPI(baseStructuresDir, backupsDir)

        // Count blocks across all pieces
        val totalCounts = mutableMapOf<String, Int>()

        for (gridX in 0 until gridSizeX) {
            for (gridZ in 0 until gridSizeZ) {
                val filename = "$gridX.0.$gridZ.nbt"
                val piecePath = "rjs-large/$name/$filename"

                try {
                    val pieceData = nbtApi.read(piecePath) as? Map<*, *> ?: continue
                    val pieceCounts = com.rhett.rhettjs.world.logic.BlockReplacer.countBlocks(pieceData)

                    // Add to total counts
                    pieceCounts.forEach { (blockId, count) ->
                        totalCounts[blockId] = totalCounts.getOrDefault(blockId, 0) + count
                    }
                } catch (e: Exception) {
                    // Skip missing pieces
                    continue
                }
            }
        }

        return totalCounts.toSortedMap()
    }

    /**
     * Replace blocks in all pieces of a large structure according to replacement map.
     * Modifies all piece files in-place (with automatic backups).
     *
     * @param namespace Namespace of the structure
     * @param name Name/path of the structure
     * @param replacementMap Map of oldBlockId → newBlockId
     * @return Number of pieces modified
     */
    fun blocksReplaceLarge(
        namespace: String,
        name: String,
        replacementMap: Map<String, String>
    ): Int {
        // Get metadata to know grid size
        val metadata = getLargeMetadata(namespace, name)
            ?: throw RuntimeException("Large structure not found: $namespace:rjs-large/$name")

        val gridSize = metadata["gridSize"] as? Map<*, *>
            ?: throw RuntimeException("Invalid metadata: missing gridSize")

        val gridSizeX = (gridSize["x"] as? Number)?.toInt() ?: 1
        val gridSizeZ = (gridSize["z"] as? Number)?.toInt() ?: 1

        // Get world path for modifying pieces
        val level = server.overworld()
        val worldPath = level.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
        val baseStructuresDir = worldPath.resolve("generated").resolve(namespace).resolve("structures")
        val backupsDir = worldPath.resolve("backups/structures")
        val nbtApi = NBTAPI(baseStructuresDir, backupsDir)

        var modifiedCount = 0

        // Replace blocks in each piece
        for (gridX in 0 until gridSizeX) {
            for (gridZ in 0 until gridSizeZ) {
                val filename = "$gridX.0.$gridZ.nbt"
                val piecePath = "rjs-large/$name/$filename"

                try {
                    // Read piece
                    val pieceData = nbtApi.read(piecePath) as? Map<*, *> ?: continue

                    // Apply replacements
                    val newData = com.rhett.rhettjs.world.logic.BlockReplacer.replaceBlocks(
                        pieceData,
                        replacementMap
                    )

                    // Write back (with backup)
                    nbtApi.write(piecePath, newData)
                    modifiedCount++
                } catch (e: Exception) {
                    // Skip errors (piece might not exist)
                    com.rhett.rhettjs.config.ConfigManager.debug(
                        "[WorldAPI] Failed to replace blocks in piece $piecePath: ${e.message}"
                    )
                    continue
                }
            }
        }

        return modifiedCount
    }

    /**
     * Replace modded blocks with vanilla equivalents in all pieces of a large structure.
     * Modifies all piece files in-place (with automatic backups).
     *
     * @param namespace Namespace of the structure
     * @param name Name/path of the structure
     * @param typeOverrides Optional map of type → value (e.g., {"wood": "oak"})
     * @return Map with "piecesModified" count and "warnings" list
     */
    fun blocksReplaceLargeVanilla(
        namespace: String,
        name: String,
        typeOverrides: Map<String, String>? = null
    ): Map<String, Any> {
        // Get list of all blocks in structure
        val blockCounts = blocksListLarge(namespace, name)
        val blockIds = blockCounts.keys.toList()

        // Generate vanilla replacement map
        val woodType = typeOverrides?.get("wood") ?: "spruce"
        val result = com.rhett.rhettjs.world.logic.BlockReplacer.generateVanillaReplacementMap(
            blockIds,
            woodType
        )

        // Apply wood type overrides if provided
        val finalReplacements = if (typeOverrides != null) {
            com.rhett.rhettjs.world.logic.BlockReplacer.applyWoodTypeOverride(
                result.replacements,
                typeOverrides
            )
        } else {
            result.replacements
        }

        // Apply replacements to all pieces
        val piecesModified = blocksReplaceLarge(namespace, name, finalReplacements)

        return mapOf(
            "piecesModified" to piecesModified,
            "warnings" to result.warnings
        )
    }
}
