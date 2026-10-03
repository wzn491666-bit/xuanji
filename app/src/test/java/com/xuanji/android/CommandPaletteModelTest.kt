package com.xuanji.android

import com.xuanji.android.ui.chat.MobileCommandCatalog
import com.xuanji.android.ui.chat.SkillEntry
import com.xuanji.android.ui.chat.MobileCommand
import com.xuanji.android.ui.chat.PaletteAction
import com.xuanji.android.ui.chat.libraryCommands
import com.xuanji.android.ui.chat.recentCommandSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteModelTest {
    @Test
    fun serverControlsAreAvailableWithoutPromptEmulation() {
        listOf("/domain", "/plan-mode", "/ask", "/fast", "/review off", "/plan-list", "/plan-view", "/plan-edit",
            "/plan-approve", "/plan-reject", "/goal", "/goal-status", "/goal-pause", "/goal-resume", "/goal-cancel", "/goal-criteria").forEach { name ->
            val command = MobileCommandCatalog.commands.single { it.name == name }
            assertTrue(name, command.mobileExecutable)
            assertTrue(name, command.nativeAction != null)
        }
    }

    @Test
    fun fullCatalogKeepsSupportedCommandsSelectableAndExplainsUnsupportedEntries() {
        val model = MobileCommandCatalog.filter("/model", emptyList()).workflows.first { it.name == "/model" }
        assertTrue(model.isSelectable(hasNativeHandler = true))
        assertEquals(false, model.isSelectable(hasNativeHandler = false))
        val capsule = MobileCommandCatalog.commands.single { it.name == "/capsule" }
        assertEquals(false, capsule.isSelectable(hasNativeHandler = true))
        assertTrue(capsule.unavailableReason.contains("胶囊"))
    }
    private val skills = listOf(
        SkillEntry("qa", "系统化验收", "builtin", enabled = true),
        SkillEntry("disabled", "不应展示", "rivet", enabled = false),
    )

    @Test
    fun emptyQueryShowsWorkflowsAndDisabledSkillsForManagement() {
        val result = MobileCommandCatalog.filter("", skills)
        assertTrue(result.workflows.any { it.name == "/plan" })
        assertEquals(listOf("qa", "disabled"), result.skills.map { it.name })
    }

    @Test
    fun queryMatchesDescriptions() {
        val result = MobileCommandCatalog.filter("验收", skills)
        assertEquals(listOf("qa"), result.skills.map { it.name })
        assertEquals(listOf("/goal-criteria"), result.workflows.map { it.name })
    }

    @Test
    fun commandInputLeavesRoomForArguments() {
        assertEquals("/plan ", MobileCommandCatalog.commands.first { it.name == "/plan" }.inputText())
        assertEquals("/skill qa ", skills.first().inputText())
    }

    @Test
    fun officialCatalogContainsCoreAndWorkflowCommands() {
        assertTrue(MobileCommandCatalog.commands.any { it.name == "/help" })
        assertTrue(MobileCommandCatalog.commands.any { it.name == "/starflow" })
        assertTrue(MobileCommandCatalog.commands.any { it.name == "/plan" && it.mobileExecutable })
    }

    @Test
    fun desktopOnlyCommandsAreSearchableButNotSelectable() {
        val result = MobileCommandCatalog.filter("starflow", emptyList())
        assertEquals(false, result.workflows.single { it.name == "/starflow" }.mobileExecutable)
        assertEquals(false, MobileCommandCatalog.commands.single { it.name == "/capsule" }.mobileExecutable)
    }

    @Test
    fun nativeRoutesAndSupportedReviewVariantsAreExecutable() {
        assertEquals(PaletteAction.MODELS, MobileCommandCatalog.commands.single { it.name == "/model" }.nativeAction)
        assertEquals(PaletteAction.COCKPIT, MobileCommandCatalog.commands.single { it.name == "/status" }.nativeAction)
        assertEquals(PaletteAction.SKILLS, MobileCommandCatalog.commands.single { it.name == "/skill list" }.nativeAction)
        assertTrue(MobileCommandCatalog.commands.single { it.name == "/team max" }.mobileExecutable)
        assertTrue(MobileCommandCatalog.commands.single { it.name == "/review l3" }.mobileExecutable)
    }

    @Test
    fun customCommandsAreSearchableAndDoNotDuplicateReservedCommands() {
        val custom = listOf(
            MobileCommand("/release", "项目发布", group = "项目命令", mobileExecutable = true, custom = true),
            MobileCommand("/model", "项目命令冲突", group = "项目命令", mobileExecutable = true, custom = true),
            MobileCommand("/starflow", "项目自定义编排", group = "项目命令", mobileExecutable = true, custom = true),
        )
        val result = MobileCommandCatalog.filter("", emptyList(), custom)
        assertTrue(result.workflows.single { it.name == "/release" }.custom)
        assertEquals(PaletteAction.MODELS, result.workflows.single { it.name == "/model" }.nativeAction)
        assertTrue(result.workflows.single { it.name == "/starflow" }.custom)
    }

    @Test
    fun slashSkillSyntaxAndMultipleWordsFindTheActualSkill() {
        assertEquals(listOf("qa"), MobileCommandCatalog.filter("/skill qa", skills).skills.map { it.name })
        assertEquals(listOf("qa"), MobileCommandCatalog.filter("QA 验收", skills).skills.map { it.name })
        assertEquals(listOf("qa"), MobileCommandCatalog.filter("／ＳＫＩＬＬ  QA", skills).skills.map { it.name })
    }

    @Test
    fun chinesePurposeAndExactCommandsHaveUsefulRanking() {
        assertEquals("/compact", MobileCommandCatalog.filter("整理", emptyList()).workflows.first().name)
        assertEquals("/handoff", MobileCommandCatalog.filter("交接", emptyList()).workflows.first().name)
        assertEquals("/team", MobileCommandCatalog.filter("/team", emptyList()).workflows.first().name)
        assertTrue(MobileCommandCatalog.filter("模型", emptyList()).workflows.any { it.name == "/effort" })
    }

    @Test
    fun browsingCollapsesPageAliasesButSearchPreservesExactSyntax() {
        val all = MobileCommandCatalog.filter("", emptyList()).workflows
        val browse = libraryCommands(all, searching = false)
        assertTrue(browse.any { it.name == "/cockpit" })
        assertEquals(false, browse.any { it.name == "/cache" })
        assertEquals("/cache", libraryCommands(MobileCommandCatalog.filter("/cache", emptyList()).workflows, true).single().name)
    }

    @Test
    fun recentsAreUniqueBoundedAndNewestFirst() {
        val recent = recentCommandSelection((1..10).map { "/command$it" }, "/command4")
        assertEquals(8, recent.size)
        assertEquals("/command4", recent.first())
        assertEquals(1, recent.count { it == "/command4" })
    }
}
