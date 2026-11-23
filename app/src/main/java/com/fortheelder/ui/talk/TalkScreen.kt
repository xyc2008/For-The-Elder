package com.fortheelder.ui.talk

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortheelder.core.AppContainer
import com.fortheelder.domain.engine.intent.IntentHit
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.domain.wish.StoredWish
import com.fortheelder.domain.wish.WishDecision
import com.fortheelder.domain.wish.WishStatus
import com.fortheelder.ui.common.ActionRow
import com.fortheelder.ui.common.BulletLine
import com.fortheelder.ui.common.CommonVoice
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.common.PrimaryWideButton
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SecondaryWideButton
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.common.SectionTitle
import com.fortheelder.ui.common.TargetStack
import com.fortheelder.ui.theme.ElderDimens
import kotlinx.coroutines.launch

/**
 * 「说两句」。
 *
 * 这一页做两件事：把用户说的写下来（[TalkViewModel.hear] 负责听，[TalkVoice] 负责措辞），
 * 再让用户**明确表态**这件事要不要被记住（FR-07 的确认闸门）。
 *
 * 页面上**不替用户决定任何事**。听出一句「想学书法」之后，常伴能做的只有一件事：
 * 把「我听懂了什么、从哪儿听出来的」摆出来，然后问一句要不要记。用户点同意之前，
 * 这件事不产生任何后续动作（AC-07.1）；用户说了不用，常伴就不再拿它来提（AC-07.3）。
 *
 * 闸门按钮**一屏只有一处**：刚说的那件事在上面「我听见的」里定，
 * 以前说过、还没拿主意的列在「常伴记住的事 → 还没问过您的」里，
 * 同一件事不会出现两组一模一样的按钮。
 */
