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
import com.fortheelder.domain.play.CellMark
import com.fortheelder.domain.play.PlayGame
import com.fortheelder.domain.play.PlayRound
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.common.PrimaryWideButton
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SecondaryWideButton
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.common.announcesWhenItChanges
import com.fortheelder.ui.common.gridCell
import com.fortheelder.ui.common.gridOf
import com.fortheelder.ui.theme.ElderDimens

/**
 * 「玩一会儿」。
 *
 * 这一页只有三步：讲清规矩 → 摆一局 → 说完结果回退路。三步之间只有一处岔路——规则页上
 * 「换一个游戏」往回走（第 23 轮加），其余地方每多一个按钮，用户就要多做一次
 * 「这个是不是要我点」的判断。目录里有两个以上游戏时，前面多一步「挑一个」，
 * 但每张卡上只有游戏的名字，规则留到进去之后说——选择页不是第二个说明书。
 *
 * 有三件事这一页**刻意不做**，每一件都对应一条验收标准：
 * - 不放倒计时、不显示「还剩几个」（AC-11.3、AC-11.2）。屏幕上跳动的数字本身就是催促，
 *   而这一页想给人的只是「手上有件事做」。
 * - 结束卡上只有一个按钮，而且**不是**「再来一局」（AC-11.3）。想再玩一次就回首页重新进来——
 *   多出来的这一步是故意的，那一步之后人是自己决定要玩的。
 * - 不保存、不上传、不比较（AC-12.2、DR-09）。这一页没有 repository，
 *   所以「上次多少秒」在这一层根本写不出来。
 *
 * 这一页也**不认识任何游戏的规则**：一格该画成什么样由 [PlayRound.markOf] 说了算，
 * 上面那句话说哪一句由 [PlayRound] 的类型分派。第 12 轮加第二个游戏时，摆格子的布局
 * 一行没动，动的只有一处判据：`PlayCell` 的入参从 `passed: Boolean` 换成 `mark: CellMark`。
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
            is PlayUiState.Choose -> ChoosePanel(games = current.games, onChoose = viewModel::choose)
            is PlayUiState.Ready -> ReadyPanel(
                game = current.game,
                canChooseAgain = current.canChooseAgain,
                onStart = viewModel::start,
                onChooseAgain = viewModel::chooseAgain,
            )
            is PlayUiState.Playing -> PlayingPanel(state = current, onTap = viewModel::tap)
            is PlayUiState.Finished -> FinishedPanel(state = current, onLeave = onBack)
        }
    }
}

/**
 * 挑一个游戏。
 *
 * 每个游戏做成**整张可点的大卡片**，上面只有名字。名字而不是说明：卡片上写满规则，
 * 用户就得在这里读两遍（进去还要再读一遍），而这一页要问的只是一句「现在想玩哪个」。
 */
@Composable
private fun ChoosePanel(games: List<PlayGame>, onChoose: (PlayGame) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapMd)) {
        Text(
            text = PlayVoice.chooseTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        QuietNote(text = PlayVoice.chooseNote)
        for (game in games) {
            GameCard(game = game, onClick = { onChoose(game) })
        }
    }
}

/** 选择页上的一张卡片。高度跟着文字长，大字号下不会把游戏名切掉。 */
@Composable
private fun GameCard(game: PlayGame, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ElderDimens.buttonHeight)
                .padding(horizontal = ElderDimens.cardPadding, vertical = ElderDimens.gapMd),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = game.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 开始之前：这个游戏叫什么、怎么玩、一局有多久，以及「不想玩这个」的退路。
 *
 * [canChooseAgain] 为真时，下面多一条 [PlayVoice.switchLabel]（第 23 轮加）。这条路**只开在这一页**，
 * 有两处地方刻意不开：
 * - 玩到一半不摆它。这会儿屏幕上每一格都在等人点，多一个按钮就是多一次「这个是不是要我点」；
 *   而在玩的一局的退路，顶上的返回键已经是了。
 * - 结束卡上不摆它（AC-11.3）。那一屏只允许有一个按钮，而它必须是**离开**这一页的那个。
 *   想再挑一个就回首页重新进来——多出来的这一步是故意的。
 *
 * 这一页可以往回走，是因为这一刻这一局**还没摆出来**（见 [PlayViewModel.chooseAgain]）：
 * 退回去不丢任何东西，也没人在等一个结果。
 */
