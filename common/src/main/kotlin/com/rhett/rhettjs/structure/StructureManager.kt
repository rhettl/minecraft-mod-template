package com.rhett.rhettjs.structure

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.structure.models.StructureData
import com.rhett.rhettjs.structure.models.StructureSize
import com.rhett.rhettjs.world.models.BlockData
import com.rhett.rhettjs.world.models.PositionedBlock
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

/**
 * Manager for Structure API operations with JavaScript.
 *
 * This is the anti-corruption layer between JavaScript and Minecraft structure files.
 * It ensures:
 * - All file I/O is async (return CompletableFuture)
 * - No Minecraft types exposed to JavaScript
 * - Pure JS objects using adapters
 * - Structure files stored in world/structures/ directory
 *
 * Design principles:
 * - Async for I/O: All operations return CompletableFuture
 * - Anti-corruption: Convert all MC types to JS via models
 * - Namespace format: "[namespace:]name" (defaults to "minecraft:")
 * - Position objects: {x, y, z, dimension?}
 */
object StructureManager {

    @Volatile
    private var server: MinecraftServer? = null

    @Volatile
    private var graalContext: Context? = null

    @Volatile
    private var structuresPath: Path? = null

    /**
     * Set the Minecraft server reference.
     * Called during server startup.
     */
    fun setServer(minecraftServer: MinecraftServer) {
        server = minecraftServer
        // Structures stored in world/structures/
        structuresPath = minecraftServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("structures")

        // Ensure structures directory exists
        structuresPath?.let { path ->
            if (!path.exists()) {
                Files.createDirectories(path)
                ConfigManager.debug("[StructureManager] Created structures directory: $path")
            }
        }

        ConfigManager.debug("[StructureManager] Minecraft server reference set")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        ConfigManager.debug("[StructureManager] GraalVM context reference set")
    }

    /**
     * Parse structure name format "[namespace:]name".
     * Returns pair of (namespace, name).
     * Defaults to "minecraft" namespace if not specified.
     */
    private fun parseStructureName(nameWithNamespace: String): Pair<String, String> {
        return if (':' in nameWithNamespace) {
            val parts = nameWithNamespace.split(':', limit = 2)
            parts[0] to parts[1]
        } else {
            "minecraft" to nameWithNamespace
        }
    }

    /**
     * Get the file path for a structure.
     * Format: structures/namespace/name.nbt
     */
    private fun getStructurePath(namespace: String, name: String): Path? {
        val basePath = structuresPath ?: return null
        return basePath.resolve(namespace).resolve("$name.nbt")
    }

    /**
     * Check if a structure exists (async).
     * Returns Promise<boolean>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun exists(nameWithNamespace: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val path = getStructurePath(namespace, name)

            if (path == null) {
                future.complete(false)
                return future
            }

            future.complete(path.exists() && path.isRegularFile())
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * List available structures (async).
     * Returns Promise<string[]> where each name is in format "namespace:name".
     *
     * @param namespaceFilter Optional namespace filter (null = all namespaces)
     */
    fun list(namespaceFilter: String?): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val basePath = structuresPath
            if (basePath == null || !basePath.exists()) {
                future.complete(emptyList())
                return future
            }

            val structures = mutableListOf<String>()

            // Scan namespace directories
            Files.list(basePath).use { namespaceDirs ->
                namespaceDirs
                    .filter { it.isDirectory() }
                    .filter { namespaceFilter == null || it.name == namespaceFilter }
                    .forEach { namespaceDir ->
                        val namespace = namespaceDir.name

                        // Scan .nbt files in namespace directory
                        if (Files.exists(namespaceDir)) {
                            Files.list(namespaceDir).use { files ->
                                files
                                    .filter { it.isRegularFile() && it.extension == "nbt" }
                                    .forEach { file ->
                                        val name = file.nameWithoutExtension
                                        structures.add("$namespace:$name")
                                    }
                            }
                        }
                    }
            }

