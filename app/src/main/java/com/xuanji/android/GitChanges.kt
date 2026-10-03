package com.xuanji.android

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class GitChange(val path: String, val status: String, val additions: Int, val deletions: Int)
data class GitWorkingTree(val isRepo: Boolean, val files: List<GitChange>)
data class GitDiff(val path: String, val text: String, val truncated: Boolean = false)

internal fun parseGitWorkingTree(response: JsonObject): GitWorkingTree = GitWorkingTree(
    isRepo = response["isRepo"]?.jsonPrimitive?.booleanOrNull == true,
    files = response["files"]?.jsonArray.orEmpty().mapNotNull { element ->
        val file = element as? JsonObject ?: return@mapNotNull null
        GitChange(
            path = file["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            status = file["status"]?.jsonPrimitive?.contentOrNull ?: "modified",
            additions = file["additions"]?.jsonPrimitive?.intOrNull ?: 0,
            deletions = file["deletions"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    },
)

enum class GitDiffLineKind { HEADER, HUNK, ADDED, REMOVED, CONTEXT }

internal fun gitDiffLineKind(line: String): GitDiffLineKind = when {
    line.startsWith("diff --git ") || line.startsWith("index ") || line.startsWith("+++") || line.startsWith("---") -> GitDiffLineKind.HEADER
    line.startsWith("@@") -> GitDiffLineKind.HUNK
    line.startsWith("+") -> GitDiffLineKind.ADDED
    line.startsWith("-") -> GitDiffLineKind.REMOVED
    else -> GitDiffLineKind.CONTEXT
}
