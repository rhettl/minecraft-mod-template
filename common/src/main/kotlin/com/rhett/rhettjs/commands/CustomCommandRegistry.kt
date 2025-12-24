package com.rhett.rhettjs.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.engine.ScriptEngine
import com.rhett.rhettjs.events.CommandEvent
import com.rhett.rhettjs.events.StartupEventsAPI
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function

/**
 * Registry for custom commands registered via StartupEvents.command().
 * Handles integration with Minecraft's Brigadier command system.
 */
object CustomCommandRegistry {

    /**
     * Register all commands that were registered via StartupEvents.command().
     *
     * @param dispatcher The command dispatcher to register commands with
     */
    fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val commandHandlers = StartupEventsAPI.getCommandHandlers()

        if (commandHandlers.isEmpty()) {
            RhettJSCommon.LOGGER.debug("[RhettJS] No custom commands to register")
            return
        }

        RhettJSCommon.LOGGER.info("[RhettJS] Registering ${commandHandlers.size} custom command(s)")

        commandHandlers.forEach { (commandName, handler) ->
            try {
                registerCommand(dispatcher, commandName, handler)
                RhettJSCommon.LOGGER.debug("[RhettJS] Registered command: /$commandName")
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Failed to register command: /$commandName", e)
            }
        }
    }

    /**
     * Register a single command with Brigadier.
     */
    private fun registerCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        commandName: String,
        handler: Function
    ) {
        dispatcher.register(
            Commands.literal(commandName)
                .requires { it.hasPermission(2) } // Operator level 2 by default
                .executes { context -> executeCommand(context, commandName, handler, emptyArray()) }
                .then(
                    Commands.argument("args", StringArgumentType.greedyString())
                        .executes { context ->
                            val argsString = StringArgumentType.getString(context, "args")
                            val args = parseArguments(argsString)
                            executeCommand(context, commandName, handler, args)
                        }
                )
        )
    }

    /**
     * Parse command arguments from a string.
     * Simple space-separated parsing for now.
     */
    private fun parseArguments(argsString: String): Array<String> {
        return argsString.trim().split(Regex("\\s+")).toTypedArray()
    }

    /**
     * Execute a command handler on the server thread.
     */
    private fun executeCommand(
        context: CommandContext<CommandSourceStack>,
        commandName: String,
        handler: Function,
        args: Array<String>
    ): Int {
        val source = context.source

        try {
            // Create command event
            val event = CommandEvent(source, commandName, args)

            // Get or create JavaScript context
            Context.enter().use { cx ->
                cx.optimizationLevel = -1
                cx.languageVersion = Context.VERSION_ES6

                // Create a scope for command execution (SERVER category has appropriate APIs)
                val scope = ScriptEngine.createScope(com.rhett.rhettjs.engine.ScriptCategory.SERVER)

                // Call the handler function
                handler.call(cx, scope, scope, arrayOf(event))

                // Process any pending microtasks (Promises)
                cx.processMicrotasks()
            }

            return 1 // Success
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Error executing command: /$commandName", e)
            source.sendFailure(Component.literal("§c[RhettJS] Command error: ${e.message}"))
            return 0 // Failure
        }
    }
}
