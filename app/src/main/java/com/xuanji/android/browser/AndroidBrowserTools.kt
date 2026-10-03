package com.xuanji.android.browser

import com.xuanji.android.AndroidMcpTool
import kotlinx.serialization.json.*

private fun schema(properties: JsonObject = JsonObject(emptyMap()), required: List<String> = emptyList()) = buildJsonObject {
    put("type", "object"); put("properties", properties); put("additionalProperties", false)
    if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
}
private fun string() = buildJsonObject { put("type", "string") }

internal val androidBrowserTools = listOf(
    AndroidMcpTool("android_browser_tabs", "List Xuanji browser tabs, active/visible state, navigation availability and the remaining capacity (maximum 8 tabs). Read-only.", schema()),
    AndroidMcpTool("android_browser_open", "Open an HTTP(S) URL in a new Xuanji browser tab. Returns a tabId; then use android_browser_snapshot to read it. No desktop Chromium required.",
        schema(buildJsonObject { put("url", string()) }, listOf("url"))),
    AndroidMcpTool("android_browser_snapshot", "Read the live DOM of a Xuanji browser tab: untrusted text, numbered controls (including select options), scroll state and snapshotId. For long text, use page.nextOffset as textOffset until page.hasMore=false; scanLimited reports the DOM scan limit. Optional region is a CSS selector for text only; viewportOnly limits text and controls to the viewport. Sensitive values are masked. Frame/shadow DOM contents are not included. Read again after every mutation.",
        schema(buildJsonObject {
            put("tabId", string())
            putJsonObject("textOffset") { put("type", "integer"); put("minimum", 0); put("maximum", 500000) }
            putJsonObject("maxChars") { put("type", "integer"); put("minimum", 1000); put("maximum", 24000) }
            putJsonObject("viewportOnly") { put("type", "boolean") }
            putJsonObject("region") { put("type", "string"); put("minLength", 1); put("maxLength", 512) }
        })),
    AndroidMcpTool("android_browser_control", "Navigate an existing Xuanji browser tab, go back/forward, reload/stop, activate a tab, or close a tab to free capacity. Requires confirm=true and a separate local approval. Browser must be visible; navigation targets the active tab. activate/close can target another listed tab. navigate requires an HTTP(S) url. Returns real browser state; navigationPending means read a new snapshot after loading.",
        schema(buildJsonObject {
            put("tabId", string())
            putJsonObject("action") { put("type", "string"); put("enum", buildJsonArray {
                listOf("navigate", "back", "forward", "reload", "stop", "activate", "close").forEach { add(JsonPrimitive(it)) }
            }) }
            put("url", string()); putJsonObject("confirm") { put("type", "boolean") }
        }, listOf("tabId", "action", "confirm"))),
    AndroidMcpTool("android_browser_action", "Operate a numbered control from a fresh snapshot: click, type (replace plain/rich text), select (value or label), check (checkbox/radio), submit (native form validation), or scroll (page, or numbered container). Requires confirm=true and a separate local approval; target tab must be active/visible. scroll requires direction and optionally amount in pixels; other actions require elementId. Sensitive fields, upload and multi-select are manual. Snapshot is consumed; success means dispatched, so read again to verify the result.",
        schema(buildJsonObject {
            put("tabId", string()); put("snapshotId", string())
            putJsonObject("elementId") { put("type", "integer"); put("minimum", 1); put("maximum", 100) }
            putJsonObject("action") { put("type", "string"); put("enum", buildJsonArray {
                listOf("click", "type", "select", "check", "submit", "scroll").forEach { add(JsonPrimitive(it)) }
            }) }
            putJsonObject("text") { put("type", "string"); put("maxLength", 16384) }
            putJsonObject("value") { put("type", "string"); put("maxLength", 512) }
            putJsonObject("optionBy") { put("type", "string"); put("enum", buildJsonArray { add(JsonPrimitive("value")); add(JsonPrimitive("label")) }) }
            putJsonObject("checked") { put("type", "boolean") }
            putJsonObject("direction") { put("type", "string"); put("enum", buildJsonArray {
                listOf("up", "down", "left", "right", "top", "bottom").forEach { add(JsonPrimitive(it)) }
            }) }
            putJsonObject("amount") { put("type", "integer"); put("minimum", 1); put("maximum", 10000) }
            putJsonObject("confirm") { put("type", "boolean") }
        }, listOf("tabId", "snapshotId", "action", "confirm"))),
)
