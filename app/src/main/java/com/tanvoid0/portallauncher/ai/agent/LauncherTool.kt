package com.tanvoid0.portallauncher.ai.agent

/**
 * One action the assistant can take inside the launcher.
 *
 * Kotlin port of `portal_ai`'s `AiTool` (see `packages/portal_ai/lib/src/tools/ai_tool.dart`),
 * trimmed to what this app needs: no namespaces (one tool set, one app) and no rich
 * `AiBlock` rendering (every tool here returns a short status string, not list-shaped
 * data). [parameters] maps an argument name to a one-line description, pasted straight
 * into the model's prompt.
 */
data class LauncherTool(
    val name: String,
    val description: String,
    val parameters: Map<String, String> = emptyMap(),
    /** Whether running this tool changes user data or device settings. */
    val mutates: Boolean = false,
    val run: suspend (LauncherToolCall) -> String
) {
    /** Single line describing this tool in the system prompt. */
    val spec: String
        get() {
            val args = parameters.entries.joinToString("; ") { "${it.key}: ${it.value}" }
            return "- $name($args)${if (mutates) " [changes data]" else ""}: $description"
        }
}

/** A tool invocation requested by the model. */
data class LauncherToolCall(val name: String, val args: Map<String, Any?>) {

    fun argString(key: String): String? {
        val value = args[key] ?: return null
        val text = (if (value is String) value else value.toString()).trim()
        return text.takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
    }

    fun argInt(key: String): Int? {
        val value = args[key]
        if (value is Number) return value.toInt()
        return argString(key)?.toIntOrNull()
    }

    override fun toString(): String = "$name($args)"
}

/**
 * Coerces arg [key] to [T], tolerant of the snake_case the prompt asks for against a
 * PascalCase Kotlin enum name -- "priority_only" must still match [DndFilterLevel]'s
 * `PriorityOnly`, since that gap is exactly where a small model's reply would otherwise
 * silently fail to match.
 */
inline fun <reified T : Enum<T>> LauncherToolCall.argEnum(key: String): T? {
    val normalized = argString(key)?.replace("_", "")?.replace(" ", "") ?: return null
    return enumValues<T>().find { it.name.equals(normalized, ignoreCase = true) }
}

/** One completed step of an agent run. */
data class LauncherAgentStep(
    val call: LauncherToolCall,
    val result: String,
    val failed: Boolean = false
)

/**
 * Resolves [name] against [tools].
 *
 * A single flat tool set has no namespace collisions today, but this stays as
 * defensive as `portal_ai`'s `resolveTool` at no cost: a name matching more than one
 * tool (a bug, since names are meant to be unique) is refused rather than guessed at.
 */
fun resolveLauncherTool(tools: List<LauncherTool>, name: String): LauncherTool? =
    tools.filter { it.name == name }.singleOrNull()
