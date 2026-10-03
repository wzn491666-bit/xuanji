package com.xuanji.android

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A tool definition exposed by the Android MCP bridge. */
data class AndroidMcpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

data class AndroidMcpToolResult(
    val text: String,
    val isError: Boolean = false,
)

/**
 * Minimal JSON-RPC layer for MCP Streamable HTTP.
 *
 * The MCP SDK used by Tianshu only needs initialize, tools/list, tools/call,
 * ping, and an acknowledgement-free notifications/initialized message for
 * this local bridge. Keeping this layer independent from sockets makes the
 * wire contract unit-testable on the JVM.
 */
class AndroidMcpProtocol(
    private val tools: List<AndroidMcpTool>,
    private val callTool: suspend (name: String, arguments: JsonObject) -> AndroidMcpToolResult,
) {
    suspend fun handle(message: JsonObject): JsonObject? {
        val method = message["method"]?.jsonPrimitive?.contentOrNull
            ?: return errorResponse(message["id"], -32600, "Missing JSON-RPC method")
        val hasId = message.containsKey("id")
        val id = message["id"]

        // JSON-RPC notifications never receive a response. This includes the
        // initialized notification sent by StreamableHTTPClientTransport.
        if (!hasId) return null

        return when (method) {
            "initialize" -> {
                val requested = message["params"]?.jsonObject?.get("protocolVersion")
                    ?.jsonPrimitive?.contentOrNull
                success(id, buildJsonObject {
                    put("protocolVersion", requested ?: "2024-11-05")
                    putJsonObject("capabilities") { putJsonObject("tools") {} }
                    putJsonObject("serverInfo") {
                        put("name", "xuanji-android")
                        put("version", "0.1")
                    }
                })
            }
            "tools/list" -> success(id, buildJsonObject {
                put("tools", buildJsonArray { tools.forEach { add(it.asJson()) } })
            })
            "tools/call" -> handleToolCall(id, message["params"]?.jsonObject)
            "ping" -> success(id, buildJsonObject {})
            else -> errorResponse(id, -32601, "Method not found: $method")
        }
    }

    private suspend fun handleToolCall(id: JsonElement?, params: JsonObject?): JsonObject {
        val name = params?.get("name")?.jsonPrimitive?.contentOrNull
        if (name.isNullOrBlank()) return errorResponse(id, -32602, "tools/call requires a tool name")
        if (tools.none { it.name == name }) return errorResponse(id, -32602, "Unknown MCP tool: $name")
        val arguments = params["arguments"]?.jsonObject ?: JsonObject(emptyMap())
        return try {
            val result = callTool(name, arguments)
            success(id, buildJsonObject {
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", result.text)
                    })
                })
                if (result.isError) put("isError", true)
            })
        } catch (t: Throwable) {
            success(id, buildJsonObject {
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", t.message ?: "Android MCP tool failed")
                    })
                })
                put("isError", true)
            })
        }
    }

    private fun AndroidMcpTool.asJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("inputSchema", inputSchema)
    }

    private fun success(id: JsonElement?, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("result", result)
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }
}
