package com.rhett.rhettjs.threading

import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.util.ErrorHandler
import org.mozilla.javascript.*
import java.util.concurrent.Executors

/**
 * Task function for executing JavaScript callbacks on worker threads.
 *
 * WARNING: Most Minecraft/game APIs are NOT thread-safe and should NOT be accessed
 * from task() callbacks. Use schedule() to return to the game thread.
 *
 * Signature: task(callback, ...args)
 * - callback: JavaScript function to execute
 * - args: Arguments to pass to callback (validated - no Java objects allowed)
 *
 * Features:
 * - Automatic detection of thread-safe APIs (via ThreadSafeAPI marker)
 * - Scope snapshot for closure variables
 * - isAvailable() helper to check if values are accessible
 */
class TaskFunction {

    companion object {
        private val WORKER_COUNT = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

        private val executor = Executors.newFixedThreadPool(
            WORKER_COUNT
        ) { runnable ->
            Thread(runnable, "RhettJS-Worker").apply {
                isDaemon = true
            }
        }

        /**
         * Shutdown the worker thread pool.
         */
        fun shutdown() {
            executor.shutdown()
        }
    }

    /**
     * Execute a JavaScript callback on a worker thread.
     *
     * @param scope The JavaScript scope
     * @param callback The JavaScript function to execute
     * @param args Arguments to pass to the callback
     * @param onComplete Optional callback when task completes (for testing)
     * @throws IllegalArgumentException if args contain Java objects (shallow check)
     */
    fun execute(
        scope: Scriptable,
        callback: Scriptable,
        args: Array<Any?> = emptyArray(),
        onComplete: (args: Array<Any?>) -> Unit = {}
    ) {
        // Validate explicit arguments (shallow check)
        validateArguments(args)

        // Auto-discover thread-safe APIs from scope
        val threadSafeAPIs = getThreadSafeAPIs(scope)

        // Create scope snapshot for closure variables
        val scopeSnapshot = createScopeSnapshot(scope)

        // Submit to worker pool
        executor.submit {
            try {
                // Create new Rhino context for this worker thread
                val cx = Context.enter()
                try {
                    cx.optimizationLevel = -1
                    cx.languageVersion = Context.VERSION_ES6

                    // Create isolated worker scope
                    val workerScope = cx.initStandardObjects()

                    // Apply captured closure variables
                    applyScopeSnapshot(workerScope, scopeSnapshot)

                    // Inject thread-safe APIs (auto-discovered)
                    injectAPIs(workerScope, threadSafeAPIs)

                    // Add isAvailable() helper
                    injectIsAvailableHelper(workerScope)

                    // Execute callback with explicit args
                    if (callback is org.mozilla.javascript.Function) {
                        callback.call(cx, workerScope, workerScope, args)
                    }

                    // Notify completion (for testing)
                    onComplete(args)

                } catch (e: Exception) {
                    ErrorHandler.logScriptError("task() callback", e)
                    // Still call onComplete even on error (for testing)
                    onComplete(args)
                } finally {
                    Context.exit()
                }
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Fatal error in task()", e)
            }
        }
    }

    /**
     * Auto-discover thread-safe APIs from scope.
     * Only includes APIs marked with ThreadSafeAPI interface.
     */
    private fun getThreadSafeAPIs(scope: Scriptable): Map<String, Any> {
        val threadSafeAPIs = mutableMapOf<String, Any>()
        var current: Scriptable? = scope

        while (current != null) {
            val ids = current.ids
            for (id in ids) {
                val name = id.toString()

                // Skip if already captured
                if (threadSafeAPIs.containsKey(name)) {
                    continue
                }

                try {
                    val value = current.get(name, current)
                    if (value != Scriptable.NOT_FOUND) {
                        // Check if it implements ThreadSafeAPI
                        if (value is ThreadSafeAPI) {
                            threadSafeAPIs[name] = value
                        }
                    }
                } catch (e: Exception) {
                    // Skip properties that can't be read
                }
            }
            current = current.parentScope
        }

        return threadSafeAPIs
    }

    /**
     * Create a snapshot of scope variables for worker thread.
     * Only captures variables, not APIs (those are handled separately).
     */
    private fun createScopeSnapshot(scope: Scriptable): Map<String, Any?> {
        val snapshot = mutableMapOf<String, Any?>()
        var current: Scriptable? = scope

        // List of global function/API names to skip
        val skipNames = setOf(
            "task", "schedule", "isAvailable",
            "StartupEvents", "ServerEvents",
            // Standard JS globals (already in workerScope)
            "Object", "Array", "String", "Number", "Boolean", "Function",
            "Math", "Date", "RegExp", "JSON", "Error", "Promise"
        )

        while (current != null) {
            val ids = current.ids
            for (id in ids) {
                val name = id.toString()

                // Skip known APIs and globals
                if (name in skipNames) {
                    continue
                }

                // Skip if already captured from inner scope
                if (snapshot.containsKey(name)) {
                    continue
                }

                try {
                    val value = current.get(name, current)
                    if (value != Scriptable.NOT_FOUND) {
                        // Skip APIs (they're handled by ThreadSafeAPI auto-discovery)
                        if (value is ThreadSafeAPI) {
                            continue
                        }

                        // Capture everything else - let it fail naturally on access
                        snapshot[name] = value
                    }
                } catch (e: Exception) {
                    // Skip properties that can't be read
                }
            }
            current = current.parentScope
        }

        return snapshot
    }

    /**
     * Apply scope snapshot to worker scope.
     * Simple direct copy - let Rhino/Java handle threading errors naturally.
     */
    private fun applyScopeSnapshot(workerScope: Scriptable, snapshot: Map<String, Any?>) {
        for ((name, value) in snapshot) {
            workerScope.put(name, workerScope, value)
        }
    }

    /**
     * Inject thread-safe APIs into worker scope.
     */
    private fun injectAPIs(workerScope: Scriptable, apis: Map<String, Any>) {
        for ((name, api) in apis) {
            workerScope.put(name, workerScope, api)
        }
    }

    /**
     * Inject isAvailable() helper function.
     */
    private fun injectIsAvailableHelper(workerScope: Scriptable) {
        val isAvailableFunc = object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<Any?>): Any {
                if (args.isEmpty()) return false
                return isValueAvailable(args[0])
            }
        }
        workerScope.put("isAvailable", workerScope, isAvailableFunc)
    }

    /**
     * Check if a value is available in worker thread context.
     * Returns false only for Java objects that aren't thread-safe.
     */
    private fun isValueAvailable(value: Any?): Boolean {
        return when (value) {
            // Java objects are not available in worker threads
            is NativeJavaObject -> false
            is NativeJavaClass -> false
            is NativeJavaMethod -> false
            // Everything else is available
            else -> true
        }
    }

    /**
     * Validate that arguments don't contain Java objects (shallow check).
     * Allows: primitives (String, Number, Boolean), Scriptable (JS objects), null
     * Rejects: Other Java objects
     */
    private fun validateArguments(args: Array<Any?>) {
        for ((index, arg) in args.withIndex()) {
            val isValid = when (arg) {
                null -> true
                is String, is Number, is Boolean -> true
                is Scriptable -> true
                else -> false  // Java objects
            }

            if (!isValid) {
                throw IllegalArgumentException(
                    "Argument at index $index is a Java object (${arg!!.javaClass.simpleName}). " +
                            "task() arguments must be primitives or JavaScript objects. " +
                            "Extract needed data before calling task()."
                )
            }
        }
    }

}
