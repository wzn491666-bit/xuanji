package com.xuanji.android.ui.chat

import kotlin.random.Random

internal enum class WelcomeComposition { COUPLET, OFFSET, QUIET }
internal enum class WelcomeTypeface { WENKAI, XIAOWEI, MASHAN, NOTOSERIF, HUANGYOU, LONGCANG, ZHIMANG, LIUJIAN }

internal data class StellarWelcomeScene(
    val id: String,
    val domain: String,
    val firstLine: String,
    val secondLine: String,
    val accent: Long,
    val composition: WelcomeComposition,
    val typeface: WelcomeTypeface,
)

// Literary scenes only. Runtime domains are controlled independently by Tianshu.
internal val stellarWelcomeScenes = listOf(
    StellarWelcomeScene("qiming", "启明", "长夜有尽", "微光先行", 0xFF3465B0, WelcomeComposition.COUPLET, WelcomeTypeface.WENKAI),
    StellarWelcomeScene("tianquan", "天权", "万象有衡", "一念见真", 0xFF956928, WelcomeComposition.QUIET, WelcomeTypeface.NOTOSERIF),
    StellarWelcomeScene("tianji", "天机", "问向深处", "路自开阔", 0xFF257B82, WelcomeComposition.OFFSET, WelcomeTypeface.HUANGYOU),
    StellarWelcomeScene("tianshu", "天枢", "心有北辰", "万象成序", 0xFF3465B0, WelcomeComposition.COUPLET, WelcomeTypeface.XIAOWEI),
    StellarWelcomeScene("tianliang", "天梁", "念有所向", "行皆有回音", 0xFFB25446, WelcomeComposition.OFFSET, WelcomeTypeface.WENKAI),
    StellarWelcomeScene("huagai", "华盖", "长路未央", "星光不负", 0xFF54784C, WelcomeComposition.QUIET, WelcomeTypeface.NOTOSERIF),
    StellarWelcomeScene("yaoguang", "瑶光", "循光寻迹", "让真相重现", 0xFF257B82, WelcomeComposition.COUPLET, WelcomeTypeface.WENKAI),
    StellarWelcomeScene("kaiyang", "开阳", "一寸有据", "万象可量", 0xFF956928, WelcomeComposition.OFFSET, WelcomeTypeface.XIAOWEI),
    StellarWelcomeScene("changgeng", "长庚", "暮色渐深", "星火未眠", 0xFF9B536C, WelcomeComposition.QUIET, WelcomeTypeface.LONGCANG),
    StellarWelcomeScene("pojun", "破军", "行至未至", "见所未见", 0xFFB25446, WelcomeComposition.OFFSET, WelcomeTypeface.MASHAN),
    StellarWelcomeScene("tianxuan", "天璇", "换一片星空", "看万象相逢", 0xFF3465B0, WelcomeComposition.COUPLET, WelcomeTypeface.ZHIMANG),
    StellarWelcomeScene("tianfu", "天府", "守住来路", "托起远方", 0xFF54784C, WelcomeComposition.QUIET, WelcomeTypeface.NOTOSERIF),
    StellarWelcomeScene("qisha", "七杀", "留一处空白", "待万物新生", 0xFF9B536C, WelcomeComposition.OFFSET, WelcomeTypeface.LIUJIAN),
    StellarWelcomeScene("wenqu", "文曲", "以简为形", "以意为光", 0xFF257B82, WelcomeComposition.COUPLET, WelcomeTypeface.ZHIMANG),
    StellarWelcomeScene("fu", "辅", "将散落微光", "凝成下一程", 0xFF956928, WelcomeComposition.OFFSET, WelcomeTypeface.HUANGYOU),
    StellarWelcomeScene("taiyi", "太一", "万象归一", "静处生明", 0xFF54784C, WelcomeComposition.QUIET, WelcomeTypeface.LIUJIAN),
)

internal fun nextStellarWelcome(previousId: String?, random: Random = Random.Default): StellarWelcomeScene {
    val candidates = stellarWelcomeScenes.filterNot { it.id == previousId }
    return candidates[random.nextInt(candidates.size)]
}

internal fun storedStellarWelcome(id: String?, fallback: StellarWelcomeScene): StellarWelcomeScene =
    stellarWelcomeScenes.firstOrNull { it.id == id } ?: fallback
