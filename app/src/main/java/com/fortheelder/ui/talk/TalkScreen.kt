package com.fortheelder.ui.talk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.fortheelder.ui.theme.ElderDimens

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

    ElderScreen(title = TalkVoice.pageTitle, onBack = onBack) {
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
        }

        when (val current = page.heard) {
            TalkUiState.Idle -> Unit
            is TalkUiState.Heard -> HeardCard(current, page, viewModel::decide)
            is TalkUiState.NotUnderstood -> NotUnderstoodCard(current)
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

        SectionCard(title = TalkVoice.examplesSection) {
            for (sentence in TalkExamples.sentences) {
                ActionRow(text = sentence, onClick = { viewModel.hear(sentence) })
            }
        }
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
                PrimaryWideButton(
                    text = TalkVoice.confirmLabel(hit.kind),
                    onClick = { onDecide(hit.kind, hit.term, WishDecision.CONFIRM) },
                )
                SecondaryWideButton(
                    text = TalkVoice.declineLabel(hit.kind),
                    onClick = { onDecide(hit.kind, hit.term, WishDecision.DECLINE) },
                )
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
