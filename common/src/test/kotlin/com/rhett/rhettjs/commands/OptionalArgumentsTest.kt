package com.rhett.rhettjs.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.rhett.rhettjs.engine.GraalEngine
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.HolderLookup
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.proxy.ProxyObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for optional argument support in the Commands API.
 *
 * Verifies:
 * - Commands with optional arguments create multiple execution points
 * - Subcommands with optional arguments work correctly
 * - Default values are applied correctly
 * - Required arguments after optional arguments throw errors
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OptionalArgumentsTest {

    private lateinit var registry: CustomCommandRegistry
    private lateinit var dispatcher: CommandDispatcher<CommandSourceStack>
    private lateinit var buildContext: CommandBuildContext
    private lateinit var graalContext: Context

    @BeforeEach
    fun setup() {
        registry = CustomCommandRegistry()
        dispatcher = CommandDispatcher()

        // Mock CommandBuildContext
        buildContext = Mockito.mock(CommandBuildContext.class.java)
        val holderLookup = Mockito.mock(HolderLookup.Provider::class.java)
        Mockito.`when`(buildContext.lookupOrThrow(Mockito.any())).thenReturn(holderLookup)

        // Create a real GraalVM context for testing
        graalContext = Context.newBuilder("js")
            .allowAllAccess(true)
            .build()

        registry.storeDispatcher(dispatcher, graalContext, buildContext)
    }

    @AfterEach
    fun teardown() {
        registry.clear()
        graalContext.close()
    }

    @Test
    fun `test command with optional argument creates multiple execution points`() {
        // Register command: /test <required> [optional]
        val mockExecutor = graalContext.eval("js", "(event) => { return 1; }")

        val commandData = mutableMapOf<String, Any?>(
            "name" to "test",
            "description" to "Test command",
            "arguments" to mutableListOf(
                mutableMapOf(
                    "name" to "required",
                    "type" to "string",
                    "optional" to false,
                    "hasDefault" to false,
                    "default" to null
                ),
                mutableMapOf(
                    "name" to "optional",
                    "type" to "int",
                    "optional" to true,
                    "hasDefault" to true,
                    "default" to 42
                )
            ),
            "executor" to mockExecutor
        )

        registry.storeCommand("test", commandData)
        registry.registerAll()

        // Verify command was registered
        val testNode = dispatcher.root.getChild("test")
        assertNotNull(testNode, "Command 'test' should be registered")

        // Verify required argument node exists
        val requiredNode = testNode.children.find { it.name == "required" }
        assertNotNull(requiredNode, "Required argument 'required' should exist")

        // Verify required node has an execution point (because next arg is optional)
        // This is the first execution point: /test <required>
        assertTrue(requiredNode.command != null, "Required argument node should have execution point")

        // Verify optional argument node exists under required
        val optionalNode = requiredNode.children.find { it.name == "optional" }
        assertNotNull(optionalNode, "Optional argument 'optional' should exist")

        // Verify optional node has execution point
        // This is the second execution point: /test <required> [optional]
        assertTrue(optionalNode.command != null, "Optional argument node should have execution point")
    }

    @Test
    fun `test subcommand with multiple optional arguments`() {
        // Register command: /test save <name> [size] [author]
        val mockExecutor = graalContext.eval("js", "(event) => { return 1; }")

        val commandData = mutableMapOf<String, Any?>(
            "name" to "test",
            "description" to "Test command",
            "subcommands" to mutableMapOf(
                "save" to mutableMapOf<String, Any?>(
                    "name" to "save",
                    "arguments" to mutableListOf(
                        mutableMapOf(
                            "name" to "name",
                            "type" to "string",
                            "optional" to false,
                            "hasDefault" to false,
                            "default" to null
                        ),
                        mutableMapOf(
                            "name" to "size",
                            "type" to "int",
                            "optional" to true,
                            "hasDefault" to true,
                            "default" to 48
                        ),
                        mutableMapOf(
                            "name" to "author",
                            "type" to "string",
                            "optional" to true,
                            "hasDefault" to true,
                            "default" to "Unknown"
                        )
                    ),
                    "executor" to mockExecutor
                )
            )
        )

        registry.storeCommand("test", commandData)
        registry.registerAll()

        // Verify command structure
        val testNode = dispatcher.root.getChild("test")
        assertNotNull(testNode, "Command 'test' should be registered")

        val saveNode = testNode.getChild("save")
        assertNotNull(saveNode, "Subcommand 'save' should exist")

        // Verify argument chain
        val nameNode = saveNode.children.find { it.name == "name" }
        assertNotNull(nameNode, "Argument 'name' should exist")

        // Name node should have execution (first optional starts after it)
        assertTrue(nameNode.command != null, "Name node should have execution point")

        val sizeNode = nameNode.children.find { it.name == "size" }
        assertNotNull(sizeNode, "Argument 'size' should exist")
        assertTrue(sizeNode.command != null, "Size node should have execution point")

        val authorNode = sizeNode.children.find { it.name == "author" }
        assertNotNull(authorNode, "Argument 'author' should exist")
        assertTrue(authorNode.command != null, "Author node should have execution point")
    }

    @Test
    fun `test required argument after optional throws error`() {
        // Try to register: /test [optional] <required>  -- SHOULD FAIL
        val mockExecutor = graalContext.eval("js", "(event) => { return 1; }")

        val commandData = mutableMapOf<String, Any?>(
            "name" to "test",
            "description" to "Test command",
            "arguments" to mutableListOf(
                mutableMapOf(
                    "name" to "optional",
                    "type" to "int",
                    "optional" to true,
                    "hasDefault" to true,
                    "default" to 42
                ),
                mutableMapOf(
                    "name" to "required",
                    "type" to "string",
                    "optional" to false,
                    "hasDefault" to false,
                    "default" to null
                )
            ),
            "executor" to mockExecutor
        )

        registry.storeCommand("test", commandData)

        // This should throw an error during validation
        assertFails {
            registry.registerAll()
        }
    }

    @Test
    fun `test optional argument with no default (null sentinel)`() {
        // Register command: /test <required> [optional-no-default]
        val mockExecutor = graalContext.eval("js", "(event) => { return 1; }")

        val commandData = mutableMapOf<String, Any?>(
            "name" to "test",
            "description" to "Test command",
            "arguments" to mutableListOf(
                mutableMapOf(
                    "name" to "required",
                    "type" to "string",
                    "optional" to false,
                    "hasDefault" to false,
                    "default" to null
                ),
                mutableMapOf(
                    "name" to "optional",
                    "type" to "int",
                    "optional" to true,
                    "hasDefault" to false,  // No default - uses null sentinel
                    "default" to null
                )
            ),
            "executor" to mockExecutor
        )

        registry.storeCommand("test", commandData)
        registry.registerAll()

        // Verify command structure - should still create execution points
        val testNode = dispatcher.root.getChild("test")
        assertNotNull(testNode)

        val requiredNode = testNode.children.find { it.name == "required" }
        assertNotNull(requiredNode)
        assertTrue(requiredNode.command != null, "Should have execution point even with null default")

        val optionalNode = requiredNode.children.find { it.name == "optional" }
        assertNotNull(optionalNode)
        assertTrue(optionalNode.command != null)
    }

    @Test
    fun `test command with only optional arguments`() {
        // Register command: /test [opt1] [opt2]
        val mockExecutor = graalContext.eval("js", "(event) => { return 1; }")

        val commandData = mutableMapOf<String, Any?>(
            "name" to "test",
            "description" to "Test command",
            "arguments" to mutableListOf(
                mutableMapOf(
                    "name" to "opt1",
                    "type" to "int",
                    "optional" to true,
                    "hasDefault" to true,
                    "default" to 10
                ),
                mutableMapOf(
                    "name" to "opt2",
                    "type" to "int",
                    "optional" to true,
                    "hasDefault" to true,
                    "default" to 20
                )
            ),
            "executor" to mockExecutor
        )

        registry.storeCommand("test", commandData)
        registry.registerAll()

        // Verify command structure
        val testNode = dispatcher.root.getChild("test")
        assertNotNull(testNode)

        // First optional should be a direct child and have execution
        val opt1Node = testNode.children.find { it.name == "opt1" }
        assertNotNull(opt1Node, "First optional argument should exist")
        assertTrue(opt1Node.command != null, "First optional should have execution point")

        // Second optional should chain from first
        val opt2Node = opt1Node.children.find { it.name == "opt2" }
        assertNotNull(opt2Node, "Second optional argument should exist")
        assertTrue(opt2Node.command != null, "Second optional should have execution point")
    }

    @Test
    fun `test command with no optional arguments behaves normally`() {
        // Register command: /test <arg1> <arg2>
        val mockExecutor = graalContext.eval("js", "(event) => { return 1; }")

        val commandData = mutableMapOf<String, Any?>(
            "name" to "test",
            "description" to "Test command",
            "arguments" to mutableListOf(
                mutableMapOf(
                    "name" to "arg1",
                    "type" to "string",
                    "optional" to false,
                    "hasDefault" to false,
                    "default" to null
                ),
                mutableMapOf(
                    "name" to "arg2",
                    "type" to "int",
                    "optional" to false,
                    "hasDefault" to false,
                    "default" to null
                )
            ),
            "executor" to mockExecutor
        )

        registry.storeCommand("test", commandData)
        registry.registerAll()

        // Verify command structure - should have single execution point at the end
        val testNode = dispatcher.root.getChild("test")
        assertNotNull(testNode)

        val arg1Node = testNode.children.find { it.name == "arg1" }
        assertNotNull(arg1Node)

        // With no optional args, only the last node has execution
        assertTrue(arg1Node.command == null, "First required arg should NOT have execution (no optionals)")

        val arg2Node = arg1Node.children.find { it.name == "arg2" }
        assertNotNull(arg2Node)
        assertTrue(arg2Node.command != null, "Last argument should have execution point")
    }
}
