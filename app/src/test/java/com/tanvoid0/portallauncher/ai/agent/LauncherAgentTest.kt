package com.tanvoid0.portallauncher.ai.agent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays a fixed script of model replies, one per turn -- Kotlin port of `ai_agent_test.dart`'s `_ScriptedClient`. */
private class ScriptedClient(private val replies: List<String?>) : LauncherAiClient {
    val prompts = mutableListOf<String>()
    private var index = 0
    override suspend fun generate(prompt: String): String? {
        prompts += prompt
        return replies.getOrNull(index++)
    }
}

private fun echoTool(mutates: Boolean = false, run: suspend (LauncherToolCall) -> String = { "done" }) =
    LauncherTool(name = "do_thing", description = "does a thing", mutates = mutates, run = run)

class LauncherAgentTest {

    @Test
    fun `extracts JSON from fenced or chatty replies`() {
        val fenced = extractJsonObject("```json\n{\"final\":\"hi\"}\n```")
        assertEquals("hi", (fenced?.get("final") as? JsonPrimitive)?.content)

        val chatty = extractJsonObject("Sure! {\"tool\":\"a\",\"args\":{}} done")
        assertEquals("a", (chatty?.get("tool") as? JsonPrimitive)?.content)

        // A model batching several calls into one turn yields the first object; the
        // loop asks again for the rest.
        val batched = extractJsonObject(
            "[{\"tool\":\"a\",\"args\":{\"name\":\"pasta\"}},{\"tool\":\"a\",\"args\":{}}]"
        )
        val batchedArgs = batched?.get("args") as? JsonObject
        assertEquals("pasta", (batchedArgs?.get("name") as? JsonPrimitive)?.content)

        assertNull(extractJsonObject("no json here"))
        assertNull(extractJsonObject("{broken"))
    }

    @Test
    fun `runs a read-only tool then finishes`() = runBlocking {
        var ran = false
        val client = ScriptedClient(
            listOf("{\"tool\":\"do_thing\",\"args\":{}}", "{\"final\":\"Done.\"}")
        )
        val agent = LauncherAgent(client = client, tools = listOf(echoTool { ran = true; "ok" }))

        val result = agent.run("do it")

        assertTrue(ran)
        assertEquals("Done.", result.message)
        assertEquals(1, result.steps.size)
        assertFalse(result.steps.single().failed)
        // The second turn must carry the tool result back to the model.
        assertTrue(client.prompts[1].contains("ok"))
    }

    @Test
    fun `a mutating tool is refused when there is nobody to approve it`() = runBlocking {
        var ran = false
        val client = ScriptedClient(
            listOf("{\"tool\":\"do_thing\",\"args\":{}}", "{\"final\":\"Could not do it.\"}")
        )
        val agent = LauncherAgent(
            client = client,
            tools = listOf(echoTool(mutates = true) { ran = true; "ok" })
        )

        val result = agent.run("do it") // no confirm callback

        assertFalse(ran)
        assertTrue(result.steps.single().failed)
        assertTrue(client.prompts[1].contains("refused"))
    }

    @Test
    fun `declining a mutating tool skips it and tells the model, the loop continues`() = runBlocking {
        var ran = false
        val client = ScriptedClient(
            listOf("{\"tool\":\"do_thing\",\"args\":{}}", "{\"final\":\"Skipped it.\"}")
        )
        val agent = LauncherAgent(
            client = client,
            tools = listOf(echoTool(mutates = true) { ran = true; "ok" })
        )

        val result = agent.run("do it", confirm = { _, _ -> false })

        assertFalse(ran)
        assertEquals("Skipped it.", result.message)
        assertTrue(client.prompts[1].contains("user declined"))
    }

    @Test
    fun `an unknown tool name is reported back without stopping the run`() = runBlocking {
        val client = ScriptedClient(
            listOf("{\"tool\":\"nonexistent\",\"args\":{}}", "{\"final\":\"Gave up.\"}")
        )
        val agent = LauncherAgent(client = client, tools = listOf(echoTool()))

        val result = agent.run("do it")

        assertTrue(result.steps.isEmpty())
        assertEquals("Gave up.", result.message)
        assertTrue(client.prompts[1].contains("unknown tool"))
    }

    @Test
    fun `stops after maxSteps steps without a final message`() = runBlocking {
        val client = ScriptedClient(List(10) { "{\"tool\":\"do_thing\",\"args\":{}}" })
        val agent = LauncherAgent(client = client, tools = listOf(echoTool()), maxSteps = 3)

        val result = agent.run("do it")

        assertEquals(3, result.steps.size)
        assertEquals("Stopped after 3 steps without finishing.", result.message)
    }

    @Test
    fun `a model that never answers ends the run instead of throwing`() = runBlocking {
        val client = ScriptedClient(listOf(null))
        val agent = LauncherAgent(client = client, tools = listOf(echoTool()))

        val result = agent.run("do it")

        assertTrue(result.steps.isEmpty())
        assertTrue(result.message.isNotEmpty())
    }
}
