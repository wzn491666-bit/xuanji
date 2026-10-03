package com.xuanji.android

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidMcpProtocolTest {
    private val tools = listOf(
        AndroidMcpTool(
            name = "android_shell_id",
            description = "Return the uid for a selected Android shell backend.",
            inputSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("backend") { put("type", "string") } }
            },
        ),
    )

    private val protocol = AndroidMcpProtocol(tools) { name, _ ->
        assertEquals("android_shell_id", name)
        AndroidMcpToolResult("uid=0(root)")
    }

    @Test
    fun initializeReturnsMcpCapabilities() = runBlocking {
        val response = protocol.handle(Json.parseToJsonElement("""
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}}
        """).jsonObject)

        assertNotNull(response)
        assertEquals(1, response!!["id"]?.jsonPrimitive?.content?.toInt())
        assertEquals("2024-11-05", response["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
        assertNotNull(response["result"]!!.jsonObject["capabilities"]!!.jsonObject["tools"])
    }

    @Test
    fun toolsListPublishesDefinitions() = runBlocking {
        val response = protocol.handle(Json.parseToJsonElement(
            """{"jsonrpc":"2.0","id":"list","method":"tools/list","params":{}}"""
        ).jsonObject)

        val listed = response!!["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(1, listed.size)
        assertEquals("android_shell_id", listed[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun toolCallReturnsTextContent() = runBlocking {
        val response = protocol.handle(Json.parseToJsonElement("""
            {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"android_shell_id","arguments":{"backend":"root"}}}
        """.trimIndent()).jsonObject)

        val result = response!!["result"]!!.jsonObject
        assertEquals("uid=0(root)", result["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(false, result["isError"]?.jsonPrimitive?.content?.toBoolean() ?: false)
    }

    @Test
    fun unknownToolReturnsJsonRpcError() = runBlocking {
        val response = protocol.handle(Json.parseToJsonElement("""
            {"jsonrpc":"2.0","id":"bad","method":"tools/call","params":{"name":"android_delete_everything","arguments":{}}}
        """.trimIndent()).jsonObject)

        assertNotNull(response!!["error"])
        assertTrue(response["error"]!!.jsonObject["message"]!!.jsonPrimitive.content.contains("Unknown MCP tool"))
    }

    @Test
    fun initializedNotificationDoesNotProduceResponse() = runBlocking {
        val response = protocol.handle(Json.parseToJsonElement(
            """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
        ).jsonObject)
        assertNull(response)
    }
}

