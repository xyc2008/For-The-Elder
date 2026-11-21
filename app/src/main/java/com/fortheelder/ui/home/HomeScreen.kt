package com.fortheelder.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.common.SectionTitle
import com.fortheelder.ui.theme.ElderDimens
import kotlinx.coroutines.delay

/** 多久重新看一次时钟。一分钟足够跨过任何时段边界，代价又小到可以忽略。 */
private const val CLOCK_POLL_MILLIS = 60_000L

/**
 * 首页。
 *
 * 这一页刻意「薄」：一句话问候 + 三个入口（说两句、玩一会儿、设置）。刚退休的人打开应用时最
 * 不需要的就是信息量，一屏里堆四五个卡片只会让人不知道该点哪一个。
 *
 * 界面上只出现**已经实现**的能力。还没做好的部分不会在这里放灰色入口或「即将上线」，
 * 那类东西点不动，反而会让人觉得应用坏了。
 *
 * 顶上那句话不一定是问候：作息与节流状态可能判定「这会儿不该迎上来说话」，此时同一张卡片
 * 会显示一句安静语（见 `InterventionVoice`）。字号与位置不变——换的是内容，不是版式。
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

    ElderScreen(title = "常伴") {
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
                    text = "告诉我您希望被怎么称呼",
                    onClick = onOpenSettings,
                    trailing = { ChevronTrailing() },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm)) {
                SectionTitle(text = "想说话的时候")
                ActionRow(
                    text = "跟常伴说两句",
                    onClick = onOpenTalk,
                    trailing = { ChevronTrailing() },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm)) {
                SectionTitle(text = "手上想有点事的时候")
                ActionRow(
                    text = "玩一会儿",
                    onClick = onOpenPlay,
                    trailing = { ChevronTrailing() },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm)) {
                SectionTitle(text = "您自己的节奏")
                ActionRow(
                    text = "设置",
                    onClick = onOpenSettings,
                    trailing = { ChevronTrailing() },
                )
            }

            QuietNote(
                text = "今天没有要做的事，也没有要签到的项目。想到就打开看看，想不起来也不会怎样。"
            )
        }
    }
}

/** 行尾的小箭头。抽出来只因为三个入口用的是同一个，免得改一处漏一处。 */
@Composable
private fun ChevronTrailing() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(28.dp),
    )
}
