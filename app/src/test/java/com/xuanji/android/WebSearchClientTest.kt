package com.xuanji.android

import com.sun.net.httpserver.HttpServer
import com.xuanji.android.search.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebSearchClientTest {
    @Test fun manualModeActuallyRequestsEverySelectedEngineAndMergesEvidence() = runBlocking {
        withServer { server ->
            val requests = CopyOnWriteArrayList<String>()
            val maximum = AtomicInteger()
            val active = AtomicInteger()
            server.createContext("/") { exchange ->
                val current = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, current) }
                try {
                    val engine = SearchEngine.entries.single { "/${it.wire}" == exchange.requestURI.path }
                    requests += engine.wire
                    val payload = exchange.requestBody.bufferedReader().readText()
                    if (engine !in setOf(SearchEngine.BING, SearchEngine.DUCKDUCKGO, SearchEngine.SEARXNG)) {
                        val body = Json.parseToJsonElement(payload).jsonObject
                        assertEquals("android", body["query"]!!.jsonPrimitive.content)
                        if (engine == SearchEngine.TAVILY) assertEquals("advanced", body["search_depth"]!!.jsonPrimitive.content)
                        if (engine == SearchEngine.EXA) assertEquals(10, body["numResults"]!!.jsonPrimitive.int)
                    } else assertTrue(exchange.requestURI.rawQuery.contains("q=android"))
                    if (engine in setOf(SearchEngine.EXA, SearchEngine.KEENABLE)) assertEquals("test-search-secret", exchange.requestHeaders.getFirst("x-api-key"))
                    Thread.sleep(30)
                    val text = fixture(engine)
                    exchange.responseHeaders.add("Content-Type", if (engine == SearchEngine.BING) "application/rss+xml" else if (engine == SearchEngine.DUCKDUCKGO) "text/html" else "application/json")
                    val bytes = text.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                } finally { active.decrementAndGet(); exchange.close() }
            }
            server.start()
            val client = WebSearchClient(endpoints = endpoints(server))
            val credentials = SearchEngine.entries.associateWith { SearchCredential("test-search-secret") }
            val report = client.search("android", SearchSettings(false, SearchEngine.entries.toSet(), SearchDepth.HIGH), credentials)
            assertEquals(SearchEngine.entries.map { it.wire }.toSet(), requests.toSet())
            assertEquals(8, requests.size)
            assertEquals(8, report.engines.count { it.successful })
            assertTrue("At most three requests may be active", maximum.get() <= 3)
            assertEquals(1, report.sources.size)
            assertEquals(SearchEngine.entries.toSet(), report.sources.single().engines)
            assertEquals("https://example.com/article", report.sources.single().url)
        }
    }

    @Test fun aFailingEngineDoesNotDiscardOtherResultsOrExposeCredentials() = runBlocking {
        withServer { server ->
            server.createContext("/") { exchange ->
                val failing = exchange.requestURI.path.endsWith("tavily")
                val bytes = (if (failing) "test-search-secret" else fixture(SearchEngine.ANYSEARCH)).toByteArray()
                exchange.sendResponseHeaders(if (failing) 401 else 200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val selected = setOf(SearchEngine.TAVILY, SearchEngine.ANYSEARCH)
            val report = WebSearchClient(endpoints = endpoints(server)).search("android", SearchSettings(false, selected),
                selected.associateWith { SearchCredential("test-search-secret") })
            assertEquals(2, report.engines.size)
            assertEquals(1, report.sources.size)
            assertTrue(report.engines.first().error!!.contains("401"))
            assertFalse(report.toJson().toString().contains("test-search-secret"))
            assertFalse(SearchCredential("test-search-secret").toString().contains("test-search-secret"))
        }
    }

    @Test fun automaticRoutingContinuesAfterFailureAndStopsAtDepthTarget() = runBlocking {
        withServer { server ->
            val requests = CopyOnWriteArrayList<String>()
            server.createContext("/") { exchange ->
                requests += exchange.requestURI.path
                val failed = exchange.requestURI.path.endsWith("tavily")
                val bytes = fixture(SearchEngine.ANYSEARCH).toByteArray()
                exchange.sendResponseHeaders(if (failed) 429 else 200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val report = WebSearchClient(endpoints = endpoints(server)).search("android", SearchSettings(true,
                setOf(SearchEngine.TAVILY, SearchEngine.ANYSEARCH, SearchEngine.BING), SearchDepth.LOW))
            assertEquals(listOf("/tavily", "/anysearch"), requests)
            assertEquals(1, report.engines.count { it.successful })
        }
    }

    @Test fun businessErrorsCaptchasAndMalformedResponsesCannotBecomeSuccess() {
        assertNotNull(runCatching { parseSearchResponse(SearchEngine.ANYSEARCH, """{"code":1,"data":{"results":[]}}""", "application/json", SearchDepth.LOW) }.exceptionOrNull())
        assertNotNull(runCatching { parseSearchResponse(SearchEngine.FIRECRAWL, """{"success":false,"data":[]}""", "application/json", SearchDepth.LOW) }.exceptionOrNull())
        assertNotNull(runCatching { parseSearchResponse(SearchEngine.TAVILY, "{}", "application/json", SearchDepth.LOW) }.exceptionOrNull())
        assertNotNull(runCatching { parseSearchResponse(SearchEngine.DUCKDUCKGO, "<form id='challenge-form'></form>", "text/html", SearchDepth.LOW) }.exceptionOrNull())
        assertNotNull(runCatching { parseSearchResponse(SearchEngine.BING, "<html>verification</html>", "text/html", SearchDepth.LOW) }.exceptionOrNull())
    }

    @Test fun configuredErrorsAreIsolatedAndAllSelectedEnginesAreReported() = runBlocking {
        val report = WebSearchClient().search("android", SearchSettings(false, setOf(SearchEngine.EXA, SearchEngine.KEENABLE)),
            mapOf(SearchEngine.EXA to SearchCredential(error = "密钥无法解密"), SearchEngine.KEENABLE to SearchCredential(error = "配置错误")))
        assertEquals(2, report.engines.size)
        assertTrue(report.engines.all { it.error != null })
        assertTrue(report.sources.isEmpty())
    }

    @Test fun duplicateResultsFromOneEngineDoNotCreateFalseCrossSourceEvidence() {
        val source = SearchSource("a", "https://example.com/article?utm_source=x#section", "s", setOf(SearchEngine.BING))
        val result = mergeSearchSources(listOf(EngineSearchResult(SearchEngine.BING, listOf(source, source))))
        assertEquals(1, result.size)
        assertEquals(setOf(SearchEngine.BING), result.single().engines)
        assertNull(canonicalSearchUrl("file:///sdcard/private.txt"))
        assertNull(canonicalSearchUrl("https://user:pass@example.com/"))
    }

    @Test fun anonymousMcpAdaptersMakeRealRequestsAndParseJsonOrSse() = runBlocking {
        withServer { server ->
            server.createContext("/") { exchange ->
                val exa = exchange.requestURI.path.endsWith("exa")
                val request = Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
                assertEquals("tools/call", request["method"]!!.jsonPrimitive.content)
                assertEquals(if (exa) "web_search_exa" else "search_web_pages", request["params"]!!.jsonObject["name"]!!.jsonPrimitive.content)
                assertNull(exchange.requestHeaders.getFirst("Authorization"))
                val text = "Title: WebView\nURL: https://developer.android.com/webview\n${if (exa) "Highlights" else "Snippets"}:\nAndroid documentation"
                val body = buildJsonObject { put("id", 1); putJsonObject("result") {
                    put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) })
                } }.toString()
                exchange.responseHeaders.add("Content-Type", if (exa) "text/event-stream" else "application/json")
                val bytes = (if (exa) "event: message\ndata: $body\n\n" else body).toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val report = WebSearchClient(endpoints = endpoints(server)).search("android", SearchSettings(false, setOf(SearchEngine.EXA, SearchEngine.KEENABLE)))
            assertEquals(2, report.engines.count { it.successful })
            assertEquals(1, report.sources.size)
            assertEquals("Android documentation", report.sources.single().snippet)
            assertNotNull(runCatching { parseSearchMcpResponse(SearchEngine.EXA,
                """{"id":1,"result":{"isError":true,"content":[]}}""", "application/json", SearchDepth.LOW) }.exceptionOrNull())
        }
    }

    @Test fun cancellingTheSearchCancelsItsSocket() = runBlocking {
        withServer { server ->
            val started = CountDownLatch(1)
            val released = CountDownLatch(1)
            val cancelled = CountDownLatch(1)
            server.createContext("/") { exchange ->
                try {
                    exchange.sendResponseHeaders(200, 100)
                    exchange.responseBody.write('{'.code); exchange.responseBody.flush()
                    started.countDown()
                    released.await(5, TimeUnit.SECONDS)
                } finally { exchange.close() }
            }
            server.start()
            val http = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.countDown() }
            }).build()
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                WebSearchClient(http, endpoints(server)).search("android", SearchSettings(false, setOf(SearchEngine.ANYSEARCH)))
            }
            try {
                assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
                job.cancelAndJoin()
                assertTrue(cancelled.await(2, TimeUnit.SECONDS))
            } finally { released.countDown(); job.cancelAndJoin() }
        }
    }

    private fun endpoints(server: HttpServer) = SearchEngine.entries.associateWith { "http://127.0.0.1:${server.address.port}/${it.wire}" }
    private fun fixture(engine: SearchEngine): String {
        val row = """{"title":"Android","url":"https://example.com/article?utm_source=test","content":"official docs"}"""
        return when (engine) {
            SearchEngine.ANYSEARCH -> """{"code":0,"data":{"results":[$row]}}"""
            SearchEngine.FIRECRAWL -> """{"success":true,"data":{"web":[$row]}}"""
            SearchEngine.BING -> "<rss><channel><item><title>Android</title><link>https://example.com/article</link><description>official docs</description></item></channel></rss>"
            SearchEngine.DUCKDUCKGO -> "<div class='result'><a class='result__a' href='https://example.com/article'>Android</a><div class='result__snippet'>official docs</div></div>"
            else -> """{"results":[$row]}"""
        }
    }
    private suspend fun withServer(block: suspend (HttpServer) -> Unit) {
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { this.executor = executor }
        try { block(server) } finally { server.stop(0); executor.shutdownNow() }
    }
}
