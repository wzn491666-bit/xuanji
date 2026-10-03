package com.xuanji.android

import com.xuanji.android.browser.browserUrl
import com.xuanji.android.browser.parseBrowserControl
import com.xuanji.android.browser.validateBrowserAction
import com.xuanji.android.browser.browserSnapshotOptions
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BrowserContractTest {
    @Test fun browserAcceptsOnlyWebAddressesWithoutEmbeddedCredentials() {
        assertEquals("https://example.com/", browserUrl("example.com"))
        assertEquals("http://192.168.1.2:8080/path", browserUrl("http://192.168.1.2:8080/path"))
        listOf("file:///sdcard/secret", "content://private/1", "javascript:alert(1)", "intent://open",
            "https://user:pass@example.com/", "", "data:text/html,<script></script>").forEach {
            assertNotNull(it, runCatching { browserUrl(it) }.exceptionOrNull())
        }
    }

    @Test fun browserControlsValidateTargetApprovalAndNavigationAddress() {
        fun request(action: String, url: String? = null, confirm: Boolean = true) = buildJsonObject {
            put("tabId", "tab-1"); put("action", action); put("confirm", confirm)
            url?.let { put("url", it) }
        }
        assertEquals("https://example.com/", parseBrowserControl(request("navigate", "example.com")).url)
        listOf("back", "forward", "reload", "stop", "activate", "close").forEach {
            assertEquals(it, parseBrowserControl(request(it)).action)
        }
        listOf(request("navigate"), request("navigate", "javascript:alert(1)"), request("close", confirm = false),
            request("unknown"), request("back", "example.com"),
            buildJsonObject { put("tabId", "tab-1"); put("action", "close"); put("confirm", "true") }).forEach {
            assertNotNull(runCatching { parseBrowserControl(it) }.exceptionOrNull())
        }
    }

    @Test fun browserActionsRequireFreshSnapshotAndActionSpecificArguments() {
        fun request(action: String, elementId: Int? = 1, extra: JsonObject = JsonObject(emptyMap())) = buildJsonObject {
            put("tabId", "tab-1"); put("snapshotId", "snapshot-1"); put("confirm", true); put("action", action)
            elementId?.let { put("elementId", it) }; extra.forEach { (key, value) -> put(key, value) }
        }
        assertEquals("scroll", validateBrowserAction(request("scroll", null, buildJsonObject { put("direction", "down") })))
        assertEquals("select", validateBrowserAction(request("select", extra = buildJsonObject { put("value", "") })))
        assertEquals("check", validateBrowserAction(request("check", extra = buildJsonObject { put("checked", false) })))
        listOf(request("click", null), request("click", 101), request("type"), request("select"), request("check"),
            request("scroll", extra = buildJsonObject { put("direction", "diagonal") }),
            request("scroll", extra = buildJsonObject { put("direction", "down"); put("amount", 0) }),
            request("check", extra = buildJsonObject { put("checked", "false") }),
            request("scroll", extra = buildJsonObject { put("direction", "down"); put("amount", "120") }),
            request("type", extra = buildJsonObject { put("text", 123) })).forEach {
            assertNotNull(runCatching { validateBrowserAction(it) }.exceptionOrNull())
        }
    }

    @Test fun browserReadPaginationBoundsAreEnforcedBeforeWebView() {
        assertEquals(12000, browserSnapshotOptions(JsonObject(emptyMap()))["maxChars"]?.jsonPrimitive?.int)
        assertEquals(24000, browserSnapshotOptions(buildJsonObject { put("textOffset", 12000); put("maxChars", 24000) })["maxChars"]?.jsonPrimitive?.int)
        listOf(buildJsonObject { put("textOffset", -1) }, buildJsonObject { put("maxChars", 25000) },
            buildJsonObject { put("viewportOnly", "yes") }, buildJsonObject { put("region", "") }).forEach {
            assertNotNull(runCatching { browserSnapshotOptions(it) }.exceptionOrNull())
        }
    }
}
