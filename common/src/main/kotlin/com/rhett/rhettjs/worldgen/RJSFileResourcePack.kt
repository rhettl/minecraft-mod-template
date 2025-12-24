package com.rhett.rhettjs.worldgen

import com.rhett.rhettjs.RhettJSCommon
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionSerializer
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.IoSupplier
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isReadable

/**
 * File-based resource pack that wraps the rjs/data/ directory.
 *
 * Allows Minecraft to load dimension JSONs and other datapack files
 * from rjs/data/ without requiring symlinks or copying.
 */
class RJSFileResourcePack(
    private val rjsDirectory: Path,
    private val packType: PackType
) : PackResources {

    private val dataDirectory = rjsDirectory.resolve(packType.directory)
    private val namespaces: Set<String> by lazy { loadNamespaces() }
    private val locationInfo = PackLocationInfo(
        "rhettjs",
        Component.literal("RhettJS Datapack"),
        PackSource.BUILT_IN,
        Optional.empty()
    )

    private fun loadNamespaces(): Set<String> {
        if (!dataDirectory.exists() || !dataDirectory.isDirectory()) {
            return emptySet()
        }

        return try {
            Files.list(dataDirectory)
                .filter { Files.isDirectory(it) }
                .map { it.fileName.toString() }
                .toList()
                .toSet()
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to load namespaces from ${dataDirectory}", e)
            emptySet()
        }
    }

    override fun getRootResource(vararg path: String): IoSupplier<InputStream>? {
        // Serve pack.mcmeta if requested
        if (path.size == 1 && path[0] == "pack.mcmeta") {
            val packMeta = rjsDirectory.resolve("pack.mcmeta")
            if (packMeta.exists() && packMeta.isRegularFile()) {
                return IoSupplier {
                    Files.newInputStream(packMeta)
                }
            }
        }
        return null
    }

    override fun getResource(packType: PackType, location: ResourceLocation): IoSupplier<InputStream>? {
        if (packType != this.packType) {
            return null
        }

        // Construct path: rjs/data/<namespace>/<path>
        val filePath = dataDirectory
            .resolve(location.namespace)
            .resolve(location.path)

        if (filePath.exists() && filePath.isRegularFile() && filePath.isReadable()) {
            return IoSupplier {
                Files.newInputStream(filePath)
            }
        }

        return null
    }

    override fun listResources(
        packType: PackType,
        namespace: String,
        path: String,
        visitor: PackResources.ResourceOutput
    ) {
        if (packType != this.packType) {
            return
        }

        val namespacePath = dataDirectory.resolve(namespace)
        if (!namespacePath.exists() || !namespacePath.isDirectory()) {
            return
        }

        val searchPath = if (path.isEmpty() || path == "/") {
            namespacePath
        } else {
            namespacePath.resolve(path.removePrefix("/"))
        }

        if (!searchPath.exists()) {
            return
        }

        try {
            Files.walk(searchPath)
                .filter { it.isRegularFile() && it.isReadable() }
                .forEach { file ->
                    val relativePath = namespacePath.relativize(file).toString().replace('\\', '/')
                    val location = ResourceLocation.fromNamespaceAndPath(namespace, relativePath)

                    visitor.accept(location, IoSupplier {
                        Files.newInputStream(file)
                    })
                }
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Failed to list resources in $searchPath", e)
        }
    }

    override fun getNamespaces(type: PackType): Set<String> {
        return if (type == packType) namespaces else emptySet()
    }

    override fun <T> getMetadataSection(serializer: MetadataSectionSerializer<T>): T? {
        return null
    }

    override fun packId(): String {
        return "RhettJS File Pack [${packType.directory}]"
    }

    override fun location(): PackLocationInfo {
        return locationInfo
    }

    override fun close() {
        // No resources to close
    }
}