            future.complete(structures.sorted())
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Delete a structure file (async).
     * Returns Promise<boolean> (true if deleted, false if didn't exist).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun delete(nameWithNamespace: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val path = getStructurePath(namespace, name)

            if (path == null || !path.exists()) {
                future.complete(false)
                return future
            }

            Files.delete(path)
            ConfigManager.debug("[StructureManager] Deleted structure: $nameWithNamespace")
            future.complete(true)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Load a structure from file (async).
     * Returns Promise<StructureData>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun load(nameWithNamespace: String): CompletableFuture<StructureData> {
        val future = CompletableFuture<StructureData>()

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val path = getStructurePath(namespace, name)

            if (path == null || !path.exists()) {
                future.completeExceptionally(IllegalArgumentException("Structure not found: $nameWithNamespace"))
                return future
            }

            // Read NBT file
            val nbt = NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap())

            // Parse structure data from NBT
            val structureData = parseStructureNBT(nbt)

            ConfigManager.debug("[StructureManager] Loaded structure: $nameWithNamespace (${structureData.blocks.size} blocks)")
            future.complete(structureData)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Save a structure to file (async).
     * Returns Promise<void>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param data Structure data to save
     */
    fun save(nameWithNamespace: String, data: StructureData): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val path = getStructurePath(namespace, name)

            if (path == null) {
                future.completeExceptionally(IllegalStateException("Structures directory not available"))
                return future
            }

            // Ensure namespace directory exists
            val namespaceDir = path.parent
            if (!namespaceDir.exists()) {
                Files.createDirectories(namespaceDir)
            }

            // Convert structure data to NBT
            val nbt = createStructureNBT(data)

            // Write NBT file
            NbtIo.writeCompressed(nbt, path)

            ConfigManager.debug("[StructureManager] Saved structure: $nameWithNamespace (${data.blocks.size} blocks)")
            future.complete(null)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Parse structure data from Minecraft NBT format.
     * Converts NBT → StructureData (anti-corruption shield).
     */
    private fun parseStructureNBT(nbt: CompoundTag): StructureData {
        // Read size
        val sizeList = nbt.getIntArray("size")
        val size = StructureSize(
            x = sizeList[0],
            y = sizeList[1],
            z = sizeList[2]
        )

        // Read palette (block states)
        val paletteList = nbt.getList("palette", 10) // 10 = CompoundTag
        val palette = mutableListOf<BlockData>()

        for (i in 0 until paletteList.size) {
            val blockNBT = paletteList.getCompound(i)
            val blockId = blockNBT.getString("Name")

            val properties = mutableMapOf<String, String>()
            if (blockNBT.contains("Properties")) {
                val propsNBT = blockNBT.getCompound("Properties")
                propsNBT.allKeys.forEach { key ->
                    properties[key] = propsNBT.getString(key)
                }
            }

            palette.add(BlockData(name = blockId, properties = properties))
        }

        // Read blocks (positions + palette indices)
        val blocksList = nbt.getList("blocks", 10) // 10 = CompoundTag
        val blocks = mutableListOf<PositionedBlock>()

        for (i in 0 until blocksList.size) {
            val blockNBT = blocksList.getCompound(i)
            val pos = blockNBT.getIntArray("pos")
            val state = blockNBT.getInt("state")

            val blockData = palette.getOrNull(state)
            if (blockData != null) {
                blocks.add(
                    PositionedBlock(
                        x = pos[0],
                        y = pos[1],
                        z = pos[2],
                        block = blockData,
                        blockEntityData = if (blockNBT.contains("nbt")) blockNBT.getCompound("nbt") else null
                    )
                )
            }
        }

        // Read metadata (custom)
        val metadata = mutableMapOf<String, String>()
        if (nbt.contains("metadata")) {
            val metaNBT = nbt.getCompound("metadata")
            metaNBT.allKeys.forEach { key ->
                metadata[key] = metaNBT.getString(key)
            }
        }

        return StructureData(
            size = size,
            blocks = blocks,
            metadata = metadata
        )
    }

    /**
     * Create Minecraft NBT structure format from StructureData.
     * Converts StructureData → NBT (anti-corruption shield).
     */
    private fun createStructureNBT(data: StructureData): CompoundTag {
        val nbt = CompoundTag()

        // Write size
        nbt.putIntArray("size", intArrayOf(data.size.x, data.size.y, data.size.z))

        // Build palette (unique block states)
        val palette = mutableListOf<BlockData>()
        val paletteIndices = mutableMapOf<BlockData, Int>()

        data.blocks.forEach { block ->
            if (block.block !in paletteIndices) {
                paletteIndices[block.block] = palette.size
                palette.add(block.block)
            }
        }

        // Write palette
        val paletteList = ListTag()
        palette.forEach { blockData ->
            val blockNBT = CompoundTag()
            blockNBT.putString("Name", blockData.name)

            if (blockData.properties.isNotEmpty()) {
                val propsNBT = CompoundTag()
                blockData.properties.forEach { (key, value) ->
                    propsNBT.putString(key, value)
                }
                blockNBT.put("Properties", propsNBT)
            }

            paletteList.add(blockNBT)
        }
        nbt.put("palette", paletteList)

        // Write blocks
        val blocksList = ListTag()
        data.blocks.forEach { block ->
            val blockNBT = CompoundTag()
            blockNBT.putIntArray("pos", intArrayOf(block.x, block.y, block.z))
            blockNBT.putInt("state", paletteIndices[block.block] ?: 0)

            // Write block entity data if present
            if (block.blockEntityData != null && block.blockEntityData is CompoundTag) {
                blockNBT.put("nbt", block.blockEntityData as CompoundTag)
            }

            blocksList.add(blockNBT)
        }
        nbt.put("blocks", blocksList)

        // Write metadata
        if (data.metadata.isNotEmpty()) {
            val metaNBT = CompoundTag()
            data.metadata.forEach { (key, value) ->
                metaNBT.putString(key, value)
            }
            nbt.put("metadata", metaNBT)
        }

        // Write data version (current MC version)
        nbt.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().dataVersion.version)

        return nbt
    }

