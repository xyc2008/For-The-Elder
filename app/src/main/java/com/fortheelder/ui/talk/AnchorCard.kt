package com.fortheelder.ui.talk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.fortheelder.domain.companion.Anchor
import com.fortheelder.domain.companion.CompanionVoice
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SecondaryWideButton
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.theme.ElderDimens

/**
 * 「常伴记着的日常」。
 *
 * 这是第四张卡，回答一个很具体的问题：**我把哪件小事安排进了自己的一天**。
 *
 * ## 什么时候才出现
 *
 * 只有当用户面前确实有起点可选、或者已经记下过至少一条时才出现。这样做的原因是
 * [CompanionVoice.anchorEmpty] 那句话里说的是「您在上面某一步上点…」——上面没有那一步的时候，
 * 这句话就是指着空气说话。宁可整张卡不存在，也不留一句对不上现实的说明。
 *
 * ## 删除就是删除
 *
 * 每条底下的按钮直接删，不弹对话框、不问「确定吗」、不留一条已删的痕迹（AC-09.3）。
 * 一条用户在手机上自己记的小事，不值得为它设一道关卡；想再记一次，回到上面点一遍就行。
 */
@Composable
internal fun AnchorCard(
    page: TalkPageState,
    onForgetAnchor: (String) -> Unit,
) {
    if (page.anchors.isEmpty() && page.offerableWishes.isEmpty()) return

    SectionCard(title = CompanionVoice.anchorCardTitle()) {
        if (page.anchors.isEmpty()) {
            Text(
                text = CompanionVoice.anchorEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            QuietNote(text = CompanionVoice.anchorNote())
            for (anchor in page.anchors) {
                AnchorBlock(anchor = anchor, onForgetAnchor = onForgetAnchor)
            }
        }
    }
}

/** 一条日常：先说记在什么时段，再说那件事是什么，最后留一个删除。 */
@Composable
private fun AnchorBlock(
    anchor: Anchor,
    onForgetAnchor: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapSm)) {
        Text(
            text = CompanionVoice.anchorSlotLabel(anchor.slot),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = CompanionVoice.anchorActionLine(anchor),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SecondaryWideButton(
            text = CompanionVoice.anchorForgetLabel(),
            onClick = { onForgetAnchor(anchor.optionId) },
        )
    }
}
