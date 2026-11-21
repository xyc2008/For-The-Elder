package com.fortheelder.ui.play

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortheelder.core.AppContainer
import com.fortheelder.domain.play.PlayGame
import com.fortheelder.domain.play.PlayRound
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.common.PrimaryWideButton
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.theme.ElderDimens

/**
 * 「玩一会儿」。
 *
 * 这一页只有三步：讲清规矩 → 摆一局 → 说完结果回退路。三步之间没有别的岔路，
 * 因为每多一个按钮，用户就要多做一次「这个是不是要我点」的判断。
 *
 * 有三件事这一页**刻意不做**，每一件都对应一条验收标准：
 * - 不放倒计时、不显示「还剩几个」（AC-11.3、AC-11.2）。屏幕上跳动的数字本身就是催促，
 *   而这一页想给人的只是「手上有件事做」。
 * - 结束卡上只有一个按钮，而且**不是**「再来一局」（AC-11.3）。想再玩一次就回首页重新进来——
 *   多出来的这一步是故意的，那一步之后人是自己决定要玩的。
 * - 不保存、不上传、不比较（AC-12.2、DR-09）。这一页没有 repository，
 *   所以「上次多少秒」在这一层根本写不出来。
 *
 * 布局上有一处是踩过坑之后定下来的：格子**不用正方形**，只用 `weight` + 最小高度。
 * 大字号下数字会比正方形高，正方形会把数字裁掉一截，而这正是
 * `ui/common/ElderComponents.kt` 里弃用 Material `TopAppBar` 的同一个原因。
 */
@Composable
fun PlayScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: PlayViewModel = viewModel { PlayViewModel(clock = container.clock) }
    val state by viewModel.state.collectAsState()

    ElderScreen(title = PlayVoice.title, onBack = onBack) {
        when (val current = state) {
            is PlayUiState.Ready -> ReadyPanel(game = current.game, onStart = viewModel::start)
            is PlayUiState.Playing -> PlayingPanel(state = current, onTap = viewModel::tap)
            is PlayUiState.Finished -> FinishedPanel(state = current, onLeave = onBack)
        }
    }
}

/** 开始之前：这个游戏叫什么、怎么玩、一局有多久。 */
@Composable
private fun ReadyPanel(game: PlayGame, onStart: () -> Unit) {
    SectionCard(title = game.name) {
        Text(
            text = PlayVoice.rule(game),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        QuietNote(text = PlayVoice.promise(game))
    }
    PrimaryWideButton(text = PlayVoice.startLabel, onClick = onStart)
}

/** 玩的时候：上面一行说现在点哪个，下面就是格子。 */
@Composable
private fun PlayingPanel(state: PlayUiState.Playing, onTap: (Int) -> Unit) {
    SectionCard {
        Text(
            text = PlayVoice.nextTarget(state.round),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        // 这一行**始终占着位置**：点错时它才有字，占位是为了让整张卡片不跟着跳一下，
        // 而用户的眼睛正盯着下面那一片格子。
        Text(
            text = PlayVoice.hint(state.round).orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    PlayGrid(game = state.game, round = state.round, onTap = onTap)
}

/** 点完之后：一句结果，一条退路。 */
@Composable
private fun FinishedPanel(state: PlayUiState.Finished, onLeave: () -> Unit) {
    SectionCard {
        Text(
            text = PlayVoice.doneLine(state.round, state.seconds),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        QuietNote(text = PlayVoice.doneNote)
    }
    PrimaryWideButton(text = PlayVoice.leaveLabel, onClick = onLeave)
}

/**
 * 格子。
 *
 * 一行摆几个由游戏自己说（[PlayGame.columns]），这一层不假定它是 5——
 * 将来的小游戏里格子数不一样，改的应该是那条目录项，不是这一页。
 *
 * 用 `chunked` 切行：最后一行不够一整行时，那几个格子在 `weight` 下会各自变宽一点。
 * 这一页的格子数永远整除，所以看不到这个效果；但切成 `rows` 之后，
 * 格子数与行宽之间就没有隐含的整除前提了。
 */
@Composable
private fun PlayGrid(game: PlayGame, round: PlayRound, onTap: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapXs)) {
        round.cells.chunked(game.columns).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ElderDimens.gapXs),
            ) {
                row.forEachIndexed { columnIndex, label ->
                    PlayCell(
                        label = label,
                        passed = round.passed(label),
                        onClick = { onTap(rowIndex * game.columns + columnIndex) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 一个格子。
 *
 * 高度用 `heightIn(min = 56dp)` 而不是固定值、也不是 `aspectRatio(1f)`：
 * 大字号下数字会比格子还高，正方形或固定高度都会把它裁掉半边。宁可变成竖长方形，
 * 也要让数字完整——这一页上数字就是全部信息。
 *
 * 点过的格子压暗但**照样能点**：点它就是 AC-12.3 要接住的那种误触。
 * 把它的点击关掉看着更清爽，代价是用户点了一下毫无反应——那比一句温和的提示糟得多。
 */
@Composable
private fun PlayCell(
    label: String,
    passed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (passed) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ElderDimens.minTouchTarget)
                .padding(horizontal = ElderDimens.gapXs, vertical = ElderDimens.gapSm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = if (passed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
