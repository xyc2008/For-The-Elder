package com.fortheelder.ui.talk

import androidx.lifecycle.ViewModel
import com.fortheelder.domain.engine.intent.IntentHit
import com.fortheelder.domain.engine.intent.IntentScan
import com.fortheelder.domain.engine.intent.IntentScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 「说两句」这一页当前显示什么。 */
sealed interface TalkUiState {
    /** 用户还没点过任何一句话。 */
    data object Idle : TalkUiState

    /** 听出来了：[hits] 至少有一条，按在原文里出现的先后排。 */
    data class Heard(val sentence: String, val hits: List<IntentHit>) : TalkUiState

    /** 没听出来。刻意做成一个独立状态而不是「空列表」，见 [IntentScan] 的说明。 */
    data class NotUnderstood(val sentence: String) : TalkUiState

    companion object {
        fun of(sentence: String, scan: IntentScan): TalkUiState = when (scan) {
            is IntentScan.Recognized -> Heard(sentence, scan.hits)
            IntentScan.NotRecognized -> NotUnderstood(sentence)
        }
    }
}

/**
 * 「说两句」里可以点选的示例话。
 *
 * 为什么要用「点一句」而不是让用户自己打字：一来这是在模拟器上唯一能真实验证的输入方式
 * （`adb shell input text` 送不了中文，见 records/04 的 GAP-02），二来刚退休的人第一次
 * 面对一个会说话的空白输入框，多半会不知道该说什么。点一句的门槛是零。
 *
 * [recognized] 与 [notRecognized] 分开，是为了让单测能对两边都提要求：前四句**必须**
 * 一直被识别出来（否则页面上演示的是坏掉的功能），最后那句**必须**一直识别不出来
 * （否则「它会老实说不知道」这个承诺就没有被展示过）。
 */
object TalkExamples {
    /** 能被识别出来的示例。每加一句都要有对应的单测守着。 */
    val recognized: List<String> = listOf(
        "我一直想学书法",
        "想学用手机",
        "最近老是睡不着",
        "帮我找找附近能下棋的地方",
    )

    /** 故意留一句听不明白的：用户得亲眼看见「不猜」长什么样，才敢信它不猜。 */
    val notRecognized: List<String> = listOf("今天天气还行")

    /** 页面上的点选顺序。 */
    val sentences: List<String> = recognized + notRecognized
}

/**
 * 「说两句」的状态。
 *
 * 这一页**不落盘**：识别结果只活在内存里，退出页面就没了。
 *
 * 为什么现在不存：存下来就要配套「用户能不能删掉、能不能说不算」的出口，那是第 8 轮的
 * 意愿确认闸门（FR-07）要一起做的事。本轮先不做半个——只写进去、删不掉的数据，
 * 对着一个 55 岁的用户是说不通的。
 */
class TalkViewModel : ViewModel() {

    private val mutableState = MutableStateFlow<TalkUiState>(TalkUiState.Idle)

    val state: StateFlow<TalkUiState> = mutableState.asStateFlow()

    /**
     * 用户点了一句示例话。
     *
     * 同步一次算完：识别是纯字符串匹配，没有 IO、没有网络，长句也在微秒级，
     * 做成异步只会多出「转圈」和一帧中间态，反而让用户看不清到底发生了什么。
     */
    fun hear(sentence: String) {
        mutableState.value = TalkUiState.of(sentence, IntentScanner.scan(sentence))
    }
}
