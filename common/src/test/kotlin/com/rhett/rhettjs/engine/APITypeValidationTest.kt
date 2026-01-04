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
     * Get actual runtime API methods by introspecting GraalEngine bindings.
     * This ACTUALLY checks what methods exist at runtime, not a manually maintained list.
     *
     * Properties (like Server.tps, World.dimensions) are excluded since they're declared differently in .d.ts.
     *
     * @param apiName The API name (e.g., "Structure", "World")
     * @return Sorted list of method names that exist in the actual runtime
     */
    private fun getRuntimeMethods(apiName: String): List<String> {
        // Get actual GraalVM context and introspect bindings
        val context = GraalEngine.getOrCreateContext()
        val bindings = context.getBindings("js")

        // Map API name to builtin binding name
        // Runtime is bound directly as "Runtime", others as "__builtin_<Name>"
        val builtinName = when (apiName) {
            "Runtime" -> "Runtime"
            "Console" -> "console"  // Special case: lowercase
            "Structure", "World", "Commands", "Server", "Store", "NBT" -> "__builtin_$apiName"
            else -> throw IllegalArgumentException("Unknown API: $apiName")
        }

        // Get the API object from bindings
        val apiObject = bindings.getMember(builtinName)
            ?: throw IllegalStateException("API not found in bindings: $builtinName")

        // Get member keys from the Value object
        // memberKeys returns a Set<String> which we convert to List
        val memberKeys = apiObject.memberKeys.toList()

        // Known properties to exclude (declared as properties in .d.ts, not methods)
        val knownProperties = setOf(
            "env",          // Runtime.env
            "dimensions",   // World.dimensions
            "tps", "players", "maxPlayers", "motd",  // Server properties
            "raw"           // Script.argv.raw
        )

        // Filter and return
        return memberKeys
            .filter { !it.startsWith("_") }      // Exclude private (underscore prefix)
            .filter { it !in knownProperties }   // Exclude known properties
            .sorted()
            .toList()
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
