package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates that TypeScript definitions match the runtime API surface.
 * Parses rhettjs.d.ts and compares against expected GraalEngine bindings.
 *
 * This ensures .d.ts stays in sync with actual runtime APIs.
 * Methods prefixed with _ are excluded (internal/private functions).
 *
 * IMPORTANT: When adding new API methods to GraalEngine, update:
 * 1. GraalEngine.createXAPIProxy() - The actual runtime binding
 * 2. getRuntimeMethods() in this test - Expected method list
 * 3. common/src/main/resources/rhettjs-types/rhettjs.d.ts - Type definitions
 *
 * If any of these are out of sync, this test will fail.
 */
class APITypeValidationTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    /**
     * Parse .d.ts file to extract declared method names for an API.
     * @param apiName The namespace name (e.g., "Structure", "World")
     * @return Sorted list of method names
     */
    private fun parseTypeDefinitions(apiName: String): List<String> {
        val dtsContent = javaClass.getResourceAsStream("/rhettjs-types/rhettjs.d.ts")
            ?.bufferedReader()
            ?.readText()
            ?: throw IllegalStateException("Type definitions not found at /rhettjs-types/rhettjs.d.ts")

        // Extract the API block: "declare namespace ApiName { ... }"
        val namespacePattern = """declare\s+namespace\s+$apiName\s*\{""".toRegex()
        val match = namespacePattern.find(dtsContent)
            ?: throw IllegalStateException("Namespace '$apiName' not found in type definitions")

        // Find matching closing brace (accounting for nested braces)
        val startIdx = match.range.last + 1
        var braceCount = 1
        var endIdx = startIdx

        for (i in startIdx until dtsContent.length) {
            when (dtsContent[i]) {
                '{' -> braceCount++
                '}' -> {
                    braceCount--
                    if (braceCount == 0) {
                        endIdx = i
                        break
                    }
                }
            }
        }

        val apiBlock = dtsContent.substring(startIdx, endIdx)

        // Extract method names: "function methodName(" or "methodName("
        // Only match lines that start with whitespace + function or just method name
        // This avoids matching parameter names inside function signatures
        val methodPattern = """^\s+(?:function\s+)?(\w+)\s*\(""".toRegex(RegexOption.MULTILINE)

        return methodPattern.findAll(apiBlock)
            .map { it.groupValues[1] }
            .filter { it != apiName } // Exclude constructor if present
            .filter { !it.matches("""^[A-Z].*""".toRegex()) } // Exclude type names (PascalCase)
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * Get expected runtime API methods based on GraalEngine implementation.
     * This list must be manually kept in sync with GraalEngine's createXAPIProxy() methods.
     * The test validates that .d.ts matches this expected surface.
     *
     * @param apiName The API name (e.g., "Structure", "World")
     * @return Sorted list of method names that should exist in runtime
     */
    private fun getRuntimeMethods(apiName: String): List<String> {
        return when (apiName) {
            "Structure" -> listOf(
                "exists", "list", "delete",
                "capture", "place",
                "captureLarge", "placeLarge", "getSize", "listLarge", "deleteLarge"
            )
            "World" -> listOf(
                "getBlock", "setBlock", "fill",
                "getPlayers", "getPlayer",
                "getTime", "setTime",
                "getWeather", "setWeather"
                // Note: "dimensions" is a property, not a method
            )
            "Commands" -> listOf(
                "register", "unregister"
            )
            "Server" -> listOf(
                "on", "once", "off",
                "broadcast", "runCommand"
                // Note: "tps", "players", "maxPlayers", "motd" are properties
            )
            "Store" -> listOf(
                "namespace", "namespaces", "clearAll", "size"
            )
            "NBT" -> listOf(
                "compound", "list", "string", "int", "double", "byte",
                "get", "set", "has", "delete", "merge"
            )
            "Runtime" -> listOf(
                "exit", "setScriptTimeout", "inspect"
                // Note: "env" is a property
            )
            else -> throw IllegalArgumentException("Unknown API: $apiName")
        }.sorted()
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }

    @Test
    fun `Structure API matches type definitions`() {
        val expected = parseTypeDefinitions("Structure")
        val actual = getRuntimeMethods("Structure")

        assertEquals(
            expected,
            actual,
            """
            Structure API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/rhettjs.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `World API matches type definitions`() {
        val expected = parseTypeDefinitions("World")
        val actual = getRuntimeMethods("World")

        assertEquals(
            expected,
            actual,
            """
            World API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/rhettjs.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `Store API matches type definitions`() {
        val expected = parseTypeDefinitions("Store")
        val actual = getRuntimeMethods("Store")

        assertEquals(
            expected,
            actual,
            """
            Store API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/rhettjs.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `Commands API matches type definitions`() {
        val expected = parseTypeDefinitions("Commands")
        val actual = getRuntimeMethods("Commands")

        assertEquals(
            expected,
            actual,
            """
            Commands API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/rhettjs.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `Server API matches type definitions`() {
        val expected = parseTypeDefinitions("Server")
        val actual = getRuntimeMethods("Server")

        assertEquals(
            expected,
            actual,
            """
            Server API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/rhettjs.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `NBT API matches type definitions`() {
        val expected = parseTypeDefinitions("NBT")
        val actual = getRuntimeMethods("NBT")

        assertEquals(
            expected,
            actual,
            """
            NBT API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/rhettjs.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `Runtime API matches type definitions`() {
        val expected = parseTypeDefinitions("Runtime")
        val actual = getRuntimeMethods("Runtime")

        assertEquals(
            expected,
            actual,
            """
            Runtime API methods don't match rhettjs.d.ts!

            Expected (from .d.ts): $expected
            Actual (from runtime): $actual

            Missing from runtime: ${expected - actual.toSet()}
            Missing from .d.ts: ${actual - expected.toSet()}

            Action: Update common/src/main/resources/rhettjs-types/rhettjs.d.ts
            """.trimIndent()
        )
    }

    @Test
    fun `type definitions file exists and is readable`() {
        val dtsContent = javaClass.getResourceAsStream("/rhettjs-types/rhettjs.d.ts")
            ?.bufferedReader()
            ?.readText()

        assertNotNull(dtsContent, "rhettjs.d.ts should be present in resources")
        assertTrue(dtsContent!!.contains("declare namespace Structure"), "Should contain Structure API")
        assertTrue(dtsContent.contains("declare namespace World"), "Should contain World API")
        assertTrue(dtsContent.contains("declare namespace Commands"), "Should contain Commands API")
    }
}
