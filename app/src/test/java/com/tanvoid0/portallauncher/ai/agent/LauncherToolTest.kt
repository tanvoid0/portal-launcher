package com.tanvoid0.portallauncher.ai.agent

import com.tanvoid0.portallauncher.data.DndFilterLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [resolveLauncherTool] and [LauncherToolCall]'s coercion -- the pure logic
 * `LauncherAgent`'s loop depends on to route a model's reply to the right tool.
 */
class LauncherToolTest {

    private fun tool(name: String) = LauncherTool(name = name, description = "d") { "ok" }

    @Test
    fun `resolves an exact name`() {
        val tools = listOf(tool("a"), tool("b"))
        assertEquals("a", resolveLauncherTool(tools, "a")?.name)
    }

    @Test
    fun `an unknown name resolves to nothing`() {
        assertNull(resolveLauncherTool(listOf(tool("a")), "missing"))
    }

    @Test
    fun `a name matching more than one tool is refused, not guessed at`() {
        // Two tools should never share a name, but if they somehow did, resolution
        // must not silently pick one -- see resolveTool in ai_tool.dart.
        assertNull(resolveLauncherTool(listOf(tool("a"), tool("a")), "a"))
    }

    @Test
    fun `argEnum matches the prompt's snake_case against a PascalCase enum name`() {
        val call = LauncherToolCall("set_dnd", mapOf("level" to "priority_only"))
        assertEquals(DndFilterLevel.PriorityOnly, call.argEnum<DndFilterLevel>("level"))
    }

    @Test
    fun `argEnum is tolerant of case and is null for anything else`() {
        val call = LauncherToolCall("set_dnd", mapOf("level" to "ALARMS_ONLY", "other" to "nonsense"))
        assertEquals(DndFilterLevel.AlarmsOnly, call.argEnum<DndFilterLevel>("level"))
        assertNull(call.argEnum<DndFilterLevel>("other"))
        assertNull(call.argEnum<DndFilterLevel>("missing"))
    }
}
