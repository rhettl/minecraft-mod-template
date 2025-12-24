package com.rhett.rhettjs.api

import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptRuntime

/**
 * JavaScript-accessible wrapper for World API.
 *
 * Provides world-specific methods as a JavaScript object:
 *   World.grab(world, x1, y1, z1, x2, y2, z2)
 *   World.grabToFile(world, x1, y1, z1, x2, y2, z2, filename, subdirectory?)
 *   World.grabLarge(world, x1, y1, z1, x2, y2, z2, name, pieceSize?, namespace?)
 *   World.listLarge(namespace?)
 *
 * Thread-safe: All operations automatically execute on main thread via server.execute().
 */
class WorldAPIWrapper(
    private val worldApi: WorldAPI
) : ScriptableObject() {

    override fun getClassName(): String = "World"

    init {
        // Add methods as BaseFunction objects
        val grabFn = GrabFunction(worldApi)
        val grabToFileFn = GrabToFileFunction(worldApi)
        val grabLargeFn = GrabLargeFunction(worldApi)
        val listLargeFn = ListLargeFunction(worldApi)
        val placeLargeFn = PlaceLargeFunction(worldApi)
        val getLargeMetadataFn = GetLargeMetadataFunction(worldApi)
        val blocksListLargeFn = BlocksListLargeFunction(worldApi)
        val blocksReplaceLargeFn = BlocksReplaceLargeFunction(worldApi)
        val blocksReplaceLargeVanillaFn = BlocksReplaceLargeVanillaFunction(worldApi)

        put("grab", this, grabFn)
        put("grabToFile", this, grabToFileFn)
        put("grabLarge", this, grabLargeFn)
        put("listLarge", this, listLargeFn)
        put("placeLarge", this, placeLargeFn)
        put("getLargeMetadata", this, getLargeMetadataFn)
        put("blocksListLarge", this, blocksListLargeFn)
        put("blocksReplaceLarge", this, blocksReplaceLargeFn)
        put("blocksReplaceLargeVanilla", this, blocksReplaceLargeVanillaFn)
    }

    override fun setParentScope(scope: Scriptable?) {
        super.setParentScope(scope)

        // Propagate parent scope to all child functions
        (get("grab", this) as? BaseFunction)?.setParentScope(scope)
        (get("grabToFile", this) as? BaseFunction)?.setParentScope(scope)
        (get("grabLarge", this) as? BaseFunction)?.setParentScope(scope)
        (get("listLarge", this) as? BaseFunction)?.setParentScope(scope)
        (get("placeLarge", this) as? BaseFunction)?.setParentScope(scope)
        (get("getLargeMetadata", this) as? BaseFunction)?.setParentScope(scope)
        (get("blocksListLarge", this) as? BaseFunction)?.setParentScope(scope)
        (get("blocksReplaceLarge", this) as? BaseFunction)?.setParentScope(scope)
        (get("blocksReplaceLargeVanilla", this) as? BaseFunction)?.setParentScope(scope)
    }

    /**
     * Grab function implementation.
     */
    private class GrabFunction(private val worldApi: WorldAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 7) {
                throw ScriptRuntime.typeError("World.grab() requires 7 arguments: world, x1, y1, z1, x2, y2, z2")
            }

            val world = ScriptRuntime.toString(args[0])
            val x1 = ScriptRuntime.toInt32(args[1])
            val y1 = ScriptRuntime.toInt32(args[2])
            val z1 = ScriptRuntime.toInt32(args[3])
            val x2 = ScriptRuntime.toInt32(args[4])
            val y2 = ScriptRuntime.toInt32(args[5])
            val z2 = ScriptRuntime.toInt32(args[6])

            val data = worldApi.grab(world, x1, y1, z1, x2, y2, z2)

            return if (data != null) {
                convertToJS(cx, scope, data)
            } else {
                null
            }
        }

        /**
         * Recursively convert Kotlin data structures to JavaScript objects.
         */
        private fun convertToJS(cx: Context, scope: Scriptable, value: Any?): Any? {
            return when (value) {
                null -> null
                is Map<*, *> -> {
                    val jsObj = cx.newObject(scope)
                    value.forEach { (k, v) ->
                        jsObj.put(k.toString(), jsObj, convertToJS(cx, scope, v))
                    }
                    jsObj
                }
                is List<*> -> {
                    val jsArray = cx.newArray(scope, value.size)
                    value.forEachIndexed { index, item ->
                        jsArray.put(index, jsArray, convertToJS(cx, scope, item))
                    }
                    jsArray
                }
                is String, is Number, is Boolean -> value
                else -> Context.javaToJS(value, scope)
            }
        }
    }

    /**
     * GrabToFile function implementation.
     */
    private class GrabToFileFunction(private val worldApi: WorldAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 8) {
                throw ScriptRuntime.typeError(
                    "World.grabToFile() requires at least 8 arguments: " +
                    "world, x1, y1, z1, x2, y2, z2, filename [, outputDir]"
                )
            }

            val world = ScriptRuntime.toString(args[0])
            val x1 = ScriptRuntime.toInt32(args[1])
            val y1 = ScriptRuntime.toInt32(args[2])
            val z1 = ScriptRuntime.toInt32(args[3])
            val x2 = ScriptRuntime.toInt32(args[4])
            val y2 = ScriptRuntime.toInt32(args[5])
            val z2 = ScriptRuntime.toInt32(args[6])
            val filename = ScriptRuntime.toString(args[7])
            val outputDir = if (args.size > 8 && args[8] != null) {
                ScriptRuntime.toString(args[8])
            } else {
                null
            }

            val path = worldApi.grabToFile(world, x1, y1, z1, x2, y2, z2, filename, outputDir)

            return path
        }
    }

    /**
     * GrabLarge function implementation.
     */
    private class GrabLargeFunction(private val worldApi: WorldAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 8) {
                throw ScriptRuntime.typeError(
                    "World.grabLarge() requires at least 8 arguments: " +
                    "world, x1, y1, z1, x2, y2, z2, name [, pieceSize, namespace]"
                )
            }

            val world = ScriptRuntime.toString(args[0])
            val x1 = ScriptRuntime.toInt32(args[1])
            val y1 = ScriptRuntime.toInt32(args[2])
            val z1 = ScriptRuntime.toInt32(args[3])
            val x2 = ScriptRuntime.toInt32(args[4])
            val y2 = ScriptRuntime.toInt32(args[5])
            val z2 = ScriptRuntime.toInt32(args[6])
            val name = ScriptRuntime.toString(args[7])

            // Parse pieceSize as [x, z] array
            val pieceSize = if (args.size > 8 && args[8] != null) {
                val pieceSizeArg = args[8]
                if (pieceSizeArg is Scriptable) {
                    // It's an array
                    val length = ScriptRuntime.toInt32(pieceSizeArg.get("length", pieceSizeArg))
                    if (length >= 2) {
                        intArrayOf(
                            ScriptRuntime.toInt32(pieceSizeArg.get(0, pieceSizeArg)),
                            ScriptRuntime.toInt32(pieceSizeArg.get(1, pieceSizeArg))
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }

            val namespace = if (args.size > 9 && args[9] != null) {
                ScriptRuntime.toString(args[9])
            } else {
                null
            }

            val metadata = worldApi.grabLarge(
                world, x1, y1, z1, x2, y2, z2,
                name, pieceSize, namespace
            )

            return convertToJS(cx, scope, metadata)
        }

        private fun convertToJS(cx: Context, scope: Scriptable, value: Any?): Any? {
            return when (value) {
                null -> null
                is Map<*, *> -> {
                    val jsObj = cx.newObject(scope)
                    value.forEach { (k, v) ->
                        jsObj.put(k.toString(), jsObj, convertToJS(cx, scope, v))
                    }
                    jsObj
                }
                is List<*> -> {
                    val jsArray = cx.newArray(scope, value.size)
                    value.forEachIndexed { index, item ->
                        jsArray.put(index, jsArray, convertToJS(cx, scope, item))
                    }
                    jsArray
                }
                is String, is Number, is Boolean -> value
                else -> Context.javaToJS(value, scope)
            }
        }
    }

    /**
     * ListLarge function implementation.
     */
    private class ListLargeFunction(private val worldApi: WorldAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            val namespace = if (args.isNotEmpty() && args[0] != null) {
                ScriptRuntime.toString(args[0])
            } else {
                null
            }

            val structures = worldApi.listLarge(namespace)

            return convertToJS(cx, scope, structures)
        }

        private fun convertToJS(cx: Context, scope: Scriptable, value: Any?): Any? {
            return when (value) {
                null -> null
                is Map<*, *> -> {
                    val jsObj = cx.newObject(scope)
                    value.forEach { (k, v) ->
                        jsObj.put(k.toString(), jsObj, convertToJS(cx, scope, v))
                    }
                    jsObj
                }
                is List<*> -> {
                    val jsArray = cx.newArray(scope, value.size)
                    value.forEachIndexed { index, item ->
                        jsArray.put(index, jsArray, convertToJS(cx, scope, item))
                    }
                    jsArray
                }
                is String, is Number, is Boolean -> value
                else -> Context.javaToJS(value, scope)
            }
        }
    }

    /**
     * PlaceLarge function implementation.
     * Usage: World.placeLarge(world, x, y, z, namespace, name, rotation?)
     */
    private class PlaceLargeFunction(private val worldApi: WorldAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 6) {
                throw ScriptRuntime.typeError(
                    "World.placeLarge() requires at least 6 arguments: " +
                    "world, x, y, z, namespace, name [, rotation]"
                )
            }

            val world = ScriptRuntime.toString(args[0])
            val x = ScriptRuntime.toInt32(args[1])
            val y = ScriptRuntime.toInt32(args[2])
            val z = ScriptRuntime.toInt32(args[3])
            val namespace = ScriptRuntime.toString(args[4])
            val name = ScriptRuntime.toString(args[5])
            val rotation = if (args.size > 6 && args[6] != null) {
                ScriptRuntime.toInt32(args[6])
            } else {
                0
            }

            val result = worldApi.placeLarge(world, x, y, z, namespace, name, rotation)

            return convertToJS(cx, scope, result)
        }

        private fun convertToJS(cx: Context, scope: Scriptable, value: Any?): Any? {
            return when (value) {
                null -> null
                is Map<*, *> -> {
                    val jsObj = cx.newObject(scope)
                    value.forEach { (k, v) ->
                        jsObj.put(k.toString(), jsObj, convertToJS(cx, scope, v))
                    }
                    jsObj
                }
                is List<*> -> {
                    val jsArray = cx.newArray(scope, value.size)
                    value.forEachIndexed { index, item ->
                        jsArray.put(index, jsArray, convertToJS(cx, scope, item))
                    }
                    jsArray
                }
                is String, is Number, is Boolean -> value
                else -> Context.javaToJS(value, scope)
            }
        }
    }

    /**
     * GetLargeMetadata function implementation.
     * Usage: World.getLargeMetadata(namespace, name)
     */
    private class GetLargeMetadataFunction(private val worldApi: WorldAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 2) {
                throw ScriptRuntime.typeError(
                    "World.getLargeMetadata() requires 2 arguments: namespace, name"
                )
            }

            val namespace = ScriptRuntime.toString(args[0])
            val name = ScriptRuntime.toString(args[1])

            val metadata = worldApi.getLargeMetadata(namespace, name)

            return if (metadata != null) {
                convertToJS(cx, scope, metadata)
            } else {
                null
            }
        }

        private fun convertToJS(cx: Context, scope: Scriptable, value: Any?): Any? {
            return when (value) {
                null -> null
                is Map<*, *> -> {
                    val jsObj = cx.newObject(scope)
                    value.forEach { (k, v) ->
                        jsObj.put(k.toString(), jsObj, convertToJS(cx, scope, v))
                    }
                    jsObj
                }
                is List<*> -> {
                    val jsArray = cx.newArray(scope, value.size)
                    value.forEachIndexed { index, item ->
                        jsArray.put(index, jsArray, convertToJS(cx, scope, item))
                    }
                    jsArray
                }
                is String, is Number, is Boolean -> value
                else -> Context.javaToJS(value, scope)
            }
        }
    }

    /**
     * BlocksListLarge function - list blocks in large structure with counts
     */
    private class BlocksListLargeFunction(private val worldApi: WorldAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 2) {
                throw ScriptRuntime.typeError("blocksListLarge() requires 2 arguments: namespace, name")
            }

            val namespace = ScriptRuntime.toString(args[0])
            val name = ScriptRuntime.toString(args[1])

            val blockCounts = worldApi.blocksListLarge(namespace, name)

            // Convert map to JavaScript object
            val jsObj = cx.newObject(scope)
            blockCounts.forEach { (blockId, count) ->
                jsObj.put(blockId, jsObj, count)
            }
            return jsObj
        }
    }

    /**
     * BlocksReplaceLarge function - replace blocks in large structure
     */
    private class BlocksReplaceLargeFunction(private val worldApi: WorldAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 3) {
                throw ScriptRuntime.typeError("blocksReplaceLarge() requires 3 arguments: namespace, name, replacementMap")
            }

            val namespace = ScriptRuntime.toString(args[0])
            val name = ScriptRuntime.toString(args[1])

            // Convert JS object to Kotlin map
            val jsMap = args[2] as? Scriptable
                ?: throw ScriptRuntime.typeError("Third argument must be an object")

            val replacementMap = mutableMapOf<String, String>()
            jsMap.ids.forEach { id ->
                val key = id.toString()
                val value = jsMap.get(key, jsMap)
                if (value != Scriptable.NOT_FOUND) {
                    replacementMap[key] = ScriptRuntime.toString(value)
                }
            }

            val piecesModified = worldApi.blocksReplaceLarge(namespace, name, replacementMap)
            return piecesModified
        }
    }

    /**
     * BlocksReplaceLargeVanilla function - replace modded blocks with vanilla in large structure
     */
    private class BlocksReplaceLargeVanillaFunction(private val worldApi: WorldAPI) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 2) {
                throw ScriptRuntime.typeError("blocksReplaceLargeVanilla() requires at least 2 arguments: namespace, name")
            }

            val namespace = ScriptRuntime.toString(args[0])
            val name = ScriptRuntime.toString(args[1])

            // Optional type overrides
            val typeOverrides = if (args.size > 2 && args[2] != null && args[2] != org.mozilla.javascript.Undefined.instance) {
                val jsMap = args[2] as? Scriptable
                    ?: throw ScriptRuntime.typeError("Third argument must be an object")

                val overrides = mutableMapOf<String, String>()
                jsMap.ids.forEach { id ->
                    val key = id.toString()
                    val value = jsMap.get(key, jsMap)
                    if (value != Scriptable.NOT_FOUND) {
                        overrides[key] = ScriptRuntime.toString(value)
                    }
                }
                overrides
            } else {
                null
            }

            val result = worldApi.blocksReplaceLargeVanilla(namespace, name, typeOverrides)

            // Convert result to JavaScript object
            return convertToJS(cx, scope, result)
        }

        private fun convertToJS(cx: Context, scope: Scriptable, value: Any?): Any? {
            return when (value) {
                null -> null
                is Map<*, *> -> {
                    val jsObj = cx.newObject(scope)
                    value.forEach { (k, v) ->
                        jsObj.put(k.toString(), jsObj, convertToJS(cx, scope, v))
                    }
                    jsObj
                }
                is List<*> -> {
                    val jsArray = cx.newArray(scope, value.size)
                    value.forEachIndexed { index, item ->
                        jsArray.put(index, jsArray, convertToJS(cx, scope, item))
                    }
                    jsArray
                }
                is String, is Number, is Boolean -> value
                else -> Context.javaToJS(value, scope)
            }
        }
    }
}