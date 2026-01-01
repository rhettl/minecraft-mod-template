// Test Helper Module
// For use in testing directory

export function formatMessage(prefix, message) {
    return `[${prefix}] ${message}`;
}

export function logTest(testName, passed) {
    const symbol = passed ? "✓" : "✗";
    console.log(`${symbol} ${testName}`);
}

export default {
    formatMessage,
    logTest
};
