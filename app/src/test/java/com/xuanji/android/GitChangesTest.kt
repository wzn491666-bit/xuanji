package com.xuanji.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GitChangesTest {
    @Test
    fun parsesOfficialSessionWorkingTree() {
        val response = Json.parseToJsonElement("""{
            "isRepo":true,"files":[{"path":"src/main.kt","status":"modified","additions":12,"deletions":3}]
        }""").jsonObject

        val changes = parseGitWorkingTree(response)

        assertEquals("src/main.kt", changes.files.single().path)
        assertEquals(12, changes.files.single().additions)
        assertEquals("modified", changes.files.single().status)
    }

    @Test
    fun nonRepositoryIsDistinctFromCleanRepository() {
        val response = Json.parseToJsonElement("""{"isRepo":false,"files":[]}""").jsonObject
        assertFalse(parseGitWorkingTree(response).isRepo)
    }

    @Test
    fun diffLinesHaveStableVisualKinds() {
        assertEquals(GitDiffLineKind.ADDED, gitDiffLineKind("+new line"))
        assertEquals(GitDiffLineKind.REMOVED, gitDiffLineKind("-old line"))
        assertEquals(GitDiffLineKind.HUNK, gitDiffLineKind("@@ -1,2 +1,3 @@"))
        assertEquals(GitDiffLineKind.HEADER, gitDiffLineKind("+++ b/file"))
    }
}
