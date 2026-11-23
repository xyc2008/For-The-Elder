package com.fortheelder.ui.home

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortheelder.core.AppContainer
import com.fortheelder.ui.common.ActionRow
import com.fortheelder.ui.common.CommonVoice
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.common.EntryCard
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.common.SectionTitle
import com.fortheelder.ui.common.TargetStack
import com.fortheelder.ui.theme.ElderDimens
import kotlinx.coroutines.delay

/** 多久重新看一次时钟。一分钟足够跨过任何时段边界，代价又小到可以忽略。 */
private const val CLOCK_POLL_MILLIS = 60_000L

/**
 * 首页。
 *
 * 这一页刻意「薄」：一句话问候，加两条主干入口（说两句、玩一会儿），再加一条次要的设置。
 * 刚退休的人打开应用时最不需要的就是信息量，一屏里堆四五张卡片只会让人不知道该点哪一个。
 *
 * ## 版式为什么是不等重的
 *
 * 两条主干用 [EntryCard]（整块可点、有徽章、高度不低于
 * [ElderDimens.entryCardMinHeight]），设置走 [ActionRow] 那一行。此前三条入口是三个
 * 同构的行，权重完全一样，看上去分不出主次——而 Nielsen Norman Group 的
 * 《Usability for Older Adults》里反复出现的失败模式正是「目标又小又挤」，
 * 同一组织的《Touch Targets on Touchscreens》给的做法是「减少屏上元素，把关键目标做大」。
 *
 * 两条主干卡的底色靠徽章区分（绿配「说话」、暖配「玩」），但**区分不只靠颜色**：
 * 图标形状与文字都在说同一件事，所以色觉异常或纯灰度下同样分得清（WCAG 1.4.1）。
 *
 * 界面上只出现**已经实现**的能力。还没做好的部分不会在这里放灰色入口或「即将上线」，
 * 那类东西点不动，反而会让人觉得应用坏了。
 *
 * 顶上那句话不一定是问候：作息与节流状态可能判定「这会儿不该迎上来说话」，此时同一张卡片
 * 会显示一句安静语（见 `InterventionVoice`）。字号与位置不变——换的是内容，不是版式。
 *
 * 页面上写死的那些字在 [HomeVoice] 里（NFR-17，取舍见 ADR-017）。
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenTalk: () -> Unit,
    onOpenPlay: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel {
        HomeViewModel(
            settings = container.settings,
            greetingEngine = container.greetingEngine,
            clock = container.clock,
            interventions = container.interventionStore,
            profileService = container.rhythmProfileService,
            anchors = container.anchorRepository,
            config = container.interventionConfig,
        )
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(CLOCK_POLL_MILLIS)
            viewModel.refresh()
        }
    }

    // 离开前台又回来时重新判一次说话的时机（见 `HomeViewModel.onStart` 的说明）。
    // 观察者必须真的摘掉：这一页被导航栈换掉时组合会被销毁，留着观察者会漏一个 Lifecycle。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onStart()
                Lifecycle.Event.ON_STOP -> viewModel.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ElderScreen(title = CommonVoice.appTitle) {
        val ready = state as? HomeUiState.Ready
        if (ready != null) {
            SectionCard {
                Text(
                    text = ready.opening.headline,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = ready.opening.note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 用户自己记下的一件小事。没有这一行的时候版式不变，不多留一条空位。
                ready.opening.anchorLine?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (ready.needsAddress) {
                ActionRow(
                    text = HomeVoice.askAddress,
                    onClick = onOpenSettings,
                    trailing = { ChevronTrailing() },
                )
            }

            EntryCard(
                title = HomeVoice.talkAction,
                subtitle = HomeVoice.sectionTalk,
                icon = Icons.Filled.Face,
                iconContainer = MaterialTheme.colorScheme.primaryContainer,
                onIconContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onOpenTalk,
            )

            EntryCard(
                title = HomeVoice.playAction,
                subtitle = HomeVoice.sectionPlay,
                icon = Icons.Filled.PlayArrow,
                iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                onIconContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onOpenPlay,
            )

            // 第 63 轮：这一组是「小节标题 + 两条可点入口行」的相邻可点栈，标题与第一行之间、
            // 两行之间都按 ElderDimens.minTouchGap 排开。原来的 gapSm = 8dp 在本机是 21px，
            // 两行的可点框几乎相接（理由见 Dimens.kt 的「Mind the Gap」一节）。
            TargetStack {
                SectionTitle(text = HomeVoice.sectionSettings)
                // 「今天先安静一会儿」放在这一组里，而不是塞进顶上那张问候卡：卡里再套一个
                // 可点的框会让那张卡从「一句问候」变成一个小面板，而且正好是用户要求安静的时候
                // 在问候的位置多摆一个东西。什么时候说话本来就属于「您自己的节奏」（ADR-055）。
                ActionRow(
                    text = if (ready.quietToday) HomeVoice.resumeAction else HomeVoice.quietAction,
                    onClick = { if (ready.quietToday) viewModel.resumeSpeech() else viewModel.muteToday() },
                    trailing = { ChevronTrailing() },
                )
                ActionRow(
                    text = HomeVoice.settingsAction,
                    onClick = onOpenSettings,
                    trailing = { ChevronTrailing() },
                )
            }

            QuietNote(text = HomeVoice.closingNote)
        }
    }
}

/** 行尾的小箭头。抽出来只因为用到它的入口行不止一处，免得改一处漏一处。 */
@Composable
private fun ChevronTrailing() {
    // a11y-decorative: 这枚箭头只是「这一行可以点」的视觉提示，文字本身已经把意思说完了
    // （「说两句」「玩一会儿」）；再给它一个语义标签，朗读会多念一句没有信息量的话。
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(28.dp),
    )
}
