package com.rhett.rhettjs.world

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.adapter.PlayerAdapter
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.world.adapter.WorldAdapter
import com.rhett.rhettjs.world.models.BlockData
import com.rhett.rhettjs.world.models.PositionedBlock
import com.rhett.rhettjs.world.models.Region
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyObject
import java.util.concurrent.CompletableFuture

/**
 * Manager for World API operations with JavaScript.
 *
 * This is the anti-corruption layer between JavaScript and Minecraft world.
 * It ensures:
 * - All world access is on main thread
 * - All operations are async (return CompletableFuture)
 * - No Minecraft types exposed to JavaScript
 * - Pure JS objects using adapters
 *
 * Design principles:
 * - Async for I/O: All world operations return CompletableFuture
 * - Main thread safety: Use server.execute() for all world access
 * - Anti-corruption: Convert all MC types to JS via adapters
 */
object WorldManager {

    @Volatile
    private var server: MinecraftServer? = null

    @Volatile
    private var graalContext: Context? = null

    @Volatile
    private var worldAdapter: WorldAdapter? = null

    /**
     * Set the Minecraft server reference.
     * Called during server startup.
     */
    fun setServer(minecraftServer: MinecraftServer) {
        server = minecraftServer
        worldAdapter = WorldAdapter(minecraftServer)
        ConfigManager.debug("[WorldManager] Minecraft server reference set")
    }

    /**
     * Set the GraalVM context reference.
     * Called when GraalEngine initializes the context.
     */
    fun setContext(context: Context) {
        graalContext = context
        ConfigManager.debug("[WorldManager] GraalVM context reference set")
    }

