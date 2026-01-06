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
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors
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

    @Volatile
    private var nbtApi: com.rhett.rhettjs.api.NBTAPI? = null

    /**
     * Set the Minecraft server reference.
     * Called during server startup.
     */
    fun setServer(minecraftServer: MinecraftServer) {
        server = minecraftServer
        // Structures stored in world/generated/ (namespaced)
        structuresPath = minecraftServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("generated")

        val backupsPath = minecraftServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("backups").resolve("structures")

        // Ensure base directories exist
        structuresPath?.let { path ->
            if (!path.exists()) {
                Files.createDirectories(path)
                ConfigManager.debug("[StructureManager] Created generated directory: $path")
            }
        }
        if (!backupsPath.exists()) {
            Files.createDirectories(backupsPath)
            ConfigManager.debug("[StructureManager] Created backups directory: $backupsPath")
        }

        // Note: NBTAPI still uses structuresPath as root, but we organize as <root>/<namespace>/structures/
        // This is handled in getStructurePath()
        nbtApi = com.rhett.rhettjs.api.NBTAPI(structuresPath, backupsPath)

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
     * Format: generated/namespace/structures/name.nbt
     */
    private fun getStructurePath(namespace: String, name: String): Path? {
        val basePath = structuresPath ?: return null
        return basePath.resolve(namespace).resolve("structures").resolve("$name.nbt")
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
     * List available structures from resource system (async).
     * Uses StructureTemplateManager to list from world/generated/, datapacks, and mods.
     * Returns Promise<string[]> where each name is in format "namespace:name".
     *
     * @param namespaceFilter Optional namespace filter (null = all namespaces)
     */
    fun list(namespaceFilter: String?): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()
        val srv = server

        if (srv == null) {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            // Execute on main thread to access StructureTemplateManager
            srv.execute {
                try {
                    // List all structures from resource system
                    // This includes: world/generated/, datapacks/, mod resources
                    // Convert Java Stream to Kotlin list first
                    val allTemplates = srv.structureManager.listTemplates()
                        .collect(Collectors.toList())

                    val structures = allTemplates
                        .filter { loc ->
                            // Filter by namespace if specified
                            namespaceFilter == null || loc.namespace == namespaceFilter
                        }
                        .filter { loc ->
                            // Exclude rjs-large pieces from regular list
                            // (they're listed separately via listLarge)
                            !loc.path.startsWith("rjs-large/")
                        }
                        .map { loc -> "${loc.namespace}:${loc.path}" }
                        .sorted()

                    ConfigManager.debug("[StructureManager] Listed ${structures.size} structures from resource system")
                    future.complete(structures)
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
     * Remove a structure file (async).
     * Returns Promise<boolean> (true if removed, false if didn't exist).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun remove(nameWithNamespace: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val path = getStructurePath(namespace, name)

            if (path == null || !path.exists()) {
                future.complete(false)
                return future
            }

            Files.delete(path)
            ConfigManager.debug("[StructureManager] Removed structure: $nameWithNamespace")
            future.complete(true)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Load a structure from resource system (async).
     * Uses StructureTemplateManager to load from world/generated/, datapacks, or mods.
     * Returns Promise<StructureData>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun load(nameWithNamespace: String): CompletableFuture<StructureData> {
        val future = CompletableFuture<StructureData>()
        val srv = server

        if (srv == null) {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            val (namespace, name) = parseStructureName(nameWithNamespace)
            val resourceLocation = ResourceLocation.fromNamespaceAndPath(namespace, name)

            // Execute on main thread to access StructureTemplateManager
            srv.execute {
                try {
                    // Load structure using Minecraft's resource system
                    // This searches: world/generated/, datapacks/, mod resources (in priority order)
                    val templateOpt = srv.structureManager.get(resourceLocation)

                    if (templateOpt.isEmpty) {
                        future.completeExceptionally(IllegalArgumentException("Structure not found: $nameWithNamespace"))
                        return@execute
                    }

                    val template = templateOpt.get()

                    // Convert StructureTemplate to NBT then parse to StructureData
                    val registryAccess = srv.registryAccess()
                    val nbt = template.save(CompoundTag())

                    // Parse structure data from NBT
                    val structureData = parseStructureNBT(nbt)

                    ConfigManager.debug("[StructureManager] Loaded structure from resource system: $nameWithNamespace (${structureData.blocks.size} blocks)")
                    future.complete(structureData)
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
     * Save a structure to file (async) with automatic backup.
     * Returns Promise<void>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param data Structure data to save
     * @param skipBackup If true, skip automatic backup (used for large structure pieces)
     */
    fun save(nameWithNamespace: String, data: StructureData, skipBackup: Boolean = false): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val api = nbtApi
            if (api == null) {
                future.completeExceptionally(IllegalStateException("NBTAPI not initialized"))
                return future
            }

            val (namespace, name) = parseStructureName(nameWithNamespace)

            // Validate structure name contains only valid characters for ResourceLocations
            if (!name.matches(Regex("^[a-z0-9/._-]+$"))) {
                future.completeExceptionally(IllegalArgumentException(
                    "Invalid structure name '$name'. Structure names must contain only lowercase letters, numbers, and characters /._-\n" +
                    "Example: 'my_structure' or 'buildings/house_1'"
                ))
                return future
            }

            // Calculate relative path: namespace/structures/name.nbt
            // This matches datapack format: generated/namespace/structures/name.nbt
            val relativePath = "$namespace/structures/$name.nbt"

            // Convert structure data to JS-friendly map
            val jsData = structureDataToJsMap(data)

            // Write using NBTAPI (handles backups automatically unless skipBackup=true)
            api.write(relativePath, jsData, skipBackup = skipBackup)

            ConfigManager.debug("[StructureManager] Saved structure: $nameWithNamespace (${data.blocks.size} blocks, backup=${!skipBackup})")
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
        // Read size (handle both int array and list formats)
        val size = if (nbt.contains("size", 11)) {
            // TAG_INT_ARRAY (type 11) - our write format
            val sizeList = nbt.getIntArray("size")
            StructureSize(
                x = sizeList[0],
                y = sizeList[1],
                z = sizeList[2]
            )
        } else if (nbt.contains("size", 9)) {
            // TAG_LIST (type 9) - StructureTemplate.save() format
            val sizeList = nbt.getList("size", 3) // 3 = TAG_INT
            if (sizeList.size < 3) {
                throw IllegalArgumentException("Structure NBT 'size' list has ${sizeList.size} elements, expected 3")
            }
            StructureSize(
                x = sizeList.getInt(0),
                y = sizeList.getInt(1),
                z = sizeList.getInt(2)
            )
        } else {
            throw IllegalArgumentException("Structure NBT missing 'size' field")
        }

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

            // Read position (handle both int array and list formats)
            val (posX, posY, posZ) = if (blockNBT.contains("pos", 11)) {
                // TAG_INT_ARRAY (type 11) - our write format
                val pos = blockNBT.getIntArray("pos")
                Triple(pos[0], pos[1], pos[2])
            } else if (blockNBT.contains("pos", 9)) {
                // TAG_LIST (type 9) - StructureTemplate.save() format
                val posList = blockNBT.getList("pos", 3) // 3 = TAG_INT
                Triple(posList.getInt(0), posList.getInt(1), posList.getInt(2))
            } else {
                continue // Skip blocks without valid position
            }

            val state = blockNBT.getInt("state")

            val blockData = palette.getOrNull(state)
            if (blockData != null) {
                blocks.add(
                    PositionedBlock(
                        x = posX,
                        y = posY,
                        z = posZ,
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
     * Convert StructureData to JS-friendly Map format.
     * This format can be passed to NBTAPI.write() which will convert it to NBT.
     */
    private fun structureDataToJsMap(data: StructureData): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        // Write size
        map["size"] = listOf(data.size.x, data.size.y, data.size.z)

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
        val paletteList = mutableListOf<Map<String, Any>>()
        palette.forEach { blockData ->
            val blockMap = mutableMapOf<String, Any>()
            blockMap["Name"] = blockData.name

            if (blockData.properties.isNotEmpty()) {
                blockMap["Properties"] = blockData.properties.toMap()
            }

            paletteList.add(blockMap)
        }
        map["palette"] = paletteList

        // Write blocks
        val blocksList = mutableListOf<Map<String, Any>>()
        data.blocks.forEach { block ->
            val blockMap = mutableMapOf<String, Any>()
            blockMap["pos"] = listOf(block.x, block.y, block.z)
            blockMap["state"] = paletteIndices[block.block] ?: 0

            // Write block entity data if present
            // Note: NBTAPI will handle CompoundTag → Map conversion if needed
            if (block.blockEntityData != null) {
                blockMap["nbt"] = block.blockEntityData as Any
            }

            blocksList.add(blockMap)
        }
        map["blocks"] = blocksList

        // Write metadata
        if (data.metadata.isNotEmpty()) {
            map["metadata"] = data.metadata.toMap()
        }

        // Write data version (current MC version)
        map["DataVersion"] = net.minecraft.SharedConstants.getCurrentVersion().dataVersion.version

        return map
    }

    /**
     * Capture a region and convert to structure data (async).
     * Returns Promise<void> after saving structure file.
     *
     * @param pos1 First corner position {x, y, z, dimension?}
     * @param pos2 Second corner position {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {author?: string, description?: string}
     * @param skipBackup If true, skip automatic backup (used for large structure pieces)
     */
    fun capture(pos1: Value, pos2: Value, nameWithNamespace: String, options: Value?, skipBackup: Boolean = false): CompletableFuture<Void> {
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
                    val saveFuture = save(nameWithNamespace, structureData, skipBackup)
                    saveFuture.whenComplete { _, throwable ->
                        if (throwable != null) {
                            future.completeExceptionally(throwable)
                        } else {
                            ConfigManager.debug("[StructureManager] Captured structure: $nameWithNamespace (${blocks.size} blocks, backup=${!skipBackup})")
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

            val mode = if (options != null && options.hasMember("mode")) {
                options.getMember("mode").asString()
            } else {
                "replace"
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
                        adapter.setBlocksInRegion(level, rotatedBlocks, updateNeighbors = true, mode = mode)

                        ConfigManager.debug("[StructureManager] Placed structure: $nameWithNamespace (${rotatedBlocks.size} blocks, rotation=$rotation, mode=$mode)")
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
     * Backup a large structure directory before modification.
     * Creates timestamped directory backup and maintains retention policy (keeps last 5).
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     * @return true if backup created or skipped (no existing structure), false if error
     */
    private fun backupLargeStructure(namespace: String, baseName: String): Boolean {
        try {
            // Check if source directory exists
            val sourceDir = structuresPath?.resolve(namespace)?.resolve("structures")?.resolve("rjs-large")?.resolve(baseName)
            if (sourceDir == null || !sourceDir.exists() || !Files.isDirectory(sourceDir)) {
                ConfigManager.debug("[StructureManager] No existing large structure to backup: $namespace:$baseName")
                return true // Nothing to backup, not an error
            }

            // Check if there are any .nbt files
            val nbtFiles = Files.list(sourceDir)
                .filter { it.extension == "nbt" }
                .toList()

            if (nbtFiles.isEmpty()) {
                ConfigManager.debug("[StructureManager] No piece files to backup: $namespace:$baseName")
                return true
            }

            // Create backup directory with timestamp
            val backupsPath = structuresPath?.parent?.resolve("backups")?.resolve("structures")?.resolve("rjs-large")
            if (backupsPath == null) {
                ConfigManager.debug("[StructureManager] Backups path not available")
                return false
            }

            val timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val backupDirName = "$baseName.$timestamp"
            val backupDir = backupsPath.resolve(backupDirName)

            // Create backup directory
            Files.createDirectories(backupDir)

            // Copy all .nbt files
            nbtFiles.forEach { file ->
                val targetFile = backupDir.resolve(file.fileName)
                Files.copy(file, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }

            ConfigManager.debug("[StructureManager] Backed up large structure: $namespace:$baseName → $backupDirName (${nbtFiles.size} pieces)")

            // Cleanup old backups (keep only 5 most recent)
            val allBackups = Files.list(backupsPath)
                .filter { Files.isDirectory(it) }
                .filter { it.fileName.toString().startsWith("$baseName.") }
                .sorted(Comparator.reverseOrder()) // Newest first (by name timestamp)
                .toList()

            if (allBackups.size > 5) {
                val toDelete = allBackups.drop(5)
                toDelete.forEach { oldBackup ->
                    Files.walk(oldBackup)
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.delete(it) }
                    ConfigManager.debug("[StructureManager] Deleted old backup: ${oldBackup.fileName}")
                }
            }

            return true
        } catch (e: Exception) {
            ConfigManager.debug("[StructureManager] Failed to backup large structure: ${e.message}")
            return false
        }
    }

    /**
     * Capture a large region split into multiple piece files (async).
     * Returns Promise<void> after saving all piece files.
     *
     * Large structures are stored in: structures/rjs-large/<name>/X_Y_Z.nbt
     * The 0_0_0.nbt file contains metadata.requires[] with required mod namespaces.
     *
     * @param pos1 First corner position {x, y, z, dimension?}
     * @param pos2 Second corner position {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {pieceSize?: {x,y,z}, author?: string, description?: string}
     */
    fun captureLarge(pos1: Value, pos2: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
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

            // Get dimension
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Get piece size (default 48x48x48)
            val pieceSizeX = if (options != null && options.hasMember("pieceSize")) {
                val ps = options.getMember("pieceSize")
                if (ps.hasMember("x")) ps.getMember("x").asInt() else 48
            } else 48

            val pieceSizeY = if (options != null && options.hasMember("pieceSize")) {
                val ps = options.getMember("pieceSize")
                if (ps.hasMember("y")) ps.getMember("y").asInt() else 48
            } else 48

            val pieceSizeZ = if (options != null && options.hasMember("pieceSize")) {
                val ps = options.getMember("pieceSize")
                if (ps.hasMember("z")) ps.getMember("z").asInt() else 48
            } else 48

            // Normalize coordinates (min to max)
            val minX = minOf(x1, x2)
            val minY = minOf(y1, y2)
            val minZ = minOf(z1, z2)
            val maxX = maxOf(x1, x2)
            val maxY = maxOf(y1, y2)
            val maxZ = maxOf(z1, z2)

            val totalSizeX = maxX - minX + 1
            val totalSizeY = maxY - minY + 1
            val totalSizeZ = maxZ - minZ + 1

            // Calculate grid dimensions
            val gridMaxX = (totalSizeX - 1) / pieceSizeX
            val gridMaxY = (totalSizeY - 1) / pieceSizeY
            val gridMaxZ = (totalSizeZ - 1) / pieceSizeZ

            ConfigManager.debug("[StructureManager] Capturing large structure: $nameWithNamespace")
            ConfigManager.debug("[StructureManager] Region: ${totalSizeX}x${totalSizeY}x${totalSizeZ}")
            ConfigManager.debug("[StructureManager] Piece size: ${pieceSizeX}x${pieceSizeY}x${pieceSizeZ}")
            ConfigManager.debug("[StructureManager] Grid: ${gridMaxX + 1}x${gridMaxY + 1}x${gridMaxZ + 1} pieces")

            // Parse structure name
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Validate structure name contains only valid characters for ResourceLocations
            if (!baseName.matches(Regex("^[a-z0-9/._-]+$"))) {
                future.completeExceptionally(IllegalArgumentException(
                    "Invalid structure name '$baseName'. Structure names must contain only lowercase letters, numbers, and characters /._-\n" +
                    "Example: 'my_structure' or 'buildings/house_1'"
                ))
                return future
            }

            // Backup existing large structure (if it exists)
            backupLargeStructure(namespace, baseName)

            // Clean up old pieces in write directory
            val largeStructDir = structuresPath?.resolve(namespace)?.resolve("structures")?.resolve("rjs-large")?.resolve(baseName)
            if (largeStructDir != null && largeStructDir.exists() && Files.isDirectory(largeStructDir)) {
                // Delete all existing .nbt files in the directory
                Files.list(largeStructDir)
                    .filter { it.extension == "nbt" }
                    .forEach { Files.delete(it) }
                ConfigManager.debug("[StructureManager] Cleared write directory for: $nameWithNamespace")
            }

            // Track all required mod namespaces (excluding minecraft)
            val requiredMods = mutableSetOf<String>()

            // Capture each piece
            val captureFutures = mutableListOf<CompletableFuture<Void>>()

            for (gridX in 0..gridMaxX) {
                for (gridY in 0..gridMaxY) {
                    for (gridZ in 0..gridMaxZ) {
                        // Calculate piece bounds
                        val pieceMinX = minX + (gridX * pieceSizeX)
                        val pieceMinY = minY + (gridY * pieceSizeY)
                        val pieceMinZ = minZ + (gridZ * pieceSizeZ)

                        val pieceMaxX = minOf(pieceMinX + pieceSizeX - 1, maxX)
                        val pieceMaxY = minOf(pieceMinY + pieceSizeY - 1, maxY)
                        val pieceMaxZ = minOf(pieceMinZ + pieceSizeZ - 1, maxZ)

                        // Create piece name: rjs-large/<name>/X_Y_Z
                        val pieceName = "$namespace:rjs-large/$baseName/${gridX}_${gridY}_${gridZ}"

                        // Create position values for capture
                        val context = graalContext
                        if (context != null) {
                            context.enter()
                            try {
                                val piecePos1 = context.eval("js", "({x: $pieceMinX, y: $pieceMinY, z: $pieceMinZ, dimension: '$dimension'})")
                                val piecePos2 = context.eval("js", "({x: $pieceMaxX, y: $pieceMaxY, z: $pieceMaxZ, dimension: '$dimension'})")

                                // Capture this piece (skip backup since we backed up the whole directory)
                                val pieceFuture = capture(piecePos1, piecePos2, pieceName, options, skipBackup = true)

                                // Add to futures list
                                captureFutures.add(pieceFuture)

                                // TODO: Collect required mod namespaces from blocks in this piece
                                // For now, we'll add this after all pieces are captured

                            } finally {
                                context.leave()
                            }
                        }
                    }
                }
            }

            // Wait for all pieces to complete
            CompletableFuture.allOf(*captureFutures.toTypedArray()).whenComplete { _, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Now update 0_0_0.nbt with requires[] metadata
                try {
                    val originPath = getStructurePath(namespace, "rjs-large/$baseName/0_0_0")

                    if (originPath != null && originPath.exists()) {
                        // Load 0_0_0.nbt
                        val originNBT = NbtIo.readCompressed(originPath, net.minecraft.nbt.NbtAccounter.unlimitedHeap())

                        // TODO: Scan all pieces to collect required mods
                        // For now, just create empty requires array
                        val metadata = if (originNBT.contains("metadata")) {
                            originNBT.getCompound("metadata")
                        } else {
                            CompoundTag()
                        }

                        // Add requires array (empty for now)
                        val requiresList = ListTag()
                        metadata.put("requires", requiresList)
                        originNBT.put("metadata", metadata)

                        // Write back
                        NbtIo.writeCompressed(originNBT, originPath)

                        ConfigManager.debug("[StructureManager] Large structure captured: $nameWithNamespace")
                        future.complete(null)
                    } else {
                        future.completeExceptionally(IllegalStateException("Origin piece 0_0_0 not found"))
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
     * Place a large structure (async).
     * Returns Promise<void> after placing all pieces.
     *
     * @param position Position to place structure {x, y, z, dimension?}
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param options Optional options {rotation?: 0|90|180|270, centered?: boolean}
     */
    fun placeLarge(position: Value, nameWithNamespace: String, options: Value?): CompletableFuture<Void> {
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

            // Get dimension
            val dimension = if (options != null && options.hasMember("dimension")) {
                options.getMember("dimension").asString()
            } else if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Get rotation (default 0)
            val rotation = if (options != null && options.hasMember("rotation")) {
                options.getMember("rotation").asInt()
            } else {
                0
            }

            // Get centered flag (default false)
            val centered = if (options != null && options.hasMember("centered")) {
                options.getMember("centered").asBoolean()
            } else {
                false
            }

            val mode = if (options != null && options.hasMember("mode")) {
                options.getMember("mode").asString()
            } else {
                "replace"
            }

            // Parse structure name
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Find all pieces using StructureTemplateManager (searches all resource sources)
            srv.execute {
                try {
                    val allTemplates = srv.structureManager.listTemplates()
                        .collect(Collectors.toList())

                    // DEBUG: Log all rjs-large paths for this namespace
                    ConfigManager.debug("[StructureManager] === Debugging placeLarge for $namespace:$baseName ===")
                    ConfigManager.debug("[StructureManager] Looking for paths starting with: rjs-large/$baseName/")

                    val allRjsLargePaths = allTemplates
                        .filter { it.namespace == namespace && it.path.startsWith("rjs-large/") }
                    ConfigManager.debug("[StructureManager] Found ${allRjsLargePaths.size} rjs-large paths in namespace $namespace:")
                    allRjsLargePaths.forEach {
                        ConfigManager.debug("[StructureManager]   - ${it.namespace}:${it.path}")
                    }

                    // Filter for pieces belonging to this large structure
                    val pieceFiles = allTemplates
                        .filter { loc ->
                            val matches = loc.namespace == namespace &&
                                loc.path.startsWith("rjs-large/$baseName/")
                            if (!matches && loc.namespace == namespace && loc.path.startsWith("rjs-large/")) {
                                ConfigManager.debug("[StructureManager] Piece ${loc.path} does NOT match filter rjs-large/$baseName/")
                            }
                            matches
                        }
                        .mapNotNull { loc ->
                            // Extract piece name (e.g., "rjs-large/castle/0_0_0" -> "0_0_0")
                            val pathParts = loc.path.split("/")
                            val pieceName = if (pathParts.size >= 3) pathParts[2] else null
                            ConfigManager.debug("[StructureManager] Extracted piece name: $pieceName from path: ${loc.path}")
                            pieceName
                        }
                        .toList()

                    ConfigManager.debug("[StructureManager] Final pieceFiles list size: ${pieceFiles.size}")
                    ConfigManager.debug("[StructureManager] Piece names: $pieceFiles")

                    if (pieceFiles.isEmpty()) {
                        ConfigManager.debug("[StructureManager] ERROR: No pieces found!")
                        future.completeExceptionally(IllegalArgumentException("Large structure not found or has no pieces: $nameWithNamespace"))
                        return@execute
                    }

                    continueWithPlacement(namespace, baseName, pieceFiles, x, y, z, dimension, rotation, centered, mode, future)
                } catch (e: Exception) {
                    ConfigManager.debug("[StructureManager] Exception during piece discovery: ${e.message}")
                    e.printStackTrace()
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Continue with large structure placement after discovering pieces.
     * Separated to handle async discovery from StructureTemplateManager.
     */
    private fun continueWithPlacement(
        namespace: String,
        baseName: String,
        pieceFiles: List<String>,
        x: Int,
        y: Int,
        z: Int,
        dimension: String,
        rotation: Int,
        centered: Boolean,
        mode: String,
        future: CompletableFuture<Void>
    ) {
        try {

            // Load 0_0_0 to get piece size
            val originLoadFuture = load("$namespace:rjs-large/$baseName/0_0_0")

            originLoadFuture.whenComplete { originData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                try {
                    val pieceSizeX = originData.size.x
                    val pieceSizeY = originData.size.y
                    val pieceSizeZ = originData.size.z

                    // Calculate total size if centered is needed
                    val totalSize = if (centered) {
                        // Find max grid coordinates
                        var maxGridX = 0
                        var maxGridY = 0
                        var maxGridZ = 0

                        pieceFiles.forEach { pieceName ->
                            val parts = pieceName.split("_")
                            if (parts.size == 3) {
                                maxGridX = maxOf(maxGridX, parts[0].toInt())
                                maxGridY = maxOf(maxGridY, parts[1].toInt())
                                maxGridZ = maxOf(maxGridZ, parts[2].toInt())
                            }
                        }

                        // Load max piece to get remainder size
                        val maxPieceLoadFuture = load("$namespace:rjs-large/$baseName/${maxGridX}_${maxGridY}_${maxGridZ}")
                        val maxPieceSize = maxPieceLoadFuture.get() // Blocking wait - TODO: make this async
                        intArrayOf(
                            maxGridX * pieceSizeX + maxPieceSize.size.x,
                            maxGridY * pieceSizeY + maxPieceSize.size.y,
                            maxGridZ * pieceSizeZ + maxPieceSize.size.z
                        )
                    } else {
                        null
                    }

                    // Calculate base position (apply centering if requested)
                    val baseX = if (centered && totalSize != null) x - totalSize[0] / 2 else x
                    val baseY = y
                    val baseZ = if (centered && totalSize != null) z - totalSize[2] / 2 else z

                    // Create position calculator using RotationHelper
                    val posCalc = com.rhett.rhettjs.world.logic.RotationHelper.createPositionCalculator(
                        startPos = intArrayOf(baseX, baseY, baseZ),
                        rotation = rotation,
                        pieceSize = intArrayOf(pieceSizeX, pieceSizeY, pieceSizeZ)
                    )

                    // Place each piece
                    val placeFutures = mutableListOf<CompletableFuture<Void>>()

                    pieceFiles.forEach { pieceName ->
                        val parts = pieceName.split("_")
                        if (parts.size == 3) {
                            val gridX = parts[0].toInt()
                            val gridY = parts[1].toInt()
                            val gridZ = parts[2].toInt()

                            // Calculate world position for this piece
                            val worldPos = posCalc(intArrayOf(gridX, gridY, gridZ))

                            // Create position value for place()
                            val context = graalContext
                            if (context != null) {
                                context.enter()
                                try {
                                    val piecePos = context.eval("js", "({x: ${worldPos[0]}, y: ${worldPos[1]}, z: ${worldPos[2]}, dimension: '$dimension'})")
                                    val pieceOptions = if (rotation != 0 || mode != "replace") {
                                        val optionsObj = mutableMapOf<String, Any>()
                                        if (rotation != 0) optionsObj["rotation"] = rotation
                                        if (mode != "replace") optionsObj["mode"] = mode
                                        val optionsJson = optionsObj.entries.joinToString(", ") { (k, v) ->
                                            if (v is String) "\"$k\": \"$v\"" else "\"$k\": $v"
                                        }
                                        context.eval("js", "({$optionsJson})")
                                    } else {
                                        null
                                    }

                                    // Place this piece
                                    val pieceFuture = place(piecePos, "$namespace:rjs-large/$baseName/$pieceName", pieceOptions)
                                    placeFutures.add(pieceFuture)

                                } finally {
                                    context.leave()
                                }
                            }
                        }
                    }

                    // Wait for all pieces to be placed
                    CompletableFuture.allOf(*placeFutures.toTypedArray()).whenComplete { _, placeThrowable ->
                        if (placeThrowable != null) {
                            future.completeExceptionally(placeThrowable)
                        } else {
                            ConfigManager.debug("[StructureManager] Placed large structure: $namespace:$baseName (${pieceFiles.size} pieces, rotation=$rotation)")
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
    }

    /**
     * Get the size of a structure (regular or large) (async).
     * Returns Promise<{x, y, z}>.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun getSize(nameWithNamespace: String): CompletableFuture<Map<String, Int>> {
        val future = CompletableFuture<Map<String, Int>>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Check if this is a large structure
            val largeDir = structuresPath?.resolve(namespace)?.resolve("structures")?.resolve("rjs-large")?.resolve(baseName)
            val isLarge = largeDir != null && largeDir.exists() && Files.isDirectory(largeDir)

            if (isLarge) {
                // Large structure: calculate from 0_0_0 + max piece
                val originLoadFuture = load("$namespace:rjs-large/$baseName/0_0_0")

                originLoadFuture.whenComplete { originData, throwable ->
                    if (throwable != null) {
                        future.completeExceptionally(throwable)
                        return@whenComplete
                    }

                    try {
                        val pieceSizeX = originData.size.x
                        val pieceSizeY = originData.size.y
                        val pieceSizeZ = originData.size.z

                        // Find max grid coordinates
                        var maxGridX = 0
                        var maxGridY = 0
                        var maxGridZ = 0

                        Files.list(largeDir!!).use { files ->
                            files.filter { it.isRegularFile() && it.extension == "nbt" }
                                .forEach { file ->
                                    val parts = file.nameWithoutExtension.split("_")
                                    if (parts.size == 3) {
                                        maxGridX = maxOf(maxGridX, parts[0].toInt())
                                        maxGridY = maxOf(maxGridY, parts[1].toInt())
                                        maxGridZ = maxOf(maxGridZ, parts[2].toInt())
                                    }
                                }
                        }

                        // Load max piece to get remainder size
                        val maxPieceLoadFuture = load("$namespace:rjs-large/$baseName/${maxGridX}_${maxGridY}_${maxGridZ}")

                        maxPieceLoadFuture.whenComplete { maxPieceData, maxThrowable ->
                            if (maxThrowable != null) {
                                future.completeExceptionally(maxThrowable)
                            } else {
                                // Calculate total size
                                val totalX = maxGridX * pieceSizeX + maxPieceData.size.x
                                val totalY = maxGridY * pieceSizeY + maxPieceData.size.y
                                val totalZ = maxGridZ * pieceSizeZ + maxPieceData.size.z

                                future.complete(mapOf("x" to totalX, "y" to totalY, "z" to totalZ))
                            }
                        }

                    } catch (e: Exception) {
                        future.completeExceptionally(e)
                    }
                }
            } else {
                // Regular structure: just load and return size
                val loadFuture = load(nameWithNamespace)

                loadFuture.whenComplete { data, throwable ->
                    if (throwable != null) {
                        future.completeExceptionally(throwable)
                    } else {
                        future.complete(mapOf("x" to data.size.x, "y" to data.size.y, "z" to data.size.z))
                    }
                }
            }

        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * List large structures from resource system (async).
     * Uses StructureTemplateManager to find rjs-large structures from all sources.
     * Returns Promise<string[]> where each name is in format "namespace:name".
     *
     * @param namespaceFilter Optional namespace filter (null = all namespaces)
     */
    fun listLarge(namespaceFilter: String?): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()
        val srv = server

        if (srv == null) {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }

        try {
            // Execute on main thread to access StructureTemplateManager
            srv.execute {
                try {
                    // List all structures, filter for rjs-large paths
                    // Convert Java Stream to Kotlin list first
                    val allTemplates = srv.structureManager.listTemplates()
                        .collect(Collectors.toList())

                    val largeStructures = allTemplates
                        .filter { loc ->
                            // Filter by namespace if specified
                            (namespaceFilter == null || loc.namespace == namespaceFilter) &&
                            // Only include rjs-large pieces
                            loc.path.startsWith("rjs-large/")
                        }
                        .mapNotNull { loc ->
                            // Extract structure name from path (e.g., "rjs-large/castle/0_0_0" -> "castle")
                            val pathParts = loc.path.split("/")
                            if (pathParts.size >= 2) {
                                val structureName = pathParts[1]
                                "${loc.namespace}:$structureName"
                            } else {
                                null
                            }
                        }
                        .distinct()  // Each structure appears once, even though it has multiple pieces
                        .sorted()

                    ConfigManager.debug("[StructureManager] Listed ${largeStructures.size} large structures from resource system")
                    future.complete(largeStructures)
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
     * Remove a large structure (all pieces) (async).
     * Returns Promise<boolean> (true if removed, false if didn't exist).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun removeLarge(nameWithNamespace: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Get large structure directory
            val largeDir = structuresPath?.resolve(namespace)?.resolve("structures")?.resolve("rjs-large")?.resolve(baseName)
            if (largeDir == null || !largeDir.exists() || !Files.isDirectory(largeDir)) {
                future.complete(false)
                return future
            }

            // Backup before deletion
            backupLargeStructure(namespace, baseName)

            // Delete all files in the directory
            Files.walk(largeDir).use { paths ->
                paths.sorted(Comparator.reverseOrder())
                    .forEach { Files.delete(it) }
            }

            ConfigManager.debug("[StructureManager] Removed large structure: $nameWithNamespace")
            future.complete(true)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * List all unique blocks in a structure with their counts (async).
     * Returns Promise<Map<String, Int>> of blockId → count.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun blocksList(nameWithNamespace: String): CompletableFuture<Map<String, Int>> {
        val future = CompletableFuture<Map<String, Int>>()

        try {
            val loadFuture = load(nameWithNamespace)
            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Count blocks by ID
                val blockCounts = mutableMapOf<String, Int>()
                structureData.blocks.forEach { block ->
                    val blockId = block.block.name
                    blockCounts[blockId] = (blockCounts[blockId] ?: 0) + 1
                }

                future.complete(blockCounts.toSortedMap())
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Extract unique mod namespaces from structure blocks (async).
     * Returns Promise<List<String>> of unique namespaces (e.g., ["minecraft", "terralith"]).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun blocksNamespaces(nameWithNamespace: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val loadFuture = load(nameWithNamespace)
            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Extract unique namespaces
                val namespaces = structureData.blocks
                    .map { block -> block.block.name.substringBefore(':') }
                    .toSet()
                    .sorted()

                future.complete(namespaces)
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Replace blocks in a structure according to replacement map (async).
     * Returns Promise<void> after saving modified structure (with backup).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param replacementMap Map of oldBlockId → newBlockId (e.g., {"terralith:stone": "minecraft:stone"})
     */
    fun blocksReplace(nameWithNamespace: String, replacementMap: Map<String, String>): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val loadFuture = load(nameWithNamespace)
            loadFuture.whenComplete { structureData, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Replace blocks
                val newBlocks = structureData.blocks.map { block ->
                    val oldId = block.block.name
                    val newId = replacementMap[oldId]

                    if (newId != null) {
                        // Replace with new block ID
                        block.copy(
                            block = BlockData(name = newId, properties = block.block.properties)
                        )
                    } else {
                        // Keep original
                        block
                    }
                }

                val newStructureData = structureData.copy(blocks = newBlocks)

                // Save modified structure (with backup)
                val saveFuture = save(nameWithNamespace, newStructureData, skipBackup = false)
                saveFuture.whenComplete { _, saveThrowable ->
                    if (saveThrowable != null) {
                        future.completeExceptionally(saveThrowable)
                    } else {
                        ConfigManager.debug("[StructureManager] Replaced blocks in: $nameWithNamespace (${replacementMap.size} mappings)")
                        future.complete(null)
                    }
                }
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Replace blocks in all pieces of a large structure (async).
     * Also updates metadata.requires[] in 0_0_0.nbt with new namespace requirements.
     * Returns Promise<void> after saving all modified pieces (with backup).
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param replacementMap Map of oldBlockId → newBlockId
     */
    fun blocksReplaceLarge(nameWithNamespace: String, replacementMap: Map<String, String>): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Get large structure directory
            val largeDir = structuresPath?.resolve(namespace)?.resolve("structures")?.resolve("rjs-large")?.resolve(baseName)
            if (largeDir == null || !largeDir.exists() || !Files.isDirectory(largeDir)) {
                future.completeExceptionally(IllegalArgumentException("Large structure not found: $nameWithNamespace"))
                return future
            }

            // Backup before modification
            backupLargeStructure(namespace, baseName)

            // Find all piece files
            val pieceFiles = Files.list(largeDir)
                .filter { it.isRegularFile() && it.extension == "nbt" }
                .map { it.nameWithoutExtension }
                .toList()

            if (pieceFiles.isEmpty()) {
                future.completeExceptionally(IllegalArgumentException("Large structure has no pieces: $nameWithNamespace"))
                return future
            }

            // Replace blocks in each piece
            val replaceFutures = pieceFiles.map { pieceName ->
                val pieceFullName = "$namespace:rjs-large/$baseName/$pieceName"
                blocksReplace(pieceFullName, replacementMap)
            }

            // Wait for all replacements to complete
            CompletableFuture.allOf(*replaceFutures.toTypedArray()).whenComplete { _, throwable ->
                if (throwable != null) {
                    future.completeExceptionally(throwable)
                    return@whenComplete
                }

                // Update metadata.requires[] in 0_0_0.nbt
                try {
                    updateLargeStructureMetadata(namespace, baseName)
                    ConfigManager.debug("[StructureManager] Replaced blocks in large structure: $nameWithNamespace (${pieceFiles.size} pieces, ${replacementMap.size} mappings)")
                    future.complete(null)
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
     * Update metadata.requires[] in 0_0_0.nbt with current namespace requirements.
     * Scans all pieces to find unique namespaces (excluding "minecraft").
     *
     * @param namespace Structure namespace
     * @param baseName Structure base name (without rjs-large prefix)
     */
    private fun updateLargeStructureMetadata(namespace: String, baseName: String) {
        val originPath = getStructurePath(namespace, "rjs-large/$baseName/0_0_0")
        if (originPath == null || !originPath.exists()) {
            ConfigManager.debug("[StructureManager] Warning: 0_0_0.nbt not found for large structure")
            return
        }

        // Load 0_0_0.nbt
        val originNBT = NbtIo.readCompressed(originPath, net.minecraft.nbt.NbtAccounter.unlimitedHeap())

        // Scan all pieces to collect required namespaces
        val largeDir = structuresPath?.resolve(namespace)?.resolve("structures")?.resolve("rjs-large")?.resolve(baseName)
        val allNamespaces = mutableSetOf<String>()

        if (largeDir != null && largeDir.exists()) {
            Files.list(largeDir)
                .filter { it.extension == "nbt" }
                .forEach { pieceFile ->
                    try {
                        val pieceNBT = NbtIo.readCompressed(pieceFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap())
                        val palette = pieceNBT.getList("palette", 10) // 10 = CompoundTag

                        for (i in 0 until palette.size) {
                            val blockTag = palette.getCompound(i)
                            val blockName = blockTag.getString("Name")
                            val blockNamespace = blockName.substringBefore(':')
                            if (blockNamespace != "minecraft") {
                                allNamespaces.add(blockNamespace)
                            }
                        }
                    } catch (e: Exception) {
                        ConfigManager.debug("[StructureManager] Warning: Failed to read piece ${pieceFile.fileName}: ${e.message}")
                    }
                }
        }

        // Update metadata
        val metadata = if (originNBT.contains("metadata")) {
            originNBT.getCompound("metadata")
        } else {
            CompoundTag()
        }

        // Create requires list
        val requiresList = ListTag()
        allNamespaces.sorted().forEach { ns ->
            requiresList.add(net.minecraft.nbt.StringTag.valueOf(ns))
        }
        metadata.put("requires", requiresList)
        originNBT.put("metadata", metadata)

        // Write back (skip backup since we already backed up the directory)
        NbtIo.writeCompressed(originNBT, originPath)

        ConfigManager.debug("[StructureManager] Updated metadata.requires[] in 0_0_0.nbt: ${allNamespaces.sorted()}")
    }

    /**
     * List available backups for a structure (async).
     * Returns Promise<List<String>> of backup timestamps.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun listBackups(nameWithNamespace: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val api = nbtApi
            if (api == null) {
                future.completeExceptionally(IllegalStateException("NBTAPI not initialized"))
                return future
            }

            val (namespace, name) = parseStructureName(nameWithNamespace)
            val relativePath = "$namespace/structures/$name.nbt"

            // Use NBTAPI's backup listing
            val backups = api.listBackups(relativePath)
            future.complete(backups)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Restore structure from backup (async).
     * Returns Promise<void> after restoration.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param timestamp Optional specific backup timestamp (e.g., "2026-01-05_15-30-45"), or null for most recent
     */
    fun restoreBackup(nameWithNamespace: String, timestamp: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val api = nbtApi
            if (api == null) {
                future.completeExceptionally(IllegalStateException("NBTAPI not initialized"))
                return future
            }

            val (namespace, name) = parseStructureName(nameWithNamespace)
            val relativePath = "$namespace/structures/$name.nbt"

            // Use NBTAPI's restore functionality
            val success = api.restoreFromBackup(relativePath, timestamp)
            if (success) {
                ConfigManager.debug("[StructureManager] Restored structure: $nameWithNamespace from backup ${timestamp ?: "latest"}")
                future.complete(null)
            } else {
                future.completeExceptionally(IllegalStateException("Failed to restore structure from backup"))
            }
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * List available directory backups for a large structure (async).
     * Returns Promise<List<String>> of backup timestamps.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     */
    fun listBackupsLarge(nameWithNamespace: String): CompletableFuture<List<String>> {
        val future = CompletableFuture<List<String>>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Get backups directory
            val backupsPath = structuresPath?.parent?.resolve("backups")?.resolve("structures")?.resolve("rjs-large")
            if (backupsPath == null || !backupsPath.exists()) {
                future.complete(emptyList())
                return future
            }

            // Find all backup directories for this structure
            val backupDirs = Files.list(backupsPath)
                .filter { Files.isDirectory(it) }
                .filter { it.fileName.toString().startsWith("$baseName.") }
                .map { dir ->
                    // Extract timestamp from directory name (e.g., "castle.2026-01-05_15-30-45" -> "2026-01-05_15-30-45")
                    dir.fileName.toString().substringAfter("$baseName.")
                }
                .sorted(Comparator.reverseOrder()) // Newest first
                .toList()

            future.complete(backupDirs)
        } catch (e: Exception) {
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Restore large structure from directory backup (async).
     * Returns Promise<void> after restoration.
     *
     * @param nameWithNamespace Structure name in format "[namespace:]name"
     * @param timestamp Optional specific backup timestamp, or null for most recent
     */
    fun restoreBackupLarge(nameWithNamespace: String, timestamp: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        try {
            val (namespace, baseName) = parseStructureName(nameWithNamespace)

            // Get backups directory
            val backupsPath = structuresPath?.parent?.resolve("backups")?.resolve("structures")?.resolve("rjs-large")
            if (backupsPath == null || !backupsPath.exists()) {
                future.completeExceptionally(IllegalArgumentException("No backups found for: $nameWithNamespace"))
                return future
            }

            // Find backup directory
            val backupDir = if (timestamp != null) {
                // Use specific timestamp
                backupsPath.resolve("$baseName.$timestamp")
            } else {
                // Find most recent backup
                Files.list(backupsPath)
                    .filter { Files.isDirectory(it) }
                    .filter { it.fileName.toString().startsWith("$baseName.") }
                    .sorted(Comparator.reverseOrder()) // Newest first
                    .findFirst()
                    .orElse(null)
            }

            if (backupDir == null || !backupDir.exists()) {
                future.completeExceptionally(IllegalArgumentException("Backup not found for: $nameWithNamespace"))
                return future
            }

            // Get target directory
            val targetDir = structuresPath?.resolve(namespace)?.resolve("structures")?.resolve("rjs-large")?.resolve(baseName)
            if (targetDir == null) {
                future.completeExceptionally(IllegalStateException("Target directory not available"))
                return future
            }

            // Create target directory if doesn't exist
            if (!targetDir.exists()) {
                Files.createDirectories(targetDir)
            }

            // Delete existing files in target
            Files.list(targetDir)
                .filter { it.extension == "nbt" }
                .forEach { Files.delete(it) }

            // Copy all .nbt files from backup
            Files.list(backupDir)
                .filter { it.extension == "nbt" }
                .forEach { backupFile ->
                    val targetFile = targetDir.resolve(backupFile.fileName)
                    Files.copy(backupFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }

            ConfigManager.debug("[StructureManager] Restored large structure: $nameWithNamespace from backup ${timestamp ?: "latest"}")
            future.complete(null)
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
