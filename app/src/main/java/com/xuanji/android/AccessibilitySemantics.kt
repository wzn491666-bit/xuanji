package com.xuanji.android

/**
 * Compose can expose text and its clickable semantics as separate accessibility
 * nodes. Keep the semantic merge pure so the rule is regression-testable on the JVM.
 */
internal fun nodeHasClickSemantics(
    selfClickable: Boolean,
    hasClickAction: Boolean,
    ancestorClickable: Boolean,
): Boolean = selfClickable || hasClickAction || ancestorClickable
