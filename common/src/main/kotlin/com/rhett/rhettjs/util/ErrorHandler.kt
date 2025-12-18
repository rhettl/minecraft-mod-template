package com.rhett.rhettjs.util

import com.rhett.rhettjs.RhettJSCommon
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.RhinoException

/**
 * Shared utility for handling and formatting JavaScript errors.
 *
 * Provides clean, user-friendly error messages by extracting JavaScript
 * stack traces instead of showing full Java stack traces.
 */
object ErrorHandler {

    /**
     * Log a JavaScript error with clean formatting.
     *
     * Extracts JavaScript stack trace and presents it in a user-friendly way.
     * Avoids showing Java stack traces which are confusing to JavaScript developers.
     *
     * @param context Description of where the error occurred (e.g., "task() callback", "schedule() callback")
     * @param error The exception that was thrown
     */
    fun logScriptError(context: String, error: Throwable) {
        val message = when (error) {
            is JavaScriptException -> {
                // JavaScript error (e.g., throw new Error())
                buildString {
                    appendLine("[RhettJS] Error in $context:")
                    appendLine("  ${error.message}")

                    if (error is RhinoException) {
                        val scriptStack = error.scriptStackTrace
                        if (scriptStack.isNotEmpty()) {
                            appendLine()
                            appendLine("JavaScript stack trace:")
                            scriptStack.lines().take(10).forEach { line ->
                                if (line.isNotBlank()) {
                                    appendLine("    $line")
                                }
                            }
                        }
                    }
                }
            }

            is EcmaError -> {
                // Runtime errors (e.g., ReferenceError, TypeError)
                buildString {
                    appendLine("[RhettJS] ${error.name} in $context:")
                    appendLine("  ${error.errorMessage}")

                    if (error.lineNumber() > 0) {
                        appendLine("  at ${error.sourceName()}:${error.lineNumber()}")
                    }

                    val scriptStack = error.scriptStackTrace
                    if (scriptStack.isNotEmpty()) {
                        appendLine()
                        appendLine("JavaScript stack trace:")
                        scriptStack.lines().take(10).forEach { line ->
                            if (line.isNotBlank()) {
                                appendLine("    $line")
                            }
                        }
                    }
                }
            }

            is EvaluatorException -> {
                // Syntax/evaluation errors
                buildString {
                    appendLine("[RhettJS] Syntax error in $context:")
                    appendLine("  ${error.message}")

                    if (error.lineNumber() > 0) {
                        appendLine("  at ${error.sourceName()}:${error.lineNumber()}")
                        if (error.columnNumber() > 0) {
                            appendLine("  column ${error.columnNumber()}")
                        }
                    }
                }
            }

            else -> {
                // Non-JavaScript errors (Java exceptions)
                // For these, we DO want the stack trace since they indicate bugs in our code
                RhettJSCommon.LOGGER.error("[RhettJS] Error in $context: ${error.message}", error)
                return
            }
        }

        // Log the formatted message (without Java stack trace)
        RhettJSCommon.LOGGER.error(message)
    }

    /**
     * Format a JavaScript error for display to a player in-game.
     * Returns a concise, single-line error message suitable for chat.
     *
     * @param error The exception to format
     * @return A formatted error message
     */
    fun formatForPlayer(error: Throwable): String {
        return when (error) {
            is JavaScriptException -> {
                val value = error.value
                if (value != null && value.toString().startsWith("Error:")) {
                    value.toString()
                } else {
                    "Error: ${error.message}"
                }
            }

            is EcmaError -> {
                "${error.name}: ${error.errorMessage}"
            }

            is EvaluatorException -> {
                "Syntax error: ${error.message}"
            }

            else -> {
                "Error: ${error.message}"
            }
        }
    }

    /**
     * Extract source location from a JavaScript error.
     * Returns a string like "server/test-threading.js:42"
     *
     * @param error The exception to extract location from
     * @return Source location string, or null if not available
     */
    fun getSourceLocation(error: Throwable): String? {
        return when (error) {
            is RhinoException -> {
                val sourceName = error.sourceName()
                val lineNumber = error.lineNumber()
                if (sourceName != null && lineNumber > 0) {
                    "$sourceName:$lineNumber"
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
