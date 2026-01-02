package com.rhett.rhettjs.commands

import com.rhett.rhettjs.adapter.CallerAdapter
import com.rhett.rhettjs.adapter.PlayerAdapter
import com.rhett.rhettjs.config.ConfigManager
import com.rhett.rhettjs.engine.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Test suite for Commands API platform integration.
 * Tests adapters, command registration, and integration patterns.
 *
 * Note: These tests focus on the adapter and registry logic,
 * not full Brigadier integration which requires a running Minecraft server.
 */
class CommandsPlatformIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    // ========================================
    // PlayerAdapter Tests (Structural)
    // ========================================

    @Test
    fun `PlayerAdapter toJS should exist and have correct signature`() {
        // This test verifies the adapter exists and has the right structure
        // Full testing requires real ServerPlayer which needs Minecraft server
        assertNotNull(PlayerAdapter)

        // Verify the method exists (will compile if signature is correct)
        val method = PlayerAdapter::class.java.declaredMethods
            .find { it.name == "toJS" }

        assertNotNull(method, "PlayerAdapter.toJS method should exist")
        assertEquals(2, method!!.parameterCount, "toJS should take 2 parameters (player, context)")
    }

    // ========================================
    // CallerAdapter Tests (Structural)
    // ========================================

    @Test
    fun `CallerAdapter toJS should exist and have correct signature`() {
        assertNotNull(CallerAdapter)

        val method = CallerAdapter::class.java.declaredMethods
            .find { it.name == "toJS" }

        assertNotNull(method, "CallerAdapter.toJS method should exist")
        assertEquals(2, method!!.parameterCount, "toJS should take 2 parameters (source, context)")
    }

    // ========================================
    // CustomCommandRegistry Tests
    // ========================================

    @Test
    fun `CustomCommandRegistry should store and retrieve commands`() {
        val registry = CustomCommandRegistry()

        val commandData = mutableMapOf<String, Any?>(
            "name" to "heal",
            "description" to "Heal yourself",
            "permission" to "admin.heal",
            "arguments" to emptyList<Map<String, String>>()
        )

        registry.storeCommand("heal", commandData)

        val retrieved = registry.getCommand("heal")
        assertNotNull(retrieved, "Should retrieve stored command")
        assertEquals("heal", retrieved?.get("name"), "Command name should match")
        assertEquals("Heal yourself", retrieved?.get("description"), "Description should match")
    }

    @Test
    fun `CustomCommandRegistry should list all command names`() {
        val registry = CustomCommandRegistry()

        registry.storeCommand("heal", mapOf("name" to "heal"))
        registry.storeCommand("tp", mapOf("name" to "tp"))
        registry.storeCommand("give", mapOf("name" to "give"))

        val names = registry.getCommandNames()
        assertEquals(3, names.size, "Should have 3 commands")
        assertTrue(names.contains("heal"), "Should contain 'heal'")
        assertTrue(names.contains("tp"), "Should contain 'tp'")
        assertTrue(names.contains("give"), "Should contain 'give'")
    }

    @Test
    fun `CustomCommandRegistry should clear all commands`() {
        val registry = CustomCommandRegistry()

        registry.storeCommand("heal", mapOf("name" to "heal"))
        registry.storeCommand("tp", mapOf("name" to "tp"))

        assertEquals(2, registry.getCommandNames().size, "Should have 2 commands before clear")

        registry.clear()

        assertEquals(0, registry.getCommandNames().size, "Should have 0 commands after clear")
    }

    @Test
    fun `CustomCommandRegistry should validate command with missing executor`() {
        val registry = CustomCommandRegistry()

        val commandData = mapOf(
            "name" to "incomplete"
            // Missing executor
        )

        assertThrows(IllegalArgumentException::class.java) {
            registry.validateCommand(commandData)
        }
    }

    @Test
    fun `CustomCommandRegistry should validate invalid argument type`() {
        val registry = CustomCommandRegistry()

        val commandData = mapOf(
            "name" to "bad",
            "arguments" to listOf(
                mapOf("name" to "arg", "type" to "invalid_type")
            ),
            "executor" to "dummy" // Just a placeholder for this test
        )

        assertThrows(IllegalArgumentException::class.java) {
            registry.validateCommand(commandData)
        }
    }

    @Test
    fun `CustomCommandRegistry should accept valid argument types`() {
        val registry = CustomCommandRegistry()

        val validTypes = listOf("string", "int", "float", "player", "item", "block", "entity")

        validTypes.forEach { type ->
            val commandData = mapOf(
                "name" to "test_$type",
                "arguments" to listOf(
                    mapOf("name" to "arg", "type" to type)
                ),
                "executor" to "dummy"
            )

            // Should not throw
            assertDoesNotThrow {
                registry.validateCommand(commandData)
            }
        }
    }

    // ========================================
    // Commands API Integration Tests
    // ========================================

    @Test
    fun `Commands API should be accessible in GraalVM context`() {
        val scriptPath = tempDir.resolve("test-commands-access.js")
        scriptPath.toFile().writeText("""
            import Commands from 'Commands';

            if (typeof Commands !== 'object') {
                throw new Error('Commands should be an object');
            }

            if (typeof Commands.register !== 'function') {
                throw new Error('Commands.register should be a function');
            }

            if (typeof Commands.unregister !== 'function') {
                throw new Error('Commands.unregister should be a function');
            }

            console.log('Commands API is accessible');
        """.trimIndent())

        val script = ScriptInfo(
            name = "test-commands-access.js",
            path = scriptPath,
            category = ScriptCategory.SERVER,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Script should execute successfully")
    }
}
