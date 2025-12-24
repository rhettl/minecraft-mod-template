package com.rhett.rhettjs.api

import net.minecraft.server.MinecraftServer
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.CompletableFuture

/**
 * JavaScript-accessible wrapper for Command API.
 *
 * Provides command execution methods as a JavaScript object:
 *   Command.execute(command) → Promise<CommandResult>
 *   Command.executeAt(x, y, z, command) → Promise<CommandResult>
 *   Command.executeAsServer(command) → Promise<CommandResult>
 *   Command.suggest(partialCommand) → Promise<string[]>
 *
 * All methods return Promises for async execution on server thread.
 *
 * Example usage:
 * ```javascript
 * Command.execute("time query daytime")
 *   .then(result => {
 *     console.log("Success:", result.success);
 *     console.log("Feedback:", result.feedback);
 *   })
 *   .catch(error => {
 *     console.error("Command failed:", error);
 *   });
 * ```
 */
class CommandAPIWrapper(
    private val commandApi: CommandAPI,
    private val server: MinecraftServer
) : ScriptableObject() {

    override fun getClassName(): String = "Command"

    init {
        // Add methods as BaseFunction objects
        val executeFn = ExecuteFunction(commandApi, server)
        val executeAtFn = ExecuteAtFunction(commandApi, server)
        val executeAsServerFn = ExecuteAsServerFunction(commandApi, server)
        val suggestFn = SuggestFunction(commandApi, server)

        put("execute", this, executeFn)
        put("executeAt", this, executeAtFn)
        put("executeAsServer", this, executeAsServerFn)
        put("suggest", this, suggestFn)
    }

    override fun setParentScope(scope: Scriptable?) {
        super.setParentScope(scope)

        // Propagate parent scope to all child functions
        (get("execute", this) as? BaseFunction)?.setParentScope(scope)
        (get("executeAt", this) as? BaseFunction)?.setParentScope(scope)
        (get("executeAsServer", this) as? BaseFunction)?.setParentScope(scope)
        (get("suggest", this) as? BaseFunction)?.setParentScope(scope)
    }

    /**
     * Execute function implementation.
     */
    private class ExecuteFunction(
        private val commandApi: CommandAPI,
        private val server: MinecraftServer
    ) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.isEmpty()) {
                throw ScriptRuntime.typeError("Command.execute() requires 1 argument: command")
            }

            val command = ScriptRuntime.toString(args[0])
            val future = commandApi.execute(command)

            return createPromiseFromFuture(cx, scope, future, server)
        }
    }

    /**
     * ExecuteAt function implementation.
     */
    private class ExecuteAtFunction(
        private val commandApi: CommandAPI,
        private val server: MinecraftServer
    ) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.size < 4) {
                throw ScriptRuntime.typeError("Command.executeAt() requires 4 arguments: x, y, z, command")
            }

            val x = ScriptRuntime.toNumber(args[0])
            val y = ScriptRuntime.toNumber(args[1])
            val z = ScriptRuntime.toNumber(args[2])
            val command = ScriptRuntime.toString(args[3])

            val future = commandApi.executeAt(x, y, z, command)

            return createPromiseFromFuture(cx, scope, future, server)
        }
    }

    /**
     * ExecuteAsServer function implementation.
     */
    private class ExecuteAsServerFunction(
        private val commandApi: CommandAPI,
        private val server: MinecraftServer
    ) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.isEmpty()) {
                throw ScriptRuntime.typeError("Command.executeAsServer() requires 1 argument: command")
            }

            val command = ScriptRuntime.toString(args[0])
            val future = commandApi.executeAsServer(command)

            return createPromiseFromFuture(cx, scope, future, server)
        }
    }

    /**
     * Suggest function implementation.
     */
    private class SuggestFunction(
        private val commandApi: CommandAPI,
        private val server: MinecraftServer
    ) : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any? {
            if (args.isEmpty()) {
                throw ScriptRuntime.typeError("Command.suggest() requires 1 argument: partialCommand")
            }

            val partialCommand = ScriptRuntime.toString(args[0])
            val future = commandApi.suggest(partialCommand)

            return createPromiseFromFuture(cx, scope, future, server)
        }
    }

    companion object {
        /**
         * Create a JavaScript Promise from a CompletableFuture.
         * The Promise resolves/rejects on the server thread when the future completes.
         */
        private fun <T> createPromiseFromFuture(
            cx: Context,
            scope: Scriptable,
            future: CompletableFuture<T>,
            server: MinecraftServer
        ): Scriptable {
            // Create a container object to hold resolve/reject functions
            val container = cx.newObject(scope)

            // Create a Promise using JavaScript's Promise constructor
            val promiseScript = """
                (function(container) {
                    return new Promise(function(resolve, reject) {
                        container.resolve = resolve;
                        container.reject = reject;
                    });
                })
            """.trimIndent()

            val promiseFactory = cx.evaluateString(scope, promiseScript, "command-promise-factory", 1, null) as Function
            val promise = promiseFactory.call(cx, scope, scope, arrayOf(container)) as Scriptable

            // Get the resolve and reject functions from the container
            val resolve = container.get("resolve", container) as Function
            val reject = container.get("reject", container) as Function

            // Handle future completion on server thread
            future.whenComplete { result, error ->
                server.execute {
                    // Get a context for this thread
                    val context = Context.enter()
                    try {
                        if (error != null) {
                            // Future failed - reject the promise
                            val errorObj = Context.javaToJS(error, scope)
                            reject.call(context, scope, scope, arrayOf(errorObj))
                        } else {
                            // Future succeeded - resolve with result
                            val jsResult = convertToJS(context, scope, result)
                            resolve.call(context, scope, scope, arrayOf(jsResult))
                        }
                    } finally {
                        Context.exit()
                    }
                }
            }

            return promise
        }

        /**
         * Recursively convert Kotlin data structures to JavaScript objects.
         */
        private fun convertToJS(cx: Context, scope: Scriptable, value: Any?): Any? {
            return when (value) {
                null -> null
                is CommandAPI.CommandResult -> {
                    // Convert CommandResult to JavaScript object
                    val jsObj = cx.newObject(scope)
                    jsObj.put("success", jsObj, value.success)
                    jsObj.put("resultCount", jsObj, value.resultCount)
                    jsObj.put("feedback", jsObj, convertToJS(cx, scope, value.feedback))
                    jsObj.put("error", jsObj, value.error)
                    jsObj
                }
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
                else -> value.toString()
            }
        }
    }
}
