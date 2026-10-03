package com.xuanji.android

import com.xuanji.android.ui.chat.nextStellarWelcome
import com.xuanji.android.ui.chat.stellarWelcomeScenes
import com.xuanji.android.ui.chat.storedStellarWelcome
import com.xuanji.android.ui.chat.stellarDomainPortraits
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StellarWelcomeTest {
    @Test
    fun everyWelcomeDomainHasAnOfficialPortrait() {
        assertEquals(stellarWelcomeScenes.map { it.id }.toSet(), stellarDomainPortraits.keys)
        assertTrue(stellarDomainPortraits.values.all { it.role.isNotBlank() && it.discipline.isNotBlank() })
        assertEquals("晨光向导", stellarDomainPortraits.getValue("qiming").role)
        assertEquals("全局统筹官", stellarDomainPortraits.getValue("tianshu").role)
        assertEquals("代码美学者", stellarDomainPortraits.getValue("wenqu").role)
        assertEquals("极简中心", stellarDomainPortraits.getValue("taiyi").role)
    }

    @Test
    fun newWelcomeCannotRepeatThePreviousScene() {
        val random = Random(37)
        var previous: String? = null
        val seen = mutableSetOf<String>()
        repeat(400) {
            val scene = nextStellarWelcome(previous, random)
            assertNotEquals(previous, scene.id)
            seen += scene.id
            previous = scene.id
        }
        assertEquals(stellarWelcomeScenes.map { it.id }.toSet(), seen)
    }

    @Test
    fun removedOrAbsentSavedSceneStillSelectsAValidScene() {
        assertTrue(nextStellarWelcome("retired-domain", Random(1)) in stellarWelcomeScenes)
        assertTrue(nextStellarWelcome(null, Random(1)) in stellarWelcomeScenes)
    }

    @Test
    fun restoringASessionUsesItsOwnSceneAndDoesNotRedraw() {
        val fallback = nextStellarWelcome(null, Random(3))
        val saved = stellarWelcomeScenes.first { it.id == "tianquan" }
        assertEquals(saved, storedStellarWelcome(saved.id, fallback))
        assertEquals(fallback, storedStellarWelcome("removed-scene", fallback))
        assertEquals(fallback, storedStellarWelcome(null, fallback))
    }
}