    /**
     * Get block at position (async).
     * Returns Promise<Block> where Block is {id: string, properties: object}.
     */
    fun getBlock(position: Value): CompletableFuture<Value> {
        val future = CompletableFuture<Value>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val context = graalContext ?: run {
            future.completeExceptionally(IllegalStateException("GraalVM context not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        try {
            // Extract position from JS
            val x = position.getMember("x").asInt()
            val y = position.getMember("y").asInt()
            val z = position.getMember("z").asInt()
            val dimension = if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Execute on main thread
            srv.execute {
                try {
                    val level = adapter.getLevel(dimension)
                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    val blockPos = BlockPos(x, y, z)
                    val blockState = level.getBlockState(blockPos)

                    // Convert to BlockData model
                    val blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockState.block)?.toString()
                        ?: "minecraft:air"

                    val properties = mutableMapOf<String, String>()
                    blockState.values.forEach { (property, value) ->
                        properties[property.name] = value.toString()
                    }

                    // Create JS block object
                    val blockObj = ProxyObject.fromMap(mapOf(
                        "id" to blockId,
                        "properties" to properties
                    ))

                    future.complete(context.asValue(blockObj))
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
     * Set block at position (async).
     * Returns Promise<void>.
     */
    fun setBlock(position: Value, blockId: String, properties: Value?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        try {
            // Extract position from JS
            val x = position.getMember("x").asInt()
            val y = position.getMember("y").asInt()
            val z = position.getMember("z").asInt()
            val dimension = if (position.hasMember("dimension")) {
                position.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Extract properties if provided
            val propsMap = mutableMapOf<String, String>()
            if (properties != null && properties.hasMembers()) {
                properties.memberKeys.forEach { key ->
                    propsMap[key] = properties.getMember(key).asString()
                }
            }

            // Execute on main thread
            srv.execute {
                try {
                    val level = adapter.getLevel(dimension)
                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    // Create PositionedBlock
                    val block = PositionedBlock(
                        x = x,
                        y = y,
                        z = z,
                        block = BlockData(name = blockId, properties = propsMap),
                        blockEntityData = null
                    )

                    // Place block using adapter
                    adapter.setBlocksInRegion(level, listOf(block), updateNeighbors = true)

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
     * Fill region with blocks (async).
     * Returns Promise<number> (count of blocks placed).
     */
    fun fill(pos1: Value, pos2: Value, blockId: String): CompletableFuture<Int> {
        val future = CompletableFuture<Int>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        try {
            // Extract positions
            val x1 = pos1.getMember("x").asInt()
            val y1 = pos1.getMember("y").asInt()
            val z1 = pos1.getMember("z").asInt()
            val x2 = pos2.getMember("x").asInt()
            val y2 = pos2.getMember("y").asInt()
            val z2 = pos2.getMember("z").asInt()

            val dimension = if (pos1.hasMember("dimension")) {
                pos1.getMember("dimension").asString()
            } else {
                "minecraft:overworld"
            }

            // Execute on main thread
            srv.execute {
                try {
                    val level = adapter.getLevel(dimension)
                    if (level == null) {
                        future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dimension"))
                        return@execute
                    }

                    // Create region
                    val region = Region.fromCorners(x1, y1, z1, x2, y2, z2)

                    // Create blocks list
                    val blocks = mutableListOf<PositionedBlock>()
                    for (x in region.minX..region.maxX) {
                        for (y in region.minY..region.maxY) {
                            for (z in region.minZ..region.maxZ) {
                                blocks.add(
                                    PositionedBlock(
                                        x = x,
                                        y = y,
                                        z = z,
                                        block = BlockData(name = blockId, properties = emptyMap()),
                                        blockEntityData = null
                                    )
                                )
                            }
                        }
                    }

                    // Place blocks using adapter
                    adapter.setBlocksInRegion(level, blocks, updateNeighbors = false)

                    future.complete(blocks.size)
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
     * Get all online players as wrapped JS objects (async).
     * Returns Promise<Player[]>.
     */
    fun getPlayers(): CompletableFuture<List<Value>> {
        val future = CompletableFuture<List<Value>>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val context = graalContext ?: run {
            future.completeExceptionally(IllegalStateException("GraalVM context not available"))
            return future
        }

        srv.execute {
            try {
                val players = srv.playerList.players.map { player ->
                    PlayerAdapter.toJS(player, context)
                }
                future.complete(players)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Get player by name or UUID (async).
     * Returns Promise<Player | null>.
     */
    fun getPlayer(nameOrUuid: String): CompletableFuture<Value?> {
        val future = CompletableFuture<Value?>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val context = graalContext ?: run {
            future.completeExceptionally(IllegalStateException("GraalVM context not available"))
            return future
        }

        srv.execute {
            try {
                // Try to find by name first
                var player = srv.playerList.getPlayerByName(nameOrUuid)

                // If not found by name, try UUID
                if (player == null) {
                    try {
                        val uuid = java.util.UUID.fromString(nameOrUuid)
                        player = srv.playerList.getPlayer(uuid)
                    } catch (e: IllegalArgumentException) {
                        // Not a valid UUID
                    }
                }

                val result = player?.let { PlayerAdapter.toJS(it, context) }
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Get current time in dimension (async).
     * Returns Promise<number> (ticks).
     */
    fun getTime(dimension: String?): CompletableFuture<Long> {
        val future = CompletableFuture<Long>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        srv.execute {
            try {
                val dim = dimension ?: "minecraft:overworld"
                val level = adapter.getLevel(dim)
                if (level == null) {
                    future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                    return@execute
                }

                future.complete(level.dayTime)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Set time in dimension (async).
     * Returns Promise<void>.
     */
    fun setTime(time: Long, dimension: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        srv.execute {
            try {
                val dim = dimension ?: "minecraft:overworld"
                val level = adapter.getLevel(dim)
                if (level == null) {
                    future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                    return@execute
                }

                level.dayTime = time
                future.complete(null)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Get current weather in dimension (async).
     * Returns Promise<string> ("clear", "rain", or "thunder").
     */
    fun getWeather(dimension: String?): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        srv.execute {
            try {
                val dim = dimension ?: "minecraft:overworld"
                val level = adapter.getLevel(dim)
                if (level == null) {
                    future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                    return@execute
                }

                val weather = when {
                    level.isThundering -> "thunder"
                    level.isRaining -> "rain"
                    else -> "clear"
                }

                future.complete(weather)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Set weather in dimension (async).
     * Returns Promise<void>.
     */
    fun setWeather(weather: String, dimension: String?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        val srv = server ?: run {
            future.completeExceptionally(IllegalStateException("Server not available"))
            return future
        }
        val adapter = worldAdapter ?: run {
            future.completeExceptionally(IllegalStateException("WorldAdapter not initialized"))
            return future
        }

        srv.execute {
            try {
                val dim = dimension ?: "minecraft:overworld"
                val level = adapter.getLevel(dim)
                if (level == null) {
                    future.completeExceptionally(IllegalArgumentException("Unknown dimension: $dim"))
                    return@execute
                }

                when (weather.lowercase()) {
                    "clear" -> {
                        level.setWeatherParameters(6000, 0, false, false)
                    }
                    "rain" -> {
                        level.setWeatherParameters(0, 6000, true, false)
                    }
                    "thunder" -> {
                        level.setWeatherParameters(0, 6000, true, true)
                    }
                    else -> {
                        future.completeExceptionally(IllegalArgumentException("Invalid weather: $weather (must be clear, rain, or thunder)"))
                        return@execute
                    }
                }

                future.complete(null)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Get entities in radius (async).
     * Returns Promise<Entity[]>.
     * TODO: Implement entity wrapping with EntityAdapter.
     */
    fun getEntities(position: Value, radius: Double): CompletableFuture<List<Value>> {
        val future = CompletableFuture<List<Value>>()
        // TODO: Implement entity queries
        future.completeExceptionally(UnsupportedOperationException("World.getEntities() not yet implemented"))
        return future
    }

    /**
     * Spawn entity at position (async).
     * Returns Promise<Entity>.
     * TODO: Implement entity spawning.
     */
    fun spawnEntity(position: Value, entityId: String, nbt: Value?): CompletableFuture<Value> {
        val future = CompletableFuture<Value>()
        // TODO: Implement entity spawning
        future.completeExceptionally(UnsupportedOperationException("World.spawnEntity() not yet implemented"))
        return future
    }

    /**
     * Get a ServerLevel by dimension name.
     * Returns null if dimension doesn't exist.
     * Exposed for use by other managers (e.g., StructureManager).
     */
    fun getLevel(dimension: String): ServerLevel? {
        return worldAdapter?.getLevel(dimension)
    }

    /**
     * Get all available dimension names.
     * Returns list of dimension resource locations (e.g., "minecraft:overworld", "rhettjs:structure-test").
     */
    fun getDimensions(): List<String> {
        val srv = server ?: return listOf("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")

        return srv.levelKeys().map { dimensionKey ->
            dimensionKey.location().toString()
        }.sorted()
    }

    /**
     * Clear all state (called on reset/reload).
     * Clears context reference (context will be recreated).
     */
    fun reset() {
        graalContext = null
        ConfigManager.debug("[WorldManager] Reset complete")
    }
}
