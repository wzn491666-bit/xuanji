package com.xuanji.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySemanticsTest {
    @Test
    fun clickableParentMakesTextNodeClickable() {
        assertTrue(nodeHasClickSemantics(selfClickable = false, hasClickAction = false, ancestorClickable = true))
    }

    @Test
    fun clickActionOnNodeIsClickableEvenWhenFlagIsFalse() {
        assertTrue(nodeHasClickSemantics(selfClickable = false, hasClickAction = true, ancestorClickable = false))
    }

    @Test
    fun plainTextNodeRemainsNonClickable() {
        assertFalse(nodeHasClickSemantics(selfClickable = false, hasClickAction = false, ancestorClickable = false))
    }
}
