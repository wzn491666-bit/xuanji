package com.xuanji.android.ui.chat

import com.xuanji.android.ThinkingDisplay

internal enum class ThinkingBodyMode { HIDDEN, PREVIEW, FULL }

internal fun thinkingGroupMode(display: ThinkingDisplay, overrideExpanded: Boolean?): ThinkingBodyMode = when {
    overrideExpanded == true -> ThinkingBodyMode.FULL
    overrideExpanded == false -> ThinkingBodyMode.HIDDEN
    display == ThinkingDisplay.EXPANDED -> ThinkingBodyMode.FULL
    display == ThinkingDisplay.PREVIEW -> ThinkingBodyMode.PREVIEW
    else -> ThinkingBodyMode.HIDDEN
}

// The global expanded setting opens the group; only a direct child click opens full text.
internal fun thinkingSegmentMode(display: ThinkingDisplay, overrideExpanded: Boolean?): ThinkingBodyMode = when {
    overrideExpanded == true -> ThinkingBodyMode.FULL
    display == ThinkingDisplay.COLLAPSED -> ThinkingBodyMode.HIDDEN
    else -> ThinkingBodyMode.PREVIEW
}
