package com.rhett.rhettjs.engine

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import com.rhett.rhettjs.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for Structure API bindings exposed to JavaScript via GraalVM.
 * Tests async file operations for structure save/load/placement.
 */
class StructureAPIBindingsTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        ConfigManager.init(tempDir)
        GraalEngine.setScriptsDirectory(tempDir)
        GraalEngine.reset()
    }

    @Test
    fun `test Structure API is importable`() {
        val script = ScriptInfo(
            name = "test-structure-import.js",
            path = createTempScript("""
                import Structure from 'Structure';

                if (typeof Structure !== 'object') {
                    throw new Error('Structure should be an object');
                }

                const methods = ['exists', 'list', 'remove', 'capture', 'place'];
                for (const method of methods) {
                    if (typeof Structure[method] !== 'function') {
                        throw new Error('Structure.' + method + ' should be a function');
                    }
                }

                console.log('Structure API import works');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure API should be importable with all methods")
    }

    @Test
    fun `test Structure exists returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-exists-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise = Structure.exists('test');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.exists should return a Promise');
                }

                console.log('Structure.exists returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.exists should return Promise")
    }

    @Test
    fun `test Structure remove returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-remove-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise = Structure.remove('test');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.remove should return a Promise');
                }

                console.log('Structure.remove returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.remove should return Promise")
    }

    @Test
    fun `test Structure list returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-list-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise = Structure.list();

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.list should return a Promise');
                }

                console.log('Structure.list returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.list should return Promise")
    }

    @Test
    fun `test Structure place returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-place-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise = Structure.place(pos, 'test');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.place should return a Promise');
                }

                console.log('Structure.place returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.place should return Promise")
    }

    @Test
    fun `test Structure capture returns Promise`() {
        val script = ScriptInfo(
            name = "test-structure-capture-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos1 = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const pos2 = { x: 5, y: 69, z: 5, dimension: 'minecraft:overworld' };
                const promise = Structure.capture(pos1, pos2, 'test');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.capture should return a Promise');
                }

                console.log('Structure.capture returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.capture should return Promise")
    }

    @Test
    fun `test Structure list accepts optional pool parameter`() {
        val script = ScriptInfo(
            name = "test-structure-list-pool.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise1 = Structure.list();
                const promise2 = Structure.list('village');

                if (!(promise1 instanceof Promise)) {
                    throw new Error('Structure.list() should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('Structure.list(pool) should return Promise');
                }

                console.log('Structure.list accepts optional pool');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.list should accept optional pool parameter")
    }

    @Test
    fun `test Structure place accepts optional rotation parameter`() {
        val script = ScriptInfo(
            name = "test-structure-place-rotation.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos = { x: 0, y: 64, z: 0, dimension: 'minecraft:overworld' };
                const promise1 = Structure.place(pos, 'test');
                const promise2 = Structure.place(pos, 'test', { rotation: 90 });

                if (!(promise1 instanceof Promise)) {
                    throw new Error('Structure.place without rotation should return Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('Structure.place with rotation should return Promise');
                }

                console.log('Structure.place accepts optional rotation');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.place should accept optional rotation parameter")
    }

    // ====== Large Structure API Tests ======

    @Test
    fun `test Structure API has large structure methods`() {
        val script = ScriptInfo(
            name = "test-large-structure-methods.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const methods = ['captureLarge', 'placeLarge', 'getSize', 'listLarge', 'removeLarge'];
                for (const method of methods) {
                    if (typeof Structure[method] !== 'function') {
                        throw new Error('Structure.' + method + ' should be a function');
                    }
                }

                console.log('Large Structure API methods exist');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure API should have large structure methods")
    }

    @Test
    fun `test Structure captureLarge returns Promise`() {
        val script = ScriptInfo(
            name = "test-captureLarge-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos1 = { x: 0, y: 64, z: 0 };
                const pos2 = { x: 100, y: 100, z: 100 };
                const promise = Structure.captureLarge(pos1, pos2, 'test:large');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.captureLarge should return a Promise');
                }

                console.log('Structure.captureLarge returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.captureLarge should return Promise")
    }

    @Test
    fun `test Structure placeLarge returns Promise`() {
        val script = ScriptInfo(
            name = "test-placeLarge-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos = { x: 0, y: 64, z: 0 };
                const promise = Structure.placeLarge(pos, 'test:large');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.placeLarge should return a Promise');
                }

                console.log('Structure.placeLarge returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.placeLarge should return Promise")
    }

    @Test
    fun `test Structure getSize returns Promise`() {
        val script = ScriptInfo(
            name = "test-getSize-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise = Structure.getSize('test:structure');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.getSize should return a Promise');
                }

                console.log('Structure.getSize returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.getSize should return Promise")
    }

    @Test
    fun `test Structure listLarge returns Promise`() {
        val script = ScriptInfo(
            name = "test-listLarge-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise1 = Structure.listLarge();
                const promise2 = Structure.listLarge('test');

                if (!(promise1 instanceof Promise)) {
                    throw new Error('Structure.listLarge() should return a Promise');
                }
                if (!(promise2 instanceof Promise)) {
                    throw new Error('Structure.listLarge(namespace) should return a Promise');
                }

                console.log('Structure.listLarge returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.listLarge should return Promise")
    }

    @Test
    fun `test Structure removeLarge returns Promise`() {
        val script = ScriptInfo(
            name = "test-removeLarge-promise.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const promise = Structure.removeLarge('test:large');

                if (!(promise instanceof Promise)) {
                    throw new Error('Structure.removeLarge should return a Promise');
                }

                console.log('Structure.removeLarge returns Promise');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.removeLarge should return Promise")
    }

    @Test
    fun `test Structure captureLarge accepts optional piece size`() {
        val script = ScriptInfo(
            name = "test-captureLarge-pieceSize.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos1 = { x: 0, y: 64, z: 0 };
                const pos2 = { x: 100, y: 100, z: 100 };

                // Default piece size
                const promise1 = Structure.captureLarge(pos1, pos2, 'test:large1');

                // Custom piece size
                const promise2 = Structure.captureLarge(pos1, pos2, 'test:large2', {
                    pieceSize: { x: 30, y: 30, z: 30 }
                });

                if (!(promise1 instanceof Promise) || !(promise2 instanceof Promise)) {
                    throw new Error('Structure.captureLarge should return Promise with/without pieceSize');
                }

                console.log('Structure.captureLarge accepts optional pieceSize');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.captureLarge should accept optional pieceSize parameter")
    }

    @Test
    fun `test Structure placeLarge accepts rotation and centered options`() {
        val script = ScriptInfo(
            name = "test-placeLarge-options.js",
            path = createTempScript("""
                import Structure from 'Structure';

                const pos = { x: 0, y: 64, z: 0 };

                const promise1 = Structure.placeLarge(pos, 'test:large');
                const promise2 = Structure.placeLarge(pos, 'test:large', { rotation: 90 });
                const promise3 = Structure.placeLarge(pos, 'test:large', { centered: true });
                const promise4 = Structure.placeLarge(pos, 'test:large', { rotation: 180, centered: true });

                if (!(promise1 instanceof Promise) || !(promise2 instanceof Promise) ||
                    !(promise3 instanceof Promise) || !(promise4 instanceof Promise)) {
                    throw new Error('Structure.placeLarge should return Promise with all options');
                }

                console.log('Structure.placeLarge accepts rotation and centered options');
            """),
            category = ScriptCategory.STARTUP,
            lastModified = System.currentTimeMillis(),
            status = ScriptStatus.LOADED
        )

        val result = GraalEngine.executeScript(script)
        assertTrue(result is ScriptResult.Success, "Structure.placeLarge should accept rotation and centered parameters")
    }

    private fun createTempScript(content: String): Path {
        val tempFile = Files.createTempFile(tempDir, "test-script-", ".js")
        Files.writeString(tempFile, content)
        return tempFile
    }
}