@Composable
fun TalkScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: TalkViewModel = viewModel {
        TalkViewModel(
            wishes = container.wishRepository,
            interventions = container.interventionStore,
            anchors = container.anchorRepository,
        )
    }
    val page by viewModel.page.collectAsState()

    // 第 62 轮：这一页要自己控制滚动位置，才能在用户点完一句话之后把回应卡带进视口。
    // 状态由这里持有、交给 [ElderScreen] 使用，滚动区仍是它内部那一个。
    val scroll = rememberScrollState()

    ElderScreen(title = TalkVoice.pageTitle, onBack = onBack, scrollState = scroll) {
        SectionCard {
            Text(
                text = TalkVoice.introLine,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            QuietNote(text = TalkVoice.introNote)
        }

        SectionCard(title = TalkVoice.saySection) {
            OutlinedTextField(
                value = page.draft,
                onValueChange = viewModel::editDraft,
                label = { Text(TalkVoice.draftLabel) },
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryWideButton(
                text = TalkVoice.submit,
                onClick = viewModel::submitDraft,
                enabled = page.draft.isNotBlank(),
            )
            QuietNote(text = TalkVoice.submitNote)

            // 第 61 轮：五句现成的话从页底搬到了输入框下面。
            //
            // 搬之前它是整页最后一张卡——页面上还有「我听见的」「常伴记住的事」「起点卡」
            // 「锚点卡」，任何一条记下来的事都能把它顶到好几屏之外，用户要找到它得先滑过
            // 整页。它要解决的恰恰是「一时想不出说什么」，而这时候人最容易做的事是放弃，
            // 不是往下滑。现在它与输入框同处一张卡：想不出就走这几句，想得出就在上面打字。
            //
            // 第 63 轮起，五句现成的话是一整列**都可点**的相邻目标，间距按 ElderDimens.minTouchGap
            // 排开（外面包一层 TargetStack）。上一轮在设备上量到它们的可点框高 168 px、相邻两句
            // 只有 21 px（21 px = 卡片内层的 8dp × 420dpi ÷ 160），点错一句等于答错了用户
            // 根本没想说的事。小节标题与第一句之间仍是卡片内层的 8dp：标题不可点，不要间隙。
            SectionTitle(text = TalkVoice.examplesSection)
            TargetStack {
                for (sentence in TalkExamples.sentences) {
                    ActionRow(text = sentence, onClick = { viewModel.hear(sentence) })
                }
            }
        }

        when (val current = page.heard) {
            TalkUiState.Idle -> Unit
            is TalkUiState.Heard -> key(current) {
                BringResponseIntoView(scroll) { HeardCard(current, page, viewModel::decide) }
            }
            is TalkUiState.NotUnderstood -> key(current) {
                BringResponseIntoView(scroll) { NotUnderstoodCard(current) }
            }
        }

        RememberedCard(
            page = page,
            onDecide = viewModel::decide,
            onForget = viewModel::forget,
            onForgetAll = viewModel::forgetAll,
        )

        // 起点卡在锚点卡上面，两者挨着：锚点卡空着时那句说明说的是「上面某一步」，
        // 中间插进别的卡就把这句话指偏了。
        StarterCard(
            page = page,
            onToggleOption = viewModel::toggleOption,
            onMarkDone = viewModel::markDone,
            onProposeAnchor = viewModel::proposeAnchor,
            onCancelAnchor = viewModel::cancelAnchorProposal,
            onConfirmAnchor = viewModel::confirmAnchor,
        )

        AnchorCard(
            page = page,
            onForgetAnchor = viewModel::forgetAnchor,
        )
    }
}

/**
 * 把刚出现的那张回应卡带进视口。
 *
 * 第 62 轮加的。第 61 轮的设备探针读数：点完一句之后，「我听见的」的顶边落在视口内
 * y≈2822 的位置，而首屏滚动区下沿是 2337——**低 485 px**。也就是说回应卡确实生成了，
 * 但用户在这一屏里一个像素都看不到，得自己往下滑才知道常伴回应了。这一层只做一件事：
 * 卡第一次落位时量一次，不在视口里就滚过去。
 *
 * 为什么是「两端 root 坐标之差**再减去当前滚动量**」：卡片的父级不是固定不动的视口，
 * 而是**跟着滚动一起走的内容列**。第 62 轮的设备诊断读数证明了这一点：滚动 0 与滚动 146
 * 两帧里，`coords.positionInRoot().y - parent.positionInRoot().y` 都是 2410，
 * 而父级顶边自己在同一段时间里上移了正好 146。也就是说这个差是「这张卡在内容里的偏移」，
 * 与滚到哪儿无关。而 [revealScrollTarget] 内部已经加过一次 `currentScroll`，再把这个差
 * 当成「卡顶在视口里的位置」交出去，就等于把滚动量算了两遍：停在顶端时（`scroll.value == 0`）
 * 恰好正确，**用户先自己滑下去再点一句时会多滚一个滚动量**，卡顶被推到视口上沿之上——
 * 第 62 轮设备探针第二段抓到的正是这一屏（回应卡只剩一行字被上沿切着，标题和回声都看不见）。
 * 减去 `scroll.value` 之后，交出去的才是 [revealScrollTarget] 文档里说的那个量。
 * 不用 `positionInParent()`：它的坐标原点语义（内容原点 vs 布局原点）在 scroll > 0 时
 * 会跟着实现细节变，而上面这条实测的 `positionInRoot` 差值是最直接的读数。
 *
 * [revealed] 保证**一次回应只动一次画面**：带进去之后用户再怎么滑，这一层都不会把它拉回来。
 * 状态随 [key] 换回应而重建，下一句回应是新的一层。
 */
@Composable
private fun BringResponseIntoView(scroll: ScrollState, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    var revealed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.onGloballyPositioned { coords ->
            if (revealed) return@onGloballyPositioned
            val parent = coords.parentLayoutCoordinates ?: return@onGloballyPositioned
            val viewportHeight = scroll.viewportSize
            if (viewportHeight <= 0) return@onGloballyPositioned
            val cardTopInViewport = coords.positionInRoot().y - parent.positionInRoot().y - scroll.value
            val target = revealScrollTarget(
                cardTopInViewport = cardTopInViewport,
                cardHeight = coords.size.height,
                viewportHeight = viewportHeight,
                currentScroll = scroll.value,
                maxScroll = scroll.maxValue,
            )
            revealed = true
            if (target != scroll.value) scope.launch { scroll.animateScrollTo(target) }
        },
    ) {
        content()
    }
}

