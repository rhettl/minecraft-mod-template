package com.rhett.rhettjs.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.rhett.rhettjs.RhettJSCommon
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.CompletableFuture

/**
 * JavaScript-accessible command builder providing a fluent API for constructing
 * Brigadier commands with typed arguments and autocomplete.
 *
 * Usage in JavaScript:
 * ```javascript
 * ServerEvents.command('mycommand', event => {
 *   event.literal('mycommand')
 *     .then(event.argument('target', event.arguments.PLAYER))
 *     .then(event.argument('amount', event.arguments.INTEGER))
 *     .executes(ctx => {
 *       const target = event.arguments.PLAYER.get(ctx, 'target');
 *       const amount = event.arguments.INTEGER.get(ctx, 'amount');
 *       // ... command logic
 *       return 1; // success
 *     })
 * });
 * ```
 */
class CommandBuilder(
    private val commandName: String,
    private val scope: Scriptable
) {
    private var rootBuilder: LiteralArgumentBuilder<CommandSourceStack>? = null

    // Expose arguments as a public property for JavaScript access
    @Suppress("unused")
    val arguments: ArgumentTypeWrappers = ArgumentTypeWrappers.create(scope)

    /**
     * Create the root literal node for the command.
     */
    fun literal(name: String): CommandBuilderNode {
        val builder = Commands.literal(name)
        rootBuilder = builder
        return CommandBuilderNode(builder, scope)
    }

    /**
     * Get the built command tree.
     * Called internally after the command is fully defined.
     */
    fun build(): LiteralArgumentBuilder<CommandSourceStack> {
        return rootBuilder ?: throw IllegalStateException("Command not initialized. Call literal() first.")
    }
}

/**
 * Represents a node in the command tree (either literal or argument).
 * Provides chainable methods for building command structure.
 */
class CommandBuilderNode(
    private val builder: ArgumentBuilder<CommandSourceStack, *>,
    private val scope: Scriptable
) {

    /**
     * Add a literal sub-command.
     *
     * @param name The literal string to match
     * @return A new node for chaining
     */
    fun literal(name: String): CommandBuilderNode {
        val subBuilder = Commands.literal(name)
        builder.then(subBuilder)
        return CommandBuilderNode(subBuilder, scope)
    }

    /**
     * Add a typed argument.
     *
     * @param name The argument name
     * @param type The argument type wrapper
     * @return A new node for chaining
     */
    fun argument(name: String, type: ArgumentTypeWrapper<*>): CommandBuilderNode {
        val argBuilder = Commands.argument(name, type.create())
        builder.then(argBuilder)
        return CommandBuilderNode(argBuilder, scope)
    }

    /**
     * Add another node to this node's children.
     *
     * @param node The child node to add
     * @return This node for chaining
     */
    fun then(node: CommandBuilderNode): CommandBuilderNode {
        // The node's builder is already added to our builder
        return node
    }

    /**
     * Set the execution handler for this node.
     *
     * @param handler JavaScript function that executes the command
     * @return This node for chaining
     */
    fun executes(handler: Function): CommandBuilderNode {
        builder.executes { context ->
            try {
                executeJavaScriptHandler(context, handler)
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Command execution error", e)
                0 // failure
            }
        }
        return this
    }

    /**
     * Add custom suggestions for this argument.
     *
     * @param suggestionsHandler JavaScript function that provides suggestions
     * @return This node for chaining
     */
    fun suggests(suggestionsHandler: Function): CommandBuilderNode {
        if (builder is RequiredArgumentBuilder<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val argBuilder = builder as RequiredArgumentBuilder<CommandSourceStack, *>

            argBuilder.suggests { context, suggestionsBuilder ->
                try {
                    provideJavaScriptSuggestions(context, suggestionsBuilder, suggestionsHandler)
                } catch (e: Exception) {
                    RhettJSCommon.LOGGER.error("[RhettJS] Suggestions error", e)
                    suggestionsBuilder.buildFuture()
                }
            }
        } else {
            RhettJSCommon.LOGGER.warn("[RhettJS] suggests() can only be called on argument nodes, not literals")
        }
        return this
    }

    /**
     * Require a specific permission level to execute this command.
     *
     * @param level Permission level (0-4, where 2 is standard operator)
     * @return This node for chaining
     */
    fun requires(level: Int): CommandBuilderNode {
        builder.requires { it.hasPermission(level) }
        return this
    }

    /**
     * Execute a JavaScript command handler.
     */
    private fun executeJavaScriptHandler(
        context: CommandContext<CommandSourceStack>,
        handler: Function
    ): Int {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6

            // Wrap context for JavaScript access
            val wrappedContext = Context.javaToJS(context, scope)

            // Call the handler
            val result = handler.call(cx, scope, scope, arrayOf(wrappedContext))

            // Process any microtasks
            cx.processMicrotasks()

            // Convert result to int (1 = success, 0 = failure)
            return when (result) {
                is Number -> result.toInt()
                is Boolean -> if (result) 1 else 0
                else -> 1 // default success
            }
        } finally {
            Context.exit()
        }
    }

    /**
     * Provide JavaScript-generated suggestions.
     */
    private fun provideJavaScriptSuggestions(
        context: CommandContext<CommandSourceStack>,
        suggestionsBuilder: SuggestionsBuilder,
        handler: Function
    ): CompletableFuture<Suggestions> {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6

            // Wrap arguments for JavaScript access
            val wrappedContext = Context.javaToJS(context, scope)
            val wrappedBuilder = Context.javaToJS(suggestionsBuilder, scope)

            // Call the handler - it should return either:
            // - An array of suggestion strings
            // - A CompletableFuture<Suggestions>
            val result = handler.call(cx, scope, scope, arrayOf(wrappedContext, wrappedBuilder))

            // Process any microtasks
            cx.processMicrotasks()

            return when {
                // If handler returns array of strings, add them as suggestions
                result is org.mozilla.javascript.NativeArray -> {
                    for (i in 0 until result.length) {
                        val suggestion = result.get(i)
                        if (suggestion != null && suggestion != org.mozilla.javascript.Undefined.instance) {
                            suggestionsBuilder.suggest(suggestion.toString())
                        }
                    }
                    suggestionsBuilder.buildFuture()
                }
                // If handler returns CompletableFuture, use it directly
                result is NativeJavaObject && result.unwrap() is CompletableFuture<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    result.unwrap() as CompletableFuture<Suggestions>
                }
                // Otherwise, build empty suggestions
                else -> suggestionsBuilder.buildFuture()
            }
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Suggestions handler error", e)
            return suggestionsBuilder.buildFuture()
        } finally {
            Context.exit()
        }
    }
}
