package com.rhett.rhettjs.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.rhett.rhettjs.RhettJSCommon
import com.rhett.rhettjs.engine.ScriptEngine
import com.rhett.rhettjs.events.CommandEvent
import com.rhett.rhettjs.events.ServerEventsAPI
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

    private var storedDispatcher: CommandDispatcher<CommandSourceStack>? = null

    /**
     * Store the command dispatcher for later registration.
     * Called during command registration event (before startup scripts run).
     *
     * @param dispatcher The command dispatcher to store
     */
    fun storeDispatcher(dispatcher: CommandDispatcher<CommandSourceStack>) {
        storedDispatcher = dispatcher
        RhettJSCommon.LOGGER.debug("[RhettJS] Stored command dispatcher for later registration")
    }

    /**
     * Register all commands that were registered via ServerEvents.basicCommand() and ServerEvents.command().
     * Called AFTER server scripts have executed.
     */
    fun registerCommands() {
        val dispatcher = storedDispatcher
        if (dispatcher == null) {
            RhettJSCommon.LOGGER.error("[RhettJS] Cannot register commands: dispatcher not stored")
            return
        }

        // Register basic commands (greedy string args)
        val basicCommandHandlers = ServerEventsAPI.getCommandHandlers()
        // Register full Brigadier commands
        val fullCommandHandlers = ServerEventsAPI.getFullCommandHandlers()

        val totalCommands = basicCommandHandlers.size + fullCommandHandlers.size

        if (totalCommands == 0) {
            RhettJSCommon.LOGGER.debug("[RhettJS] No custom commands to register")
            return
        }

        RhettJSCommon.LOGGER.info("[RhettJS] Registering $totalCommands custom command(s)")

        // Register basic commands
        basicCommandHandlers.forEach { (commandName, handler) ->
            try {
                registerBasicCommand(dispatcher, commandName, handler)
                RhettJSCommon.LOGGER.debug("[RhettJS] Registered basic command: /$commandName")
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Failed to register basic command: /$commandName", e)
            }
        }

        // Register full Brigadier commands
        RhettJSCommon.LOGGER.info("[RhettJS] Found ${fullCommandHandlers.size} full commands to register")
        fullCommandHandlers.forEach { (commandName, commandBuilder) ->
            try {
                RhettJSCommon.LOGGER.info("[RhettJS] Attempting to register full command: /$commandName")
                registerFullCommand(dispatcher, commandBuilder)
                RhettJSCommon.LOGGER.info("[RhettJS] Successfully registered full command: /$commandName")
            } catch (e: Exception) {
                RhettJSCommon.LOGGER.error("[RhettJS] Failed to register full command: /$commandName", e)
            }
        }
    }

    /**
     * Register a basic command with greedy string argument parsing.
     */
    private fun registerBasicCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        commandName: String,
        handler: Function
    ) {
        dispatcher.register(
            Commands.literal(commandName)
                .requires { it.hasPermission(2) } // Operator level 2 by default
                .executes { context -> executeBasicCommand(context, commandName, handler, emptyArray()) }
                .then(
                    Commands.argument("args", StringArgumentType.greedyString())
                        .executes { context ->
                            val argsString = StringArgumentType.getString(context, "args")
                            val args = parseArguments(argsString)
                            executeBasicCommand(context, commandName, handler, args)
                        }
                )
        )
    }

    /**
     * Register a full Brigadier command.
     * The command structure is already fully defined in the LiteralArgumentBuilder.
     */
    private fun registerFullCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        commandBuilder: com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
    ) {
        dispatcher.register(commandBuilder)
    }

    /**
     * Parse command arguments from a string.
     * Simple space-separated parsing for now.
     */
    private fun parseArguments(argsString: String): Array<String> {
        return argsString.trim().split(Regex("\\s+")).toTypedArray()
    }

    /**
     * Execute a basic command handler on the server thread.
     */
    private fun executeBasicCommand(
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

                // Inject Command API
                val player = source.entity as? net.minecraft.server.level.ServerPlayer
                val commandAPI = com.rhett.rhettjs.api.CommandAPI(source.server, player)
                val commandWrapper = com.rhett.rhettjs.api.CommandAPIWrapper(commandAPI, source.server)
                commandWrapper.parentScope = scope
                scope.put("Command", scope, commandWrapper)

                // Call the handler function
                handler.call(cx, scope, scope, arrayOf(event))

                // Process any pending microtasks (Promises)
                cx.processMicrotasks()
            }

            return 1 // Success
        } catch (e: Exception) {
            RhettJSCommon.LOGGER.error("[RhettJS] Error executing basic command: /$commandName", e)
            source.sendFailure(Component.literal("§c[RhettJS] Command error: ${e.message}"))
            return 0 // Failure
        }
    }
}