    /**
     * Capture a region and convert to structure data (async).
     * Returns Promise<void> after saving structure file.
     *
     * @param pos1 First corner position {x, y, z, dimension?}
     * @param pos2 Second corner position {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {author?: string, description?: string}
     */
    fun capture(pos1: Value, pos2: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            // Extract positions from JS
            val x1 = pos1.getMember("x").asInt()
            val y1 = pos1.getMember("y").asInt()
            val z1 = pos1.getMember("z").asInt()
            val x2 = pos2.getMember("x").asInt()
            val y2 = pos2.getMember("y").asInt()
            val z2 = pos2.getMember("z").asInt()

            // Get dimension from pos1 unless options.dimension is specified
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Extract metadata from options
            val metadata = mutableMapOf<String, String>()
            if (options != null) {
                if (options.hasMember("author")) {
                    metadata["author"] = options.getMember("author").asString()
                }
                if (options.hasMember("description")) {
                    metadata["description"] = options.getMember("description").asString()
                }
            }

            // Execute capture on main thread
            srv.execute {
                try {
                    val worldAdapter = com.rhett.rhettjs.world.WorldManager
                    val level = worldAdapter.getLevel(dimension)

                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    // Normalize coordinates (min to max)
                    val minX = minOf(x1, x2)
                    val minY = minOf(y1, y2)
                    val minZ = minOf(z1, z2)
                    val maxX = maxOf(x1, x2)
                    val maxY = maxOf(y1, y2)
                    val maxZ = maxOf(z1, z2)

                    val sizeX = maxX - minX + 1
                    val sizeY = maxY - minY + 1
                    val sizeZ = maxZ - minZ + 1

                    // Capture blocks in the region
                    val blocks = mutableListOf<PositionedBlock>()

                    for (x in minX..maxX) {
                        for (y in minY..maxY) {
                            for (z in minZ..maxZ) {
                                val blockPos = net.minecraft.core.BlockPos(x, y, z)
                                val blockState = level.getBlockState(blockPos)

                                // Convert to BlockData model
                                val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                    .getKey(blockState.block)?.toString() ?: "minecraft:air"

                                val properties = mutableMapOf<String, String>()
                                blockState.values.forEach { (property, value) ->
                                    properties[property.name] = value.toString()
                                }

                                // Get block entity data if present
                                val blockEntity = level.getBlockEntity(blockPos)
                                val blockEntityData = if (blockEntity != null) {
                                    blockEntity.saveWithoutMetadata(level.registryAccess())
                                } else {
                                    null
                                }

                                // Store as relative position
                                blocks.add(
                                    PositionedBlock(
                                        x = x - minX,
                                        y = y - minY,
                                        z = z - minZ,
                                        block = BlockData(name = blockId, properties = properties),
                                        blockEntityData = blockEntityData
                                    )
                                )
                            }
                        }
                    }

                    // Create structure data
                    val structureData = StructureData(
                        size = StructureSize(sizeX, sizeY, sizeZ),
                        blocks = blocks,
                        metadata = metadata
                    )

                    // Save structure to file
                    val saveFuture = save(nameWithNamespace, structureData)
                    saveFuture.whenComplete { _, throwable ->
                        if (throwable != null) {
                            future.completeExceptionally(throwable)
                        } else {
                            ConfigManager.debug("[StructureManager] Captured structure: $nameWithNamespace (${blocks.size} blocks)")
                            future.complete(null)
                        }
                    }

                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Place a structure at a position (async).
     * Returns Promise<void> after placing all blocks.
     *
     * @param position Position to place structure {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {rotation?: 0|90|180|270, centered?: boolean}
     */
    fun place(position: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            // Extract position from JS
            val x = position.getMember("x").asInt()
            val y = position.getMember("y").asInt()
            val z = position.getMember("z").asInt()

            // Get dimension from position unless options.dimension is specified
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Extract options
            val rotation = if (options != null && options.hasMember("rotation")) {
                options.getMember("rotation").asInt()
            } else {
                0
            }

            val centered = if (options != null && options.hasMember("centered")) {
                options.getMember("centered").asBoolean()
            } else {
                false
            }

            // Load structure first
            val loadFuture = load(nameWithNamespace)

            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Execute placement on main thread
                srv.execute {
                    try {
                        val worldAdapter = com.rhett.rhettjs.world.WorldManager
                        val level = worldAdapter.getLevel(dimension)

                        if (level == null) {
                            future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                            return@execute
                        }

                        // Calculate base position (apply centering if requested)
                        val baseX = if (centered) x - structureData.size.x / 2 else x
                        val baseY = y
                        val baseZ = if (centered) z - structureData.size.z / 2 else z

                        // Apply rotation to blocks if needed
                        val rotatedBlocks = if (rotation == 0) {
                            // No rotation - use blocks as-is with base offset
                            structureData.blocks.map { block ->
                                PositionedBlock(
                                    x = baseX + block.x,
                                    y = baseY + block.y,
                                    z = baseZ + block.z,
                                    block = block.block,
                                    blockEntityData = block.blockEntityData
                                )
                            }
                        } else {
                            // Apply rotation using RotationHelper
                            val rotationHelper = com.rhett.rhettjs.world.logic.RotationHelper

                            // For single structure placement, we don't use grid positioning
                            // We rotate each block around the structure's origin (0,0,0)
                            val blockStateRotator = rotationHelper.createBlockStateRotator(rotation)

                            structureData.blocks.map { block ->
                                // Rotate position manually for single-block rotation
                                val (rotatedX, rotatedZ) = when (rotation) {
                                    90 -> Pair(-block.z, block.x)
                                    180 -> Pair(-block.x, -block.z)
                                    270 -> Pair(block.z, -block.x)
                                    else -> Pair(block.x, block.z)
                                }

                                // Rotate block state properties
                                val rotatedBlockData = blockStateRotator(block.block)

                                PositionedBlock(
                                    x = baseX + rotatedX,
                                    y = baseY + block.y,
                                    z = baseZ + rotatedZ,
                                    block = rotatedBlockData,
                                    blockEntityData = block.blockEntityData
                                )
                            }
                        }

                        // Place blocks using world adapter
                        val adapter = com.rhett.rhettjs.world.adapter.WorldAdapter(srv)
                        adapter.setBlocksInRegion(level, rotatedBlocks, updateNeighbors = true)

                        ConfigManager.debug("[StructureManager] Placed structure: $nameWithNamespace (${rotatedBlocks.size} blocks, rotation=$rotation)")
                        future.complete(null)

                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Clear all state (called on reset/reload).
     * Clears context reference (context will be recreated).
     */
    fun reset() {
        graalContext = null
        ConfigManager.debug("[StructureManager] Reset complete")
    }
}
