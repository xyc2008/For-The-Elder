package com.fortheelder.ui.talk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fortheelder.domain.companion.AnchorSlot
import com.fortheelder.domain.companion.CompanionVoice
import com.fortheelder.domain.companion.StarterCatalog
import com.fortheelder.domain.companion.StarterOption
import com.fortheelder.ui.common.ActionRow
import com.fortheelder.ui.common.BulletLine
import com.fortheelder.ui.common.PrimaryWideButton
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SecondaryWideButton
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.common.TargetStack
import com.fortheelder.ui.theme.ElderDimens

/**
 * 「可以这样开始」。
 *
 * 这张卡**只在用户确认过某件想做的事之后才出现**（[TalkPageState.offerableWishes]）。
 * 没表态就不推任何东西这条规矩，在这里体现为「整张卡不存在」，而不是一张空卡或一组灰按钮。
 *
 * 每个起点都说清两件事：**大概几分钟**、**要准备什么**。少了这两句，用户没法判断此刻
 * 做不做得到，递过去就是添乱（FR-08）。所以这两个字段不是装饰，是这张卡能不能用的前提。
 *
 * ## 为什么一次只摊开一个
 *
 * 三个起点各配两个按钮，一屏就是八个按钮。对老人来说按钮一多就等于没有按钮——
 * 得先读完八个才能挑一个，读完就不想做了。所以默认只列起点标题，点一下才摊开，
 * 摊开的那一条里才有按钮（[TalkViewModel.toggleOption]）。
 *
 * ## 摊开之后只有三个动作
 *
 * 「这一步做完了」「把这一步记成日常」「先不记」——都是用户点出来的。
 * 没有第四个动作，也没有任何自动发生的动作。
 */
@Composable
internal fun StarterCard(
    page: TalkPageState,
    onToggleOption: (String) -> Unit,
    onMarkDone: (String) -> Unit,
    onProposeAnchor: (String) -> Unit,
    onCancelAnchor: () -> Unit,
    onConfirmAnchor: (String, AnchorSlot) -> Unit,
) {
    val wishes = page.offerableWishes
    if (wishes.isEmpty()) return

    for (wish in wishes) {
        SectionCard(title = CompanionVoice.optionsIntro(wish.term)) {
            QuietNote(text = CompanionVoice.optionsNote())
            for (option in StarterCatalog.byTerm(wish.term)) {
                OptionBlock(
                    option = option,
                    page = page,
                    onToggle = { onToggleOption(option.id) },
                    onMarkDone = { onMarkDone(option.id) },
                    onProposeAnchor = { onProposeAnchor(option.id) },
                    onCancelAnchor = onCancelAnchor,
                    onConfirmAnchor = { slot -> onConfirmAnchor(option.id, slot) },
                )
            }
        }
    }
}

/**
 * 一个起点。
 *
 * 收起时只有一行「标题 + 大概几分钟」：这两样是用户用来挑一个的信息，其余都得摊开才看。
 */
@Composable
private fun OptionBlock(
    option: StarterOption,
    page: TalkPageState,
    onToggle: () -> Unit,
    onMarkDone: () -> Unit,
    onProposeAnchor: () -> Unit,
    onCancelAnchor: () -> Unit,
    onConfirmAnchor: (AnchorSlot) -> Unit,
) {
    val open = page.openOption == option.id
    val recorded = page.anchorOf(option.id)

    Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm)) {
        ActionRow(
            text = "${option.title}　${CompanionVoice.minutesLine(option.minutes)}",
            onClick = onToggle,
            trailing = {
                Icon(
                    imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) TalkVoice.collapseStep else TalkVoice.expandStep,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            },
        )

        if (open) {
            BulletLine(text = option.firstStep)
            BulletLine(text = CompanionVoice.prepLine(option.prep))

            PrimaryWideButton(text = CompanionVoice.doneLabel(), onClick = onMarkDone)

            page.encouragements[option.id]?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            when {
                // 已经记过的不再给「记成日常」：同一步记两次没有任何意义，唯一索引也会挡下来，
                // 与其让按钮点了没反应，不如直接换成一句说清现状的话。
                recorded != null -> QuietNote(text = CompanionVoice.anchorAlreadyLine(recorded.slot))

                // 正在问时段：四个时段按钮 + 一个退路，选哪个都由用户定。
                page.anchorAskFor == option.id -> {
                    Text(
                        text = CompanionVoice.anchorAsk(option.title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // 第 66 轮：四个时段行 + 一个退路是五个相邻的可点目标，点错一格就把这件事
                    // 记到了错的时段上。它们换成 TargetStack；上面那句问法是不可点的正文，留在 gapSm。
                    TargetStack {
                        for (slot in CompanionVoice.confirmableSlots) {
                            ActionRow(
                                text = CompanionVoice.confirmSlotLabel(slot),
                                onClick = { onConfirmAnchor(slot) },
                            )
                        }
                        SecondaryWideButton(
                            text = CompanionVoice.cancelAnchorLabel(),
                            onClick = onCancelAnchor,
                        )
                    }
                }

                else -> SecondaryWideButton(text = CompanionVoice.rememberLabel(), onClick = onProposeAnchor)
            }
        }
    }
}