/** 听出来了：把听懂的内容、判断依据、以及要不要记的问法一起摆出来。 */
@Composable
private fun HeardCard(
    state: TalkUiState.Heard,
    page: TalkPageState,
    onDecide: (IntentKind, String, WishDecision) -> Unit,
) {
    SectionCard(title = TalkVoice.heardSection) {
        EchoLine(state.sentence)
        for (hit in state.hits) {
            WishBlock(
                hit = hit,
                wish = page.wishOf(hit.kind, hit.term),
                onDecide = onDecide,
            )
        }
    }
}

/** 没听出来：明确说出来，并给一条不费力的退路。 */
@Composable
private fun NotUnderstoodCard(state: TalkUiState.NotUnderstood) {
    SectionCard(title = TalkVoice.notUnderstoodSection) {
        EchoLine(state.sentence)
        Text(
            text = TalkVoice.notUnderstoodLine,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        QuietNote(text = TalkVoice.notUnderstoodNote)
    }
}

/**
 * 一件事的闸门区块。
 *
 * [wish] 为 `null` 表示这件事还没落库（用户刚说完、写入还在路上），
 * 这时按 [WishStatus.PENDING] 显示：按钮照常在，按下去也必须有用——
 * 落库这一侧的保证在 [com.fortheelder.data.wish.WishRepository] 的串行化里。
 */
@Composable
private fun WishBlock(
    hit: IntentHit,
    wish: StoredWish?,
    onDecide: (IntentKind, String, WishDecision) -> Unit,
) {
    val status = wish?.status ?: WishStatus.PENDING

    Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm)) {
        Text(
            text = "${TalkVoice.kindLabel(hit.kind)}：${hit.term}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        BulletLine(text = TalkVoice.evidenceLine(hit.evidence))

        when (status) {
            WishStatus.PENDING -> {
                Text(
                    text = TalkVoice.prompt(hit.kind),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 第 66 轮：这一对是全页后果最重的两个按钮（确认就留、拒绝就不留），
                // 从前它们之间只有卡片内层的 gapSm = 8dp（21px）。上面那句问法与下面那句
                // 说明都是不可点的正文，仍留在 gapSm 上；只有按钮这一段换成 TargetStack。
                TargetStack {
                    PrimaryWideButton(
                        text = TalkVoice.confirmLabel(hit.kind),
                        onClick = { onDecide(hit.kind, hit.term, WishDecision.CONFIRM) },
                    )
                    SecondaryWideButton(
                        text = TalkVoice.declineLabel(hit.kind),
                        onClick = { onDecide(hit.kind, hit.term, WishDecision.DECLINE) },
                    )
                }
                QuietNote(text = TalkVoice.gateNote)
            }

            WishStatus.CONFIRMED -> {
                Text(
                    text = TalkVoice.decidedLine(hit.kind, WishDecision.CONFIRM),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (wish != null) {
                    for (line in TalkVoice.offerLines(wish)) {
                        BulletLine(text = line)
                    }
                }
            }

            WishStatus.DECLINED -> {
                Text(
                    text = TalkVoice.decidedLine(hit.kind, WishDecision.DECLINE),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                QuietNote(text = TalkVoice.reopenNote)
                SecondaryWideButton(
                    text = TalkVoice.reopenLabel(hit.kind),
                    onClick = { onDecide(hit.kind, hit.term, WishDecision.CONFIRM) },
                )
            }
        }
    }
}

/**
 * 「常伴记住的事」。
 *
 * 三组东西放在一张卡里，因为它们回答的是同一个问题的不同侧面——「你到底记了我什么」：
 * 还没拿主意的、已经定下来的、以及「一件都不留」的出口。
 *
 * [page] 里已经定下来的那部分不带闸门按钮（都定过了），还没拿主意的带按钮，
 * 但**刚说完、正显示在上面「我听见的」里的那几件不重复列在这里**——
 * 同一屏上两组一模一样的按钮，对老人来说是「我该点哪个」而不是「多一个入口」。
 */
@Composable
private fun RememberedCard(
    page: TalkPageState,
    onDecide: (IntentKind, String, WishDecision) -> Unit,
    onForget: (IntentKind, String) -> Unit,
    onForgetAll: () -> Unit,
) {
    var confirmingClear by remember { mutableStateOf(false) }
    val heardKeys: Set<Pair<IntentKind, String>> =
        (page.heard as? TalkUiState.Heard)?.hits?.map { it.kind to it.term }?.toSet().orEmpty()
    val pendingHere = page.toConfirm.filterNot { (it.kind to it.term) in heardKeys }

    SectionCard(title = TalkVoice.rememberedSection) {
        if (page.remembered.isEmpty()) {
            Text(
                text = TalkVoice.rememberedEmpty,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            QuietNote(text = TalkVoice.rememberedEmptyNote)
        } else {
            QuietNote(text = TalkVoice.rememberedNote)

            for (wish in page.settled) {
                SettledBlock(wish = wish, onDecide = onDecide, onForget = onForget)
            }

            if (pendingHere.isNotEmpty()) {
                SectionTitle(text = TalkVoice.pendingSection)
                QuietNote(text = TalkVoice.pendingNote)
                for (wish in pendingHere) {
                    PendingBlock(wish = wish, onDecide = onDecide, onForget = onForget)
                }
            }

            SecondaryWideButton(text = TalkVoice.clearAll, onClick = { confirmingClear = true })
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(CommonVoice.confirmClearTitle) },
            text = { Text(TalkVoice.clearAllConfirmText) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        onForgetAll()
                    }
                ) { Text(CommonVoice.confirmClear) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text(CommonVoice.dismissClear) }
            },
        )
    }
}

/** 已经定下来的一条：说清状态，留着删除，拒绝过的还给一个改主意的入口。 */
@Composable
private fun SettledBlock(
    wish: StoredWish,
    onDecide: (IntentKind, String, WishDecision) -> Unit,
    onForget: (IntentKind, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm)) {
        Text(
            text = "${TalkVoice.kindLabel(wish.kind)}：${wish.term}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = TalkVoice.statusLabel(wish.status),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // 第 66 轮：拒绝过的那一条会多出「改主意」这个按钮，与「不再记着」相邻，
        // 这一段换成 TargetStack；上面两行状态文字不可点，仍留在 gapSm 上。
        TargetStack {
            if (wish.status == WishStatus.DECLINED) {
                SecondaryWideButton(
                    text = TalkVoice.reopenLabel(wish.kind),
                    onClick = { onDecide(wish.kind, wish.term, WishDecision.CONFIRM) },
                )
            }
            SecondaryWideButton(
                text = TalkVoice.forgetThis,
                onClick = { onForget(wish.kind, wish.term) },
            )
        }
    }
}

/** 还没拿主意的一条：问法在这里再出现一次，用户回到这一页也定得下来。 */
@Composable
private fun PendingBlock(
    wish: StoredWish,
    onDecide: (IntentKind, String, WishDecision) -> Unit,
    onForget: (IntentKind, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm)) {
        Text(
            text = "${TalkVoice.kindLabel(wish.kind)}：${wish.term}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        BulletLine(text = TalkVoice.evidenceLine(wish.evidence))
        Text(
            text = TalkVoice.prompt(wish.kind),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // 第 66 轮：这三个按钮是相邻可点目标，整段换 16dp 栈；
        // 上面的标题/证据/问法都不可点，仍按 8dp 留白，不跟着撑开。
        TargetStack {
            PrimaryWideButton(
                text = TalkVoice.confirmLabel(wish.kind),
                onClick = { onDecide(wish.kind, wish.term, WishDecision.CONFIRM) },
            )
            SecondaryWideButton(
                text = TalkVoice.declineLabel(wish.kind),
                onClick = { onDecide(wish.kind, wish.term, WishDecision.DECLINE) },
            )
            SecondaryWideButton(
                text = TalkVoice.forgetThis,
                onClick = { onForget(wish.kind, wish.term) },
            )
        }
    }
}

/** 把用户刚说的那句话原样再念一遍，让他知道是在对哪一句作答。 */
@Composable
private fun EchoLine(sentence: String) {
    Text(
        text = TalkVoice.echoLine(sentence),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 已经记下来的那一条，用来渲染「我听见的」。 */
private fun TalkPageState.wishOf(kind: IntentKind, term: String): StoredWish? =
    remembered.firstOrNull { it.kind == kind && it.term == term }
