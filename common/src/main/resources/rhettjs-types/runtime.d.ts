// RhettJS Runtime API Type Definitions
// Version: 0.3.0
// Last updated: 2026-01-06

/**
 * Runtime environment and lifecycle control
 * Available globally (like window, process, or console)
 */
declare global {
    namespace Runtime {
        /** Environment constants */
        const env: {
            /** Maximum worker threads (determined at boot, max 4) */
            MAX_WORKER_THREADS: number;
            /** Minecraft ticks per second (always 20) */
            TICKS_PER_SECOND: number;
            /** Debug mode enabled in config */
            IS_DEBUG: boolean;
            /** RhettJS version */
            RJS_VERSION: string;
        };

        /**
         * Stop script execution immediately
         * @example Runtime.exit();
         */
        function exit(): void;

        /**
         * Set script timeout (must be called before async operations)
         * @param timeoutMs - Timeout in milliseconds (min: 1000)
         * @example Runtime.setScriptTimeout(120000); // 2 minutes
         */
        function setScriptTimeout(timeoutMs: number): void;
    }
}

// For module compatibility (if someone does `import Runtime from 'Runtime'`)
export default globalThis.Runtime;