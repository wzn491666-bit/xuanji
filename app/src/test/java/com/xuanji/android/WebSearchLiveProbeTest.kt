package com.xuanji.android

import com.xuanji.android.search.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in network probe. Does not read or send any stored user credential. */
class WebSearchLiveProbeTest {
    @Test fun publicInterfacesReturnRealSources() = runBlocking {
        assumeTrue(System.getenv("XUANJI_LIVE_SEARCH") == "1")
        val engines = SearchEngine.entries.toSet() - SearchEngine.SEARXNG
        val report = WebSearchClient().search("Android WebView documentation", SearchSettings(false, engines, SearchDepth.LOW))
        report.engines.forEach { result -> println("${result.engine.label}: ${result.sources.size} sources, ${result.elapsedMs} ms, ${result.error ?: "ok"}") }
        assertEquals(engines, report.engines.map { it.engine }.toSet())
        assertTrue("At least one public service must respond", report.engines.any { it.successful })
        assertTrue(report.sources.isNotEmpty())
        assertTrue(report.sources.all { it.url.startsWith("http") && it.engines.isNotEmpty() })
    }
}
