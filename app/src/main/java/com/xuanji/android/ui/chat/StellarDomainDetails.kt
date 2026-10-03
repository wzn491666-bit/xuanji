package com.xuanji.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

internal data class StellarDomainPortrait(val role: String, val discipline: String)

// Official concepts page, checked on 2026-10-02. These portraits do not switch the runtime domain.
internal val stellarDomainPortraits = mapOf(
    "qiming" to StellarDomainPortrait("晨光向导", "先探后断：面对陌生或模糊的问题，先用轻量只读探针转一圈，关键结论用两种独立来源交叉验证；给的是全景和分步路径，不抢你的方向盘。"),
    "tianshu" to StellarDomainPortrait("全局统筹官", "全局统筹：接任务先铺开整张依赖图——从入口到改动点经过哪些模块、谁依赖谁、每个单元怎么验证，再拆成可独立验证的小块推进。多模块联动时这种先看图再走路的习惯最出效果。"),
    "tianquan" to StellarDomainPortrait("方案审查官", "称量习惯：不急着站队，先把方案声称和代码现实对一遍；收益、代价、影响面一起称，凭证据下判断，最后落成带文件行号、意见分级的可执行计划。"),
    "tianliang" to StellarDomainPortrait("交付执行官", "交付纪律：动手前先核对计划里的文件、符号、行号还准不准；任务多就分波做，每波跑真验证再收口，最后说清做了什么、遗留什么、偏了哪里。"),
    "yaoguang" to StellarDomainPortrait("复现验证官", "复现审验：绿灯不算数，先把原缺陷真实复现出来，再让修复把它变绿；还会回到时间轴看这族问题是不是老毛病，安静不响的机制也要审。"),
    "tianji" to StellarDomainPortrait("前提质疑官", "前提质疑：方案看起来越顺越会追问「如果前提不成立呢」，主动列隐含假设、找更短路径、查没人讨论的分支，让方案上线前先被自己打磨一遍。"),
    "pojun" to StellarDomainPortrait("探索先锋", "探索冲动：遇到不确定先跑最短、能回退的实验去探边界，失败当作收窄了未知范围；探完把路和教训整理成地图，让后来者接得上。"),
    "tianxuan" to StellarDomainPortrait("跨域寻迹者", "换视角：一条路走不通时先退后一步，去完全无关的领域找同构思路；每冒出灵感立刻派反证，修的是投下阴影的根子，不是水面上的波纹。"),
    "tianfu" to StellarDomainPortrait("结构守护者", "守护意识：动手前先弄清每个接口是对谁的承诺、被谁依赖，改动尽量小而强化结构；确实要破坏就带迁移方案，不搞静默 breaking。"),
    "kaiyang" to StellarDomainPortrait("对账师", "测量对账：对「它到底在干什么」零信任——先自己推一个期望值，再插桩量真实值，两边对不上就是根因现场；自己说的每句「全绿」也要拿命令输出对账。"),
    "huagai" to StellarDomainPortrait("守昼者", "长程守信：马拉松式任务里不把「大部分绿了」当完成；先立测量基线，追着审查 FAIL 一路清，最后把测试和判断文档留下，让接手的人不用从头猜。"),
    "changgeng" to StellarDomainPortrait("守夜人", "终局收束：长任务快收工时，界面和视觉要亲眼看过——多主题、多宽度都截一遍；把判断依据、否决过的假设、没走完的岔路留成交接材料。"),
    "qisha" to StellarDomainPortrait("肃秋剪枝官", "减法审计：注意力被太多「可能有用」的东西占着时，挨个问：它最近真的触发过吗、触发后行为变了吗？答不上来就提名，但只列名单不替你砍。"),
    "wenqu" to StellarDomainPortrait("代码美学者", "美学敏感：功能一样不少，只是对命名、结构和界面质感更较真；用最少噪声让意图自明，改完界面会真截图亲眼看一眼渲染结果。"),
    "fu" to StellarDomainPortrait("认知调校师", "认知蒸馏：不写业务，调的是认知场——模型行为不对时先分清是提示词问题还是能力问题，再把散落经验提炼成动作、判据、反例，放进正确的提示位置。"),
    "taiyi" to StellarDomainPortrait("极简中心", "中虚静气：慢不是停——动手前先停一下把问题看完整，不被催促带节奏；收束时把路标留下、把路上的尘放下，适合想要安静专注节奏的任务。"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StellarDomainDetails(scene: StellarWelcomeScene, onDismiss: () -> Unit) {
    val portrait = stellarDomainPortraits.getValue(scene.id)
    val scheme = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(scene.domain, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(portrait.role, style = MaterialTheme.typography.bodyMedium, color = scheme.secondary)
                }
                ChatIconButton("关闭星域详情", Icons.Default.Close, onDismiss)
            }
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(scene.id, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = scheme.onSurfaceVariant)
                Text(portrait.discipline, style = MaterialTheme.typography.bodyLarge)
                HorizontalDivider(color = scheme.outlineVariant)
                Text("星域是一种认知姿态，影响系统提示词、工具白名单与决策阈值。", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Text("当前星笺仅为欢迎主题，查看详情不会切换会话星域。", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            TextButton(
                onClick = { uri.openUri("https://tianshuharness.com/docs/concepts#star-domains") },
                modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("天枢官方星域说明")
            }
        }
    }
}