@Composable
private fun ReadyPanel(
    game: PlayGame,
    canChooseAgain: Boolean,
    onStart: () -> Unit,
    onChooseAgain: () -> Unit,
) {
    SectionCard(title = game.name) {
        Text(
            text = PlayVoice.rule(game),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        QuietNote(text = PlayVoice.promise(game))
    }
    PrimaryWideButton(text = PlayVoice.startLabel, onClick = onStart)
    if (canChooseAgain) {
        SecondaryWideButton(text = PlayVoice.switchLabel, onClick = onChooseAgain)
    }
}

/** 玩的时候：上面一行说要做什么，下面就是格子。 */
@Composable
private fun PlayingPanel(state: PlayUiState.Playing, onTap: (Int) -> Unit) {
    SectionCard {
        Text(
            text = PlayVoice.headline(state.round),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.announcesWhenItChanges(),
        )
        // 这一行**始终占着位置**：点错时它才有字，占位是为了让整张卡片不跟着跳一下，
        // 而用户的眼睛正盯着下面那一片格子。
        //
        // 占着位置这一点同时让它成为一个良定义的播报区（第 18 轮）：节点一直都在，
        // 变的只是字，所以「读这行」这件事有明确的触发时刻。同一套理由用在下面的结束语上。
        Text(
            text = PlayVoice.hint(state.round).orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.announcesWhenItChanges(),
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
            modifier = Modifier.announcesWhenItChanges(),
        )
        QuietNote(text = PlayVoice.doneNote(state.round))
    }
    PrimaryWideButton(text = PlayVoice.leaveLabel, onClick = onLeave)
}

/**
 * 格子。
 *
 * 一行摆几个由游戏自己说（[PlayGame.columns]），这一层不假定它是 5——
 * 第二个游戏是 4×4，这一段的布局一行都没动，改的只有每格画成什么样的那条判据。
 *
 * 用 `chunked` 切行：最后一行不够一整行时，那几个格子在 `weight` 下会各自变宽一点。
 * 这一页的格子数永远整除，所以看不到这个效果；但切成 `rows` 之后，
 * 格子数与行宽之间就没有隐含的整除前提了。
 *
 * 每格画成什么样问 [PlayRound.markOf]（按**位置**问，不按字问）：找一样的盘面上
 * 有两个字相同，按字查会把两张一起改掉，第一张刚点下去就看着像成了。
 */
@Composable
private fun PlayGrid(game: PlayGame, round: PlayRound, onTap: (Int) -> Unit) {
    val rows = (round.cells.size + game.columns - 1) / game.columns
    Column(
        modifier = Modifier.gridOf(rows = rows, columns = game.columns),
        verticalArrangement = Arrangement.spacedBy(ElderDimens.gapXs),
    ) {
        round.cells.chunked(game.columns).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ElderDimens.gapXs),
            ) {
                row.forEachIndexed { columnIndex, label ->
                    val index = rowIndex * game.columns + columnIndex
                    PlayCell(
                        label = label,
                        mark = round.markOf(index),
                        row = rowIndex,
                        column = columnIndex,
                        onClick = { onTap(index) },
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
 * 三种样子对应三种颜色，可点性**三种都一样**：点它就是在接住 AC-12.3 要的那种误触。
 * 把已经了了的格子的点击关掉看着更清爽，代价是用户点了一下毫无反应——
 * 那比一句温和的提示糟得多。
 *
 * 每格还要报出自己在第几行第几列（第 18 轮）：朗读时格子是连成一串念的，
 * 没有行列信息，老人听完一串数字不知道自己听到的是哪一格。
 */
@Composable
private fun PlayCell(
    label: String,
    mark: CellMark,
    row: Int,
    column: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when (mark) {
        CellMark.PLAIN -> MaterialTheme.colorScheme.surface
        CellMark.PICKED -> MaterialTheme.colorScheme.secondaryContainer
        CellMark.DONE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (mark) {
        CellMark.PLAIN -> MaterialTheme.colorScheme.onSurface
        CellMark.PICKED -> MaterialTheme.colorScheme.onSecondaryContainer
        CellMark.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier.gridCell(row = row, column = column),
        shape = MaterialTheme.shapes.medium,
        color = container,
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
                color = content,
            )
        }
    }
}
