package com.fortheelder.ui.talk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortheelder.domain.engine.intent.IntentKind
import com.fortheelder.ui.common.ActionRow
import com.fortheelder.ui.common.BulletLine
import com.fortheelder.ui.common.ElderScreen
import com.fortheelder.ui.common.QuietNote
import com.fortheelder.ui.common.SectionCard
import com.fortheelder.ui.theme.ElderDimens

/**
 * 「说两句」。
 *
 * 这一页只做一件事：让用户看见**常伴听懂了什么、是从哪句话听出来的**。听不明白的时候
 * 就直接说听不明白。
 *
 * 页面上**不提任何建议**：需求和用户都还没确认过这个意愿（docs/01 的 AC-07.1），
 * 刚被听出一句「想学书法」就顺势推课程，正是这个项目要避免的那种推销。
 * 确认闸门在第 8 轮，这一轮先把「听」这件事本身做对、做诚实。
 */
@Composable
fun TalkScreen(onBack: () -> Unit) {
    val viewModel: TalkViewModel = viewModel { TalkViewModel() }
    val state by viewModel.state.collectAsState()

    ElderScreen(title = "说两句", onBack = onBack) {
        SectionCard {
            Text(
                text = "您可以点下面这几句，看看常伴听懂了什么。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            QuietNote(
                text = "常伴只听得懂它学过的那几种说法，也没有联网。" +
                    "听不明白的时候它会直接说听不明白，不会瞎猜。"
            )
        }

        SectionCard(title = "点一句试试") {
            for (sentence in TalkExamples.sentences) {
                ActionRow(text = sentence, onClick = { viewModel.hear(sentence) })
            }
        }

        when (val current = state) {
            TalkUiState.Idle -> Unit
            is TalkUiState.Heard -> HeardCard(current)
            is TalkUiState.NotUnderstood -> NotUnderstoodCard(current)
        }

        QuietNote(text = "常伴现在只是听，还不会替您安排任何事。")
    }
}

/** 听出来了：把听懂的内容和判断依据一起摆出来。 */
@Composable
private fun HeardCard(state: TalkUiState.Heard) {
    SectionCard(title = "我听见的") {
        EchoLine(state.sentence)
        for (hit in state.hits) {
            Column(verticalArrangement = Arrangement.spacedBy(ElderDimens.gapXs)) {
                Text(
                    text = "${hit.kind.label()}：${hit.term}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                BulletLine(text = "是从「${hit.evidence}」这几个字听出来的。")
            }
        }
    }
}

/** 没听出来：明确说出来，并给一条不费力的退路。 */
@Composable
private fun NotUnderstoodCard(state: TalkUiState.NotUnderstood) {
    SectionCard(title = "这句我没听明白") {
        EchoLine(state.sentence)
        Text(
            text = "没关系，换个说法也行，或者点上面别的句子。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        QuietNote(text = "没听懂的时候，常伴不会随便找个话题接着聊。")
    }
}

/** 把用户点的那句话原样再念一遍，让他知道是在对哪一句作答。 */
@Composable
private fun EchoLine(sentence: String) {
    Text(
        text = "您点的是：「$sentence」",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 类别的中文说法。
 *
 * 用「哪一类」而不是「百分比置信度」：这是词表匹配，没有概率可言，报一个 0.87 是编的。
 */
private fun IntentKind.label(): String = when (this) {
    IntentKind.INTEREST -> "想做的事"
    IntentKind.STATE -> "现在的状态"
    IntentKind.REQUEST -> "想找人帮的事"
}
